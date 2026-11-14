package com.cogwork.cogworkbridge.content.equipment;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** The three mirror layouts supported by Create's symmetry wand. */
public final class SymmetryGeometry {
    private SymmetryGeometry() { }

    /** Transform a whole placement with one transform, preserving multi-block relationships. */
    public static java.util.List<Map<BlockPos, IBlockState>> groups(BlockPos origin,
            Map<BlockPos, IBlockState> source, int mode, int alignment) {
        int[][] transforms = mode == 0 ? new int[][]{{alignment == 0 ? 1 : 0}}
                : mode == 1 && alignment == 1 ? new int[][]{{2}, {3}, {3, 2}}
                : mode == 2 ? new int[][]{{0}, {1}, {1, 0}, {2}, {0, 2}, {1, 2}, {1, 0, 2}}
                : new int[][]{{0}, {1}, {1, 0}};
        java.util.List<Map<BlockPos, IBlockState>> result = new java.util.ArrayList<>();
        for (int[] sequence : transforms) {
            Map<BlockPos, IBlockState> copy = new LinkedHashMap<>();
            for (var entry : source.entrySet()) {
                Point point = new Point(entry.getKey(), entry.getValue());
                for (int kind : sequence) point = transform(point, origin, kind);
                copy.put(point.pos.toImmutable(), point.state);
            }
            if (java.util.Collections.disjoint(copy.keySet(), source.keySet())
                    && result.stream().noneMatch(old -> old.keySet().equals(copy.keySet()))) result.add(copy);
        }
        return result;
    }

    public static Map<BlockPos, IBlockState> targets(BlockPos origin, BlockPos source,
                                                    IBlockState state, int mode, int alignment) {
        Map<BlockPos, IBlockState> targets = new LinkedHashMap<>();
        Point original = new Point(source, state);
        if (mode == 0) {
            add(targets, source, transform(original, origin, alignment == 0 ? 1 : 0));
        } else if (mode == 1 && alignment == 1) {
            Point first = transform(original, origin, 2);
            Point second = transform(original, origin, 3);
            add(targets, source, first);
            add(targets, source, second);
            add(targets, source, transform(second, origin, 2));
        } else {
            Point x = transform(original, origin, 0);
            Point z = transform(original, origin, 1);
            Point xz = transform(z, origin, 0);
            add(targets, source, x);
            add(targets, source, z);
            add(targets, source, xz);
            if (mode == 2) {
                add(targets, source, transform(original, origin, 2));
                add(targets, source, transform(x, origin, 2));
                add(targets, source, transform(z, origin, 2));
                add(targets, source, transform(xz, origin, 2));
            }
        }
        return targets;
    }

    private static void add(Map<BlockPos, IBlockState> targets, BlockPos source, Point point) {
        if (!point.pos.equals(source)) targets.putIfAbsent(point.pos.toImmutable(), point.state);
    }

    private static Point transform(Point point, BlockPos origin, int kind) {
        int dx = point.pos.getX() - origin.getX();
        int dz = point.pos.getZ() - origin.getZ();
        return switch (kind) {
            case 0 -> new Point(new BlockPos(origin.getX() - dx, point.pos.getY(), point.pos.getZ()),
                    mirror(point.state, Mirror.FRONT_BACK, EnumFacing.Axis.X));
            case 1 -> new Point(new BlockPos(point.pos.getX(), point.pos.getY(), origin.getZ() - dz),
                    mirror(point.state, Mirror.LEFT_RIGHT, EnumFacing.Axis.Z));
            case 2 -> new Point(new BlockPos(origin.getX() - dz, point.pos.getY(), origin.getZ() - dx),
                    mirror(rotateCounterClockwise(point.state), Mirror.FRONT_BACK, EnumFacing.Axis.X));
            default -> new Point(new BlockPos(origin.getX() + dz, point.pos.getY(), origin.getZ() + dx),
                    mirror(rotateCounterClockwise(point.state), Mirror.LEFT_RIGHT, EnumFacing.Axis.Z));
        };
    }

    private static IBlockState rotateCounterClockwise(IBlockState original) {
        IBlockState rotated = original.withRotation(Rotation.COUNTERCLOCKWISE_90);
        for (IProperty<?> property : original.getPropertyKeys()) {
            if (!(property instanceof PropertyDirection direction)) continue;
            EnumFacing old = original.getValue(direction);
            EnumFacing next = Rotation.COUNTERCLOCKWISE_90.rotate(old);
            if (next != old && rotated.getValue(direction) == old && direction.getAllowedValues().contains(next))
                rotated = rotated.withProperty(direction, next);
        }
        return rotated;
    }

    private static IBlockState mirror(IBlockState original, Mirror mirror, EnumFacing.Axis axis) {
        IBlockState mirrored = original.withMirror(mirror);
        for (IProperty<?> property : original.getPropertyKeys()) {
            if (!(property instanceof PropertyDirection direction)) continue;
            EnumFacing old = original.getValue(direction);
            if (old.getAxis() == axis && mirrored.getValue(direction) == old
                    && direction.getAllowedValues().contains(old.getOpposite()))
                mirrored = mirrored.withProperty(direction, old.getOpposite());
        }
        return mirrored;
    }

    private record Point(BlockPos pos, IBlockState state) { }
}
