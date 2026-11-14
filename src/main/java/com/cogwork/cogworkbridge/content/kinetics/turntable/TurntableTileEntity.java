package com.cogwork.cogworkbridge.content.kinetics.turntable;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.util.EnumFacing;
public final class TurntableTileEntity extends KineticTileEntity {
    @Override protected boolean acceptsConnection(EnumFacing facing) { return facing == EnumFacing.DOWN; }
}
