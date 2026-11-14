package com.cogwork.cogworkbridge.content.kinetics.motor;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.Rotation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/** Drives its own axis at a fixed speed; rejects connections on the wrong faces. */
public class CreativeMotorTileEntity extends KineticTileEntity {

    private static final float DEFAULT_SPEED = 16.0F;
    public static final float MAX_SPEED = 256.0F;

    private float configuredSpeed = DEFAULT_SPEED;

    @Override
    public boolean isSource() {
        return true;
    }

    @Override
    protected Rotation computeSourceRotation() {
        float axisSpeed = configuredSpeed;
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof CreativeMotorBlock
                    && state.getValue(CreativeMotorBlock.FACING).getAxisDirection()
                            == EnumFacing.AxisDirection.NEGATIVE) {
                axisSpeed = -axisSpeed;
            }
        }
        return Rotation.of(getAxis(), axisSpeed);
    }

    @Override
    protected boolean acceptsConnection(EnumFacing facing) {
        return facing.getAxis() == getAxis();
    }

    @Override
    public float getStressCapacity() {
        return 16384.0F;
    }

    public float getConfiguredSpeed() {
        return configuredSpeed;
    }

    public void setConfiguredSpeed(float speed) {
        float next = limitSpeed(speed);
        if (Float.compare(configuredSpeed, next) == 0) {
            return;
        }
        configuredSpeed = next;
        if (world != null && !world.isRemote) {
            KineticNetworkManager.discard(world);
            refreshKinetics();
        } else {
            markDirty();
            sync();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setFloat("ConfiguredSpeed", configuredSpeed);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        configuredSpeed = compound.hasKey("ConfiguredSpeed")
                ? limitSpeed(compound.getFloat("ConfiguredSpeed")) : DEFAULT_SPEED;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setFloat("ConfiguredSpeed", configuredSpeed);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        configuredSpeed = tag.hasKey("ConfiguredSpeed")
                ? limitSpeed(tag.getFloat("ConfiguredSpeed")) : DEFAULT_SPEED;
    }

    private static float limitSpeed(float speed) {
        return Float.isFinite(speed) ? Math.max(-MAX_SPEED, Math.min(MAX_SPEED, speed)) : DEFAULT_SPEED;
    }
}
