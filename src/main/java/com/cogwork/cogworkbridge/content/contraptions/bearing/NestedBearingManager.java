package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform;
import com.cogwork.cogworkbridge.content.contraptions.transform.NestedContraptionPose;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns stabilized bearing children for one parent contraption. World tiles in
 * a FakeWorld only contribute kinetic state; this manager is the sole owner
 * of child entity creation, ticking, synchronization, and restoration.
 */
final class NestedBearingManager {

    static final int MAX_DEPTH = 8;

    private final BearingContraptionEntity owner;
    private final Map<BlockPos, Child> children = new LinkedHashMap<>();

    NestedBearingManager(BearingContraptionEntity owner) {
        this.owner = owner;
    }

    void prepareForCapture() {
        if (!owner.world.isRemote && owner.getMovingWorldChunk() != null) {
            discover();
        }
    }

    void sendSnapshots() {
        for (Child child : new ArrayList<>(children.values())) {
            BearingContraptionEntity entity = child.entity(owner);
            if (entity != null) {
                entity.refreshNestedPosition();
                entity.sendSnapshotToTrackingPlayers();
                entity.sendNestedSnapshots();
            }
        }
    }

    void tick() {
        if (owner.world.isRemote || owner.getContraption() == null
                || owner.getMovingWorldChunk() == null) {
            return;
        }
        if (owner.getNestedDepth() >= MAX_DEPTH) {
            return;
        }
        discover();
        for (Child child : new ArrayList<>(children.values())) {
            child.tick(owner);
        }
    }

    /** Wait until every saved child has an owner before moving the parent to blocks. */
    boolean canReleaseChildren() {
        discover();
        for (Child child : children.values()) {
            BearingContraptionEntity entity = child.entity(owner);
            if (entity == null || !entity.hasContraptionData()
                    || !entity.canReleaseNestedChildren()) {
                return false;
            }
        }
        return true;
    }

    void releaseChildren() {
        int turns = Math.floorMod(Math.round((float)
                ContraptionTransform.quarterTurn(owner.getAngle()) / 90.0F), 4);
        EnumFacing.Axis axis = owner.getFacing().getAxis();
        for (Child child : new ArrayList<>(children.values())) {
            child.release(owner, axis, turns);
        }
        children.clear();
    }

    void reorientChildren(EnumFacing.Axis axis, int turns) {
        discover();
        Map<BlockPos, Child> rotated = new LinkedHashMap<>();
        for (Child child : children.values()) {
            child.reorient(owner, axis, turns);
            rotated.put(child.localPos, child);
        }
        children.clear();
        children.putAll(rotated);
    }

    void detachForEntityUnload() {
        for (Child child : new ArrayList<>(children.values())) {
            child.detachForEntityUnload(owner);
        }
        children.clear();
    }

    void discardChildren() {
        for (Child child : new ArrayList<>(children.values())) {
            child.discard(owner);
        }
        children.clear();
    }

    void propagateStallToChildren() {
        if (owner.world.isRemote) {
            return;
        }
        for (Child child : new ArrayList<>(children.values())) {
            child.stall(owner);
        }
    }

    void writeToNBT(NBTTagCompound root) {
        NBTTagList list = new NBTTagList();
        for (Child child : children.values()) {
            NBTTagCompound tag = child.writeToNBT(owner);
            if (tag != null) {
                list.appendTag(tag);
            }
        }
        root.setTag("NestedBearings", list);
    }

    void persistForSave() {
        for (Child child : children.values()) {
            BearingContraptionEntity entity = child.entity(owner);
            MechanicalBearingTileEntity bearing = child.bearing(owner);
            if (entity == null || bearing == null || !entity.hasContraptionData()) {
                continue;
            }
            entity.persistNestedForSave();
            if (bearing.carriedContraptionSnapshot() != entity.getContraption()) {
                bearing.syncSavedContraption(entity.getContraption());
            }
            owner.persistNestedBearingTile(child.localPos);
        }
    }

    boolean preparePortablePickup() {
        discover();
        for (Child child : new ArrayList<>(children.values())) {
            BearingContraptionEntity entity = child.entity(owner);
            MechanicalBearingTileEntity bearing = child.bearing(owner);
            if (entity == null || bearing == null || !entity.preparePortablePickup()) return false;
            bearing.syncSavedContraption(entity.getContraption());
            owner.persistNestedBearingTile(child.localPos);
        }
        return true;
    }

    void readFromNBT(NBTTagCompound root) {
        children.clear();
        NBTTagList list = root.getTagList("NestedBearings", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            Child child = Child.readFromNBT(list.getCompoundTagAt(i));
            if (child != null) {
                children.put(child.localPos, child);
            }
        }
    }

    private void discover() {
        java.util.Set<BlockPos> found = new java.util.LinkedHashSet<>();
        for (TileEntity tile :
                new ArrayList<>(owner.getMovingWorldChunk().chunkTileEntityMap.values())) {
            if (tile instanceof MechanicalBearingTileEntity bearing) {
                BlockPos localPos = tile.getPos().toImmutable();
                found.add(localPos);
                if (!children.containsKey(localPos)) {
                    Child child = Child.create(owner, localPos, bearing);
                    if (child != null) {
                        children.put(localPos, child);
                    }
                }
            }
        }
        bindLoadedChildren(found);
    }

    private void bindLoadedChildren(java.util.Set<BlockPos> found) {
        java.util.Iterator<Map.Entry<BlockPos, Child>> iterator =
                children.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Child> entry = iterator.next();
            Child child = entry.getValue();
            if (!found.contains(entry.getKey())) {
                child.discard(owner);
                iterator.remove();
                continue;
            }
            MechanicalBearingTileEntity bearing = child.bearing(owner);
            if (bearing == null || !bearing.isCarriedAssembled()
                    || bearing.carriedContraptionSnapshot() == null) {
                child.discard(owner);
                iterator.remove();
                continue;
            }
            child.bindOrSpawn(owner, bearing);
        }
    }

    static NestedBearingBinding.State classify(
            boolean explicitRestore, UUID expectedEntityId,
            BearingContraptionEntity entityRef, boolean snapshotAvailable) {
        return NestedBearingBinding.classify(
                explicitRestore, expectedEntityId != null,
                entityRef != null && !entityRef.isDead,
                snapshotAvailable);
    }

    java.util.List<BearingContraptionEntity> railwayChildren() {
        java.util.List<BearingContraptionEntity> result=new ArrayList<>();
        for(Child child:children.values()) {
            BearingContraptionEntity entity=child.entity(owner);
            if(entity!=null&&!entity.isDead)result.add(entity);
        }
        return result;
    }
    BearingContraptionEntity railwayChildAt(BlockPos local) {
        Child child=children.get(local);return child==null?null:child.entity(owner);
    }
    void registerRailwayChild(BlockPos local,BearingContraptionEntity entity) {
        Child child=new Child(local,entity.getFacing(),entity.getUniqueID(),entity.getAngle(),entity.getPreviousAngle(),0,false);
        child.entityRef=entity;child.expectedEntityId=entity.getUniqueID();children.put(local.toImmutable(),child);
    }
    void discardRailwayChildren() {
        for(BearingContraptionEntity child:railwayChildren())child.discardRailwayPortalCopy();
        children.clear();
    }

    static final class Child {
        private BlockPos localPos;
        private EnumFacing facing;
        private UUID entityId;
        private UUID expectedEntityId;
        private BearingContraptionEntity entityRef;
        private float angle;
        private float previousAngle;
        private float speed;
        private boolean stalled;
        private Float stabilizationOffset;

        private Child(BlockPos localPos, EnumFacing facing, UUID entityId,
                      float angle, float previousAngle, float speed,
                      boolean stalled) {
            this.localPos = localPos.toImmutable();
            this.facing = facing;
            this.entityId = entityId;
            this.angle = angle;
            this.previousAngle = previousAngle;
            this.speed = speed;
            this.stalled = stalled;
        }

        private static Child create(BearingContraptionEntity owner,
                                    BlockPos localPos,
                                    MechanicalBearingTileEntity bearing) {
            if (!bearing.isCarriedAssembled()
                    || bearing.carriedContraptionSnapshot() == null) {
                return null;
            }
            IBlockState state = owner.getMovingWorldChunk().getBlockState(localPos);
            if (!(state.getBlock() instanceof MechanicalBearingBlock)
                    || !state.getPropertyKeys().contains(MechanicalBearingBlock.FACING)) {
                return null;
            }
            EnumFacing childFacing = state.getValue(MechanicalBearingBlock.FACING);
            UUID expected = bearing.getCarriedContraptionEntityUuid();
            Child child = new Child(localPos, childFacing, expected,
                    bearing.getCarriedAngleDegrees(),
                    bearing.getCarriedPreviousAngleDegrees(), 0.0F, false);
            Float levelAngle = child.levelAngle(owner);
            if (levelAngle != null) {
                child.stabilizationOffset = (float) ContraptionTransform.shortestDelta(
                        levelAngle, child.angle);
            }
            child.expectedEntityId = expected;
            BearingContraptionEntity recorded =
                    expected == null ? null
                            : BearingContraptionEntity.findByUuid(owner.world, expected);
            if (recorded != null && !recorded.isDead) {
                child.entityRef = recorded;
            }
            child.bindOrSpawn(owner, bearing);
            return child;
        }

        private static Child readFromNBT(NBTTagCompound tag) {
            if (!tag.hasKey("LocalX") || !tag.hasKey("Facing")) {
                return null;
            }
            BlockPos local = new BlockPos(
                    tag.getInteger("LocalX"),
                    tag.getInteger("LocalY"),
                    tag.getInteger("LocalZ"));
            EnumFacing facing = EnumFacing.byIndex(tag.getByte("Facing"));
            UUID id = tag.hasUniqueId("Entity") ? tag.getUniqueId("Entity") : null;
            Child child = new Child(local, facing, id,
                    tag.getFloat("Angle"), tag.getFloat("PreviousAngle"),
                    tag.getFloat("Speed"), tag.getBoolean("Stalled"));
            if (tag.hasKey("StabilizationOffset")) {
                child.stabilizationOffset = tag.getFloat("StabilizationOffset");
            }
            child.expectedEntityId = id;
            return child;
        }

        private NBTTagCompound writeToNBT(BearingContraptionEntity parent) {
            BearingContraptionEntity entity = entity(parent);
            if (entity != null && entity.getContraption() != null) {
                angle = entity.getAngle();
                previousAngle = entity.getPreviousAngle();
                speed = entity.getSpeed();
                stalled = entity.isStalled();
                entityId = entity.getUniqueID();
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("LocalX", localPos.getX());
            tag.setInteger("LocalY", localPos.getY());
            tag.setInteger("LocalZ", localPos.getZ());
            tag.setByte("Facing", (byte) facing.getIndex());
            tag.setFloat("Angle", angle);
            tag.setFloat("PreviousAngle", previousAngle);
            tag.setFloat("Speed", speed);
            tag.setBoolean("Stalled", stalled);
            if (stabilizationOffset != null) {
                tag.setFloat("StabilizationOffset", stabilizationOffset);
            }
            if (entityId != null) {
                tag.setUniqueId("Entity", entityId);
            }
            return tag;
        }

        private void tick(BearingContraptionEntity parent) {
            MechanicalBearingTileEntity bearing = bearing(parent);
            BearingContraptionEntity child = entity(parent);
            if (bearing == null) {
                return;
            }
            if (child == null || !child.hasContraptionData()) {
                if (bearing.isCarriedAssembled()
                        && bearing.carriedContraptionSnapshot() != null) {
                    bindOrSpawn(parent, bearing);
                }
                return;
            }
            float requestedSpeed = bearing.getSpeed();
            boolean driven = requestedSpeed != 0.0F;
            Float levelAngle = levelAngle(parent);
            float requestedAngle;
            if (!driven && levelAngle != null) {
                if (stabilizationOffset == null) {
                    stabilizationOffset = (float) ContraptionTransform.shortestDelta(
                            levelAngle, angle);
                }
                requestedAngle = (float) ContraptionTransform.normalize(
                        levelAngle + stabilizationOffset);
                requestedSpeed = (float) (ContraptionTransform.shortestDelta(
                        angle, requestedAngle) / 0.3D);
            } else {
                requestedAngle = (float) ContraptionTransform.advance(
                        angle, requestedSpeed);
            }
            speed = requestedSpeed;
            child.setSpeed(requestedSpeed);
            boolean advanced = child.advanceToward(requestedAngle, requestedSpeed);
            angle = child.getAngle();
            previousAngle = child.getPreviousAngle();
            if (levelAngle == null) {
                stabilizationOffset = null;
            } else if (driven) {
                stabilizationOffset = (float) ContraptionTransform.shortestDelta(
                        levelAngle, angle);
            }
            stalled = !advanced && requestedSpeed != 0.0F;
            bearing.setCarriedRotation(angle, 0.0F, stalled);
            child.sendPoseToTrackingPlayers();
            if (stalled) {
                parent.setStalled(true);
            }
        }

        private Float levelAngle(BearingContraptionEntity parent) {
            ContraptionTransform.Point up = parent.worldUpInLocalCoordinates();
            double sine;
            double cosine = up.y();
            switch (facing.getAxis()) {
                case X -> sine = up.z();
                case Z -> sine = -up.x();
                default -> {
                    return null;
                }
            }
            if (sine * sine + cosine * cosine < 1.0E-10D) {
                return null;
            }
            return (float) ContraptionTransform.normalize(
                    Math.toDegrees(Math.atan2(sine, cosine)));
        }

        private void spawn(BearingContraptionEntity parent) {
            MechanicalBearingTileEntity bearing = bearing(parent);
            if (bearing == null || bearing.carriedContraptionSnapshot() == null) {
                return;
            }
            if (parent.getNestedDepth() + 1 > MAX_DEPTH) {
                return;
            }
            if (claimExisting(parent, bearing)) {
                return;
            }
            BearingContraptionEntity entity = new BearingContraptionEntity(
                    parent.world, parent, localPos, facing,
                    bearing.carriedContraptionSnapshot()
                            .translatedTo(localPos.offset(facing)),
                    new NestedContraptionPose(localPos, facing, angle, speed, stalled));
            entity.prepareNestedChildren();
            entityRef = entity;
            entityId = entity.getUniqueID();
            expectedEntityId = entityId;
            stalled = false;
            boolean spawned = parent.world.spawnEntity(entity);
            if (!spawned) {
                entityRef = null;
                entityId = null;
                expectedEntityId = null;
                return;
            } else {
                bearing.bindCarriedContraptionEntity(entity);
                bearing.syncSavedContraption(entity.getContraption());
                parent.persistNestedBearingTile(localPos);
            }
            entity.sendSnapshotToTrackingPlayers();
        }

        private void discard(BearingContraptionEntity parent) {
            BearingContraptionEntity entity = entity(parent);
            if (entity != null) {
                entity.discardWithoutRestore();
            }
            entityId = null;
            expectedEntityId = null;
            entityRef = null;
        }

        private void detachForEntityUnload(BearingContraptionEntity parent) {
            BearingContraptionEntity entity = entity(parent);
            if (entity != null) {
                entity.setParentContraption(parent, localPos);
            }
            // Keep the UUID persisted in the parent structure. Never restore
            // child blocks merely because the parent entity left the world.
        }

        private void stall(BearingContraptionEntity parent) {
            stalled = true;
            BearingContraptionEntity child = entity(parent);
            if (child != null) {
                child.setStalled(true);
            }
            MechanicalBearingTileEntity bearing = bearing(parent);
            if (bearing != null) {
                bearing.setCarriedRotation(bearing.getCarriedAngleDegrees(),
                        0.0F, true);
            }
        }

        private void bindOrSpawn(BearingContraptionEntity parent,
                                 MechanicalBearingTileEntity bearing) {
            if (!claimExisting(parent, bearing)
                    && classify(false, entityId != null ? entityId : expectedEntityId,
                            null, bearing.carriedContraptionSnapshot() != null)
                    == NestedBearingBinding.State.NEEDS_SPAWN) {
                spawn(parent);
            }
        }

        private boolean claimExisting(
                BearingContraptionEntity parent,
                MechanicalBearingTileEntity bearing) {
            BearingContraptionEntity candidate = findCandidate(parent);
            if (candidate == null || !candidate.hasContraptionData()) {
                return false;
            }
            UUID expectedBeforeBinding = entityId != null
                    ? entityId : expectedEntityId;
            if (!NestedBearingBinding.sameEntity(expectedBeforeBinding, candidate.getUniqueID())
                    || candidate.getParentContraptionUuid() != null
                    && !candidate.getParentContraptionUuid().equals(parent.getUniqueID())) {
                return false;
            }
            NestedBearingBinding.State state = classify(
                    false, expectedBeforeBinding, candidate,
                    bearing.carriedContraptionSnapshot() != null);
            if (state == NestedBearingBinding.State.WAITING_FOR_ENTITY) {
                return false;
            }
            if (candidate.getParentContraption() == parent
                    && localPos.equals(candidate.getParentLocalBearingPos())
                    && candidate.getEntityId() == bearing.getCarriedContraptionEntityId()
                    && candidate.getUniqueID().equals(bearing.getCarriedContraptionEntityUuid())
                    && bearing.carriedContraptionSnapshot() == candidate.getContraption()) {
                entityRef = candidate;
                entityId = candidate.getUniqueID();
                expectedEntityId = entityId;
                return true;
            }
            candidate.setParentContraption(parent, localPos);
            if (!parent.getUniqueID().equals(candidate.getParentContraptionUuid())
                    || !NestedBearingBinding.sameEntity(
                    expectedBeforeBinding, candidate.getUniqueID())) {
                return false;
            }
            entityRef = candidate;
            entityId = candidate.getUniqueID();
            expectedEntityId = entityId;
            bearing.bindCarriedContraptionEntity(candidate);
            bearing.syncSavedContraption(candidate.getContraption());
            angle = candidate.getAngle();
            previousAngle = candidate.getPreviousAngle();
            speed = candidate.getSpeed();
            stalled = candidate.isStalled();
            bearing.setCarriedRotation(angle, speed, stalled);
            parent.persistNestedBearingTile(localPos);
            return true;
        }

        private void release(BearingContraptionEntity parent,
                             EnumFacing.Axis axis, int turns) {
            BearingContraptionEntity entity = entity(parent);
            MechanicalBearingTileEntity bearing = bearing(parent);
            if (entity == null || bearing == null) {
                throw new IllegalStateException("Unresolved nested bearing during release");
            }
            Vec3d center = parent.localBlockCenterToWorldAtAngle(localPos,
                    ContraptionTransform.quarterTurn(parent.getAngle()));
            BlockPos worldBearing = new BlockPos(center);
            EnumFacing worldFacing = BearingContraption.rotateFacing(facing, axis, turns);
            entity.reorientForRelease(worldBearing, worldFacing, axis, turns);
            bearing.syncSavedContraption(entity.getContraption());
            bearing.bindCarriedContraptionEntity(entity);
            bearing.setCarriedRotation(entity.getAngle(), entity.getSpeed(), entity.isStalled());
            bearing.releaseAutomaticCarriedAssembly();
            parent.persistNestedBearingTile(localPos);
            entity.sendSnapshotToTrackingPlayers();
            entity.sendNestedSnapshots();
        }

        private void reorient(BearingContraptionEntity parent,
                              EnumFacing.Axis axis, int turns) {
            BearingContraptionEntity entity = entity(parent);
            MechanicalBearingTileEntity bearing = bearing(parent);
            if (entity == null || bearing == null) {
                throw new IllegalStateException("Unresolved nested bearing during reorientation");
            }
            QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(
                    new QuarterTurnGrid.GridPos3(localPos.getX(), localPos.getY(), localPos.getZ()),
                    QuarterTurnGrid.Axis.valueOf(axis.name()), turns);
            BlockPos nextLocal = new BlockPos(rotated.x(), rotated.y(), rotated.z());
            EnumFacing nextFacing = BearingContraption.rotateFacing(facing, axis, turns);
            entity.reorientWithinParent(parent, nextLocal, nextFacing, axis, turns);
            bearing.syncSavedContraption(entity.getContraption());
            bearing.bindCarriedContraptionEntity(entity);
            bearing.setCarriedRotation(entity.getAngle(), entity.getSpeed(), entity.isStalled());
            parent.persistNestedBearingTile(localPos);
            localPos = nextLocal;
            facing = nextFacing;
            stabilizationOffset = null;
            angle = entity.getAngle();
            previousAngle = entity.getPreviousAngle();
            speed = entity.getSpeed();
        }

        private BearingContraptionEntity findCandidate(
                BearingContraptionEntity parent) {
            BearingContraptionEntity candidate = entity(parent);
            if (candidate != null) {
                return candidate;
            }
            UUID expected = entityId != null ? entityId : expectedEntityId;
            if (expected == null) {
                return null;
            }
            candidate = BearingContraptionEntity.findByUuid(parent.world, expected);
            if (candidate == null) {
                return null;
            }
            return candidate;
        }

        private MechanicalBearingTileEntity bearing(
                BearingContraptionEntity parent) {
            if (parent.getMovingWorldChunk() == null) {
                return null;
            }
            TileEntity tile =
                    parent.getMovingWorldChunk().getTileEntity(localPos);
            return tile instanceof MechanicalBearingTileEntity bearing
                    ? bearing : null;
        }

        private BearingContraptionEntity entity(
                BearingContraptionEntity parent) {
            if (entityRef != null
                    && !entityRef.isDead) {
                return entityRef;
            }
            entityRef = null;
            if (entityId == null) {
                return null;
            }
            for (BearingContraptionEntity candidate :
                    parent.world.getEntities(BearingContraptionEntity.class,
                            ignored -> true)) {
                if (!candidate.isDead
                        && entityId.equals(candidate.getUniqueID())
                        && (candidate.getParentContraption() == parent
                        || candidate.getParentContraptionUuid() != null
                        && candidate.getParentContraptionUuid()
                        .equals(parent.getUniqueID()))) {
                    return candidate;
                }
            }
            return null;
        }

    }
}
