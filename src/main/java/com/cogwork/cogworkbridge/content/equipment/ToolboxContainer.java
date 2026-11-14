package com.cogwork.cogworkbridge.content.equipment;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class ToolboxContainer extends Container {
    private final ToolboxTileEntity toolbox;
    private final InventoryPlayer playerInventory;

    public ToolboxContainer(InventoryPlayer player, ToolboxTileEntity toolbox, EntityPlayer entity) {
        this.toolbox = toolbox;
        this.playerInventory = player;
        int[] xs = {79, 112, 145, 151, 145, 112, 79, 73};
        int[] ys = {37, 31, 37, 70, 103, 109, 103, 70};
        for (int compartment = 0; compartment < 8; compartment++)
            for (int row = 0; row < 4; row++)
                addSlotToContainer(new Slot(toolbox, row * 9 + compartment,
                        row == 0 ? xs[compartment] : -10000, row == 0 ? ys[compartment] : -10000));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlotToContainer(new Slot(player, column + row * 9 + 9, 8 + column * 18, 165 + row * 18));
        for (int column = 0; column < 9; column++)
            addSlotToContainer(new Slot(player, column, 8 + column * 18, 223));
        toolbox.openInventory(entity);
    }

    public ToolboxTileEntity toolbox() { return toolbox; }
    @Override public boolean canInteractWith(EntityPlayer player) { return toolbox.isUsableByPlayer(player); }
    @Override public void onContainerClosed(EntityPlayer player) { super.onContainerClosed(player); toolbox.closeInventory(player); }
    @Override public boolean canDragIntoSlot(Slot slot) { return slot.inventory != toolbox && super.canDragIntoSlot(slot); }

    @Override public ItemStack slotClick(int slotId, int dragType, ClickType type, EntityPlayer player) {
        if (!canInteractWith(player)) return ItemStack.EMPTY;
        int compartment = slotId >= 0 && slotId < 32 ? slotId / 4 : -1;
        if (compartment >= 0 && type == ClickType.SWAP && dragType >= 0 && dragType < 9) {
            if (!player.world.isRemote) ToolboxLinks.toggle(player, toolbox, compartment, dragType);
            return ItemStack.EMPTY;
        }
        if (compartment >= 0 && type == ClickType.PICKUP && slotId % 4 == 0) {
            ItemStack carried = playerInventory.getItemStack();
            if (carried.isEmpty() && toolbox.count(compartment) == 0) {
                toolbox.clearFilter(compartment);
                return ItemStack.EMPTY;
            }
            if (!carried.isEmpty() && toolbox.isItemValidForSlot(compartment, carried)) {
                for (int row = 0; row < 4; row++) {
                    ItemStack current = toolbox.getStackInSlot(row * 9 + compartment);
                    if (current.isEmpty() || ItemStack.areItemsEqual(current, carried)
                            && ItemStack.areItemStackTagsEqual(current, carried) && current.getCount() < current.getMaxStackSize()) {
                        slotId = compartment * 4 + row;
                        break;
                    }
                }
            }
        }
        ItemStack result = super.slotClick(slotId, dragType, type, player);
        if (compartment >= 0) toolbox.settle(compartment);
        return result;
    }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (!slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack original = slot.getStack();
        ItemStack before = original.copy();
        if (index < 32) {
            if (!mergeItemStack(original, 32, inventorySlots.size(), true)) return ItemStack.EMPTY;
            if (original.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
            toolbox.settle(index / 4);
        } else {
            ItemStack remaining = toolbox.insert(original, false);
            if (remaining.getCount() == original.getCount()) return ItemStack.EMPTY;
            slot.putStack(remaining);
        }
        return before;
    }
}
