package com.cogwork.cogworkbridge.content.equipment;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import javax.annotation.Nullable;

/** Eight compartments, each with four slots; the last GUI column stays empty. */
public final class ToolboxTileEntity extends TileEntityLockable implements net.minecraft.util.ITickable {
    private NonNullList<ItemStack> items = NonNullList.withSize(36, ItemStack.EMPTY);
    private NonNullList<ItemStack> filters = NonNullList.withSize(8, ItemStack.EMPTY);
    private final InvWrapper handler = new InvWrapper(this);
    private java.util.UUID identity = java.util.UUID.randomUUID();
    private int viewers;
    private final boolean[] settlePending = new boolean[8];
    private float openProgress, previousProgress, drawerProgress, previousDrawerProgress;
    public java.util.UUID identity() { return identity; }
    @Override public String getName() { return hasCustomName() ? customName : "container.cogworkbridge.toolbox"; }
    private String customName;
    @Override public boolean hasCustomName() { return customName != null && !customName.isEmpty(); }
    public void setCustomName(String name) { customName = name; }
    @Override public int getSizeInventory() { return 36; }
    @Override public boolean isEmpty() { for (ItemStack stack : items) if (!stack.isEmpty()) return false; return true; }
    @Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack decrStackSize(int slot, int count) {
        ItemStack result = ItemStackHelper.getAndSplit(items, slot, count);
        if (!result.isEmpty()) { if (slot % 9 < 8) settlePending[slot % 9] = true; contentsChanged(); }
        return result;
    }
    @Override public ItemStack removeStackFromSlot(int slot) {
        ItemStack result = ItemStackHelper.getAndRemove(items, slot);
        if (!result.isEmpty()) { if (slot % 9 < 8) settlePending[slot % 9] = true; contentsChanged(); }
        return result;
    }
    @Override public void setInventorySlotContents(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getInventoryStackLimit()) stack.setCount(getInventoryStackLimit());
        if (!stack.isEmpty() && slot % 9 < 8 && filters.get(slot % 9).isEmpty()) {
            ItemStack sample = stack.copy(); sample.setCount(1); filters.set(slot % 9, sample);
        }
        contentsChanged();
    }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public boolean isUsableByPlayer(EntityPlayer player) { return world != null && world.getTileEntity(pos) == this && player.getDistanceSq(pos.add(.5, .5, .5)) <= 64D; }
    @Override public void openInventory(EntityPlayer player) { if (world != null && !world.isRemote) { viewers++; syncViewers(); } }
    @Override public void closeInventory(EntityPlayer player) { if (world != null && !world.isRemote) { viewers = Math.max(0, viewers - 1); syncViewers(); } }
    public float openProgress(float partial) { return previousProgress + (openProgress - previousProgress) * partial; }
    public float drawerProgress(float partial) { return previousDrawerProgress + (drawerProgress - previousDrawerProgress) * partial; }
    @Override public void update() {
        if (world != null && !world.isRemote)
            for (int compartment = 0; compartment < 8; compartment++) if (settlePending[compartment]) settle(compartment);
        previousProgress = openProgress;
        previousDrawerProgress = drawerProgress;
        float target = viewers > 0 ? 1F : 0F;
        openProgress += Math.max(-.2F, Math.min(.2F, target - openProgress));
        drawerProgress += (target - drawerProgress) * .2F;
    }
    private void syncViewers() { markDirty(); world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2); }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 36 || slot % 9 == 8 || stack.isEmpty() || stack.getItem() instanceof net.minecraft.item.ItemBlock blockItem && blockItem.getBlock() instanceof ToolboxBlock) return false;
        ItemStack filter = filters.get(slot % 9);
        return filter.isEmpty() || sameItem(filter, stack);
    }
    private static boolean sameItem(ItemStack a, ItemStack b) { return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b); }
    public ItemStack filter(int compartment) { return compartment >= 0 && compartment < 8 ? filters.get(compartment) : ItemStack.EMPTY; }
    public int count(int compartment) {
        int total = 0;
        for (int row = 0; row < 4; row++) total += getStackInSlot(row * 9 + compartment).getCount();
        return total;
    }
    public void clearFilter(int compartment) {
        if (compartment >= 0 && compartment < 8 && count(compartment) == 0 && !filters.get(compartment).isEmpty()) {
            filters.set(compartment, ItemStack.EMPTY); contentsChanged();
        }
    }
    public void settle(int compartment) {
        if (compartment < 0 || compartment >= 8) return;
        settlePending[compartment] = false;
        for (int row = 0; row < 4; row++) {
            int destination = row * 9 + compartment;
            ItemStack first = items.get(destination);
            for (int later = row + 1; later < 4; later++) {
                int source = later * 9 + compartment;
                ItemStack next = items.get(source);
                if (next.isEmpty()) continue;
                if (first.isEmpty()) { items.set(destination, next); items.set(source, ItemStack.EMPTY); first = next; continue; }
                if (!sameItem(first, next)) continue;
                int moved = Math.min(next.getCount(), first.getMaxStackSize() - first.getCount());
                if (moved > 0) { first.grow(moved); next.shrink(moved); if (next.isEmpty()) items.set(source, ItemStack.EMPTY); }
            }
        }
        contentsChanged();
    }
    public ItemStack deposit(ItemStack stack) { return insert(stack, true); }
    public ItemStack insert(ItemStack stack, boolean existingOnly) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack remaining = stack.copy();
        for (int pass = 0; pass < (existingOnly ? 1 : 2) && !remaining.isEmpty(); pass++) for (int compartment = 0; compartment < 8 && !remaining.isEmpty(); compartment++) {
            ItemStack filter = filter(compartment);
            if (pass == 0 && (filter.isEmpty() || !sameItem(filter, remaining))) continue;
            if (pass == 1 && !filter.isEmpty()) continue;
            if (!isItemValidForSlot(compartment, remaining)) continue;
            if (filter.isEmpty()) { ItemStack sample = remaining.copy(); sample.setCount(1); filters.set(compartment, sample); }
            for (int row = 0; row < 4 && !remaining.isEmpty(); row++) {
                int slot = row * 9 + compartment;
                ItemStack existing = items.get(slot);
                if (!existing.isEmpty() && !sameItem(existing, remaining)) continue;
                int room = existing.isEmpty() ? remaining.getMaxStackSize() : existing.getMaxStackSize() - existing.getCount();
                int moved = Math.min(room, remaining.getCount());
                if (moved <= 0) continue;
                if (existing.isEmpty()) { ItemStack inserted = remaining.copy(); inserted.setCount(moved); items.set(slot, inserted); }
                else existing.grow(moved);
                remaining.shrink(moved);
            }
        }
        if (remaining.getCount() != stack.getCount()) contentsChanged();
        return remaining;
    }
    private void contentsChanged() {
        markDirty();
        if (world != null && !world.isRemote && world.isBlockLoaded(pos))
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }
    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) { }
    @Override public int getFieldCount() { return 0; }
    @Override public void clear() { items.clear(); filters.clear(); contentsChanged(); }
    @Override public String getGuiID() { return "minecraft:chest"; }
    @Override public Container createContainer(InventoryPlayer inventory, EntityPlayer player) { return new ToolboxContainer(inventory, this, player); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); ItemStackHelper.saveAllItems(tag, items);
        NBTTagCompound filterTag = new NBTTagCompound(); ItemStackHelper.saveAllItems(filterTag, filters); tag.setTag("Filters", filterTag);
        tag.setUniqueId("ToolboxId", identity);
        tag.setInteger("Viewers", viewers);
        if (hasCustomName()) tag.setString("CustomName", customName);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); items = NonNullList.withSize(36, ItemStack.EMPTY); ItemStackHelper.loadAllItems(tag, items);
        filters = NonNullList.withSize(8, ItemStack.EMPTY);
        if (tag.hasKey("Filters", 10)) ItemStackHelper.loadAllItems(tag.getCompoundTag("Filters"), filters);
        else for (int c = 0; c < 8; c++) for (int row = 0; row < 4; row++) {
            ItemStack stack = items.get(row * 9 + c);
            if (!stack.isEmpty()) { ItemStack sample = stack.copy(); sample.setCount(1); filters.set(c, sample); break; }
        }
        if (tag.hasUniqueId("ToolboxId")) identity = tag.getUniqueId("ToolboxId");
        viewers = world != null && world.isRemote ? Math.max(0, tag.getInteger("Viewers")) : 0;
        customName = tag.hasKey("CustomName", 8) ? tag.getString("CustomName") : null;
    }
    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler) : super.getCapability(capability, side);
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager network, net.minecraft.network.play.server.SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
