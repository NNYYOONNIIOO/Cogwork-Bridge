package com.cogwork.cogworkbridge.content.logistics.port;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.SlotItemHandler;

public final class PackagePortContainer extends Container {
    private final World world;
    private final BlockPos pos;
    private final PackagePortTileEntity port;
    public PackagePortContainer(InventoryPlayer player, World world, BlockPos pos, PackagePortTileEntity port) {
        this.world = world; this.pos = pos.toImmutable(); this.port = port;
        for (int row = 0; row < 2; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new SlotItemHandler(port.inventory(), row * 9 + col, 27 + col * 18, 9 + row * 18));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(player, col + row * 9 + 9, 38 + col * 18, 108 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col, 38 + col * 18, 166));
        if (!world.isRemote) port.openViewer();
    }
    public PackagePortTileEntity port() { return port; }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.world == world && world.isBlockLoaded(pos) && world.getTileEntity(pos) == port && player.getDistanceSq(pos) <= 64;
    }
    @Override public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player); if (!world.isRemote && world.getTileEntity(pos) == port) port.closeViewer();
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot slot = inventorySlots.get(index); if (!slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack().copy(), original = stack.copy();
        if (index < 18 ? !mergeItemStack(stack, 18, inventorySlots.size(), true) : !mergeItemStack(stack, 0, 18, false)) return ItemStack.EMPTY;
        slot.putStack(stack.isEmpty() ? ItemStack.EMPTY : stack); slot.onSlotChanged(); return original;
    }
}
