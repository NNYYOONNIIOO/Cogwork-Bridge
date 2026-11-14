package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

import javax.annotation.Nullable;
import java.util.UUID;

public final class StockLinkTileEntity extends TileEntity implements ITickable {
    private UUID network = UUID.randomUUID();
    private int priority;
    private boolean registered;

    public UUID getNetwork() { return network; }
    public int getPriority() { return priority; }

    public void setNetwork(UUID next) {
        if (next == null || next.equals(network)) return;
        if (registered) StockLinkNetwork.remove(this, network);
        network = next;
        if (registered) StockLinkNetwork.add(this);
        changed();
    }

    @Nullable public PackagerTileEntity getPackager() {
        if (world == null || priority >= 15 || !(world.getBlockState(pos).getBlock() instanceof StockLinkBlock)) return null;
        EnumFacing outward = world.getBlockState(pos).getValue(StockLinkBlock.FACING);
        TileEntity candidate = world.getTileEntity(pos.offset(outward.getOpposite()));
        return candidate instanceof PackagerTileEntity packager ? packager : null;
    }

    @Override public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote && !registered) { registered = true; StockLinkNetwork.add(this); }
    }

    private void unregister() {
        if (registered) { StockLinkNetwork.remove(this, network); registered = false; }
    }

    @Override public void invalidate() { unregister(); super.invalidate(); }
    @Override public void onChunkUnload() { unregister(); super.onChunkUnload(); }

    @Override public void update() {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof StockLinkBlock)) return;
        EnumFacing support = world.getBlockState(pos).getValue(StockLinkBlock.FACING).getOpposite();
        int next = 0;
        for (EnumFacing side : EnumFacing.values())
            if (side != support) next = Math.max(next, world.getRedstonePower(pos.offset(side), side));
        if (next != priority) { priority = next; changed(); }
    }

    private void changed() {
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setUniqueId("Frequency", network);
        tag.setInteger("Priority", priority);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasUniqueId("Frequency")) network = tag.getUniqueId("Frequency");
        priority = Math.max(0, Math.min(15, tag.getInteger("Priority")));
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
