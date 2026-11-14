package com.cogwork.cogworkbridge.content.kinetics.gauge;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;

/** Gauge readings come from the same synchronized network as the machine. */
public final class GaugeTileEntity extends KineticTileEntity {
    private float dial;
    private float previousDial;
    private float dialTarget;
    private int lastComparator = -1;

    @Override public void update() {
        super.update();
        if (world == null) return;
        if (world.isRemote) {
            previousDial = dial;
            dial += (dialTarget - dial) * .125F;
        } else {
            float nextTarget = calculateDialTarget();
            if (Float.compare(dialTarget, nextTarget) != 0) {
                dialTarget = nextTarget;
                markDirty();
                sync();
            }
            int signal = getComparatorValue();
            if (signal != lastComparator) {
                lastComparator = signal;
                world.updateComparatorOutputLevel(pos, getBlockType());
            }
        }
    }

    private float calculateDialTarget() {
        IBlockState state = getContraptionRenderState() != null ? getContraptionRenderState()
                : world == null ? null : world.getBlockState(pos);
        if (state == null || !(state.getBlock() instanceof GaugeBlock block)) return 0F;
        if (block.isStressGauge()) {
            if (isOverstressed()) return 1.125F;
            return getNetworkCapacity() <= 0F ? 0F : MathHelper.clamp(getNetworkStress() / getNetworkCapacity(), 0F, 1.125F);
        }
        float speed = Math.abs(getSpeed());
        if (speed < 16F) return speed / 16F * .45F;
        if (speed < 64F) return .45F + (speed - 16F) / 48F * .30F;
        return Math.min(1.125F, .75F + (speed - 64F) / 192F * .375F);
    }

    public float getDialTarget() { return dialTarget; }
    public float getDial(float partialTicks) { return previousDial + (dial - previousDial) * partialTicks; }
    public int getComparatorValue() { return MathHelper.ceil(MathHelper.clamp(getDialTarget() * 14F, 0F, 15F)); }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setFloat("GaugeTarget", dialTarget);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        dialTarget = tag.getFloat("GaugeTarget");
    }

    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setFloat("GaugeTarget", dialTarget);
        return tag;
    }

    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        dialTarget = tag.getFloat("GaugeTarget");
    }
}
