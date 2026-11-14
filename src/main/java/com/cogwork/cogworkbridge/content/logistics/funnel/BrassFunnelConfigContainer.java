package com.cogwork.cogworkbridge.content.logistics.funnel;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BrassFunnelConfigContainer extends Container {
    private final World world;
    private final BlockPos pos;
    private int amount;
    private boolean exact;
    private int lastAmount = Integer.MIN_VALUE;
    private int lastMode = -1;

    public BrassFunnelConfigContainer(World world, BlockPos pos) {
        this.world = world;
        this.pos = pos.toImmutable();
        syncFromTile();
    }

    public BlockPos getPos() { return pos; }
    public int getAmount() { return amount; }
    public boolean isExact() { return exact; }

    public void syncFromTile() {
        if (world.getTileEntity(pos) instanceof FunnelTileEntity funnel) {
            amount = Math.max(1, Math.min(64, funnel.getAmount()));
            exact = funnel.isExact();
        }
    }

    public void apply(int amount, boolean exact) {
        if (world.getTileEntity(pos) instanceof BrassFunnelTileEntity funnel) {
            funnel.setAmount(Math.max(1, Math.min(64, amount)), exact);
            syncFromTile();
        }
    }

    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.world == world && player.getDistanceSq(pos) <= 64
                && world.getTileEntity(pos) instanceof BrassFunnelTileEntity;
    }

    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);
        listener.sendWindowProperty(this, 0, amount);
        listener.sendWindowProperty(this, 1, exact ? 1 : 0);
    }

    @Override public void detectAndSendChanges() {
        syncFromTile();
        super.detectAndSendChanges();
        if (amount != lastAmount || exact != (lastMode != 0)) {
            for (IContainerListener listener : listeners) {
                listener.sendWindowProperty(this, 0, amount);
                listener.sendWindowProperty(this, 1, exact ? 1 : 0);
            }
            lastAmount = amount;
            lastMode = exact ? 1 : 0;
        }
    }

    @Override public void updateProgressBar(int id, int value) {
        if (id == 0) amount = Math.max(1, Math.min(64, value));
        if (id == 1) exact = value != 0;
    }

    @Override public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return net.minecraft.item.ItemStack.EMPTY;
    }
}
