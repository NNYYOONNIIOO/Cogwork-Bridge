package com.cogwork.cogworkbridge.content.equipment;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public final class BlueprintContainer extends Container {
    private final BlueprintEntity blueprint;
    private final int section;
    private final InventoryPlayer inventory;
    private final InventoryBasic ghost = new InventoryBasic("blueprint", false, 11);
    public BlueprintContainer(InventoryPlayer inventory, BlueprintEntity blueprint, int section) {
        this.blueprint = blueprint; this.inventory = inventory; this.section = section;
        for (int i = 0; i < 11; i++) ghost.setInventorySlotContents(i, blueprint.pattern(section, i).copy());
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addGhost(row * 3 + col, 29 + col * 18, 21 + row * 18);
        addGhost(9, 123, 40); addGhost(10, 135, 57);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 131 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(inventory, col, 8 + col * 18, 189));
    }
    private void addGhost(int index, int x, int y) {
        addSlotToContainer(new Slot(ghost, index, x, y) {
            @Override public boolean canTakeStack(EntityPlayer player) { return false; }
            @Override public boolean isItemValid(ItemStack stack) { return false; }
        });
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return !blueprint.isDead && player.world == blueprint.world && player.getDistanceSq(blueprint) <= 64D
                && section >= 0 && section < blueprint.size() * blueprint.size();
    }
    public void setGhost(int slot, ItemStack stack) {
        if (slot < 0 || slot >= 11) return;
        ItemStack copy = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!copy.isEmpty()) copy.setCount(1);
        ghost.setInventorySlotContents(slot, copy);
        if (!blueprint.world.isRemote) {
            blueprint.setPattern(section, slot, copy);
            ghost.setInventorySlotContents(9, blueprint.pattern(section, 9).copy());
        }
    }
    public void clearGhost() { for (int i = 0; i < 11; i++) setGhost(i, ItemStack.EMPTY); }
    @Override public ItemStack slotClick(int id, int drag, ClickType click, EntityPlayer player) {
        if (!canInteractWith(player)) return ItemStack.EMPTY;
        if (id >= 0 && id < 11) {
            if (click == ClickType.PICKUP || click == ClickType.QUICK_MOVE)
                setGhost(id, click == ClickType.QUICK_MOVE ? ItemStack.EMPTY : inventory.getItemStack());
            return ItemStack.EMPTY;
        }
        return super.slotClick(id, drag, click, player);
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
}
