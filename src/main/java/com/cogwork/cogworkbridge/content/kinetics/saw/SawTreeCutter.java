package com.cogwork.cogworkbridge.content.kinetics.saw;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockVine;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finds the remaining blocks of a cut tree or a vertical plant column. */
final class SawTreeCutter {
    private static final int LEAF_REACH = 4;

    private SawTreeCutter() { }

    static List<BlockPos> collect(World world, BlockPos cut, IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockCactus || block instanceof BlockReed) {
            List<BlockPos> column = new ArrayList<>();
            for (BlockPos next = cut.up(); next.getY() < world.getActualHeight() && world.isBlockLoaded(next);
                 next = next.up()) {
                if (world.getBlockState(next).getBlock() != block) break;
                column.add(next.toImmutable());
            }
            column.sort(Comparator.comparingInt(BlockPos::getY).reversed());
            return column;
        }
        if (!block.isWood(world, cut) || !cutSeparatesTrunk(world, cut)) {
            return List.of();
        }

        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> candidates = new ArrayDeque<>();
        visited.add(cut.toImmutable());
        enqueueUpperNeighbours(world, cut, visited, candidates);
        while (!candidates.isEmpty()) {
            BlockPos next = candidates.removeFirst();
            IBlockState nextState = world.getBlockState(next);
            if (!nextState.getBlock().isWood(world, next)) continue;
            logs.add(next);
            enqueueUpperNeighbours(world, next, visited, candidates);
        }
        ArrayDeque<LeafStep> leafFrontier = new ArrayDeque<>();
        leafFrontier.addLast(new LeafStep(cut.toImmutable(), 0));
        for (BlockPos log : logs) {
            leafFrontier.addLast(new LeafStep(log, 0));
        }
        Set<BlockPos> leafVisited = new HashSet<>(logs);
        leafVisited.add(cut);
        List<BlockPos> foliage = new ArrayList<>();
        while (!leafFrontier.isEmpty()) {
            LeafStep step = leafFrontier.removeFirst();
            if (step.distance() >= LEAF_REACH) continue;
            for (BlockPos candidate : BlockPos.getAllInBox(step.pos().add(-1, -1, -1),
                    step.pos().add(1, 1, 1))) {
                BlockPos next = candidate.toImmutable();
                if (!valid(world, next) || !leafVisited.add(next)) continue;
                IBlockState nextState = world.getBlockState(next);
                Block nextBlock = nextState.getBlock();
                if (nextBlock instanceof BlockVine || nextBlock instanceof BlockCocoa) {
                    foliage.add(next.toImmutable());
                    continue;
                }
                if (!nextBlock.isLeaves(nextState, world, next)
                        || nextState.getPropertyKeys().contains(BlockLeaves.DECAYABLE)
                        && !nextState.getValue(BlockLeaves.DECAYABLE)) {
                    continue;
                }
                foliage.add(next.toImmutable());
                leafFrontier.addLast(new LeafStep(next.toImmutable(), step.distance() + 1));
            }
        }
        logs.sort(Comparator.comparingInt(BlockPos::getY).reversed());
        foliage.sort(Comparator.comparingInt(BlockPos::getY).reversed());
        logs.addAll(foliage);
        return logs;
    }

    private static boolean cutSeparatesTrunk(World world, BlockPos cut) {
        BlockPos above = cut.up();
        if (!valid(world, above) || !world.getBlockState(above).getBlock().isWood(world, above)) return true;
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(above.toImmutable());
        queue.add(above.toImmutable());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockPos below = current.down();
            if (!below.equals(cut) && valid(world, below)
                    && world.getBlockState(below).getBlock().isWood(world, below)) {
                return false;
            }
            for (EnumFacing direction : EnumFacing.HORIZONTALS) {
                BlockPos next = current.offset(direction);
                if (!valid(world, next) || !visited.add(next)) continue;
                if (world.getBlockState(next).getBlock().isWood(world, next)) {
                    queue.addLast(next.toImmutable());
                }
            }
        }
        return true;
    }

    private static void enqueueUpperNeighbours(World world, BlockPos pos, Set<BlockPos> visited,
                                                ArrayDeque<BlockPos> candidates) {
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos next = pos.add(x, y, z);
                    if (valid(world, next) && visited.add(next)) {
                        candidates.addLast(next.toImmutable());
                    }
                }
            }
        }
    }

    private static boolean valid(World world, BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < world.getActualHeight() && world.isBlockLoaded(pos);
    }

    private record LeafStep(BlockPos pos, int distance) { }
}
