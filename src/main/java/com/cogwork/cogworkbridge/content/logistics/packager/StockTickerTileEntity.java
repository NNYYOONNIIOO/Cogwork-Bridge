package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import javax.annotation.Nullable;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public final class StockTickerTileEntity extends TileEntity {
    private UUID network = UUID.randomUUID();
    private String previousAddress = "";
    private boolean clientLocked;
    private final List<net.minecraft.item.ItemStack> categories = new ArrayList<>();
    private final java.util.Map<UUID, Long> hiddenCategories = new java.util.HashMap<>();
    private final ItemStackHandler receivedPayments = new ItemStackHandler(27) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };
    public UUID getNetwork() { return network; }
    public boolean isLocked() { return world != null && world.isRemote ? clientLocked : LogisticsNetworkSecurity.isLocked(world, network); }
    public String previousAddress() { return previousAddress; }
    public void setPreviousAddress(String address) {
        previousAddress = address.substring(0, Math.min(25, address.length()));
        markDirty();
        if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }
    public ItemStackHandler payments() { return receivedPayments; }
    public List<net.minecraft.item.ItemStack> categories() { return java.util.Collections.unmodifiableList(categories); }
    public boolean categoryHidden(UUID player, int index) { return index >= 0 && index < 45 && (hiddenCategories.getOrDefault(player, 0L) & 1L << index) != 0; }
    public void toggleCategory(UUID player, int index) {
        if (index < 0 || index >= categories.size()) return;
        long flags = hiddenCategories.getOrDefault(player, 0L) ^ 1L << index;
        if (flags == 0) hiddenCategories.remove(player); else hiddenCategories.put(player, flags);
        categoriesChanged();
    }
    public boolean addCategory(net.minecraft.item.ItemStack filter) {
        if (categories.size() >= 45 || !com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(filter)) return false;
        var copy = filter.copy(); copy.setCount(1); categories.add(copy); categoriesChanged(); return true;
    }
    public net.minecraft.item.ItemStack removeCategory(int index) {
        if (index < 0 || index >= categories.size()) return net.minecraft.item.ItemStack.EMPTY;
        var removed = categories.remove(index);
        hiddenCategories.replaceAll((player, bits) -> (bits & ((1L << index) - 1)) | ((bits >>> (index + 1)) << index));
        hiddenCategories.values().removeIf(bits -> bits == 0); categoriesChanged(); return removed;
    }
    private void categoriesChanged() {
        markDirty(); if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }
    public void setNetwork(UUID network) {
        if (network == null || this.network.equals(network)) return;
        this.network = network;
        markDirty();
        if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setUniqueId("Frequency", network); tag.setString("PreviousAddress", previousAddress);
        tag.setTag("Payments", receivedPayments.serializeNBT()); NBTTagList filters = new NBTTagList();
        for (var filter : categories) filters.appendTag(filter.writeToNBT(new NBTTagCompound()));
        tag.setTag("Categories", filters); NBTTagList hidden = new NBTTagList();
        for (var entry : hiddenCategories.entrySet()) {
            NBTTagCompound value = new NBTTagCompound(); value.setUniqueId("Player", entry.getKey()); value.setLong("Flags", entry.getValue()); hidden.appendTag(value);
        }
        tag.setTag("HiddenCategories", hidden); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); if (tag.hasUniqueId("Frequency")) network = tag.getUniqueId("Frequency"); previousAddress = tag.getString("PreviousAddress");
        if (tag.hasKey("Payments", 10)) receivedPayments.deserializeNBT(tag.getCompoundTag("Payments")); if (tag.hasKey("Locked")) clientLocked = tag.getBoolean("Locked");
        categories.clear(); NBTTagList filters = tag.getTagList("Categories", 10);
        for (int i = 0; i < Math.min(45, filters.tagCount()); i++) {
            var filter = new net.minecraft.item.ItemStack(filters.getCompoundTagAt(i));
            if (com.cogwork.cogworkbridge.content.fluids.ListFilterItem.isFilterItem(filter)) categories.add(filter);
        }
        hiddenCategories.clear(); NBTTagList hidden = tag.getTagList("HiddenCategories", 10);
        for (int i = 0; i < hidden.tagCount(); i++) {
            NBTTagCompound entry = hidden.getCompoundTagAt(i);
            if (entry.hasUniqueId("Player")) hiddenCategories.put(entry.getUniqueId("Player"), entry.getLong("Flags"));
        }
    }
    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) { return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side); }
    @Nullable @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) { return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(receivedPayments) : super.getCapability(capability, side); }
    @Override public NBTTagCompound getUpdateTag() { NBTTagCompound tag = writeToNBT(new NBTTagCompound()); tag.setBoolean("Locked", isLocked()); return tag; }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
