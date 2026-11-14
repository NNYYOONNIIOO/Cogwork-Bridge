package com.cogwork.cogworkbridge.util;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import java.util.*;

/** Immutable unions of legacy collision boxes; coordinates use upstream's 1/16-block units. */
public final class BlockShape {
    private final List<AxisAlignedBB> boxes;
    private final AxisAlignedBB bounds;
    private BlockShape(List<AxisAlignedBB> boxes) {
        this.boxes = List.copyOf(boxes);
        AxisAlignedBB bounds = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) bounds = bounds.union(boxes.get(i));
        this.bounds = bounds;
    }
    public static BlockShape pixels(double... coordinates) {
        if (coordinates.length == 0 || coordinates.length % 6 != 0) throw new IllegalArgumentException("Expected cuboids");
        List<AxisAlignedBB> boxes = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 6) boxes.add(new AxisAlignedBB(coordinates[i] / 16, coordinates[i + 1] / 16,
                coordinates[i + 2] / 16, coordinates[i + 3] / 16, coordinates[i + 4] / 16, coordinates[i + 5] / 16));
        return new BlockShape(boxes);
    }
    public AxisAlignedBB bounds() { return bounds; }
    public void addCollisionBoxes(BlockPos pos, AxisAlignedBB query, List<AxisAlignedBB> result) {
        for (AxisAlignedBB local : boxes) {
            AxisAlignedBB box = local.offset(pos);
            if (box.intersects(query)) result.add(box);
        }
    }
    public RayTraceResult rayTrace(BlockPos pos, Vec3d start, Vec3d end) {
        Vec3d localStart = start.subtract(new Vec3d(pos)), localEnd = end.subtract(new Vec3d(pos));
        RayTraceResult nearest = null;
        for (AxisAlignedBB box : boxes) {
            RayTraceResult hit = box.calculateIntercept(localStart, localEnd);
            if (hit != null && (nearest == null || hit.hitVec.squareDistanceTo(localStart) < nearest.hitVec.squareDistanceTo(localStart))) nearest = hit;
        }
        return nearest == null ? null : new RayTraceResult(nearest.hitVec.add(new Vec3d(pos)), nearest.sideHit, pos);
    }
    public BlockShape rotate(EnumFacing.Axis axis, int quarterTurns) {
        List<AxisAlignedBB> rotated = new ArrayList<>();
        for (AxisAlignedBB box : boxes) {
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            for (int corner = 0; corner < 8; corner++) {
                double x = (corner & 1) == 0 ? box.minX : box.maxX;
                double y = (corner & 2) == 0 ? box.minY : box.maxY;
                double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
                for (int turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++) {
                    double previousX = x, previousY = y;
                    switch (axis) {
                        case X -> { y = 1 - z; z = previousY; }
                        case Y -> { x = 1 - z; z = previousX; }
                        case Z -> { x = 1 - y; y = previousX; }
                    }
                }
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            rotated.add(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return new BlockShape(rotated);
    }
    public Map<EnumFacing, BlockShape> fromUp() {
        Map<EnumFacing, BlockShape> shapes = new EnumMap<>(EnumFacing.class);
        shapes.put(EnumFacing.UP, this); shapes.put(EnumFacing.DOWN, rotate(EnumFacing.Axis.X, 2));
        shapes.put(EnumFacing.NORTH, rotate(EnumFacing.Axis.X, 3)); shapes.put(EnumFacing.SOUTH, rotate(EnumFacing.Axis.X, 1));
        shapes.put(EnumFacing.WEST, rotate(EnumFacing.Axis.Z, 1)); shapes.put(EnumFacing.EAST, rotate(EnumFacing.Axis.Z, 3));
        return Collections.unmodifiableMap(shapes);
    }
    public Map<EnumFacing, BlockShape> horizontal(EnumFacing original) {
        Map<EnumFacing, BlockShape> shapes = new EnumMap<>(EnumFacing.class);
        for (EnumFacing facing : EnumFacing.HORIZONTALS)
            shapes.put(facing, rotate(EnumFacing.Axis.Y, facing.getHorizontalIndex() - original.getHorizontalIndex()));
        return Collections.unmodifiableMap(shapes);
    }
}
