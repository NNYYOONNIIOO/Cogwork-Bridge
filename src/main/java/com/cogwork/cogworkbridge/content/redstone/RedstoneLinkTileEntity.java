package com.cogwork.cogworkbridge.content.redstone;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

public final class RedstoneLinkTileEntity extends TileEntity implements ITickable {
    private ItemStack first = ItemStack.EMPTY, second = ItemStack.EMPTY;
    private int receivedPower, transmittedPower;
    private boolean receiver, registered;

    public RedstoneLinkNetwork.Key key() { return RedstoneLinkNetwork.key(first, second); }
    public ItemStack getFrequency(int index) { return (index == 0 ? first : second).copy(); }
    public boolean isReceiver() { return receiver; }
    public int getReceivedPower() { return receiver ? receivedPower : 0; }
    public int getTransmittedPower() { return receiver ? 0 : transmittedPower; }
    public boolean isPowered() { return receiver ? receivedPower > 0 : transmittedPower > 0; }

    public void setFrequency(int index, ItemStack stack) {
        RedstoneLinkNetwork.Key old = key();
        ItemStack copy = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!copy.isEmpty()) copy.setCount(1);
        if (index == 0) first = copy; else second = copy;
        if (registered) { RedstoneLinkNetwork.remove(this, old); RedstoneLinkNetwork.add(this); }
        changed();
    }
    public void toggleReceiver() {
        receiver = !receiver;
        receivedPower = 0;
        if (world != null && !world.isRemote) {
            samplePower();
            RedstoneLinkNetwork.changed(this);
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
        }
        changed();
    }
    public void setReceivedPower(int power) {
        if (receivedPower == power) return;
        receivedPower = power;
        if (world != null && !world.isRemote) {
            world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
            if (world.getBlockState(pos).getBlock() instanceof RedstoneLinkBlock)
                world.notifyNeighborsOfStateChange(pos.offset(world.getBlockState(pos).getValue(RedstoneLinkBlock.FACING).getOpposite()), getBlockType(), false);
        }
        changed();
    }
    private void samplePower() {
        int power = 0;
        if (!receiver && world != null) for (EnumFacing side : EnumFacing.VALUES)
            power = Math.max(power, world.getRedstonePower(pos.offset(side), side));
        if (power == transmittedPower) return;
        transmittedPower = power;
        if (registered) RedstoneLinkNetwork.changed(this);
        changed();
    }
    @Override public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote && !registered) { registered = true; samplePower(); RedstoneLinkNetwork.add(this); }
    }
    private void unregister() { if (registered && world != null && !world.isRemote) { registered = false; RedstoneLinkNetwork.remove(this, key()); } }
    @Override public void invalidate() { unregister(); super.invalidate(); }
    @Override public void onChunkUnload() { unregister(); super.onChunkUnload(); }
    @Override public void update() { if (world != null && !world.isRemote && !receiver) samplePower(); }
    private void changed() { markDirty(); if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); if (!first.isEmpty()) tag.setTag("First", first.writeToNBT(new NBTTagCompound()));
        if (!second.isEmpty()) tag.setTag("Second", second.writeToNBT(new NBTTagCompound()));
        tag.setBoolean("Receiver", receiver); tag.setInteger("Power", receivedPower);
        tag.setInteger("TransmittedPower", transmittedPower); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); first = tag.hasKey("First", 10) ? new ItemStack(tag.getCompoundTag("First")) : ItemStack.EMPTY;
        second = tag.hasKey("Second", 10) ? new ItemStack(tag.getCompoundTag("Second")) : ItemStack.EMPTY;
        receiver = tag.getBoolean("Receiver"); receivedPower = Math.max(0, Math.min(15, tag.getInteger("Power")));
        transmittedPower = Math.max(0, Math.min(15, tag.getInteger("TransmittedPower")));
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
