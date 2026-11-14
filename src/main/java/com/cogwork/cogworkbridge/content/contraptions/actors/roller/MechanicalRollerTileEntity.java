package com.cogwork.cogworkbridge.content.contraptions.actors.roller;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionItemCollector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.concurrent.atomic.AtomicInteger;

/** Default roller mode: clear the two blocks above the visited surface. */
public final class MechanicalRollerTileEntity extends TileEntity {
    public enum RollingMode { TUNNEL_PAVE, STRAIGHT_FILL, WIDE_FILL }

    private static final AtomicInteger NEXT_BREAKER_ID = new AtomicInteger(-3000000);
    private static final DamageSource ROLLER_DAMAGE = new DamageSource("cogworkbridge.roller");
    private static final int MAX_FILL_DEPTH = 12;

    private final int breakerId = NEXT_BREAKER_ID.getAndDecrement();
    private World targetWorld;
    private BlockPos breakingPos;
    private int breakingState = -1;
    private int progress;
    private int delay;
    private long lastProgressTick = Long.MIN_VALUE;
    private long lastDamageTick = Long.MIN_VALUE;
    private long lastPaveTick = Long.MIN_VALUE;
    private boolean worldChanged;
    private double lastRenderTime = Double.NaN;
    private float wheelAngle;
    private float tutorialAnimatedSpeed = Float.NaN;
    private RollingMode mode = RollingMode.TUNNEL_PAVE;
    private ItemStack material = ItemStack.EMPTY;

    public RollingMode getMode() {
        return mode;
    }

    public ItemStack getMaterial() {
        return material.copy();
    }

    public void setMode(RollingMode mode) {
        if (mode != null && this.mode != mode) {
            applySettings(mode, material);
            shareSettings();
        }
    }

    public void setMaterial(ItemStack stack) {
        if (isValidMaterial(stack)) {
            applySettings(mode, stack);
            shareSettings();
        }
    }

    public boolean isValidMaterial(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (world == null || !(stack.getItem() instanceof ItemBlock item)) {
            return false;
        }
        Block block = item.getBlock();
        IBlockState state = block.getStateFromMeta(item.getMetadata(stack.getMetadata()));
        return block != Blocks.AIR && !block.hasTileEntity(state)
                && Block.FULL_BLOCK_AABB.equals(state.getBoundingBox(world, pos))
                && state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB;
    }

    public void copyAdjacentSettings() {
        if (world == null || world.isRemote) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalRollerBlock)) {
            return;
        }
        EnumFacing side = state.getValue(MechanicalRollerBlock.FACING).rotateY();
        for (EnumFacing direction : new EnumFacing[]{side, side.getOpposite()}) {
            BlockPos otherPos = pos.offset(direction);
            if (world.getBlockState(otherPos) == state
                    && world.getTileEntity(otherPos) instanceof MechanicalRollerTileEntity other) {
                applySettings(other.mode, other.material);
                shareSettings();
                return;
            }
        }
    }

    private void shareSettings() {
        if (world == null || world.isRemote) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalRollerBlock)) {
            return;
        }
        EnumFacing side = state.getValue(MechanicalRollerBlock.FACING).rotateY();
        for (EnumFacing direction : new EnumFacing[]{side, side.getOpposite()}) {
            for (int distance = 1; distance < 100; distance++) {
                BlockPos otherPos = pos.offset(direction, distance);
                if (!world.isBlockLoaded(otherPos) || world.getBlockState(otherPos) != state
                        || !(world.getTileEntity(otherPos) instanceof MechanicalRollerTileEntity other)) {
                    break;
                }
                other.applySettings(mode, material);
            }
        }
    }

    private void applySettings(RollingMode mode, ItemStack stack) {
        this.mode = mode;
        material = stack.copy();
        if (!material.isEmpty()) {
            material.setCount(1);
        }
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!Float.isNaN(tutorialAnimatedSpeed)) tag.setFloat("TutorialAnimatedSpeed", tutorialAnimatedSpeed);
        tag.setInteger("RollerMode", mode.ordinal());
        if (!material.isEmpty()) {
            tag.setTag("RollerMaterial", material.writeToNBT(new NBTTagCompound()));
        } else {
            tag.removeTag("RollerMaterial");
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tutorialAnimatedSpeed = tag.hasKey("TutorialAnimatedSpeed") ? tag.getFloat("TutorialAnimatedSpeed") : Float.NaN;
        int storedMode = tag.getInteger("RollerMode");
        mode = RollingMode.values()[Math.max(0, Math.min(RollingMode.values().length - 1, storedMode))];
        material = tag.hasKey("RollerMaterial", 10)
                ? new ItemStack(tag.getCompoundTag("RollerMaterial")) : ItemStack.EMPTY;
        if (!material.isEmpty()) {
            material.setCount(1);
        }
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public void handleUpdateTag(NBTTagCompound tag) { readFromNBT(tag); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) {
        handleUpdateTag(packet.getNbtCompound());
    }

    public boolean tickMovingStep(BearingContraptionEntity contraption,
                                  float fromAngle, float toAngle, float bearingSpeed) {
        if (world == null || contraption.world.isRemote) {
            stopMoving();
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalRollerBlock)) {
            stopMoving();
            return false;
        }
        EnumFacing facing = state.getValue(MechanicalRollerBlock.FACING);
        Vec3d tip = localTip(contraption, facing);
        Vec3d before = contraption.actorStepStart(tip, fromAngle);
        Vec3d after = contraption.localToWorld(tip, toAngle);
        Vec3d motion = after.subtract(before);
        Vec3d direction = contraption.localToWorld(tip.add(new Vec3d(facing.getDirectionVec())), toAngle)
                .subtract(after);
        if (motion.lengthSquared() < 1.0E-12D || motion.dotProduct(direction) <= 0.0D) {
            stopMoving();
            return false;
        }
        World realWorld = contraption.world;
        BlockPos surface = new BlockPos(after);
        if (!realWorld.isBlockLoaded(surface)) {
            stopMoving();
            return false;
        }
        Vec3d tickMotion = contraption.actorTickMovement(tip, fromAngle, bearingSpeed);
        damageEntities(contraption, surface, tickMotion.length());

        if (mode != RollingMode.TUNNEL_PAVE) {
            clearBreaking();
            return pave(contraption, surface);
        }

        BlockPos target = null;
        float greatestHardness = -1.0F;
        IBlockState paveState = materialState();
        boolean canPave = paveState != null && ContraptionItemCollector.hasMatchingItem(contraption, material);
        for (int offset = canPave ? 0 : 1; offset <= 2; offset++) {
            BlockPos candidate = surface.up(offset);
            if ((!contraption.isNested() && candidate.equals(contraption.getBearingPos()))
                    || !realWorld.isBlockLoaded(candidate)) {
                continue;
            }
            IBlockState candidateState = realWorld.getBlockState(candidate);
            if (offset == 0 && candidateState.getBlock() == paveState.getBlock()) {
                continue;
            }
            if (canBreak(realWorld, candidate, candidateState)) {
                float hardness = candidateState.getBlockHardness(realWorld, candidate);
                if (hardness >= greatestHardness) {
                    target = candidate;
                    greatestHardness = hardness;
                }
            }
        }
        if (target == null) {
            clearBreaking();
            return pave(contraption, surface);
        }
        IBlockState targetState = realWorld.getBlockState(target);
        int stateId = Block.getStateId(targetState);
        if (targetWorld != realWorld || !target.equals(breakingPos) || stateId != breakingState) {
            clearBreaking();
            targetWorld = realWorld;
            breakingPos = target.toImmutable();
            breakingState = stateId;
        }
        long tick = realWorld.getTotalWorldTime();
        if (lastProgressTick == tick) {
            return true;
        }
        lastProgressTick = tick;
        if (delay-- > 0) {
            return true;
        }
        double distance = tickMotion.length();
        float animationSpeed = distance < 1.0D / 512.0D ? 0.0F
                : (float) (Math.floor((distance * 1000.0D + 100.0D) / 100.0D) * 100.0D);
        float breakSpeed = Math.max(1.0F / 128.0F, Math.min(16.0F, animationSpeed * 1.5F / 500.0F));
        progress = Math.min(10, progress + (greatestHardness <= 0.0F
                ? 10 : Math.max(1, (int) (breakSpeed / greatestHardness))));
        realWorld.playSound(null, target,
                targetState.getBlock().getSoundType(targetState, realWorld, target, null).getHitSound(),
                SoundCategory.BLOCKS, 0.25F, 1.0F);
        if (progress < 10) {
            delay = Math.max(0, (int) (greatestHardness / breakSpeed));
            realWorld.sendBlockBreakProgress(breakerId, target, progress);
            return true;
        }
        clearBreaking();
        float dropChance = targetState.getBlock().getHarvestLevel(targetState) > 0 ? 0.0F : 0.5F;
        ContraptionItemCollector.destroyAndCollect(contraption, target, dropChance);
        if (realWorld.getBlockState(target) != targetState) {
            worldChanged = true;
        }
        return true;
    }

    private IBlockState materialState() {
        if (!(material.getItem() instanceof ItemBlock item)) {
            return null;
        }
        return item.getBlock().getStateFromMeta(item.getMetadata(material.getMetadata()));
    }

    private boolean pave(BearingContraptionEntity contraption, BlockPos surface) {
        IBlockState state = materialState();
        World realWorld = contraption.world;
        if (state == null || !ContraptionItemCollector.hasMatchingItem(contraption, material)) {
            return false;
        }
        long tick = realWorld.getTotalWorldTime();
        if (lastPaveTick == tick) {
            return true;
        }
        int depthLimit = mode == RollingMode.TUNNEL_PAVE ? 0 : MAX_FILL_DEPTH;
        for (int depth = 0; depth <= depthLimit; depth++) {
            int radius = mode == RollingMode.WIDE_FILL ? (depth + 1) / 2 : 0;
            boolean hasPassable = false;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) + Math.abs(z) > radius) {
                        continue;
                    }
                    BlockPos target = surface.add(x, -depth, z);
                    if (!realWorld.isBlockLoaded(target)
                            || (!contraption.isNested() && target.equals(contraption.getBearingPos()))) {
                        continue;
                    }
                    IBlockState existing = realWorld.getBlockState(target);
                    if (existing.getBlock() == state.getBlock()) {
                        hasPassable = true;
                        continue;
                    }
                    if (!isReplaceableForPaving(realWorld, target, existing)) {
                        continue;
                    }
                    if (ContraptionItemCollector.placeMatchingItem(contraption, material, target, state)) {
                        worldChanged = true;
                        lastPaveTick = tick;
                        return true;
                    }
                }
            }
            if (!hasPassable && depth > 0) {
                break;
            }
        }
        return false;
    }

    private static boolean isReplaceableForPaving(World world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.PORTAL || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY) {
            return false;
        }
        return block.isReplaceable(world, pos) || block.isLeaves(state, world, pos)
                || state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB;
    }

    private static boolean canBreak(World world, BlockPos pos, IBlockState state) {
        Material material = state.getMaterial();
        if (material == Material.AIR || material.isLiquid() || state.getBlock() instanceof BlockRailBase
                || state.getBlockHardness(world, pos) < 0.0F
                || state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB) {
            return false;
        }
        for (EnumFacing side : EnumFacing.values()) {
            Block block = world.getBlockState(pos.offset(side)).getBlock();
            if (block == Blocks.PORTAL || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY) {
                return false;
            }
        }
        return true;
    }

    private void damageEntities(BearingContraptionEntity contraption, BlockPos surface, double motion) {
        World realWorld = contraption.world;
        if (lastDamageTick == realWorld.getTotalWorldTime()
                || realWorld.getBlockState(surface).isNormalCube()) {
            return;
        }
        lastDamageTick = realWorld.getTotalWorldTime();
        float damage = (float) Math.max(2.0D, Math.min(10.0D, 6.0D * Math.pow(motion, 0.4D) + 1.0D));
        for (Entity entity : realWorld.getEntitiesWithinAABBExcludingEntity(contraption, new AxisAlignedBB(surface))) {
            if (!(entity instanceof EntityItem) && !(entity instanceof BearingContraptionEntity)
                    && !entity.isRidingSameEntity(contraption)) {
                entity.attackEntityFrom(ROLLER_DAMAGE, damage);
            }
        }
    }

    private Vec3d localTip(BearingContraptionEntity contraption, EnumFacing facing) {
        BlockPos anchor = contraption.getAnchor();
        return new Vec3d(anchor.getX() + pos.getX() + 0.5D + facing.getXOffset() * 0.45D,
                anchor.getY() + pos.getY() - 1.5D,
                anchor.getZ() + pos.getZ() + 0.5D + facing.getZOffset() * 0.45D);
    }

    public float movingAnimationSpeed(BearingContraptionEntity contraption, EnumFacing facing) {
        if (!contraption.hasMotion()) {
            return 0.0F;
        }
        Vec3d tip = localTip(contraption, facing);
        float angle = contraption.getAngle();
        Vec3d before = contraption.localToWorld(tip, angle);
        Vec3d movement = contraption.actorTickMovement(tip, angle, contraption.getSpeed());
        Vec3d direction = contraption.localToWorld(tip.add(new Vec3d(facing.getDirectionVec())), angle)
                .subtract(before);
        double distance = movement.length();
        float speed = distance < 1.0D / 512.0D ? 0.0F
                : (float) (Math.floor((distance * 1000.0D + 100.0D) / 100.0D) * 100.0D);
        return movement.dotProduct(direction) < 0.0D ? -speed : speed;
    }

    public float wheelAngle(double time, float speed) {
        if (!Float.isNaN(tutorialAnimatedSpeed)) return (float) ((time * tutorialAnimatedSpeed / 20D) % 360D);
        if (!Double.isNaN(lastRenderTime)) {
            double elapsed = Math.max(0.0D, Math.min(1.0D, time - lastRenderTime));
            wheelAngle = (wheelAngle + (float) elapsed * speed / 20.0F) % 360.0F;
        }
        lastRenderTime = time;
        return wheelAngle;
    }

    public boolean consumeWorldChanged() {
        boolean changed = worldChanged;
        worldChanged = false;
        return changed;
    }

    public void stopMoving() {
        clearBreaking();
    }

    private void clearBreaking() {
        if (targetWorld != null && breakingPos != null && progress > 0) {
            targetWorld.sendBlockBreakProgress(breakerId, breakingPos, -1);
        }
        targetWorld = null;
        breakingPos = null;
        breakingState = -1;
        progress = 0;
        delay = 0;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(2.0D);
    }

    @Override
    public void invalidate() {
        stopMoving();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        stopMoving();
        super.onChunkUnload();
    }
}
