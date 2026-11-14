package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import java.util.UUID;

/** Stores an addressed order and emits a request on each rising redstone edge. */
public final class RedstoneRequesterTileEntity extends TileEntity {
    private UUID network = UUID.randomUUID();
    private java.util.List<StockOrder.Line> order = new java.util.ArrayList<>();
    private java.util.List<StockOrder.Craft> crafts = new java.util.ArrayList<>();
    public java.util.List<StockOrder.Craft> crafts() { return StockOrder.readCrafts(StockOrder.writeCrafts(crafts)); }
    public void setCrafts(java.util.List<StockOrder.Craft> value) {
        crafts = StockOrder.readCrafts(StockOrder.writeCrafts(value));
        if (!StockOrder.matchesCrafts(order, crafts)) crafts.clear();
        changed();
    }
    private String address = "";
    private boolean powered, success, partial;

    public UUID getNetwork() { return network; }
    public boolean succeeded() { return success; }
    public boolean allowsPartial() { return partial; }
    public String address() { return address; }
    public java.util.List<StockOrder.Line> order() { return StockOrder.normalize(order); }
    public void setNetwork(UUID value) { if (value != null && !value.equals(network)) { network = value; changed(); } }
    public void configure(ItemStack stack, int count, String destination) {
        configure(java.util.List.of(new StockOrder.Line(stack, count)), destination);
    }
    public void configure(java.util.List<StockOrder.Line> lines, String destination) {
        order = StockOrder.normalize(lines);
        if (!StockOrder.matchesCrafts(order, crafts)) crafts.clear();
        address = destination.substring(0, Math.min(25, destination.length())); changed();
    }
    public void configure(java.util.List<StockOrder.Line> lines, String destination, boolean allowPartial) {
        partial = allowPartial; configure(lines, destination);
    }
    public void readItem(ItemStack stack) {
        if (!stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag.hasUniqueId("LogisticsFrequency")) network = tag.getUniqueId("LogisticsFrequency");
        if (tag.hasKey("AutoRequest", 10)) {
            NBTTagCompound request = tag.getCompoundTag("AutoRequest");
            order = request.hasKey("Items", 9) ? StockOrder.read(request.getTagList("Items", 10))
                    : StockOrder.normalize(java.util.List.of(new StockOrder.Line(new ItemStack(request.getCompoundTag("Item")), request.getInteger("Amount"))));
            address = request.getString("Address");
            if (address.length() > 25) address = address.substring(0, 25);
            partial = request.getBoolean("Partial");
            setCrafts(StockOrder.readCrafts(request.getTagList("Crafts", 10)));
        }
        changed();
    }
    public void writeItem(ItemStack stack) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setUniqueId("LogisticsFrequency", network);
        NBTTagCompound request = new NBTTagCompound();
        request.setTag("Items", StockOrder.write(order));
        request.setString("Address", address);
        request.setBoolean("Partial", partial);
        request.setTag("Crafts", StockOrder.writeCrafts(crafts));
        tag.setTag("AutoRequest", request);
        stack.setTagCompound(tag);
    }
    public void updatePower() {
        if (world == null || world.isRemote) return;
        boolean next = world.isBlockPowered(pos);
        if (next == powered) return;
        powered = next; success = false;
        if (next && !order.isEmpty() && world.getMinecraftServer() != null) {
            success = StockLinkNetwork.requestEncodedOrder(world.getMinecraftServer(), network, order, address, partial, crafts) > 0;
            world.playSound(null, pos, success ? net.minecraft.init.SoundEvents.BLOCK_NOTE_PLING : net.minecraft.init.SoundEvents.ENTITY_VILLAGER_NO,
                    net.minecraft.util.SoundCategory.BLOCKS, .5F, success ? 1.5F : 1F);
            if (success) world.playSound(null, pos, com.cogwork.cogworkbridge.registry.AllSounds.STOCK_LINK,
                    net.minecraft.util.SoundCategory.BLOCKS, 1F, 1F);
            if (success) world.addBlockEvent(pos, world.getBlockState(pos).getBlock(), 17, 1);
            else if (world instanceof net.minecraft.world.WorldServer server)
                server.spawnParticle(net.minecraft.util.EnumParticleTypes.CRIT_MAGIC,
                        pos.getX() + .5D, pos.getY() + 1D, pos.getZ() + .5D, 3, .1D, .1D, .1D, .01D);
        }
        changed(); world.updateComparatorOutputLevel(pos, world.getBlockState(pos).getBlock());
    }
    private void changed() { markDirty(); if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2); }
    @Override public boolean receiveClientEvent(int id, int value) {
        if (id == 17) {
            if (world != null && world.isRemote) com.cogwork.cogworkbridge.CogworkBridge.proxy.requesterEffect(world, pos);
            return true;
        }
        return super.receiveClientEvent(id, value);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setUniqueId("Frequency", network);
        tag.setTag("Items", StockOrder.write(order)); tag.setTag("Crafts", StockOrder.writeCrafts(crafts)); tag.setString("Address", address);
        tag.setBoolean("Powered", powered); tag.setBoolean("Success", success); tag.setBoolean("Partial", partial); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); if (tag.hasUniqueId("Frequency")) network = tag.getUniqueId("Frequency");
        order = tag.hasKey("Items", 9) ? StockOrder.read(tag.getTagList("Items", 10))
                : StockOrder.normalize(java.util.List.of(new StockOrder.Line(new ItemStack(tag.getCompoundTag("Item")), tag.getInteger("Amount"))));
        address = tag.getString("Address");
        crafts = StockOrder.readCrafts(tag.getTagList("Crafts", 10));
        if (!StockOrder.matchesCrafts(order, crafts)) crafts.clear();
        if (address.length() > 25) address = address.substring(0, 25);
        powered = tag.getBoolean("Powered"); success = tag.getBoolean("Success"); partial = tag.getBoolean("Partial");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
