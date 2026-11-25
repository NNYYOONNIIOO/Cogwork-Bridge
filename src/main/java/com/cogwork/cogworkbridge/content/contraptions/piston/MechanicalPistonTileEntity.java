package com.cogwork.cogworkbridge.content.contraptions.piston;

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
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Carries an existing pole/head assembly and the blocks reached by its head. */
public final class MechanicalPistonTileEntity extends KineticTileEntity implements LinearContraptionController {
    private static final int MAX_POLES = 64;
    private static final int MAX_PUSH_DEPTH = BearingContraption.MAX_BLOCKS;
    private UUID contraptionId;
    private final ContraptionEntityReference contraptionEntity=new ContraptionEntityReference();
    private final SequencedTravel sequencedTravel = new SequencedTravel();
    private int initialExtension, extensionLength, movementMode;
    private float extension, previousSpeed = Float.NaN;
    private boolean running, requestAssembly;

    @Override public float getStressImpact() { return 4F; }
    @Override public boolean ownsLinearContraption(UUID id) { return id != null && id.equals(contraptionId); }
    @Override public boolean ignoresStructureBlock(BlockPos candidate) { return pos.equals(candidate); }
    @Override public void onContraptionRestored(UUID id) {
        if (!ownsLinearContraption(id)) return;
        contraptionId = null;
        running = false;
        markDirty();
        sync();
    }
    public void requestAssembly() { requestAssembly = true; }
    public void onLengthBroken() {
        if (running) return;
        extension = initialExtension = extensionLength = 0;
        requestAssembly = true;
        markDirty();
        sync();
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    public int getMovementMode() { return movementMode; }
    public void setMovementMode(int mode) { movementMode = Math.floorMod(mode, 3); markDirty(); sync(); }
    public int visualState() {
        if (running) return 2;
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof MechanicalPistonBlock)) return 0;
        EnumFacing direction = world.getBlockState(pos).getValue(MechanicalPistonBlock.FACING);
        IBlockState front = world.getBlockState(pos.offset(direction));
        return front.getBlock() instanceof PistonPoleBlock pole
                && front.getValue(PistonPoleBlock.FACING).getAxis() == direction.getAxis() ? 1 : 0;
    }

    private BearingContraptionEntity findEntity() {
        BearingContraptionEntity entity=contraptionEntity.get(world,contraptionId);
        return entity!=null&&entity.isLinearContraption()&&pos.equals(entity.getElevatorController())?entity:null;
    }

    private static boolean isPole(IBlockState state, EnumFacing direction) {
        return state.getBlock() == AllBlocks.PISTON_EXTENSION_POLE
                && state.getValue(PistonPoleBlock.FACING).getAxis() == direction.getAxis();
    }
    private static boolean isHead(IBlockState state, EnumFacing direction) {
        return state.getBlock() instanceof PistonPoleBlock pole && pole.isHead()
                && state.getValue(PistonPoleBlock.FACING) == direction;
    }
    private float movementSpeed(EnumFacing direction) {
        int sign = direction.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1 : -1;
        if (direction.getAxis() == EnumFacing.Axis.Z) sign = -sign;
        return MathHelper.clamp(-getSpeed() * sign / 512F, -.49F, .49F);
    }

    private void assemble() {
        IBlockState base = world.getBlockState(pos);
        if (!(base.getBlock() instanceof MechanicalPistonBlock piston) || getSpeed() == 0F) return;
        EnumFacing direction = base.getValue(MechanicalPistonBlock.FACING);
        List<BlockPos> poles = new ArrayList<>();
        int front = 0, back = 0;
        while (front < MAX_POLES) {
            BlockPos candidate = pos.offset(direction, front + 1);
            if (!world.isBlockLoaded(candidate)) return;
            IBlockState state = world.getBlockState(candidate);
            if (!isPole(state, direction) && !isHead(state, direction)) break;
            poles.add(candidate);
            front++;
            if (isHead(state, direction)) break;
        }
        if (front > 0 && !isHead(world.getBlockState(pos.offset(direction, front)), direction)) return;
        while (front + back < MAX_POLES) {
            BlockPos candidate = pos.offset(direction.getOpposite(), back + 1);
            if (!world.isBlockLoaded(candidate)) return;
            if (!isPole(world.getBlockState(candidate), direction)) break;
            poles.add(candidate);
            back++;
        }
        int length = front + back;
        float speed = movementSpeed(direction);
        if (length == 0 || speed > 0 && front >= length || speed < 0 && front <= 0) return;

        List<BlockPos> seeds = new ArrayList<>();
        if (speed > 0 || piston.isSticky()) {
            int limit = speed > 0 ? MAX_PUSH_DEPTH : 1;
            for (int i = 1; i <= limit; i++) {
                BlockPos candidate = pos.offset(direction, front + i);
                if (!world.isBlockLoaded(candidate)) return;
                IBlockState state = world.getBlockState(candidate);
                if (state.getBlock().isReplaceable(world, candidate)) break;
                if (state.getBlockHardness(world, candidate) < 0 || state.getPushReaction() == net.minecraft.block.material.EnumPushReaction.BLOCK) {
                    if (speed > 0) return;
                    break;
                }
                if (speed < 0 && state.getPushReaction() == net.minecraft.block.material.EnumPushReaction.PUSH_ONLY) break;
                seeds.add(candidate);
                if (speed > 0 && i == limit) {
                    BlockPos next = candidate.offset(direction);
                    if (!world.isBlockLoaded(next) || !world.getBlockState(next).getBlock().isReplaceable(world, next)) return;
                }
            }
        }
        BearingContraption structure = BearingContraption.captureLinear(world, pos, pos, seeds,
                speed > 0 ? direction : direction.getOpposite(),
                positions -> ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        if (structure == null) return;
        for (BlockPos pole : poles) structure.replaceAtLocal(pole.subtract(pos), world.getBlockState(pole), null);
        IBlockState inner = (front == 0
                ? (piston.isSticky() ? AllBlocks.STICKY_PISTON_HEAD : AllBlocks.MECHANICAL_PISTON_HEAD)
                : AllBlocks.PISTON_EXTENSION_POLE).getDefaultState().withProperty(PistonPoleBlock.FACING, direction);
        structure.replaceAtLocal(BlockPos.ORIGIN, inner, null);
        CarriedBearingAssembly carried = CarriedBearingAssembly.prepare(world, structure, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        BearingContraptionEntity entity = BearingContraptionEntity.createLinear(world, pos, pos, direction, structure, false);
        entity.prepareNestedChildren();
        if (!world.spawnEntity(entity)) return;
        com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(entity, structure);
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.assembled(world, pos, structure);
        carried.removeFromWorld();
        structure.removeFromWorld(world, true, Set.of(pos));
        contraptionId = entity.getUniqueID();
        initialExtension = front;
        extensionLength = length;
        extension = front;
        running = true;
        markDirty(); sync();
        entity.sendSnapshotToTrackingPlayers(); entity.sendNestedSnapshots();
    }

    public boolean disassemble(boolean force) {
        if (!running) return true;
        if (!force && (movementMode == 2 || movementMode == 1 && Math.round(extension) != initialExtension)) return false;
        BearingContraptionEntity entity = findEntity();
        if (entity == null) return false;
        int target = MathHelper.clamp(Math.round(extension), 0, extensionLength);
        if (!entity.moveLinear(target - initialExtension)) {
            target = MathHelper.clamp((int) Math.floor(extension), 0, extensionLength);
            if (!entity.moveLinear(target - initialExtension)) return false;
        }
        extension = target;
        entity.restoreAndDiscard();
        if (!entity.isDead) return false;
        contraptionId = null; running = false;
        markDirty(); sync();
        return true;
    }

    @Override public void update() {
        super.update();
        if (world == null || world.isRemote || world instanceof FakeWorld) return;
        float speed = getSpeed();
        if (Float.compare(previousSpeed, speed) != 0) {
            previousSpeed = speed;
            requestAssembly = true;
        }
        if (requestAssembly || !running && world.getTotalWorldTime() % 10 == 0) {
            requestAssembly = false;
            if (running && speed == 0) disassemble(false);
            else if (!running && speed != 0) assemble();
        }
        if (!running || speed == 0 || !(world.getBlockState(pos).getBlock() instanceof MechanicalPistonBlock)) return;
        BearingContraptionEntity entity = findEntity();
        if (entity == null) return;
        EnumFacing direction = world.getBlockState(pos).getValue(MechanicalPistonBlock.FACING);
        float before = extension;
        float next = MathHelper.clamp(extension + sequencedTravel.limit(this, movementSpeed(direction)), 0, extensionLength);
        boolean complete = entity.moveLinear(next - initialExtension);
        extension = initialExtension + entity.getVerticalOffset();
        sequencedTravel.moved(extension - before);
        if (!entity.isLinearActorBlocking() && (!complete
                || !entity.isStalled() && (extension <= 0 || extension >= extensionLength))) {
            if (disassemble(false) && !complete) requestAssembly = true;
        }
        markDirty(); sync();
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (contraptionId != null) tag.setUniqueId("LinearContraption", contraptionId);
        tag.setInteger("InitialExtension", initialExtension);
        tag.setInteger("ExtensionLength", extensionLength);
        tag.setFloat("Extension", extension);
        tag.setBoolean("Running", running);
        tag.setInteger("MovementMode", movementMode);
        tag.setTag("SequencedTravel", sequencedTravel.write());
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        contraptionId = tag.hasUniqueId("LinearContraption") ? tag.getUniqueId("LinearContraption") : null;
        initialExtension = tag.getInteger("InitialExtension"); extensionLength = tag.getInteger("ExtensionLength");
        extension = tag.getFloat("Extension"); running = tag.getBoolean("Running");
        movementMode = Math.floorMod(tag.getInteger("MovementMode"), 3);
        sequencedTravel.read(tag.getCompoundTag("SequencedTravel"));
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        tag.setFloat("Speed", rotation.getSpeed()); tag.setByte("Axis", (byte) rotation.getAxis().ordinal());
        return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        boolean wasRunning = running;
        readFromNBT(tag);
        super.handleUpdateTag(tag);
        if (world != null && world.isRemote && wasRunning != running) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
