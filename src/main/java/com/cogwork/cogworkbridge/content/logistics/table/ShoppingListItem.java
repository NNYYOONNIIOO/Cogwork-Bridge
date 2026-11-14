package com.cogwork.cogworkbridge.content.logistics.table;

import com.cogwork.cogworkbridge.content.logistics.packager.StockLinkNetwork;
import com.cogwork.cogworkbridge.content.logistics.packager.StockOrder;
import com.cogwork.cogworkbridge.content.logistics.packager.StockTickerTileEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** A purchase list stores shop positions; prices are rechecked at checkout. */
public final class ShoppingListItem extends Item {
    public ShoppingListItem() { setMaxStackSize(1); }

    public static void addPurchase(TableClothTileEntity cloth, EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) return;
        if (!cloth.validShop() || cloth.owner() == null) { message(player, "cogworkbridge.shop.invalid"); return; }
        if (cloth.price().isEmpty()) { message(player, "cogworkbridge.shop.unpriced"); return; }
        ItemStack held = player.getHeldItemMainhand();
        if (!held.isEmpty() && held.getItem() != AllItems.SHOPPING_LIST) return;
        NBTTagCompound shop = cloth.shopData();
        boolean newList = held.isEmpty();
        if (newList) {
            held = new ItemStack(AllItems.SHOPPING_LIST);
            NBTTagCompound tag = new NBTTagCompound(); tag.setUniqueId("Owner", cloth.owner());
            tag.setUniqueId("Network", shop.getUniqueId("Network")); tag.setInteger("Dimension", player.world.provider.getDimension());
            tag.setString("Address", shop.getString("Address")); held.setTagCompound(tag);
        }
        NBTTagCompound tag = held.getTagCompound();
        if (tag == null || !tag.hasUniqueId("Owner") || !tag.hasUniqueId("Network")
                || !tag.getUniqueId("Owner").equals(cloth.owner()) || !tag.getUniqueId("Network").equals(shop.getUniqueId("Network"))
                || tag.getInteger("Dimension") != player.world.provider.getDimension()) { message(player, "cogworkbridge.shop.invalid"); return; }
        NBTTagList purchases = tag.getTagList("Purchases", 10);
        List<StockOrder.Line> desired = new ArrayList<>();
        for (int i = 0; i < purchases.tagCount(); i++) {
            NBTTagCompound purchase = purchases.getCompoundTagAt(i); int count = purchase.getInteger("Count");
            BlockPos previous = BlockPos.fromLong(purchase.getLong("Pos"));
            if (count < 1 || count > 64 || !player.world.isBlockLoaded(previous)
                    || !(player.world.getTileEntity(previous) instanceof TableClothTileEntity other) || !other.validShop()
                    || !tag.getUniqueId("Owner").equals(other.owner()) || !tag.getUniqueId("Network").equals(other.shopData().getUniqueId("Network"))) {
                message(player, "cogworkbridge.shop.invalid"); return;
            }
            for (StockOrder.Line line : other.order())
                if (!append(desired, line.item(), (long) line.count() * count)) { message(player, "cogworkbridge.shop.checkout_failed"); return; }
        }
        for (StockOrder.Line line : cloth.order())
            if (!append(desired, line.item(), line.count())) { message(player, "cogworkbridge.shop.checkout_failed"); return; }
        List<StockLinkNetwork.StockEntry> stock = StockLinkNetwork.summary(player.getServer(), tag.getUniqueId("Network"));
        for (StockOrder.Line wanted : desired) {
            int available = 0;
            for (StockLinkNetwork.StockEntry entry : stock)
                if (ItemStack.areItemsEqual(entry.item, wanted.item()) && ItemStack.areItemStackTagsEqual(entry.item, wanted.item())) { available = entry.count; break; }
            if (available < wanted.count()) { message(player, "cogworkbridge.shop.out_of_stock"); return; }
        }
        NBTTagCompound entry = null;
        for (int i = 0; i < purchases.tagCount(); i++)
            if (purchases.getCompoundTagAt(i).getLong("Pos") == cloth.getPos().toLong()) { entry = purchases.getCompoundTagAt(i); break; }
        if (entry == null) {
            if (purchases.tagCount() >= 45) return;
            entry = new NBTTagCompound(); entry.setLong("Pos", cloth.getPos().toLong()); purchases.appendTag(entry);
        }
        entry.setInteger("Count", Math.min(64, entry.getInteger("Count") + 1));
        tag.setTag("Purchases", purchases); if (newList) player.setHeldItem(EnumHand.MAIN_HAND, held);
        player.inventory.markDirty(); message(player, "cogworkbridge.shop.list_added");
    }

    public static void checkout(StockTickerTileEntity ticker, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand); NBTTagCompound tag = held.getTagCompound();
        if (tag == null || !tag.hasUniqueId("Owner") || !tag.hasUniqueId("Network")
                || !ticker.getNetwork().equals(tag.getUniqueId("Network")) || tag.getInteger("Dimension") != player.world.provider.getDimension()) {
            message(player, "cogworkbridge.shop.invalid"); return;
        }
        List<StockOrder.Line> orders = new ArrayList<>(), costs = new ArrayList<>();
        NBTTagList purchases = tag.getTagList("Purchases", 10);
        if (purchases.tagCount() == 0 || purchases.tagCount() > 45) return;
        for (int i = 0; i < purchases.tagCount(); i++) {
            NBTTagCompound entry = purchases.getCompoundTagAt(i); int count = entry.getInteger("Count");
            BlockPos pos = BlockPos.fromLong(entry.getLong("Pos"));
            if (count < 1 || count > 64 || !player.world.isBlockLoaded(pos)
                    || !(player.world.getTileEntity(pos) instanceof TableClothTileEntity cloth) || !cloth.validShop()
                    || !tag.getUniqueId("Owner").equals(cloth.owner()) || !ticker.getNetwork().equals(cloth.shopData().getUniqueId("Network"))) {
                message(player, "cogworkbridge.shop.invalid"); return;
            }
            ItemStack price = cloth.price();
            if (price.isEmpty() || !append(costs, price, (long) price.getCount() * count)) { message(player, "cogworkbridge.shop.unpriced"); return; }
            for (StockOrder.Line line : cloth.order())
                if (!append(orders, line.item(), (long) line.count() * count)) { message(player, "cogworkbridge.shop.checkout_failed"); return; }
        }
        List<ItemStack> inventory = new ArrayList<>();
        for (ItemStack stack : player.inventory.mainInventory) inventory.add(stack.copy());
        ItemStackHandler payments = new ItemStackHandler(27); payments.deserializeNBT(ticker.payments().serializeNBT());
        for (StockOrder.Line cost : costs) {
            int remaining = cost.count();
            for (ItemStack stack : inventory) {
                if (remaining == 0) break;
                if (stack.isEmpty() || !ItemStack.areItemsEqual(stack, cost.item()) || !ItemStack.areItemStackTagsEqual(stack, cost.item())) continue;
                int amount = Math.min(stack.getCount(), remaining); stack.shrink(amount); remaining -= amount;
            }
            int insert = cost.count();
            while (insert > 0) {
                int amount = Math.min(insert, cost.item().getMaxStackSize()); ItemStack part = cost.item().copy(); part.setCount(amount);
                if (!ItemHandlerHelper.insertItemStacked(payments, part, false).isEmpty()) { remaining = 1; break; }
                insert -= amount;
            }
            if (remaining != 0) { message(player, "cogworkbridge.shop.checkout_failed"); return; }
        }
        int expected = 0; for (StockOrder.Line line : orders) expected += line.count();
        if (expected == 0 || StockLinkNetwork.requestOrder(player.getServer(), ticker.getNetwork(), orders, tag.getString("Address"), false) != expected) {
            message(player, "cogworkbridge.shop.checkout_failed"); return;
        }
        for (int i = 0; i < inventory.size(); i++) player.inventory.mainInventory.set(i, inventory.get(i));
        ticker.payments().deserializeNBT(payments.serializeNBT()); ticker.markDirty();
        player.setHeldItem(hand, ItemStack.EMPTY); player.inventory.markDirty(); message(player, "cogworkbridge.shop.checkout_success");
    }
    private static boolean append(List<StockOrder.Line> lines, ItemStack stack, long amount) {
        if (stack.isEmpty() || amount <= 0 || amount > 4096) return false;
        for (int i = 0; i < lines.size(); i++) {
            StockOrder.Line old = lines.get(i);
            if (!ItemStack.areItemsEqual(old.item(), stack) || !ItemStack.areItemStackTagsEqual(old.item(), stack)) continue;
            if (old.count() + amount > 4096) return false;
            lines.set(i, new StockOrder.Line(old.item(), (int) (old.count() + amount))); return true;
        }
        if (lines.size() >= 45) return false;
        ItemStack sample = stack.copy(); sample.setCount(1); lines.add(new StockOrder.Line(sample, (int) amount)); return true;
    }
    private static void message(EntityPlayer player, String key) { player.sendStatusMessage(new TextComponentTranslation(key), true); }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!player.isSneaking()) return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
        return new ActionResult<>(EnumActionResult.SUCCESS, ItemStack.EMPTY);
    }
    @Override public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        NBTTagCompound tag = stack.getTagCompound(); if (tag == null) return;
        NBTTagList entries = tag.getTagList("Purchases", 10);
        for (int i = 0; i < Math.min(45, entries.tagCount()); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i); BlockPos pos = BlockPos.fromLong(entry.getLong("Pos"));
            if (world != null && world.isBlockLoaded(pos) && world.getTileEntity(pos) instanceof TableClothTileEntity cloth)
                for (StockOrder.Line line : cloth.order()) tooltip.add(line.item().getDisplayName() + " x" + (line.count() * entry.getInteger("Count")));
        }
    }
}
