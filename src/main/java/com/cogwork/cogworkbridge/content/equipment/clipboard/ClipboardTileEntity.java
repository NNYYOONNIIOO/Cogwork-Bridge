package com.cogwork.cogworkbridge.content.equipment.clipboard;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public final class ClipboardTileEntity extends TileEntity {
    private NBTTagCompound content = new NBTTagCompound();

    public NBTTagCompound content() { return content.copy(); }
    public boolean isWritten() { return ClipboardData.isWritten(content); }
    public void setContent(NBTTagCompound data) {
        content = data.copy();
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setTag(ClipboardData.KEY, content.copy()); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); content = tag.getCompoundTag(ClipboardData.KEY).copy();
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        boolean wasWritten = isWritten();
        readFromNBT(packet.getNbtCompound());
        if (world != null && world.isRemote && wasWritten != isWritten())
            world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
