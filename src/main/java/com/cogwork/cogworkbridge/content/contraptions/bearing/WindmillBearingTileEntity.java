package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.Rotation;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;

public final class WindmillBearingTileEntity extends MechanicalBearingTileEntity {
    public static final int MAX_GENERATED_SPEED = 16;
    private boolean reversed;
    private int sails;
    public boolean isReversed() { return reversed; }
    public void setReversed(boolean reversed) { if (this.reversed != reversed) { this.reversed = reversed; onAssemblyChanged(); } }
    @Override public boolean isSource() { return true; }
    @Override public float getStressImpact() { return 0; }
    @Override public float getStressCapacity() { return 512F; }
    @Override protected Rotation computeSourceRotation() {
        return isAssembled() ? Rotation.of(getAxis(), Math.max(1, Math.min(MAX_GENERATED_SPEED, sails / 8)) * (reversed ? -1 : 1)) : Rotation.NONE;
    }
    @Override protected boolean canStartAssembly() { return true; }
    private static int countSails(BearingContraption contraption) {
        int count = 0;
        for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
            if (snapshot.state().getBlock() instanceof SailBlock || snapshot.state().getBlock() == Blocks.WOOL) count++;
        }
        return count;
    }
    @Override protected boolean acceptsAssembly(BearingContraption contraption) {
        int count = countSails(contraption);
        if (count < 8) return false;
        sails = count; return true;
    }
    @Override protected void onAssemblyChanged() {
        if (world != null && !world.isRemote) { KineticNetworkManager.discard(world); markDirty(); sync(); }
    }
    @Override public void syncSavedContraption(BearingContraption contraption) {
        super.syncSavedContraption(contraption);
        int count = countSails(contraption);
        if (sails != count) { sails = count; onAssemblyChanged(); }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("Sails", sails); tag.setBoolean("Reverse", reversed); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); sails = tag.getInteger("Sails"); reversed = tag.getBoolean("Reverse");
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag(); tag.setInteger("Sails", sails); tag.setBoolean("Reverse", reversed); return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag); sails = tag.getInteger("Sails"); reversed = tag.getBoolean("Reverse");
    }
}
