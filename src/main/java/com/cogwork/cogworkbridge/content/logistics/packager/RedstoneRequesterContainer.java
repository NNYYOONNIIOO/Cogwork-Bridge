package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Server-owned ghost inventory; setting a request never consumes a player item. */
public final class RedstoneRequesterContainer extends Container {
    private final World world;
    private final BlockPos pos;
    private final RedstoneRequesterTileEntity requester;
    private final InventoryPlayer playerInventory;
    private final InventoryBasic ghost = new InventoryBasic("request", false, 9);
    private final int[] amounts = new int[9], lastSent = new int[9];

    public RedstoneRequesterContainer(InventoryPlayer player, World world, BlockPos pos, RedstoneRequesterTileEntity requester) {
        this.playerInventory = player; this.world = world; this.pos = pos.toImmutable(); this.requester = requester;
        Arrays.fill(amounts, 1); Arrays.fill(lastSent, -1);
        List<StockOrder.Line> saved = requester.order();
        int slot = 0;
        for (StockOrder.Line line : saved) {
            int remaining = line.count();
            while (remaining > 0 && slot < 9) {
                int amount = Math.min(256, remaining);
                ghost.setInventorySlotContents(slot, line.item().copy()); amounts[slot++] = amount;
                remaining -= amount;
            }
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlotToContainer(new Slot(player, col + row * 9 + 9, 5 + col * 18, 142 + row * 18));
        for (int col = 0; col < 9; col++) addSlotToContainer(new Slot(player, col, 5 + col * 18, 200));
        for (int i = 0; i < 9; i++) addSlotToContainer(new Slot(ghost, i, 27 + i * 20, 28) {
            @Override public boolean canTakeStack(EntityPlayer player) { return false; }
            @Override public boolean isItemValid(ItemStack stack) { return false; }
        });
    }
    public RedstoneRequesterTileEntity requester() { return requester; }
    public ItemStack ghostItem(int slot) { return ghost.getStackInSlot(slot); }
    public int amount(int slot) { return amounts[slot]; }
    public void setClientAmount(int slot, int value) { amounts[slot] = Math.max(1, Math.min(256, value)); }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.world == world && world.isBlockLoaded(pos) && world.getTileEntity(pos) == requester && player.getDistanceSq(pos) <= 64
                && (world.isRemote || LogisticsNetworkSecurity.mayInteract(world, requester.getNetwork(), player));
    }
    @Override public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
        if (!canInteractWith(player)) return ItemStack.EMPTY;
        if (slotId >= 36 && slotId < 45) {
            int slot = slotId - 36;
            if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
                ItemStack held = clickType == ClickType.PICKUP ? playerInventory.getItemStack() : ItemStack.EMPTY;
                ItemStack copy = held.isEmpty() ? ItemStack.EMPTY : held.copy();
                if (!copy.isEmpty()) copy.setCount(1);
                ghost.setInventorySlotContents(slot, copy); amounts[slot] = 1; save();
            }
            return ItemStack.EMPTY;
        }
        if (clickType == ClickType.QUICK_MOVE && slotId >= 0 && slotId < 36) {
            ItemStack stack = inventorySlots.get(slotId).getStack();
            if (!stack.isEmpty()) for (int i = 0; i < 9; i++) {
                if (!ghost.getStackInSlot(i).isEmpty()) continue;
                ItemStack copy = stack.copy(); copy.setCount(1); ghost.setInventorySlotContents(i, copy); amounts[i] = 1; save(); break;
            }
            return ItemStack.EMPTY;
        }
        return super.slotClick(slotId, dragType, clickType, player);
    }
    @Override public boolean canDragIntoSlot(Slot slot) { return slot.inventory != ghost; }
    @Override public boolean canMergeSlot(ItemStack stack, Slot slot) { return slot.inventory != ghost && super.canMergeSlot(stack, slot); }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
    private List<StockOrder.Line> lines() {
        List<StockOrder.Line> lines = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (!ghost.getStackInSlot(i).isEmpty())
            lines.add(new StockOrder.Line(ghost.getStackInSlot(i), amounts[i]));
        return lines;
    }
    private void save() {
        if (!world.isRemote) requester.configure(lines(), requester.address(), requester.allowsPartial());
    }
    public void applyConfig(String address, boolean partial, int[] values) {
        for (int i = 0; i < 9; i++) amounts[i] = Math.max(1, Math.min(256, values[i]));
        requester.configure(lines(), address, partial);
    }
    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);
        for (int i = 0; i < 9; i++) listener.sendWindowProperty(this, i, amounts[i]);
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        for (int i = 0; i < 9; i++) if (lastSent[i] != amounts[i]) {
            for (IContainerListener listener : listeners) listener.sendWindowProperty(this, i, amounts[i]);
            lastSent[i] = amounts[i];
        }
    }
    @Override public void updateProgressBar(int id, int value) { if (id >= 0 && id < 9) setClientAmount(id, value); }
}
