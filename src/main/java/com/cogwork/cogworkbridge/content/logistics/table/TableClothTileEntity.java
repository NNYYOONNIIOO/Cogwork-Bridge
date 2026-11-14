package com.cogwork.cogworkbridge.content.logistics.table;

import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import com.cogwork.cogworkbridge.content.logistics.packager.StockOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TableClothTileEntity extends TileEntity {
    private final List<ItemStack> displayed = new ArrayList<>();
    private EnumFacing facing = EnumFacing.NORTH;
    private NBTTagCompound shop = new NBTTagCompound();
    private java.util.UUID owner;
    private ItemStack price = ItemStack.EMPTY;
    private int priceCount = 1;
    public boolean isShop() { return !StockOrder.read(shop.getTagList("Items", 10)).isEmpty(); }
    public NBTTagCompound shopData() { var data=shop.copy();if(!price.isEmpty())data.setTag("Price",price.serializeNBT());data.setInteger("PriceCount",priceCount);return data; }
    public java.util.UUID owner() { return owner; }
    public ItemStack price() { ItemStack copy = price.copy(); if (!copy.isEmpty()) copy.setCount(priceCount); return copy; }
    public int priceCount() { return priceCount; }
    public boolean maySetPrice(EntityPlayer player) { return isShop() && (owner == null || owner.equals(player.getUniqueID())); }
    public void setPriceCount(EntityPlayer player, int value) {
        if (!maySetPrice(player)) return;
        priceCount = Math.max(1, Math.min(100, value)); changed();
    }
    public java.util.List<StockOrder.Line> order() { return StockOrder.read(shop.getTagList("Items", 10)); }
    public boolean validShop() {
        if (world == null || !shop.getBoolean("Valid") || !shop.hasUniqueId("Network")) return false;
        BlockPos ticker = BlockPos.fromLong(shop.getLong("Ticker"));
        return world.isBlockLoaded(ticker) && world.getTileEntity(ticker) instanceof com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity target
                && target.getNetwork().equals(shop.getUniqueId("Network"));
    }
    public void setPrice(ItemStack stack, EntityPlayer player) {
        if (!maySetPrice(player) || stack.getItem() instanceof com.cogwork.cogworkbridge.content.fluids.ListFilterItem
                || stack.getItem() instanceof ShoppingListItem) return;
        price = stack.copy(); if (!price.isEmpty()) price.setCount(1); changed();
    }
    public List<ItemStack> items() {
        if (!isShop()) return Collections.unmodifiableList(displayed);
        List<ItemStack> samples = new ArrayList<>();
        for (StockOrder.Line line : StockOrder.read(shop.getTagList("Items", 10))) {
            if (samples.size() >= 4) break;
            samples.add(line.item());
        }
        return samples;
    }
    public EnumFacing facing() { return facing; }
    public void loadShop(NBTTagCompound data, EntityPlayer player) {
        shop = data.copy(); owner = player.getUniqueID(); facing = player.getHorizontalFacing().getOpposite();
        price=data.hasKey("Price",10)?new ItemStack(data.getCompoundTag("Price")):ItemStack.EMPTY;
        priceCount=Math.max(1,Math.min(100,data.hasKey("PriceCount")?data.getInteger("PriceCount"):1));if(!price.isEmpty())price.setCount(1);
        BlockPos target = BlockPos.fromLong(shop.getLong("Ticker"));
        shop.setBoolean("Valid", shop.hasKey("Ticker") && shop.getInteger("Dimension") == world.provider.getDimension()
                && pos.distanceSq(target) < 128D * 128D);
        changed();
        if (shop.getBoolean("Valid"))
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAction(world, pos, player, "table_cloth_shop");
    }
    public void interact(EntityPlayer player, EnumHand hand) {
        if (isShop()) { ShoppingListItem.addPurchase(this, player, hand); return; }
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) {
            if (displayed.isEmpty()) return;
            player.setHeldItem(hand, displayed.remove(displayed.size() - 1));
        } else {
            if (displayed.size() >= 4) return;
            ItemStack item = held.copy(); item.setCount(1); displayed.add(item);
            if (!player.capabilities.isCreativeMode) held.shrink(1);
            facing = player.getHorizontalFacing().getOpposite();
        }
        changed();
    }
    public void dropContents() { for (ItemStack stack : displayed) ItemTransfer.drop(world, pos, stack); displayed.clear(); }
    private void changed() { markDirty(); if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); NBTTagList items = new NBTTagList();
        for (ItemStack stack : displayed) items.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("Displayed", items); tag.setInteger("Facing", facing.getHorizontalIndex());
        if (!shop.isEmpty()) tag.setTag("Shop", shop.copy()); if (owner != null) tag.setUniqueId("Owner", owner);
        if (!price.isEmpty()) tag.setTag("Price", price.writeToNBT(new NBTTagCompound()));
        tag.setInteger("PriceCount", priceCount);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); displayed.clear(); NBTTagList items = tag.getTagList("Displayed", 10);
        for (int i = 0; i < Math.min(4, items.tagCount()); i++) {
            ItemStack stack = new ItemStack(items.getCompoundTagAt(i)); if (!stack.isEmpty()) displayed.add(stack);
        }
        facing = EnumFacing.byHorizontalIndex(tag.getInteger("Facing"));
        shop = tag.getCompoundTag("Shop").copy(); owner = tag.hasUniqueId("Owner") ? tag.getUniqueId("Owner") : null;
        price = tag.hasKey("Price", 10) ? new ItemStack(tag.getCompoundTag("Price")) : ItemStack.EMPTY;
        priceCount = Math.max(1, Math.min(100, tag.hasKey("PriceCount") ? tag.getInteger("PriceCount") : price.getCount()));
        if (!price.isEmpty()) price.setCount(1);
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
