package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An addressed order retains counts separately from the item sample. */
public final class StockOrder {
    private StockOrder() { }
    public record Line(ItemStack item, int count) { }
    public record Craft(List<ItemStack> pattern, int count) { }
    public static NBTTagList writeCrafts(List<Craft> crafts) {
        NBTTagList tags = new NBTTagList(); int total = 0;
        for (Craft craft : crafts) {
            if (craft.pattern().size() != 9 || craft.count() < 1 || craft.count() > 256
                    || total + craft.count() > 256 || craft.pattern().stream().allMatch(ItemStack::isEmpty)) continue;
            NBTTagCompound tag = new NBTTagCompound(); tag.setTag("Pattern", writePattern(craft.pattern()));
            tag.setInteger("Count", craft.count()); tags.appendTag(tag); total += craft.count();
        }
        return tags;
    }
    public static List<Craft> readCrafts(NBTTagList tags) {
        List<Craft> result = new ArrayList<>(); int total = 0;
        for (int i = 0; i < Math.min(64, tags.tagCount()); i++) {
            NBTTagCompound tag = tags.getCompoundTagAt(i);
            List<ItemStack> pattern = readPattern(tag.getTagList("Pattern", 10)); int count = tag.getInteger("Count");
            if (pattern.size() != 9 || count < 1 || count > 256 || total + count > 256
                    || pattern.stream().allMatch(ItemStack::isEmpty)) continue;
            result.add(new Craft(pattern, count)); total += count;
        }
        return result;
    }
    public static boolean matchesCrafts(List<Line> order, List<Craft> crafts) {
        if (crafts.isEmpty()) return false;
        List<Line> expected = new ArrayList<>();
        for (Craft craft : crafts) for (ItemStack item : craft.pattern())
            if (!item.isEmpty()) expected.add(new Line(item, craft.count()));
        List<Line> actual = normalize(order); expected = normalize(expected);
        if (actual.size() != expected.size()) return false;
        for (Line line : actual) {
            boolean found = false;
            for (Line needed : expected) if (line.count() == needed.count() && ItemStack.areItemsEqual(line.item(), needed.item())
                    && ItemStack.areItemStackTagsEqual(line.item(), needed.item())) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }
    public static List<Line> normalize(List<Line> input) {
        List<Line> result = new ArrayList<>();
        for (Line line : input) {
            if (line == null || line.item() == null || line.item().isEmpty() || line.count() <= 0 || line.count() > 4096) continue;
            int index = -1;
            for (int i = 0; i < result.size(); i++) {
                ItemStack item = result.get(i).item();
                if (ItemStack.areItemsEqual(item, line.item()) && ItemStack.areItemStackTagsEqual(item, line.item())) { index = i; break; }
            }
            if (index >= 0) {
                Line old = result.get(index);
                result.set(index, new Line(old.item(), Math.min(4096, old.count() + line.count())));
            } else if (result.size() < 45) {
                ItemStack sample = line.item().copy(); sample.setCount(1);
                result.add(new Line(sample, line.count()));
            }
        }
        return result;
    }
    public static NBTTagList write(List<Line> lines) {
        NBTTagList result = new NBTTagList();
        for (Line line : normalize(lines)) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setTag("Item", line.item().writeToNBT(new NBTTagCompound()));
            entry.setInteger("Count", line.count()); result.appendTag(entry);
        }
        return result;
    }
    public static List<Line> read(NBTTagList data) {
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < Math.min(45, data.tagCount()); i++) {
            NBTTagCompound entry = data.getCompoundTagAt(i);
            result.add(new Line(new ItemStack(entry.getCompoundTag("Item")), entry.getInteger("Count")));
        }
        return normalize(result);
    }
    public static NBTTagList writePattern(List<ItemStack> pattern) {
        NBTTagList result = new NBTTagList();
        if (pattern.size() != 9) return result;
        for (ItemStack stack : pattern) {
            NBTTagCompound slot = new NBTTagCompound();
            if (!stack.isEmpty()) slot.setTag("Item", stack.writeToNBT(new NBTTagCompound()));
            result.appendTag(slot);
        }
        return result;
    }
    public static List<ItemStack> readPattern(NBTTagList data) {
        if (data.tagCount() != 9) return new ArrayList<>();
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            NBTTagCompound slot = data.getCompoundTagAt(i);
            ItemStack stack = slot.hasKey("Item", 10) ? new ItemStack(slot.getCompoundTag("Item")) : ItemStack.EMPTY;
            if (!stack.isEmpty()) stack.setCount(1);
            result.add(stack);
        }
        return result;
    }
    public static boolean matchesPattern(Map<UUID, List<Line>> order, List<ItemStack> pattern) {
        List<Line> lines = new ArrayList<>();
        for (List<Line> input : order.values()) lines.addAll(input);
        lines = normalize(lines);
        int[] remaining = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) remaining[i] = lines.get(i).count();
        for (ItemStack sample : pattern) {
            if (sample.isEmpty()) continue;
            int index = -1;
            for (int i = 0; i < lines.size(); i++)
                if (remaining[i] > 0 && ItemStack.areItemsEqual(lines.get(i).item(), sample)
                        && ItemStack.areItemStackTagsEqual(lines.get(i).item(), sample)) { index = i; break; }
            if (index < 0) return false;
            remaining[index]--;
        }
        for (int count : remaining) if (count != 0) return false;
        return true;
    }
}
