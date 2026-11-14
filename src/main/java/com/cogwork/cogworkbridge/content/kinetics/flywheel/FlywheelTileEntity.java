package com.cogwork.cogworkbridge.content.kinetics.flywheel;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.util.math.AxisAlignedBB;
public final class FlywheelTileEntity extends KineticTileEntity {
    private float visualSpeed, angle;
    @Override public void update() {
        if (world != null && world.isRemote) {
            visualSpeed += (getSpeed() - visualSpeed) / 64F;
            angle = (angle + visualSpeed * .3F) % 360F;
        } else super.update();
    }
    public float angle(float partial) { return angle + visualSpeed * .3F * partial; }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(2); }
}
