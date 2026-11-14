package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

public final class AnalogLeverTileEntity extends TileEntity implements ITickable {
    private int value, changeTimer;
    private float animation, previousAnimation;
    public int value() { return value; }
    public float animation(float partial) { return previousAnimation + (animation - previousAnimation) * partial; }
    public void change(int delta) {
        int next = Math.max(0, Math.min(15, value + delta));
        if (next != value) { value = next; changeTimer = 15; changed(); }
    }
    @Override public void update() {
        if (world == null) return;
        previousAnimation = animation;
        if (world.isRemote) animation += (value - animation) * .2F;
        else if (changeTimer > 0 && --changeTimer == 0) {
            markDirty();
            AnalogLeverBlock.notifyOutputs(world, pos, world.getBlockState(pos));
        }
    }
    private void changed() {
        markDirty();
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else if (world != null) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); tag.setInteger("State", value); tag.setInteger("ChangeTimer", changeTimer); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); value = Math.max(0, Math.min(15, tag.getInteger("State"))); changeTimer = Math.max(0, tag.getInteger("ChangeTimer")); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
