package com.cogwork.cogworkbridge.content.kinetics.cogwheel;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.util.EnumFacing;

/** Meshes on the four faces perpendicular to its axis. */
public class CogwheelTileEntity extends KineticTileEntity {
    @Override
    protected boolean isGear() {
        return true;
    }

    @Override
    protected boolean acceptsConnection(EnumFacing facing) {
        return facing.getAxis() != getAxis();
    }

    @Override
    public float getStressImpact() {
        return 0.5F;
    }
}
