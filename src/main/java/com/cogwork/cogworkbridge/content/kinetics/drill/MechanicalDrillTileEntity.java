package com.cogwork.cogworkbridge.content.kinetics.drill;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionItemCollector;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class MechanicalDrillTileEntity extends KineticTileEntity {
    private final DrillBreakingProgress breaker = new DrillBreakingProgress();
    private long lastDamageTick = Long.MIN_VALUE;
    private boolean worldChanged;

    @Override
    public float getStressImpact() {
        return 4.0F;
    }

    @Override
    public void update() {
        super.update();
        if (world == null || world.isRemote || world instanceof FakeWorld) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalDrillBlock) || getSpeed() == 0.0F) {
            breaker.clear();
            return;
        }
        BlockPos target = pos.offset(state.getValue(MechanicalDrillBlock.FACING));
        breaker.tick(world, target, Math.abs(getSpeed()) / 100.0F, false,
                targetPos -> world.destroyBlock(targetPos, true));
    }

    public void wakeBreaker() {
        breaker.wake();
    }

    public void stopMovingBreaker() {
        breaker.clear();
    }

    /** Processes the swept drill tip before the bearing advances each angular slice. */
    public boolean tickMovingStep(BearingContraptionEntity contraption,
                                  float fromAngle, float toAngle, float bearingSpeed) {
        if (world == null) {
            breaker.clear();
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalDrillBlock)) {
            breaker.clear();
            return false;
        }
        EnumFacing facing = state.getValue(MechanicalDrillBlock.FACING);
        Vec3d tip = localTip(contraption, facing);
        Vec3d before = contraption.actorStepStart(tip, fromAngle);
        Vec3d after = contraption.localToWorld(tip, toAngle);
        Vec3d direction = new Vec3d(facing.getDirectionVec());
        Vec3d worldDirection = contraption.localToWorld(tip.add(direction), toAngle).subtract(after);
        Vec3d motion = after.subtract(before);
        if (motion.lengthSquared() < 1.0E-12D || motion.dotProduct(worldDirection) < -1.0E-8D) {
            breaker.clear();
            return false;
        }
        World realWorld = contraption.world;
        BlockPos tipPos = new BlockPos(after);
        if (realWorld.isBlockLoaded(tipPos)
                && realWorld.getBlockState(tipPos).getCollisionBoundingBox(realWorld, tipPos) == null) {
            damageMovingEntities(contraption, tipPos, motion.length());
        }
        BlockPos target = contraption.findToolTarget(pos, facing, toAngle, breaker.currentTarget(),
                (candidate, blockState) -> blockState.getBlockHardness(realWorld, candidate) >= 0.0F
                        && !(blockState.getBlock() instanceof net.minecraft.block.BlockRailBase)
                        && blockState.getCollisionBoundingBox(realWorld, candidate) != null);
        if (target == null) {
            breaker.clear();
            return false;
        }
        float animationSpeed = movingAnimationSpeed(contraption, facing, fromAngle, bearingSpeed);
        float breakSpeed = Math.max(1.0F / 128.0F, Math.min(16.0F, animationSpeed / 500.0F));
        IBlockState beforeState = realWorld.isBlockLoaded(target) ? realWorld.getBlockState(target) : null;
        boolean breaking = breaker.tick(realWorld, target, breakSpeed, true,
                targetPos -> ContraptionItemCollector.destroyAndCollect(contraption, targetPos));
        if (beforeState != null && beforeState != realWorld.getBlockState(target)) {
            worldChanged = true;
        }
        return breaking;
    }

    public boolean consumeWorldChanged() {
        boolean changed = worldChanged;
        worldChanged = false;
        return changed;
    }

    public float movingAnimationSpeed(BearingContraptionEntity contraption, EnumFacing facing,
                                      float angle, float bearingSpeed) {
        Vec3d tip = localTip(contraption, facing);
        double distance = contraption.actorTickMovement(tip, angle, bearingSpeed).length();
        return distance < 1.0D / 512.0D ? 0.0F
                : (float) (Math.floor((distance * 1000.0D + 100.0D) / 100.0D) * 100.0D);
    }

    private Vec3d localTip(BearingContraptionEntity contraption, EnumFacing facing) {
        BlockPos anchor = contraption.getAnchor();
        return new Vec3d(anchor.getX() + pos.getX() + 0.5D + facing.getXOffset() * 0.65D,
                anchor.getY() + pos.getY() + 0.5D + facing.getYOffset() * 0.65D,
                anchor.getZ() + pos.getZ() + 0.5D + facing.getZOffset() * 0.65D);
    }

    private void damageMovingEntities(BearingContraptionEntity contraption, BlockPos target, double motion) {
        World realWorld = contraption.world;
        long now = realWorld.getTotalWorldTime();
        if (lastDamageTick == now) {
            return;
        }
        lastDamageTick = now;
        float damage = (float) Math.max(2.0D, Math.min(10.0D, 6.0D * Math.pow(motion, 0.4D) + 1.0D));
        for (Entity entity : realWorld.getEntitiesWithinAABBExcludingEntity(contraption, new AxisAlignedBB(target))) {
            if (!(entity instanceof EntityItem) && !(entity instanceof BearingContraptionEntity)) {
                entity.attackEntityFrom(MechanicalDrillBlock.DRILL_DAMAGE, damage);
            }
        }
    }

    @Override
    public void invalidate() {
        breaker.clear();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        breaker.clear();
        super.onChunkUnload();
    }
}
