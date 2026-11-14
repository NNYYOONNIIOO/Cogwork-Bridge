package com.cogwork.cogworkbridge.content.redstone;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Twelve ghost slots, two for each of the six controller keys. */
public final class LinkedControllerContainer extends Container {
    private final InventoryPlayer inventory;
    private final InventoryBasic frequencies = new InventoryBasic("controller", false, 12);
    private final int selectedSlot;
    private final ItemStack controller;

    public LinkedControllerContainer(InventoryPlayer inventory) {
        this.inventory = inventory;
        selectedSlot = inventory.currentItem;
        controller = inventory.getStackInSlot(selectedSlot);
        for (int i = 0; i < 12; i++) frequencies.setInventorySlotContents(i, LinkedControllerItem.frequency(controller, i));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(inventory, col + row * 9 + 9, 9 + col * 18, 131 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inventory, col, 9 + col * 18, 189));
        for (int col = 0; col < 6; col++) for (int row = 0; row < 2; row++) {
            int index = col * 2 + row;
            addSlotToContainer(new Slot(frequencies, index, 12 + col * 24 + (col >= 4 ? 11 : 0), 34 + row * 18) {
                @Override public boolean canTakeStack(EntityPlayer player) { return false; }
                @Override public boolean isItemValid(ItemStack stack) { return false; }
            });
        }
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.inventory == inventory && inventory.currentItem == selectedSlot
                && inventory.getStackInSlot(selectedSlot).getItem() instanceof LinkedControllerItem
                && (player.world.isRemote || inventory.getStackInSlot(selectedSlot) == controller);
    }
    @Override public ItemStack slotClick(int slot, int drag, ClickType click, EntityPlayer player) {
        if (!canInteractWith(player)) return ItemStack.EMPTY;
        if (slot == 27 + selectedSlot || click == ClickType.SWAP && drag == selectedSlot) return ItemStack.EMPTY;
        if (slot >= 36 && slot < 48 && (click == ClickType.PICKUP || click == ClickType.QUICK_MOVE)) {
            ItemStack source = click == ClickType.QUICK_MOVE ? ItemStack.EMPTY : inventory.getItemStack();
            ItemStack copy = source.isEmpty() ? ItemStack.EMPTY : source.copy();
            if (!copy.isEmpty()) copy.setCount(1);
            frequencies.setInventorySlotContents(slot - 36, copy);
            if (!player.world.isRemote) { LinkedControllerItem.setFrequency(controller, slot - 36, copy); inventory.markDirty(); }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slot, drag, click, player);
    }
    @Override public boolean canDragIntoSlot(Slot slot) {
        return slot.inventory != frequencies && !(slot.inventory == inventory && slot.getSlotIndex() == selectedSlot);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
}
