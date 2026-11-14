package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/** Server-authoritative ghost slots belonging to the filter in the selected hotbar slot. */
public final class ListFilterContainer extends Container {
    private static final int PLAYER_SLOTS = 36;
    private final InventoryPlayer inventory;
    private final InventoryBasic ghost = new InventoryBasic("filter", false, ListFilterItem.SLOT_COUNT);
    private final int selectedSlot;
    private final ItemStack holder;
    private boolean blacklist;
    private boolean respectData;
    private int previousFlags = -1;

    public ListFilterContainer(InventoryPlayer inventory) {
        this.inventory = inventory;
        selectedSlot = inventory.currentItem;
        holder = inventory.getStackInSlot(selectedSlot);
        blacklist = ListFilterItem.blacklist(holder);
        respectData = ListFilterItem.respectData(holder);
        NonNullList<ItemStack> entries = ListFilterItem.entries(holder);
        for (int i = 0; i < entries.size(); i++) ghost.setInventorySlotContents(i, entries.get(i));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(new Slot(inventory, column + row * 9 + 9, 38 + column * 18, 121 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlotToContainer(new Slot(inventory, column, 38 + column * 18, 179));
        }
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(new Slot(ghost, column + row * 9, 23 + column * 18, 25 + row * 18) {
                    @Override public boolean canTakeStack(EntityPlayer player) { return false; }
                    @Override public boolean isItemValid(ItemStack stack) { return false; }
                });
            }
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return player.inventory == inventory && inventory.currentItem == selectedSlot
                && inventory.getStackInSlot(selectedSlot).getItem() instanceof ListFilterItem
                && (player.world.isRemote || inventory.getStackInSlot(selectedSlot) == holder);
    }

    public boolean isBlacklist() { return blacklist; }
    public boolean respectsData() { return respectData; }

    /** Updates one ghost slot from a client-side JEI drop or a validated server packet. */
    public void applyGhostEntry(int index, ItemStack stack) {
        if (index < 0 || index >= ListFilterItem.SLOT_COUNT) return;
        ItemStack copy = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!copy.isEmpty()) copy.setCount(1);
        ghost.setInventorySlotContents(index, copy);
        save();
    }

    /** Options: deny, allow, respect data, ignore data, clear. */
    public void applyOption(int option) {
        switch (option) {
            case 0 -> blacklist = true;
            case 1 -> blacklist = false;
            case 2 -> respectData = true;
            case 3 -> respectData = false;
            case 4 -> {
                for (int i = 0; i < ghost.getSizeInventory(); i++) ghost.setInventorySlotContents(i, ItemStack.EMPTY);
            }
            default -> { return; }
        }
        save();
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (!canInteractWith(player)) return ItemStack.EMPTY;
        int holderSlot = 27 + selectedSlot;
        if (slotId == holderSlot || clickType == ClickType.SWAP && dragType == selectedSlot) return ItemStack.EMPTY;
        if (slotId >= PLAYER_SLOTS && slotId < PLAYER_SLOTS + ListFilterItem.SLOT_COUNT) {
            int index = slotId - PLAYER_SLOTS;
            if (clickType == ClickType.QUICK_MOVE) {
                ghost.setInventorySlotContents(index, ItemStack.EMPTY);
                save();
            } else if (clickType == ClickType.PICKUP) {
                ItemStack carried = inventory.getItemStack();
                ItemStack copy = carried.isEmpty() ? ItemStack.EMPTY : carried.copy();
                if (!copy.isEmpty()) copy.setCount(1);
                ghost.setInventorySlotContents(index, copy);
                save();
            }
            return ItemStack.EMPTY;
        }
        if (clickType == ClickType.QUICK_MOVE && slotId >= 0 && slotId < PLAYER_SLOTS) {
            ItemStack item = inventorySlots.get(slotId).getStack();
            if (!item.isEmpty()) {
                for (int i = 0; i < ghost.getSizeInventory(); i++) {
                    ItemStack present = ghost.getStackInSlot(i);
                    if (!present.isEmpty() && present.isItemEqual(item)
                            && ItemStack.areItemStackTagsEqual(present, item)) return ItemStack.EMPTY;
                }
                for (int i = 0; i < ghost.getSizeInventory(); i++) {
                    if (!ghost.getStackInSlot(i).isEmpty()) continue;
                    ItemStack copy = item.copy();
                    copy.setCount(1);
                    ghost.setInventorySlotContents(i, copy);
                    save();
                    break;
                }
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }

    @Override
    public boolean canDragIntoSlot(Slot slot) {
        return slot.inventory != ghost && !(slot.inventory == inventory && slot.getSlotIndex() == selectedSlot);
    }

    @Override
    public boolean canMergeSlot(ItemStack stack, Slot slot) {
        return canDragIntoSlot(slot) && super.canMergeSlot(stack, slot);
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }

    private void save() {
        if (inventory.player.world.isRemote) return;
        NonNullList<ItemStack> entries = NonNullList.withSize(ListFilterItem.SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < entries.size(); i++) entries.set(i, ghost.getStackInSlot(i));
        ListFilterItem.write(holder, entries, blacklist, respectData);
        inventory.markDirty();
    }

    private int flags() { return (blacklist ? 1 : 0) | (respectData ? 2 : 0); }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        listener.sendWindowProperty(this, 0, flags());
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int current = flags();
        if (current == previousFlags) return;
        for (IContainerListener listener : listeners) listener.sendWindowProperty(this, 0, current);
        previousFlags = current;
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) {
            blacklist = (value & 1) != 0;
            respectData = (value & 2) != 0;
        }
    }
}
