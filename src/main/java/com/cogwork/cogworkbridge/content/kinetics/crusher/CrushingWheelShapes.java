package com.cogwork.cogworkbridge.content.kinetics.crusher;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

final class CrushingWheelShapes {
    private static final double LOW = 2.0D / 16.0D;
    private static final double HIGH = 14.0D / 16.0D;
    private static final double PLANE_LOW = 7.0D / 16.0D;
    private static final double PLANE_HIGH = 9.0D / 16.0D;

    private CrushingWheelShapes() { }

    static AxisAlignedBB bounds(EnumFacing facing) {
        switch (facing.getAxis()) {
            case X: return new AxisAlignedBB(PLANE_LOW, LOW, LOW, PLANE_HIGH, HIGH, HIGH);
            case Y: return new AxisAlignedBB(LOW, PLANE_LOW, LOW, HIGH, PLANE_HIGH, HIGH);
            case Z: return new AxisAlignedBB(LOW, LOW, PLANE_LOW, HIGH, HIGH, PLANE_HIGH);
            default: return BlockBounds.FULL;
        }
    }

    private static final class BlockBounds {
        private static final AxisAlignedBB FULL = new AxisAlignedBB(0, 0, 0, 1, 1, 1);
    }
}
