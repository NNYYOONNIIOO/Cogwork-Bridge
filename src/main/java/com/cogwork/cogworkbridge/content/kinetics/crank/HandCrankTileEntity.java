package com.cogwork.cogworkbridge.content.kinetics.crank;

import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.kinetic.Rotation;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

public class HandCrankTileEntity extends KineticTileEntity {
    public static final float GENERATED_SPEED = 32F;
    protected int inUse;
    protected boolean backwards;
    private float visualAngle;
    private float visualVelocity;
    private float previousVisualAngle;

    public void turn(boolean reverse) {
        boolean changed = inUse == 0 || backwards != reverse;
        inUse = 10;
        backwards = reverse;
        if (changed) sourceChanged();
    }
    protected void sourceChanged() {
        if (world == null || world.isRemote) return;
        KineticNetworkManager.discard(world);
        markDirty();
        sync();
    }
    @Override public boolean isSource() { return true; }
    @Override protected Rotation computeSourceRotation() {
        if (inUse <= 0 || world == null || !(world.getBlockState(pos).getBlock() instanceof HandCrankBlock)) return Rotation.NONE;
        EnumFacing facing = world.getBlockState(pos).getValue(HandCrankBlock.FACING);
        float sign = facing.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE ? -1 : 1;
        return Rotation.of(facing.getAxis(), (backwards ? -GENERATED_SPEED : GENERATED_SPEED) * sign);
    }
    @Override public float getStressCapacity() { return 8.0F; }
    @Override public void update() {
        super.update();
        if (world == null) return;
        previousVisualAngle = visualAngle;
        visualVelocity += (getSpeed() * .3F - visualVelocity) / 4F;
        visualAngle += visualVelocity;
        if (inUse > 0 && --inUse == 0 && !world.isRemote) sourceChanged();
    }
    public float getHandleAngle(float partial) { return previousVisualAngle + (visualAngle - previousVisualAngle) * partial; }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("InUse", inUse); tag.setBoolean("Backwards", backwards);
        tag.setFloat("HandleAngle", visualAngle); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); inUse = Math.max(0, tag.getInteger("InUse")); backwards = tag.getBoolean("Backwards");
        visualAngle = tag.getFloat("HandleAngle"); previousVisualAngle = visualAngle;
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("InUse", inUse); tag.setBoolean("Backwards", backwards); tag.setFloat("HandleAngle", visualAngle);
        return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag); inUse = tag.getInteger("InUse"); backwards = tag.getBoolean("Backwards");
        visualAngle = tag.getFloat("HandleAngle"); previousVisualAngle = visualAngle;
    }
}
