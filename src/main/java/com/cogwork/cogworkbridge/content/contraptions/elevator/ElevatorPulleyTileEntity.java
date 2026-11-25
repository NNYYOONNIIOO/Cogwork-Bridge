package com.cogwork.cogworkbridge.content.contraptions.elevator;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionGuiProxy;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionEntityReference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.CarriedBearingAssembly;
import com.cogwork.cogworkbridge.content.contraptions.bearing.LinearContraptionController;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Server-authoritative floor selection and vertical motion of a captured structure. */
public final class ElevatorPulleyTileEntity extends KineticTileEntity implements LinearContraptionController, com.cogwork.cogworkbridge.content.redstone.ThresholdObservable {
    private static final int MAX_LENGTH = 384;
    private UUID contraptionId;
    private final ContraptionEntityReference contraptionEntity=new ContraptionEntityReference();
    private BlockPos mirrorParent;
    private final List<BlockPos> mirrorChildren = new ArrayList<>();
    private BlockPos captureAnchor = BlockPos.ORIGIN;
    private BlockPos column = BlockPos.ORIGIN;
    private EnumFacing columnFacing = EnumFacing.NORTH;
    private int contactRelativeY;
    private int targetFloorY;
    private int selectedFloor;
    private float offset, previousOffset, movement;

    public boolean isRunning() { return contraptionId != null || mirrorParent != null; }
    @Override public int thresholdMinimum() { return 0; }
    @Override public int thresholdMaximum() { return pos.getY(); }
    @Override public int thresholdValue() { return pos.getY() - (int) getCableOffset(.5F); }
    @Override public boolean ownsLinearContraption(UUID id) { return id != null && id.equals(contraptionId); }
    public float getCableOffset(float partial) {
        if (mirrorParent != null && world != null && world.isBlockLoaded(mirrorParent)
                && world.getTileEntity(mirrorParent) instanceof ElevatorPulleyTileEntity parent && parent != this)
            return parent.getCableOffset(partial);
        BearingContraptionEntity entity = findEntity();
        if (entity != null) {
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial;
            return (float) Math.max(0, pos.getY() - y - 1);
        }
        return previousOffset + (offset - previousOffset) * partial;
    }
    @Override public float getStressImpact() { return 4F; }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).expand(0, -MAX_LENGTH - 1, 0); }

    public void clicked() {
        if (world == null || world.isRemote || world instanceof FakeWorld) return;
        if (mirrorParent != null) {
            if (world.isBlockLoaded(mirrorParent)
                    && world.getTileEntity(mirrorParent) instanceof ElevatorPulleyTileEntity parent && parent != this)
                parent.clicked();
            return;
        }
        if (isRunning()) disassemble(); else assemble();
    }

    public void removePulley() {
        if (mirrorParent == null) { disassemble(); return; }
        if (world.isBlockLoaded(mirrorParent)
                && world.getTileEntity(mirrorParent) instanceof ElevatorPulleyTileEntity parent) {
            parent.mirrorChildren.remove(pos);
            parent.markDirty();
        }
        mirrorParent = null;
    }

    private void assemble() {
        if (getSpeed() == 0F) return;
        BlockPos anchor = null;
        for (int length = 1; length <= MAX_LENGTH && pos.getY() - length >= 0; length++) {
            BlockPos candidate = pos.down(length);
            if (!world.isBlockLoaded(candidate)) return;
            IBlockState state = world.getBlockState(candidate);
            if (!state.getBlock().isReplaceable(world, candidate)
                    && state.getCollisionBoundingBox(world, candidate) != null) { anchor = candidate; break; }
        }
        if (anchor == null) return;
        BearingContraption structure = BearingContraption.capture(world, pos, anchor, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        if (structure == null) return;
        BearingContraption.Snapshot mobileContact = null;
        for (BearingContraption.Snapshot snapshot : structure.blocks().values()) {
            if (!(snapshot.state().getBlock() instanceof RedstoneContactBlock)) continue;
            EnumFacing facing = snapshot.state().getValue(RedstoneContactBlock.FACING);
            if (!facing.getAxis().isHorizontal()) continue;
            if (mobileContact != null) return;
            mobileContact = snapshot;
        }
        if (mobileContact == null) return;
        List<ElevatorPulleyTileEntity> mirrors = findMirrors(structure, anchor);
        columnFacing = mobileContact.state().getValue(RedstoneContactBlock.FACING).getOpposite();
        column = mobileContact.pos().offset(columnFacing.getOpposite()).toImmutable();
        for (TileEntity tile : new ArrayList<>(world.loadedTileEntityList))
            if (tile instanceof ElevatorPulleyTileEntity other && other != this && other.isRunning()
                    && other.column.getX() == column.getX() && other.column.getZ() == column.getZ()
                    && other.columnFacing == columnFacing) return;
        contactRelativeY = mobileContact.pos().getY() - anchor.getY();
        captureAnchor = anchor.toImmutable();
        targetFloorY = mobileContact.pos().getY();
        // Keep the cabin's contact as an elevator contact in its captured
        // structure, while preserving its facing and tile data on restoration.
        structure.replaceAtLocal(mobileContact.pos().subtract(structure.anchor()),
                AllBlocks.ELEVATOR_CONTACT.getDefaultState().withProperty(RedstoneContactBlock.FACING,
                        mobileContact.state().getValue(RedstoneContactBlock.FACING)), mobileContact.tileData());
        CarriedBearingAssembly carried = CarriedBearingAssembly.prepare(world, structure, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        BearingContraptionEntity entity = BearingContraptionEntity.createElevator(world, pos, anchor, structure);
        entity.prepareNestedChildren();
        if (!world.spawnEntity(entity)) return;
        com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(entity, structure);
        carried.removeFromWorld();
        structure.removeFromWorld(world, true);
        contraptionId = entity.getUniqueID();
        mirrorChildren.clear();
        for (ElevatorPulleyTileEntity mirror : mirrors) {
            mirror.mirrorParent = pos.toImmutable();
            mirror.offset = mirror.previousOffset = pos.getY() - anchor.getY() - 1;
            mirror.markDirty();
            mirror.sync();
            mirrorChildren.add(mirror.getPos().toImmutable());
        }
        gatherColumnContacts();
        List<RedstoneContactTileEntity> availableFloors = floors();
        selectedFloor = 0;
        for (int i = 0; i < availableFloors.size(); i++)
            if (availableFloors.get(i).getPos().getY() == targetFloorY) selectedFloor = i;
        offset = previousOffset = pos.getY() - anchor.getY() - 1;
        movement = 0;
        entity.sendSnapshotToTrackingPlayers();
        entity.sendNestedSnapshots();
        markDirty(); sync();
    }

    private List<ElevatorPulleyTileEntity> findMirrors(BearingContraption structure, BlockPos anchor) {
        List<ElevatorPulleyTileEntity> mirrors = new ArrayList<>();
        for (BearingContraption.Snapshot block : structure.blocks().values()) {
            BlockPos support = block.pos();
            if (support.getY() != anchor.getY() || structure.blocks().containsKey(support.up())) continue;
            BlockPos otherPos = new BlockPos(support.getX(), pos.getY(), support.getZ());
            if (otherPos.equals(pos) || !world.isBlockLoaded(otherPos)
                    || !(world.getTileEntity(otherPos) instanceof ElevatorPulleyTileEntity other) || other.isRunning()) continue;
            boolean clear = true;
            for (int y = anchor.getY() + 1; y < pos.getY(); y++) {
                BlockPos segment = new BlockPos(otherPos.getX(), y, otherPos.getZ());
                IBlockState state = world.getBlockState(segment);
                if (!world.isBlockLoaded(segment) || !state.getBlock().isReplaceable(world, segment)
                        && state.getCollisionBoundingBox(world, segment) != null) { clear = false; break; }
            }
            if (clear) mirrors.add(other);
        }
        return mirrors;
    }

    private void releaseMirrors() {
        for (BlockPos child : mirrorChildren) {
            if (!world.isBlockLoaded(child) || !(world.getTileEntity(child) instanceof ElevatorPulleyTileEntity mirror)
                    || !pos.equals(mirror.mirrorParent)) continue;
            mirror.mirrorParent = null;
            mirror.offset = mirror.previousOffset = 0F;
            mirror.markDirty();
            mirror.sync();
        }
        mirrorChildren.clear();
    }

    public void disassemble() {
        BearingContraptionEntity entity = findEntity();
        if (entity == null) return;
        float nearest = Math.round(entity.getVerticalOffset());
        if (Math.abs(entity.getVerticalOffset() - nearest) > .001F && !entity.moveElevator(nearest)) return;
        entity.restoreAndDiscard();
        if (!entity.isDead) return;
        contraptionId = null;
        movement = 0;
        offset = previousOffset = 0;
        markDirty(); sync();
    }

    public void onContraptionRestored(UUID id) {
        if (!id.equals(contraptionId)) return;
        releaseMirrors();
        contraptionId = null;
        movement = 0F;
        offset = previousOffset = 0F;
        markDirty();
        sync();
    }

    private BearingContraptionEntity findEntity() {
        BearingContraptionEntity entity=contraptionEntity.get(world,contraptionId);
        return entity!=null&&entity.isElevator()&&pos.equals(entity.getElevatorController())?entity:null;
    }

    private List<RedstoneContactTileEntity> floors() {
        List<RedstoneContactTileEntity> result = new ArrayList<>();
        if (world == null) return result;
        for (TileEntity tile : new ArrayList<>(world.loadedTileEntityList)) {
            if (!(tile instanceof RedstoneContactTileEntity contact)) continue;
            BlockPos p = tile.getPos();
            IBlockState state = world.getBlockState(p);
            if (p.getX() == column.getX() && p.getZ() == column.getZ()
                    && state.getBlock() == AllBlocks.ELEVATOR_CONTACT
                    && state.getValue(RedstoneContactBlock.FACING) == columnFacing
                    && p.getY() - contactRelativeY < pos.getY()
                    && pos.getY() - (p.getY() - contactRelativeY) <= MAX_LENGTH + 1)
                result.add(contact);
        }
        result.sort(Comparator.comparingInt(c -> c.getPos().getY()));
        return result;
    }

    /** Like the upstream column, turn matching ordinary contacts into floor anchors. */
    private void gatherColumnContacts() {
        int minY = Math.max(0, pos.getY() - MAX_LENGTH - 1 + contactRelativeY);
        int maxY = Math.min(world.getHeight() - 1, pos.getY() - 1 + contactRelativeY);
        for (int y = minY; y <= maxY; y++) {
            BlockPos floor = new BlockPos(column.getX(), y, column.getZ());
            if (!world.isBlockLoaded(floor)) continue;
            IBlockState state = world.getBlockState(floor);
            if (state.getBlock() != AllBlocks.REDSTONE_CONTACT
                    || state.getValue(RedstoneContactBlock.FACING) != columnFacing) continue;
            TileEntity old = world.getTileEntity(floor);
            NBTTagCompound saved = old == null ? null : old.writeToNBT(new NBTTagCompound());
            world.setBlockState(floor, AllBlocks.ELEVATOR_CONTACT.getDefaultState()
                    .withProperty(RedstoneContactBlock.FACING, columnFacing), 3);
            if (saved != null && world.getTileEntity(floor) instanceof RedstoneContactTileEntity replacement) {
                replacement.readFromNBT(saved);
                replacement.markDirty();
            }
        }
    }

    public void selectFloor(int delta, net.minecraft.entity.player.EntityPlayer player) {
        List<RedstoneContactTileEntity> floors = floors();
        if (floors.isEmpty()) return;
        selectedFloor = Math.max(0, Math.min(floors.size() - 1, selectedFloor + Integer.signum(delta)));
        markDirty(); sync();
    }

    @javax.annotation.Nullable
    public RedstoneContactTileEntity getSelectedFloor() {
        List<RedstoneContactTileEntity> available = floors();
        return available.isEmpty() ? null : available.get(Math.max(0, Math.min(selectedFloor, available.size() - 1)));
    }

    public void confirmFloor() {
        List<RedstoneContactTileEntity> floors = floors();
        if (!floors.isEmpty()) {
            selectedFloor = Math.max(0, Math.min(selectedFloor, floors.size() - 1));
            targetFloorY = floors.get(selectedFloor).getPos().getY();
            markDirty();
        }
    }

    public boolean isAtFloor(int floorY) {
        if (contraptionId == null || targetFloorY != floorY) return false;
        BearingContraptionEntity entity = findEntity();
        if (entity == null) return false;
        float goal = targetFloorY - contactRelativeY - captureAnchor.getY();
        return Math.abs(goal - entity.getVerticalOffset()) < .001F;
    }

    @Override public void update() {
        previousOffset = offset;
        super.update();
        if (world == null || world.isRemote || world instanceof FakeWorld) return;
        if (mirrorParent != null) {
            if (!world.isBlockLoaded(mirrorParent)) return;
            if (world.getTileEntity(mirrorParent) instanceof ElevatorPulleyTileEntity parent
                    && parent.contraptionId != null && parent.mirrorChildren.contains(pos)) {
                if (Float.compare(offset, parent.offset) != 0) {
                    offset = parent.offset;
                    markDirty();
                    sync();
                }
            } else {
                mirrorParent = null;
                offset = previousOffset = 0F;
                markDirty();
                sync();
            }
            return;
        }
        if (contraptionId == null) return;
        BearingContraptionEntity entity = findEntity();
        if (entity == null) return; // An unloaded structure must never be recreated or discarded.
        if (world.getTotalWorldTime() % 20L == 0L) gatherColumnContacts();
        for (RedstoneContactTileEntity floor : floors()) if (floor.isCalling()) {
            targetFloorY = floor.getPos().getY();
            break;
        }
        float goal = targetFloorY - contactRelativeY - captureAnchor.getY();
        float difference = goal - entity.getVerticalOffset();
        float limit = Math.min(1.99F, Math.abs(getSpeed()) / 256F);
        float acceleration = .0075F + Math.min(1F, limit) * .01F;
        float desired = Math.copySign(Math.min(Math.min(Math.abs(difference), limit),
                (float) Math.sqrt(2 * Math.abs(difference) * acceleration)), difference);
        movement += Math.max(-acceleration, Math.min(acceleration, desired - movement));
        if (limit == 0F) movement = 0F;
        if (Math.abs(difference) < .0001F) movement = 0F;
        if (movement != 0F) {
            float next = entity.getVerticalOffset() + Math.copySign(Math.min(Math.abs(movement), Math.abs(difference)), difference);
            if (!entity.moveElevator(next)) movement = 0F;
        }
        float cable = pos.getY() - captureAnchor.getY() - 1 - entity.getVerticalOffset();
        if (Float.compare(cable, offset) != 0) { offset = cable; markDirty(); sync(); }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (contraptionId != null) tag.setUniqueId("ElevatorContraption", contraptionId);
        if (mirrorParent != null) tag.setLong("MirrorParent", mirrorParent.toLong());
        NBTTagList children = new NBTTagList();
        for (BlockPos child : mirrorChildren) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("Pos", child.toLong());
            children.appendTag(entry);
        }
        tag.setTag("MirrorChildren", children);
        tag.setLong("CaptureAnchor", captureAnchor.toLong()); tag.setLong("Column", column.toLong());
        tag.setByte("ColumnFacing", (byte) columnFacing.getIndex()); tag.setInteger("ContactY", contactRelativeY);
        tag.setInteger("TargetFloorY", targetFloorY); tag.setInteger("SelectedFloor", selectedFloor);
        tag.setFloat("CableOffset", offset); tag.setFloat("Movement", movement);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        contraptionId = tag.hasUniqueId("ElevatorContraption") ? tag.getUniqueId("ElevatorContraption") : null;
        mirrorParent = tag.hasKey("MirrorParent") ? BlockPos.fromLong(tag.getLong("MirrorParent")) : null;
        mirrorChildren.clear();
        NBTTagList children = tag.getTagList("MirrorChildren", 10);
        for (int i = 0; i < children.tagCount(); i++) mirrorChildren.add(BlockPos.fromLong(children.getCompoundTagAt(i).getLong("Pos")));
        captureAnchor = BlockPos.fromLong(tag.getLong("CaptureAnchor")); column = BlockPos.fromLong(tag.getLong("Column"));
        columnFacing = EnumFacing.byIndex(tag.getByte("ColumnFacing")); contactRelativeY = tag.getInteger("ContactY");
        targetFloorY = tag.getInteger("TargetFloorY"); selectedFloor = tag.getInteger("SelectedFloor");
        offset = previousOffset = tag.getFloat("CableOffset"); movement = tag.getFloat("Movement");
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        tag.setFloat("Speed", rotation.getSpeed()); tag.setByte("Axis", (byte) rotation.getAxis().ordinal());
        return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        float oldOffset = offset;
        readFromNBT(tag);
        super.handleUpdateTag(tag);
        previousOffset = oldOffset;
    }
}
