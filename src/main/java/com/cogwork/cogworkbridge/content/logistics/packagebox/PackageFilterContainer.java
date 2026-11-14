package com.cogwork.cogworkbridge.content.logistics.packagebox;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class PackageFilterContainer extends Container {
    private final InventoryPlayer inventory;
    private final int slot;
    private final ItemStack holder;
    public PackageFilterContainer(InventoryPlayer inventory) {
        this.inventory = inventory; slot = inventory.currentItem; holder = inventory.getStackInSlot(slot);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inventory, col + row * 9 + 9, 40 + col * 18, 101 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inventory, col, 40 + col * 18, 159));
    }
    public String address() { return PackageFilterItem.address(holder); }
    public void setAddress(String address) { PackageFilterItem.setAddress(holder, address); inventory.markDirty(); }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.inventory == inventory && inventory.currentItem == slot && inventory.getStackInSlot(slot).getItem() instanceof PackageFilterItem
                && (player.world.isRemote || inventory.getStackInSlot(slot) == holder);
    }
    @Override public ItemStack slotClick(int id, int button, ClickType type, EntityPlayer player) {
        if (id == 27 + slot || type == ClickType.SWAP && button == slot) return ItemStack.EMPTY;
        return super.slotClick(id, button, type, player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int slot) { return ItemStack.EMPTY; }
}
