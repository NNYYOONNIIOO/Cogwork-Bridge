package com.cogwork.cogworkbridge.content.contraptions.actors.plough;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionItemCollector;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** The state here is transient; the plough only acts while carried by a contraption. */
public final class MechanicalPloughTileEntity extends TileEntity {
    private static final AtomicInteger NEXT_BREAKER_ID = new AtomicInteger(-2000000);
    private static final GameProfile PLOUGH_PROFILE = new GameProfile(
            UUID.fromString("0657a61a-5f72-48ac-afaf-afce10d6e338"), "CRPlough");

    private final int breakerId = NEXT_BREAKER_ID.getAndDecrement();
    private World targetWorld;
    private BlockPos breakingPos;
    private BlockPos visitedPos;
    private int breakingState = -1;
    private int progress;
    private int delay;
    private long lastProgressTick = Long.MIN_VALUE;
    private long lastPushTick = Long.MIN_VALUE;
    private FakePlayer fakePlayer;
    private boolean worldChanged;

    public boolean tickMovingStep(BearingContraptionEntity contraption,
                                  float fromAngle, float toAngle, float bearingSpeed) {
        if (world == null || !(contraption.world instanceof WorldServer realWorld)) {
            stopMoving();
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalPloughBlock)) {
            stopMoving();
            return false;
        }

        EnumFacing facing = state.getValue(MechanicalPloughBlock.FACING);
        BlockPos anchor = contraption.getAnchor();
        Vec3d tip = new Vec3d(anchor.getX() + pos.getX() + 0.5D + facing.getXOffset() * 0.45D,
                anchor.getY() + pos.getY() + 0.5D,
                anchor.getZ() + pos.getZ() + 0.5D + facing.getZOffset() * 0.45D);
        Vec3d before = contraption.actorStepStart(tip, fromAngle);
        Vec3d after = contraption.localToWorld(tip, toAngle);
        Vec3d facingVector = contraption.localToWorld(tip.add(new Vec3d(facing.getDirectionVec())), toAngle)
                .subtract(after);
        Vec3d movement = after.subtract(before);
        if (movement.lengthSquared() < 1.0E-12D || movement.dotProduct(facingVector) < -1.0E-8D) {
            stopMoving();
            return false;
        }

        BlockPos target = new BlockPos(after);
        if (!realWorld.isBlockLoaded(target)
                || (!contraption.isNested() && target.equals(contraption.getBearingPos()))) {
            stopMoving();
            return false;
        }
        Vec3d tickMotion = contraption.actorTickMovement(tip, fromAngle, bearingSpeed);
        if (!target.equals(visitedPos)) {
            visitedPos = target.toImmutable();
            pushEntities(contraption, target, tickMotion);
            tillBelow(realWorld, target);
        }

        IBlockState targetState = realWorld.getBlockState(target);
        if (!canBreak(realWorld, target, targetState)) {
            clearBreaking();
            return false;
        }
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
        float breakSpeed = Math.max(1.0F / 128.0F, Math.min(16.0F, animationSpeed / 500.0F));
        float hardness = targetState.getBlockHardness(realWorld, target);
        progress = Math.min(10, progress + (hardness <= 0.0F
                ? 10 : Math.max(1, (int) (breakSpeed / hardness))));
        realWorld.playSound(null, target,
                targetState.getBlock().getSoundType(targetState, realWorld, target, null).getHitSound(),
                SoundCategory.BLOCKS, 0.25F, 1.0F);
        if (progress < 10) {
            delay = Math.max(0, (int) (hardness / breakSpeed));
            realWorld.sendBlockBreakProgress(breakerId, target, progress);
            return true;
        }

        clearBreaking();
        ContraptionItemCollector.destroyAndCollect(contraption, target);
        if (realWorld.getBlockState(target) != targetState) {
            worldChanged = true;
            tillBelow(realWorld, target);
        }
        return false;
    }

    private static boolean canBreak(World world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        if (state.getMaterial() == Material.AIR || state.getMaterial().isLiquid()
                || block == Blocks.PORTAL || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY
                || state.getBlockHardness(world, pos) < 0.0F
                || !world.isBlockLoaded(pos.down())
                || world.getBlockState(pos.down()).getBlock() == Blocks.FARMLAND) {
            return false;
        }
        return block == Blocks.SNOW_LAYER || state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB;
    }

    private void tillBelow(WorldServer realWorld, BlockPos activePos) {
        if (!realWorld.isBlockLoaded(activePos.down())) {
            return;
        }
        Vec3d start = new Vec3d(activePos.getX() + 0.5D, activePos.getY() + 0.5D,
                activePos.getZ() + 0.5D);
        RayTraceResult hit = realWorld.rayTraceBlocks(start, start.add(0.0D, -1.0D, 0.0D), false, true, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        if (fakePlayer == null || fakePlayer.world != realWorld) {
            fakePlayer = new FakePlayer(realWorld, PLOUGH_PROFILE);
        }
        fakePlayer.setPosition(start.x, start.y, start.z);
        fakePlayer.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HOE));
        if (!realWorld.isBlockModifiable(fakePlayer, hit.getBlockPos())) {
            return;
        }
        IBlockState before = realWorld.getBlockState(hit.getBlockPos());
        fakePlayer.getHeldItemMainhand().onItemUse(fakePlayer, realWorld, hit.getBlockPos(),
                EnumHand.MAIN_HAND, hit.sideHit, 0.5F, 1.0F, 0.5F);
        if (realWorld.getBlockState(hit.getBlockPos()) != before) {
            worldChanged = true;
        }
    }

    private void pushEntities(BearingContraptionEntity contraption, BlockPos target, Vec3d motion) {
        World realWorld = contraption.world;
        if (lastPushTick == realWorld.getTotalWorldTime()
                || realWorld.getBlockState(target).isNormalCube()) {
            return;
        }
        lastPushTick = realWorld.getTotalWorldTime();
        Vec3d boost = motion.add(0.0D, motion.length() / 4.0D, 0.0D);
        if (boost.lengthSquared() > 16.0D) {
            boost = boost.normalize().scale(4.0D);
        }
        for (Entity entity : realWorld.getEntitiesWithinAABBExcludingEntity(contraption, new AxisAlignedBB(target))) {
            if (entity instanceof EntityItem || entity instanceof BearingContraptionEntity
                    || entity.isRidingSameEntity(contraption)) {
                continue;
            }
            entity.addVelocity(boost.x, boost.y, boost.z);
            entity.velocityChanged = true;
        }
    }

    public boolean consumeWorldChanged() {
        boolean changed = worldChanged;
        worldChanged = false;
        return changed;
    }

    public void stopMoving() {
        clearBreaking();
        visitedPos = null;
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
    public void invalidate() {
        stopMoving();
        fakePlayer = null;
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        stopMoving();
        fakePlayer = null;
        super.onChunkUnload();
    }
}
