package com.cogwork.cogworkbridge.content.contraptions.mounted;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRailPowered;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Connects powered and activator rails through the rail inside an assembler. */
public final class CartAssemblerRailPower {
    private static final int POWERED = 1;
    private static final int ACTIVATOR = 3;
    private static final int MAX_DISTANCE = 8;

    private CartAssemblerRailPower() { }

    /** Called by the vanilla powered-rail hook; -1 leaves vanilla behavior intact. */
    public static int intercept(Object caller, Object worldObject, Object posObject,
                                boolean forward, int distance, Object axisObject) {
        if (!(worldObject instanceof World world) || !(posObject instanceof BlockPos pos)
                || !(axisObject instanceof BlockRailBase.EnumRailDirection axis)) return -1;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof CartAssemblerBlock)) return -1;
        int kind = caller == Blocks.GOLDEN_RAIL ? POWERED : caller == Blocks.ACTIVATOR_RAIL ? ACTIVATOR : 0;
        return kind != 0 && sourceAt(world, pos, state, forward, distance, axis, kind) ? 1 : 0;
    }

    /** A station bridges either vanilla powered rail network and controller rails. */
    public static int signalStrength(World world, BlockPos pos, IBlockState state) {
        int direct = world.getRedstonePowerFromNeighbors(pos);
        if (direct > 0) return direct;
        int controller = ControllerRailBlock.power(world, pos);
        if (controller > 0) return controller;
        BlockRailBase.EnumRailDirection shape = railShape(state);
        if (shape == null) return 0;
        for (int kind : new int[]{POWERED, ACTIVATOR})
            if (findSource(world, pos, shape, true, 0, kind)
                    || findSource(world, pos, shape, false, 0, kind)) return 15;
        return 0;
    }

    private static boolean findSource(World world, BlockPos pos, BlockRailBase.EnumRailDirection shape,
                                      boolean forward, int distance, int kind) {
        if (distance >= MAX_DISTANCE) return false;
        int dx = 0, dy = 0, dz = 0;
        boolean checkBelow = true;
        BlockRailBase.EnumRailDirection axis;
        switch (shape) {
            case NORTH_SOUTH -> { dz = forward ? 1 : -1; axis = BlockRailBase.EnumRailDirection.NORTH_SOUTH; }
            case EAST_WEST -> { dx = forward ? -1 : 1; axis = BlockRailBase.EnumRailDirection.EAST_WEST; }
            case ASCENDING_EAST -> {
                dx = forward ? -1 : 1;
                if (!forward) { dy = 1; checkBelow = false; }
                axis = BlockRailBase.EnumRailDirection.EAST_WEST;
            }
            case ASCENDING_WEST -> {
                dx = forward ? -1 : 1;
                if (forward) { dy = 1; checkBelow = false; }
                axis = BlockRailBase.EnumRailDirection.EAST_WEST;
            }
            case ASCENDING_NORTH -> {
                dz = forward ? 1 : -1;
                if (!forward) { dy = 1; checkBelow = false; }
                axis = BlockRailBase.EnumRailDirection.NORTH_SOUTH;
            }
            case ASCENDING_SOUTH -> {
                dz = forward ? 1 : -1;
                if (forward) { dy = 1; checkBelow = false; }
                axis = BlockRailBase.EnumRailDirection.NORTH_SOUTH;
            }
            default -> { return false; }
        }
        BlockPos next = pos.add(dx, dy, dz);
        return sourceAt(world, next, forward, distance, axis, kind)
                || checkBelow && sourceAt(world, next.down(), forward, distance, axis, kind);
    }

    private static boolean sourceAt(World world, BlockPos pos, boolean forward, int distance,
                                    BlockRailBase.EnumRailDirection axis, int kind) {
        return world.isBlockLoaded(pos)
                && sourceAt(world, pos, world.getBlockState(pos), forward, distance, axis, kind);
    }

    private static boolean sourceAt(World world, BlockPos pos, IBlockState state, boolean forward, int distance,
                                    BlockRailBase.EnumRailDirection axis, int kind) {
        boolean station = state.getBlock() instanceof CartAssemblerBlock
                && world.getTileEntity(pos) instanceof CartAssemblerTileEntity;
        if (!station && railKind(world, pos, state) != kind) return false;
        BlockRailBase.EnumRailDirection shape = railShape(state);
        if (shape == null || horizontalAxis(shape) != horizontalAxis(axis)) return false;
        return world.isBlockPowered(pos) || findSource(world, pos, shape, forward, distance + 1, kind);
    }

    private static int railKind(World world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.GOLDEN_RAIL) return POWERED;
        if (block == Blocks.ACTIVATOR_RAIL) return ACTIVATOR;
        if (block instanceof CartAssemblerBlock) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof CartAssemblerTileEntity assembler) return assembler.getRailType();
        }
        return 0;
    }

    private static BlockRailBase.EnumRailDirection railShape(IBlockState state) {
        if (state.getBlock() instanceof CartAssemblerBlock) return state.getValue(CartAssemblerBlock.SHAPE);
        if (state.getBlock() instanceof BlockRailPowered) return state.getValue(BlockRailPowered.SHAPE);
        return null;
    }

    private static int horizontalAxis(BlockRailBase.EnumRailDirection shape) {
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> 0;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> 1;
            default -> -1;
        };
    }
}
