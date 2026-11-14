package com.cogwork.cogworkbridge.content.logistics.packager;

import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Waits for every fragment in an order, then redistributes its contents into parcels. */
public final class RepackagerTileEntity extends PackagerTileEntity {
    private record Fragment(int slot, ItemStack stack) { }
    @Override public boolean linked() { return false; }

    @Override public ItemStack acceptPackage(ItemStack parcel, boolean simulate) {
        if (parcel.getCount() != 1 || !acceptsAddress(parcel) || !box.isEmpty() || !queued.isEmpty() || animation > 0)
            return parcel;
        IItemHandler target = target();
        if (target == null) return parcel;
        ItemStack result = ItemHandlerHelper.insertItemStacked(target, parcel.copy(), simulate);
        if (!simulate && result.isEmpty()) startAnimation(true, parcel);
        return result;
    }

    @Override public void pack() {
        if (world == null || world.isRemote || !box.isEmpty() || !queued.isEmpty() || animation > 0 || cooldown > 0) return;
        IItemHandler source = target();
        if (source == null) return;
        Map<UUID, TreeMap<Integer, Fragment>> orders = new HashMap<>();
        java.util.Set<UUID> invalid = new java.util.HashSet<>();
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack offered = source.extractItem(slot, 1, true);
            if (!PackageItem.isPackage(offered)) continue;
            UUID order = PackageItem.fragmentOrder(offered);
            if (order == null) {
                ItemStack extracted = source.extractItem(slot, 1, false);
                if (!PackageItem.isPackage(extracted)) { restore(source, slot, extracted); continue; }
                box = extracted; String address = signAddress();
                if (!address.isEmpty()) PackageItem.setAddress(box, address);
                startAnimation(false, ItemStack.EMPTY); return;
            }
            int index = PackageItem.fragmentIndex(offered);
            if (index < 0 || index >= 1024) { invalid.add(order); continue; }
            TreeMap<Integer, Fragment> parts = orders.computeIfAbsent(order, ignored -> new TreeMap<>());
            if (parts.putIfAbsent(index, new Fragment(slot, offered.copy())) != null) invalid.add(order);
        }
        for (Map.Entry<UUID, TreeMap<Integer, Fragment>> candidate : orders.entrySet()) {
            TreeMap<Integer, Fragment> parts = candidate.getValue();
            if (invalid.contains(candidate.getKey()) || parts.isEmpty()) continue;
            int last = parts.lastKey();
            if (parts.size() != last + 1 || !PackageItem.lastFragment(parts.get(last).stack())) continue;
            boolean complete = true;
            for (int i = 0; i < last; i++) if (PackageItem.lastFragment(parts.get(i).stack())) { complete = false; break; }
            if (!complete) continue;
            List<Fragment> extracted = new ArrayList<>();
            for (Fragment expected : parts.values()) {
                ItemStack actual = source.extractItem(expected.slot(), 1, false);
                if (!actual.isEmpty()) extracted.add(new Fragment(expected.slot(), actual));
                if (actual.isEmpty() || !ItemStack.areItemsEqual(actual, expected.stack())
                        || !ItemStack.areItemStackTagsEqual(actual, expected.stack())) { complete = false; break; }
            }
            if (!complete) {
                for (Fragment fragment : extracted) restore(source, fragment.slot(), fragment.stack());
                continue;
            }
            String address = signAddress();
            if (address.isEmpty()) address = PackageItem.address(extracted.get(0).stack());
            NBTTagCompound context = new NBTTagCompound();
            List<ItemStack> available = new ArrayList<>();
            for (Fragment fragment : extracted) {
                NBTTagCompound parcelContext = PackageItem.orderContext(fragment.stack());
                if (!parcelContext.isEmpty()) context = parcelContext;
                ItemStackHandler contents = PackageItem.contents(fragment.stack());
                for (int slot = 0; slot < contents.getSlots(); slot++)
                    if (!contents.getStackInSlot(slot).isEmpty()) available.add(contents.getStackInSlot(slot).copy());
            }
            List<StockOrder.Craft> crafts = StockOrder.readCrafts(context.getTagList("Crafts", 10));
            List<ItemStack> legacyPattern = StockOrder.readPattern(context.getTagList("Craft", 10));
            if (crafts.isEmpty() && legacyPattern.size() == 9) crafts.add(new StockOrder.Craft(legacyPattern, 1));
            for (StockOrder.Craft craft : crafts) for (int repetition = 0; repetition < craft.count(); repetition++) {
                List<ItemStack> pattern = craft.pattern();
                ItemStackHandler recipeBox = new ItemStackHandler(PackageItem.SLOTS);
                boolean validPattern = true;
                List<ItemStack> remaining = new ArrayList<>();
                for (ItemStack stack : available) remaining.add(stack.copy());
                for (int i = 0; i < pattern.size(); i++) {
                    ItemStack wanted = pattern.get(i);
                    if (wanted.isEmpty()) continue;
                    ItemStack found = ItemStack.EMPTY;
                    for (ItemStack stack : remaining)
                        if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, wanted) && ItemStack.areItemStackTagsEqual(stack, wanted)) { found = stack; break; }
                    if (found.isEmpty()) { validPattern = false; break; }
                    ItemStack one = found.copy(); one.setCount(1); found.shrink(1); recipeBox.setStackInSlot(i, one);
                }
                if (!validPattern) break;
                available = remaining;
                ItemStack parcel = PackageItem.create(recipeBox, address, world.rand);
                NBTTagCompound recipeContext = context.copy(); recipeContext.removeTag("Crafts");
                recipeContext.setTag("Craft", StockOrder.writePattern(pattern));
                PackageItem.setOrderContext(parcel, recipeContext);
                queued.add(parcel);
            }
            List<ItemStack> ordered = new ArrayList<>();
            for (StockOrder.Line line : StockOrder.read(context.getTagList("Items", 10))) {
                int needed = line.count();
                for (ItemStack stack : available) {
                    if (needed <= 0) break;
                    if (stack.isEmpty() || !ItemStack.areItemsEqual(stack, line.item()) || !ItemStack.areItemStackTagsEqual(stack, line.item())) continue;
                    int amount = Math.min(needed, stack.getCount());
                    ItemStack part = stack.copy(); part.setCount(amount); ordered.add(part);
                    stack.shrink(amount); needed -= amount;
                }
            }
            for (ItemStack stack : available) if (!stack.isEmpty()) ordered.add(stack);
            ItemStackHandler output = new ItemStackHandler(PackageItem.SLOTS);
            boolean any = false;
            for (ItemStack stack : ordered) {
                    ItemStack remaining = stack.copy();
                    while (!remaining.isEmpty()) {
                        int before = remaining.getCount();
                        ItemStack left = ItemHandlerHelper.insertItemStacked(output, remaining, false);
                        if (left.getCount() != before) any = true;
                        if (!left.isEmpty()) {
                            if (!any) { ItemTransfer.drop(world, pos, left); break; }
                            queued.add(PackageItem.create(output, address, world.rand));
                            output = new ItemStackHandler(PackageItem.SLOTS); any = false;
                        }
                        remaining = left;
                    }
            }
            if (any) queued.add(PackageItem.create(output, address, world.rand));
            // Empty parcels still represent an order; preserve one if no contents remain.
            if (queued.isEmpty()) queued.add(PackageItem.create(output, address, world.rand));
            int index = 0, ordinaryCount = 0;
            for (ItemStack parcel : queued)
                if (StockOrder.readPattern(PackageItem.orderContext(parcel).getTagList("Craft", 10)).isEmpty()) ordinaryCount++;
            for (ItemStack parcel : queued) {
                if (!StockOrder.readPattern(PackageItem.orderContext(parcel).getTagList("Craft", 10)).isEmpty()) continue;
                boolean lastFragment = ++index == ordinaryCount;
                PackageItem.setFragment(parcel, candidate.getKey(), index - 1, lastFragment);
                if (lastFragment && !context.isEmpty()) {
                    NBTTagCompound remainderContext = context.copy(); remainderContext.removeTag("Craft"); remainderContext.removeTag("Crafts");
                    PackageItem.setOrderContext(parcel, remainderContext);
                }
            }
            box = queued.removeFirst(); startAnimation(false, ItemStack.EMPTY); return;
        }
    }

    private void restore(IItemHandler source, int slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack left = source.insertItem(slot, stack, false);
        if (!left.isEmpty()) left = ItemHandlerHelper.insertItemStacked(source, left, false);
        if (!left.isEmpty()) ItemTransfer.drop(world, pos, left);
    }
}
