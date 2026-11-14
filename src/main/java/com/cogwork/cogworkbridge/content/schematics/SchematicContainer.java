package com.cogwork.cogworkbridge.content.schematics;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.SlotItemHandler;

public final class SchematicContainer extends Container {
    public final World world;
    public final BlockPos pos;
    public final SchematicInventoryTile tile;
    public final boolean cannon;
    public SchematicContainer(InventoryPlayer player, World world, BlockPos pos, SchematicInventoryTile tile) {
        this.world = world; this.pos = pos.toImmutable(); this.tile = tile;
        cannon = tile instanceof SchematicannonTile;
        int[][] coords = cannon ? new int[][]{{15,65},{171,65},{134,19},{174,19},{15,19}}
                : new int[][]{{21,59},{166,59}};
        for (int i = 0; i < coords.length; i++) {
            final int slot = i;
            addSlotToContainer(new SlotItemHandler(tile.items(), i, coords[i][0], coords[i][1]) {
                @Override public boolean isItemValid(ItemStack stack) { return tile.accepts(slot, stack); }
            });
        }
        int y = cannon ? 161 : 107;
        int x = cannon ? 37 : 38;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(player, col + row * 9 + 9, x + col * 18, y + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col, x + col * 18, y + 58));
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.world == world && world.isBlockLoaded(pos) && world.getTileEntity(pos) == tile && player.getDistanceSq(pos) <= 64;
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index);
        if (!slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(), original = stack.copy();
        int machine = cannon ? 5 : 2;
        if (index < machine) {
            if (!mergeItemStack(stack, machine, inventorySlots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean inserted = false;
            for (int i = 0; i < machine && !stack.isEmpty(); i++) {
                if (tile.accepts(i, stack) && mergeItemStack(stack, i, i + 1, false)) inserted = true;
            }
            if (!inserted) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        return original;
    }
}
