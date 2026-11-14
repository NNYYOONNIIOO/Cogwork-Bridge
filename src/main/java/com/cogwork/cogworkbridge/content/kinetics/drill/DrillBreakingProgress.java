package com.cogwork.cogworkbridge.content.kinetics.drill;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Server-side block breaking progress shared by stationary and carried tools. */
public final class DrillBreakingProgress {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(-1000000);

    private final int breakerId = NEXT_ID.getAndDecrement();
    private World world;
    private BlockPos target;
    private int stateId = -1;
    private int progress;
    private int delay;

    public boolean tick(World targetWorld, BlockPos targetPos, float speed, boolean moving,
                 Consumer<BlockPos> destroyBlock) {
        return tick(targetWorld, targetPos, speed, moving, false, destroyBlock);
    }

    public boolean tick(World targetWorld, BlockPos targetPos, float speed, boolean moving,
                        boolean allowNonSolid, Consumer<BlockPos> destroyBlock) {
        if (!targetWorld.isBlockLoaded(targetPos)) {
            clear();
            return false;
        }
        IBlockState state = targetWorld.getBlockState(targetPos);
        float hardness = state.getBlockHardness(targetWorld, targetPos);
        if (!canBreak(targetWorld, targetPos, state, hardness, moving, allowNonSolid)) {
            clear();
            return false;
        }
        int nextStateId = Block.getStateId(state);
        if (world != targetWorld || !targetPos.equals(target) || stateId != nextStateId) {
            clear();
            world = targetWorld;
            target = targetPos.toImmutable();
            stateId = nextStateId;
        }
        if (delay-- > 0) {
            return true;
        }
        float breakSpeed = Math.max(speed, 0.0001F);
        int increment = hardness <= 0.0F ? 10 : Math.max(1, (int) (breakSpeed / hardness));
        progress = Math.min(10, progress + increment);
        targetWorld.playSound(null, targetPos,
                state.getBlock().getSoundType(state, targetWorld, targetPos, null).getHitSound(),
                SoundCategory.BLOCKS, 0.25F, 1.0F);
        if (progress >= 10) {
            clear();
            destroyBlock.accept(targetPos);
            return false;
        }
        delay = Math.max(0, (int) (hardness / breakSpeed));
        targetWorld.sendBlockBreakProgress(breakerId, targetPos, progress);
        return true;
    }

    private static boolean canBreak(World world, BlockPos pos, IBlockState state,
                                    float hardness, boolean moving, boolean allowNonSolid) {
        Material material = state.getMaterial();
        if (material == Material.AIR || material.isLiquid() || hardness < 0.0F) {
            return false;
        }
        return !moving || (!(state.getBlock() instanceof BlockRailBase)
                && (allowNonSolid || state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB));
    }

    public void wake() {
        delay = 0;
    }

    public BlockPos currentTarget() {
        return target;
    }

    public void clear() {
        if (world != null && target != null && progress > 0) {
            world.sendBlockBreakProgress(breakerId, target, -1);
        }
        world = null;
        target = null;
        stateId = -1;
        progress = 0;
        delay = 0;
    }
}
