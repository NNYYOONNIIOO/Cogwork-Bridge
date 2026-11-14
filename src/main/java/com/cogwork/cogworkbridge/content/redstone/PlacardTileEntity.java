package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

public final class PlacardTileEntity extends TileEntity implements ITickable {
    private ItemStack item = ItemStack.EMPTY; private int remaining;
    public ItemStack getItem() { return item.copy(); }
    public boolean isPowered() { return remaining > 0; }
    public void setItem(ItemStack stack) { item = stack.copy(); changed(false); }
    public void pulse() { remaining = 19; changed(true); }
    @Override public void update() { if (world != null && !world.isRemote && remaining > 0 && --remaining == 0) changed(true); }
    private void changed(boolean power) {
        markDirty(); if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        if (power) { var state = world.getBlockState(pos); world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
            if (state.getBlock() instanceof PlacardBlock) world.notifyNeighborsOfStateChange(pos.offset(PlacardBlock.outward(state).getOpposite()), state.getBlock(), false); }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); if (!item.isEmpty()) tag.setTag("Item", item.writeToNBT(new NBTTagCompound())); tag.setInteger("PoweredTicks", remaining); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); item = tag.hasKey("Item", 10) ? new ItemStack(tag.getCompoundTag("Item")) : ItemStack.EMPTY; remaining = tag.getInteger("PoweredTicks"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
