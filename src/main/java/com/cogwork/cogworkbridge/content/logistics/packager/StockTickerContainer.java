package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IContainerListener;
import net.minecraftforge.items.ItemStackHandler;

public final class StockTickerContainer extends Container {
    private final World world;
    private final BlockPos pos;
    private final ItemStackHandler display = new ItemStackHandler(45);
    // JEI transfers while its recipe screen is active; keep the cart with the open container.
    private final java.util.List<StockOrder.Line> order = new java.util.ArrayList<>();
    private final java.util.List<StockOrder.Craft> crafts = new java.util.ArrayList<>();
    public java.util.List<StockOrder.Line> order() { return order; }
    public java.util.List<StockOrder.Craft> crafts() { return crafts; }
    public void addCraftingOrder(java.util.List<ItemStack> pattern, int count) {
        int available = 256 - crafts.stream().mapToInt(StockOrder.Craft::count).sum();
        if (available <= 0 || crafts.size() >= 64) return;
        count = Math.min(available, Math.max(1, Math.min(64, count)));
        StockOrder.Craft craft = new StockOrder.Craft(pattern, count);
        crafts.add(craft);
        for (ItemStack item : craft.pattern()) if (!item.isEmpty()) order.add(new StockOrder.Line(item, count));
        java.util.List<StockOrder.Line> merged = StockOrder.normalize(order);
        order.clear(); order.addAll(merged);
    }
    private int updateTimer;
    public StockTickerContainer(World world, BlockPos pos) { this.world = world; this.pos = pos.toImmutable(); }
    public BlockPos position() { return pos; }
    public ItemStackHandler display() { return display; }
    @Override public void addListener(IContainerListener listener) {
        super.addListener(listener);
        if (listener instanceof EntityPlayerMP player) sendStock(player);
    }
    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (!world.isRemote && ++updateTimer >= 20) {
            updateTimer = 0;
            for (IContainerListener listener : listeners) if (listener instanceof EntityPlayerMP player) sendStock(player);
        }
    }
    private void sendStock(EntityPlayerMP player) {
        java.util.UUID network = StockLinkNetwork.networkOf(world.getTileEntity(pos));
        if (network == null || !canInteractWith(player)) return;
        java.util.List<StockLinkNetwork.StockEntry> stock = StockLinkNetwork.summary(player.getServer(), network);
        // Send bounded pages and publish them as one snapshot on the client.
        // Always send one page, even for an empty inventory, to clear stale data.
        for (int offset = 0; offset < stock.size() || offset == 0; offset += StockTickerDataPacket.PAGE_SIZE)
            com.cogwork.cogworkbridge.network.ModNetwork.CHANNEL.sendTo(new StockTickerDataPacket(windowId, stock, offset), player);
    }
    @Override public boolean canInteractWith(EntityPlayer player) {
        return player.world == world && world.isBlockLoaded(pos)
                && (world.getTileEntity(pos) instanceof StockTickerTileEntity || world.getTileEntity(pos) instanceof RedstoneRequesterTileEntity)
                && player.getDistanceSq(pos) <= 64D
                && (world.isRemote || StockLinkNetwork.networkOf(world.getTileEntity(pos)) != null
                    && LogisticsNetworkSecurity.mayInteract(world, StockLinkNetwork.networkOf(world.getTileEntity(pos)), player));
    }
    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) { return ItemStack.EMPTY; }
}
