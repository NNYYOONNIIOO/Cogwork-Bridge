package com.cogwork.cogworkbridge.content.kinetics.speedController;

import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.nbt.NBTTagCompound;

/** Persists the controller target and turns its large-cog link into an absolute-speed edge. */
public final class SpeedControllerTileEntity extends KineticTileEntity {
    public static final int DEFAULT_SPEED = 16;
    public static final int MAX_SPEED = 256;

    private int targetSpeed = DEFAULT_SPEED;

    public int getTargetSpeed() {
        return targetSpeed;
    }

    public void setTargetSpeed(int speed) {
        int next = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, speed));
        if (targetSpeed == next) {
            return;
        }
        targetSpeed = next;
        if (world != null && !world.isRemote && getSpeed() != 0F
                && world.getBlockState(pos.up()).getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock cog && cog.isLarge())
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "speed_controller");
        markDirty();
        needsSync = true;
        if (world != null && !world.isRemote) {
            KineticNetworkManager.discard(world);
            refreshKinetics();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("TargetSpeed", targetSpeed);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        targetSpeed = tag.hasKey("TargetSpeed")
                ? Math.max(-MAX_SPEED, Math.min(MAX_SPEED, tag.getInteger("TargetSpeed")))
                : DEFAULT_SPEED;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("TargetSpeed", targetSpeed);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        targetSpeed = tag.hasKey("TargetSpeed")
                ? Math.max(-MAX_SPEED, Math.min(MAX_SPEED, tag.getInteger("TargetSpeed")))
                : DEFAULT_SPEED;
    }
}
