package com.cogwork.cogworkbridge.content.kinetics.crusher;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;

public final class CrushingWheelTileEntity extends KineticTileEntity {
    private float lastControllerSpeed = Float.NaN;

    @Override
    public void update() {
        super.update();
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof CrushingWheelBlock)) return;
        float speed = getSpeed();
        if (Float.compare(speed, lastControllerSpeed) != 0) {
            lastControllerSpeed = speed;
            IBlockState state = world.getBlockState(pos);
            CrushingWheelBlock.updateControllers(world, pos, state);
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(1.0D);
    }
}
