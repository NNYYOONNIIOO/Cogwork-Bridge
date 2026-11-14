package com.cogwork.cogworkbridge.content.kinetics.placement;

import net.minecraft.util.EnumFacing;

public final class KineticPlacementContext {
    private static final ThreadLocal<EnumFacing.Axis> FORCED_AXIS = new ThreadLocal<>();

    private KineticPlacementContext() {
    }

    public static EnumFacing.Axis getForcedAxis() {
        return FORCED_AXIS.get();
    }

    static void setForcedAxis(EnumFacing.Axis axis) {
        FORCED_AXIS.set(axis);
    }

    static void clear() {
        FORCED_AXIS.remove();
    }
}
