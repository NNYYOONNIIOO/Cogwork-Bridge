package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.List;

public final class AttributeFilterContainer extends Container {
    private final InventoryPlayer inventory;
    private final InventoryBasic sample = new InventoryBasic("attribute_reference", false, 1);
    private final int selectedSlot;
    private final ItemStack holder;
    private final List<AttributeFilterItem.Attribute> selected;
    private int mode;
    private int lastMode = -1;

    public AttributeFilterContainer(InventoryPlayer inventory) {
        this.inventory = inventory;
        selectedSlot = inventory.currentItem;
        holder = inventory.getStackInSlot(selectedSlot);
        selected = AttributeFilterItem.selected(holder);
        mode = AttributeFilterItem.mode(holder);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlotToContainer(new Slot(inventory, column + row * 9 + 9, 51 + column * 18, 107 + row * 18));
        for (int column = 0; column < 9; column++) addSlotToContainer(new Slot(inventory, column, 51 + column * 18, 165));
        addSlotToContainer(new Slot(sample, 0, 16, 27) {
            @Override public boolean canTakeStack(EntityPlayer player) { return false; }
            @Override public boolean isItemValid(ItemStack stack) { return false; }
        });
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.inventory == inventory && inventory.currentItem == selectedSlot
                && inventory.getStackInSlot(selectedSlot).getItem() instanceof AttributeFilterItem
                && (player.world.isRemote || inventory.getStackInSlot(selectedSlot) == holder);
    }
    public int getMode() { return mode; }
    public ItemStack getSample() { return sample.getStackInSlot(0); }
    public List<AttributeFilterItem.Attribute> getSelected() { return java.util.Collections.unmodifiableList(selected); }
    public void applyOption(int option, int index) {
        if (option >= 0 && option <= 2) mode = option;
        else if (option == 3) selected.clear();
        else if (option == 4 || option == 5) {
            List<AttributeFilterItem.Attribute> choices = AttributeFilterItem.attributesFor(getSample());
            if (index < 0 || index >= choices.size() || selected.size() >= AttributeFilterItem.MAX_ATTRIBUTES) return;
            AttributeFilterItem.Attribute choice = choices.get(index);
            for (AttributeFilterItem.Attribute existing : selected)
                if (existing.type().equals(choice.type()) && existing.value().equals(choice.value())) return;
            selected.add(option == 5 ? choice.invert() : choice);
        } else return;
        if (!inventory.player.world.isRemote) {
            AttributeFilterItem.write(holder, mode, selected);
            inventory.markDirty();
        }
    }
    @Override public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (!canInteractWith(player)) return ItemStack.EMPTY;
        if (slotId == 27 + selectedSlot || clickType == ClickType.SWAP && dragType == selectedSlot) return ItemStack.EMPTY;
        if (slotId == 36 && clickType == ClickType.PICKUP) {
            ItemStack carried = inventory.getItemStack();
            ItemStack copy = carried.isEmpty() ? ItemStack.EMPTY : carried.copy();
            if (!copy.isEmpty()) copy.setCount(1);
            sample.setInventorySlotContents(0, copy);
            return ItemStack.EMPTY;
        }
        if (clickType == ClickType.QUICK_MOVE && slotId >= 0 && slotId < 36) {
            ItemStack stack = inventorySlots.get(slotId).getStack();
            ItemStack copy = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            if (!copy.isEmpty()) copy.setCount(1);
            sample.setInventorySlotContents(0, copy);
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }
    @Override public boolean canDragIntoSlot(Slot slot) {
        return slot.inventory != sample && !(slot.inventory == inventory && slot.getSlotIndex() == selectedSlot);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
    @Override public void addListener(IContainerListener listener) { super.addListener(listener); listener.sendWindowProperty(this, 0, mode); }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (mode != lastMode) {
            for (IContainerListener listener : listeners) listener.sendWindowProperty(this, 0, mode);
            lastMode = mode;
        }
    }
    @Override public void updateProgressBar(int id, int value) { if (id == 0) mode = value; }
}
