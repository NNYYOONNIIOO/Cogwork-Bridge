package com.cogwork.cogworkbridge.content.contraptions.pulley;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.CarriedBearingAssembly;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionGuiProxy;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionEntityReference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.LinearContraptionController;
import com.cogwork.cogworkbridge.content.contraptions.bearing.SequencedTravel;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Rope length is the actuator position; the captured structure owns its own snapshot. */
public final class RopePulleyTileEntity extends KineticTileEntity implements LinearContraptionController, com.cogwork.cogworkbridge.content.redstone.ThresholdObservable {
    private static final int MAX_LENGTH = 384;
    private UUID contraptionId;
    private final ContraptionEntityReference contraptionEntity=new ContraptionEntityReference();
    private final SequencedTravel sequencedTravel = new SequencedTravel();
    private float cableOffset, previousOffset, previousSpeed = Float.NaN;
    private int initialLength;
    private boolean running, needsContraption, requestAssembly, removing;
    private BlockPos mirrorParent;
    private final List<BlockPos> mirrorChildren = new ArrayList<>();
    private final Map<BlockPos, IBlockState> displacedWater = new LinkedHashMap<>();
    // 0: always place, 1: only at starting length, 2: never place.
    private int movementMode;

    public boolean isRunning() { return running; }
    @Override public int thresholdMinimum() { return 0; }
    @Override public int thresholdMaximum() { return pos.getY(); }
    @Override public int thresholdValue() { return pos.getY() - (int) getCableOffset(.5F); }
    public int getMovementMode() { return movementMode; }
    public void setMovementMode(int mode) { movementMode = Math.floorMod(mode, 3); markDirty(); sync(); }
    @Override public boolean ownsLinearContraption(UUID id) { return id != null && id.equals(contraptionId); }
    @Override public void onContraptionRestored(UUID id) { if (ownsLinearContraption(id)) { contraptionId = null; needsContraption = false; markDirty(); } }
    @Override public float getStressImpact() { return 4F; }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).expand(0, -MAX_LENGTH - 1, 0); }
    public float getCableOffset(float partial) {
        if (mirrorParent != null && world != null && world.isBlockLoaded(mirrorParent)
                && world.getTileEntity(mirrorParent) instanceof RopePulleyTileEntity parent && parent != this)
            return parent.getCableOffset(partial);
        BearingContraptionEntity entity = findEntity();
        if (entity != null) {
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial;
            return (float) Math.max(0D, pos.getY() - y - 1D);
        }
        return previousOffset + (cableOffset - previousOffset) * partial;
    }
    public void requestAssembly() { requestAssembly = true; }
    public void removePulley() {
        removing = true;
        if (mirrorParent != null && world.isBlockLoaded(mirrorParent)
                && world.getTileEntity(mirrorParent) instanceof RopePulleyTileEntity parent) {
            parent.mirrorChildren.remove(pos);
            parent.markDirty(); parent.sync();
            mirrorParent = null;
            running = false;
        } else disassemble(true);
        removeSegments();
    }
    public void onRopeBroken(BlockPos broken) {
        if (running) return;
        removeSegments();
        RopeSegmentBlock.setInternal(true);
        try {
            IBlockState brokenWater = displacedWater.remove(broken);
            if (brokenWater != null && world.isAirBlock(broken)) world.setBlockState(broken, brokenWater, 3);
            for (BlockPos cursor = broken.down(); cursor.getY() >= 0 && world.isBlockLoaded(cursor); cursor = cursor.down()) {
                if (!RopeSegmentBlock.isSegment(world.getBlockState(cursor))) break;
                restoreSegment(cursor);
            }
        } finally { RopeSegmentBlock.setInternal(false); }
        cableOffset = previousOffset = 0;
        markDirty(); sync();
    }
    private int range() { return Math.max(0, Math.min(MAX_LENGTH, pos.getY() - 1)); }
    private BearingContraptionEntity findEntity() {
        BearingContraptionEntity entity=contraptionEntity.get(world,contraptionId);
        return entity!=null&&entity.isLinearContraption()&&pos.equals(entity.getElevatorController())?entity:null;
    }
    private int scanSegments() {
        int length = 0;
        while (length < range() && world.isBlockLoaded(pos.down(length + 1))
                && RopeSegmentBlock.isSegment(world.getBlockState(pos.down(length + 1)))) length++;
        return length;
    }
    private void removeSegments() {
        if (world == null || world.isRemote) return;
        RopeSegmentBlock.setInternal(true);
        try {
            for (int i = 1; i <= MAX_LENGTH && world.isBlockLoaded(pos.down(i)); i++) {
                BlockPos segment = pos.down(i);
                if (!RopeSegmentBlock.isSegment(world.getBlockState(segment))) break;
                restoreSegment(segment);
            }
        } finally { RopeSegmentBlock.setInternal(false); }
    }
    private void restoreSegment(BlockPos segment) {
        IBlockState water = displacedWater.remove(segment);
        world.setBlockState(segment, water == null ? Blocks.AIR.getDefaultState() : water, 3);
    }
    private boolean canWriteSegments(int length) {
        for (int i = 1; i <= length; i++) {
            BlockPos segment = pos.down(i);
            if (!world.isBlockLoaded(segment) || !world.getBlockState(segment).getBlock().isReplaceable(world, segment)) return false;
        }
        return true;
    }
    private boolean writeSegments(int length) {
        if (!canWriteSegments(length)) return false;
        RopeSegmentBlock.setInternal(true);
        try {
            for (int i = 1; i <= length; i++) {
                BlockPos segment = pos.down(i);
                IBlockState previous = world.getBlockState(segment);
                if (previous.getMaterial() == Material.WATER) displacedWater.put(segment.toImmutable(), previous);
                else displacedWater.remove(segment);
                world.setBlockState(segment, (i == length ? AllBlocks.PULLEY_MAGNET : AllBlocks.ROPE).getDefaultState(), 3);
            }
        } finally { RopeSegmentBlock.setInternal(false); }
        return true;
    }
    private void assemble() {
        if (getSpeed() == 0F || running || mirrorParent != null) return;
        int length = scanSegments();
        if (length >= range() && getSpeed() > 0F || length <= 0 && getSpeed() < 0F) return;
        BlockPos anchor = pos.down(length + 1);
        if (!world.isBlockLoaded(anchor)) return;
        BearingContraption structure = world.isAirBlock(anchor) ? null : BearingContraption.capture(world, pos, anchor,
                positions -> ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        if (structure == null && !world.getBlockState(anchor).getBlock().isReplaceable(world, anchor) && getSpeed() > 0F) return;
        if (structure != null) {
            CarriedBearingAssembly carried = CarriedBearingAssembly.prepare(world, structure, positions ->
                    ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
            BearingContraptionEntity entity = BearingContraptionEntity.createLinear(world, pos, anchor, EnumFacing.UP, structure, false);
            entity.prepareNestedChildren();
            if (!world.spawnEntity(entity)) return;
            com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(entity, structure);
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.assembled(world, pos, structure);
            removeSegments(); carried.removeFromWorld(); structure.removeFromWorld(world, true);
            contraptionId = entity.getUniqueID(); needsContraption = true;
            attachMirrors(structure, anchor, length);
            entity.sendSnapshotToTrackingPlayers(); entity.sendNestedSnapshots();
        } else { removeSegments(); needsContraption = false; }
        cableOffset = previousOffset = initialLength = length;
        running = true; markDirty(); sync();
    }
    private void attachMirrors(BearingContraption structure, BlockPos anchor, int length) {
        mirrorChildren.clear();
        for (BlockPos block : structure.blocks().keySet()) {
            if (block.getY() != anchor.getY()) continue;
            BlockPos candidate = new BlockPos(block.getX(), pos.getY(), block.getZ());
            if (candidate.equals(pos) || !world.isBlockLoaded(candidate)
                    || !(world.getTileEntity(candidate) instanceof RopePulleyTileEntity child)
                    || child.running || child.mirrorParent != null || child.scanSegments() != length) continue;
            child.removeSegments();
            child.mirrorParent = pos.toImmutable();
            child.initialLength = length;
            child.cableOffset = child.previousOffset = length;
            child.running = true;
            child.needsContraption = false;
            child.markDirty(); child.sync();
            mirrorChildren.add(candidate.toImmutable());
        }
    }
    private boolean disassemble(boolean force) {
        if (!running) return true;
        if (!force && (movementMode == 2 || movementMode == 1 && Math.round(cableOffset) != initialLength)) return false;
        BearingContraptionEntity entity = findEntity();
        if (needsContraption && entity == null) return false;
        int length = MathHelper.clamp(Math.round(cableOffset), 0, range());
        // Check before restoring the structure so a blocked rope cannot leave
        // the actuator claiming that its already restored entity still exists.
        if (!removing && !canWriteSegments(length)) return false;
        for (BlockPos childPos : mirrorChildren) {
            if (!world.isBlockLoaded(childPos)) return false;
            if (world.getTileEntity(childPos) instanceof RopePulleyTileEntity child
                    && pos.equals(child.mirrorParent) && !child.canWriteSegments(length)) return false;
        }
        if (entity != null) {
            if (!entity.moveLinear(initialLength - length)) return false;
            entity.restoreAndDiscard();
            if (!entity.isDead) return false;
        }
        if (!removing && !writeSegments(length)) return false;
        for (BlockPos childPos : new ArrayList<>(mirrorChildren)) {
            if (world.getTileEntity(childPos) instanceof RopePulleyTileEntity child && pos.equals(child.mirrorParent)) {
                child.writeSegments(length);
                child.mirrorParent = null;
                child.running = false;
                child.cableOffset = child.previousOffset = length;
                child.requestAssembly = true;
                child.markDirty(); child.sync();
            }
        }
        mirrorChildren.clear();
        contraptionId = null; needsContraption = false; running = false;
        cableOffset = previousOffset = length;
        markDirty(); sync(); return true;
    }
    @Override public void update() {
        previousOffset = cableOffset;
        super.update();
        if (world == null || world.isRemote || world instanceof FakeWorld) return;
        if (mirrorParent != null) {
            if (!world.isBlockLoaded(mirrorParent)) return;
            if (world.getTileEntity(mirrorParent) instanceof RopePulleyTileEntity parent && parent.running) {
                cableOffset = parent.cableOffset;
                return;
            }
            int length = MathHelper.clamp(Math.round(cableOffset), 0, range());
            if (!writeSegments(length)) return;
            mirrorParent = null;
            running = false;
            requestAssembly = true;
            markDirty(); sync();
        }
        float speed = getSpeed();
        if (Float.compare(previousSpeed, speed) != 0) { previousSpeed = speed; requestAssembly = true; }
        if (requestAssembly) {
            requestAssembly = false;
            if (running && speed == 0F) disassemble(false);
            else if (!running) assemble();
        }
        if (running && speed == 0F && world.getTotalWorldTime() % 10L == 0) disassemble(false);
        if (!running || speed == 0F) return;
        BearingContraptionEntity entity = findEntity();
        if (needsContraption && entity == null) return;
        if (cableOffset > 200F) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "pulley_maxed");
        float before = cableOffset;
        float next = MathHelper.clamp(cableOffset + sequencedTravel.limit(this, MathHelper.clamp(speed / 512F, -.49F, .49F)), 0F, range());
        if (entity != null) {
            boolean complete = entity.moveLinear(initialLength - next);
            cableOffset = initialLength - entity.getVerticalOffset();
            if (!complete && !entity.isLinearActorBlocking()) disassemble(false);
        } else {
            int oldGrid = MathHelper.floor(cableOffset);
            int nextGrid = MathHelper.floor(next);
            if (nextGrid > oldGrid && !world.getBlockState(pos.down(nextGrid + 1)).getBlock().isReplaceable(world, pos.down(nextGrid + 1))) {
                cableOffset = nextGrid;
                if (disassemble(true)) requestAssembly = true;
            } else cableOffset = next;
        }
        sequencedTravel.moved(cableOffset - before);
        if (running && (entity == null || !entity.isStalled())
                && (cableOffset <= 0F || cableOffset >= range())) disassemble(false);
        markDirty(); sync();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (contraptionId != null) tag.setUniqueId("LinearContraption", contraptionId);
        tag.setFloat("CableOffset", cableOffset); tag.setInteger("InitialLength", initialLength);
        tag.setBoolean("Running", running); tag.setBoolean("NeedsContraption", needsContraption);
        tag.setInteger("MovementMode", movementMode);
        tag.setTag("SequencedTravel", sequencedTravel.write());
        if (mirrorParent != null) tag.setLong("MirrorParent", mirrorParent.toLong());
        NBTTagList children = new NBTTagList();
        for (BlockPos child : mirrorChildren) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setLong("Pos", child.toLong()); children.appendTag(entry);
        }
        tag.setTag("MirrorChildren", children);
        NBTTagList water = new NBTTagList();
        for (Map.Entry<BlockPos, IBlockState> entry : displacedWater.entrySet()) {
            if (entry.getValue().getBlock().getRegistryName() == null) continue;
            NBTTagCompound saved = new NBTTagCompound();
            saved.setLong("Pos", entry.getKey().toLong());
            saved.setString("Block", entry.getValue().getBlock().getRegistryName().toString());
            saved.setInteger("Meta", entry.getValue().getBlock().getMetaFromState(entry.getValue()));
            water.appendTag(saved);
        }
        tag.setTag("RopeWater", water);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        contraptionId = tag.hasUniqueId("LinearContraption") ? tag.getUniqueId("LinearContraption") : null;
        cableOffset = previousOffset = tag.getFloat("CableOffset"); initialLength = tag.getInteger("InitialLength");
        running = tag.getBoolean("Running"); needsContraption = tag.getBoolean("NeedsContraption");
        movementMode = Math.floorMod(tag.getInteger("MovementMode"), 3);
        sequencedTravel.read(tag.getCompoundTag("SequencedTravel"));
        mirrorParent = tag.hasKey("MirrorParent") ? BlockPos.fromLong(tag.getLong("MirrorParent")) : null;
        mirrorChildren.clear();
        NBTTagList children = tag.getTagList("MirrorChildren", 10);
        for (int i = 0; i < children.tagCount(); i++) mirrorChildren.add(BlockPos.fromLong(children.getCompoundTagAt(i).getLong("Pos")));
        if (tag.hasKey("RopeWater", 9)) {
            displacedWater.clear();
            NBTTagList water = tag.getTagList("RopeWater", 10);
            for (int i = 0; i < water.tagCount(); i++) {
                NBTTagCompound saved = water.getCompoundTagAt(i);
                Block block = Block.getBlockFromName(saved.getString("Block"));
                if (block != null) {
                    IBlockState state = block.getStateFromMeta(saved.getInteger("Meta"));
                    if (state.getMaterial() == Material.WATER) displacedWater.put(BlockPos.fromLong(saved.getLong("Pos")), state);
                }
            }
        }
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        tag.removeTag("RopeWater");
        tag.setFloat("Speed", rotation.getSpeed()); tag.setByte("Axis", (byte) rotation.getAxis().ordinal());
        return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        float old = cableOffset; readFromNBT(tag); super.handleUpdateTag(tag); previousOffset = old;
    }
}
