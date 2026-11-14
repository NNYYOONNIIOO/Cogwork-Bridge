package com.cogwork.cogworkbridge.content.kinetics.crank;

import net.minecraft.nbt.NBTTagCompound;

/** One timed turn per activation. The bearing consumes the precise angle as a sequence. */
public final class ValveHandleTileEntity extends HandCrankTileEntity {
    private int targetDegrees = 45;
    private int cooldown;
    private long sequenceId;
    private int totalTicks;
    private float startAngle, targetAngle;

    public int getTargetDegrees() { return targetDegrees; }
    public long getSequenceId() { return sequenceId; }
    public void setTargetDegrees(int degrees) {
        targetDegrees = Math.max(-180, Math.min(180, degrees));
        if (targetDegrees == 0) targetDegrees = 1;
        sourceChanged();
    }
    @Override public void turn(boolean reverse) {
        if (inUse > 0 || cooldown > 0 || getSpeed() != 0) return;
        backwards = reverse ^ targetDegrees < 0;
        startAngle = targetAngle;
        float step = Math.abs(targetDegrees) > 135 ? 180F : 90F;
        float axisSign = world != null && world.getBlockState(pos).getValue(HandCrankBlock.FACING).getAxisDirection()
                == net.minecraft.util.EnumFacing.AxisDirection.NEGATIVE ? -1F : 1F;
        targetAngle = startAngle + step * (backwards ? -1F : 1F) * axisSign;
        inUse = (int) Math.ceil(Math.abs(targetDegrees) / 9.6D) + 2;
        totalTicks = inUse;
        cooldown = 4;
        sequenceId++;
        sourceChanged();
    }
    @Override public void update() {
        super.update();
        if (inUse == 0 && cooldown > 0) cooldown--;
    }
    public boolean isTurning() { return inUse > 0; }
    @Override public float getHandleAngle(float partial) {
        if (inUse > 0 && totalTicks > 0) {
            float progress = Math.max(0F, Math.min(1F, (totalTicks - inUse + partial) / totalTicks));
            return startAngle + (targetAngle - startAngle) * progress;
        }
        return getSpeed() != 0 && inUse == 0 && cooldown == 0 ? super.getHandleAngle(partial) : targetAngle;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("TargetDegrees", targetDegrees); tag.setInteger("Cooldown", cooldown);
        tag.setLong("ValveSequence", sequenceId); tag.setInteger("TotalTicks", totalTicks);
        tag.setFloat("StartAngle", startAngle); tag.setFloat("TargetAngle", targetAngle); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); targetDegrees = tag.hasKey("TargetDegrees") ? Math.max(-180, Math.min(180, tag.getInteger("TargetDegrees"))) : 45;
        if (targetDegrees == 0) targetDegrees = 1;
        cooldown = tag.getInteger("Cooldown"); sequenceId = tag.getLong("ValveSequence"); totalTicks = tag.getInteger("TotalTicks");
        startAngle = tag.getFloat("StartAngle"); targetAngle = tag.getFloat("TargetAngle");
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag(); tag.setInteger("TargetDegrees", targetDegrees);
        tag.setLong("ValveSequence", sequenceId); tag.setInteger("TotalTicks", totalTicks); tag.setInteger("Cooldown", cooldown);
        tag.setFloat("StartAngle", startAngle); tag.setFloat("TargetAngle", targetAngle); return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag); targetDegrees = tag.hasKey("TargetDegrees") ? tag.getInteger("TargetDegrees") : 45;
        sequenceId = tag.getLong("ValveSequence"); totalTicks = tag.getInteger("TotalTicks"); cooldown = tag.getInteger("Cooldown");
        startAngle = tag.getFloat("StartAngle"); targetAngle = tag.getFloat("TargetAngle");
    }
}
