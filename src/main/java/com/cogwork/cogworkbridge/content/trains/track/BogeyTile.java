package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;

public final class BogeyTile extends TileEntity {
    private int renderedTick = -1;
    private float previousAngle, angle, lastYaw;
    private boolean hasPosition;
    private double lastX, lastY, lastZ;
    private boolean tutorialWheels;
    public void animateTutorialWheels(float distance) {
        tutorialWheels = true; previousAngle = angle;
        double radius = getBlockType() instanceof BogeyBlock bogey && bogey.isLarge() ? 12.5D / 16D : 6.5D / 16D;
        angle += (float) Math.toDegrees(distance / radius);
    }
    @Override public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (tutorialWheels) { tag.setBoolean("TutorialWheels", true); tag.setFloat("TutorialAngle", angle); }
        return tag;
    }
    @Override public void readFromNBT(net.minecraft.nbt.NBTTagCompound tag) {
        super.readFromNBT(tag); tutorialWheels = tag.getBoolean("TutorialWheels");
        if (tutorialWheels) previousAngle = angle = tag.getFloat("TutorialAngle");
    }

    /** Integrate actual client carriage displacement, including reverse and slopes. */
    public float wheelAngle(float partial) {
        if (tutorialWheels) return previousAngle + (angle - previousAngle) * partial;
        if (!(world instanceof FakeWorld fake)
                || !(fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity carriage)) return 0F;
        if (renderedTick != carriage.ticksExisted) {
            renderedTick = carriage.ticksExisted;
            previousAngle = angle;
            float yawNow = carriage.getAngle();
            if (hasPosition) {
                double dx = carriage.posX - lastX, dy = carriage.posY - lastY, dz = carriage.posZ - lastZ;
                double yaw = Math.toRadians(lastYaw + MathHelper.wrapDegrees(yawNow - lastYaw) * .5F);
                EnumFacing.Axis axis = getBlockType() instanceof BogeyBlock
                        ? getBlockType().getStateFromMeta(getBlockMetadata()).getValue(BogeyBlock.AXIS) : EnumFacing.Axis.Z;
                double along = axis == EnumFacing.Axis.X ? dx * Math.cos(yaw) - dz * Math.sin(yaw)
                        : dx * Math.sin(yaw) + dz * Math.cos(yaw);
                if (Math.abs(along) > 1E-7D) {
                    double distance = Math.copySign(Math.sqrt(dx * dx + dy * dy + dz * dz), along);
                    double radius = getBlockType() instanceof BogeyBlock bogey && bogey.isLarge() ? 12.5D / 16D : 6.5D / 16D;
                    angle += (float) Math.toDegrees(distance / radius);
                }
            }
            lastX = carriage.posX; lastY = carriage.posY; lastZ = carriage.posZ;
            lastYaw = yawNow;
            hasPosition = true;
        }
        return previousAngle + (angle - previousAngle) * partial;
    }

    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(2D); }
}
