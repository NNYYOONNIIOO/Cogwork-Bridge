package com.cogwork.cogworkbridge.content.equipment.clipboard;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Shared item and block data, with fixed limits for network and save input. */
public final class ClipboardData {
    public static final String KEY = "CogworkBridgeClipboard";
    public static final int MAX_PAGES = 50;
    public static final int ROWS = 12;
    public static final int MAX_TEXT = 64;

    private ClipboardData() { }

    public static boolean isWritten(NBTTagCompound data) { return !data.isEmpty(); }
    public static boolean isReadOnly(NBTTagCompound data) { return data.getBoolean("ReadOnly"); }
    public static int pageCount(NBTTagCompound data) {
        return Math.max(1, Math.min(MAX_PAGES, data.getTagList("Pages", 10).tagCount()));
    }

    public static void applyEdit(NBTTagCompound data, int page, String[] lines, int checked, ItemStack[] icons) {
        if (page < 0 || page >= MAX_PAGES || (isReadOnly(data) && page >= pageCount(data))) return;
        if (isReadOnly(data)) setPage(data, page, lines(data, page), checked, icons(data, page));
        else setPage(data, page, lines, checked, icons);
    }

    public static NBTTagCompound fromItem(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        return root == null ? new NBTTagCompound() : root.getCompoundTag(KEY).copy();
    }

    public static void toItem(ItemStack stack, NBTTagCompound data) {
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        root.setTag(KEY, data.copy());
        stack.setTagCompound(root);
    }

    /** Merge entries from one clipboard into another without duplicating text. */
    public static void merge(NBTTagCompound destination, NBTTagCompound source) {
        if (isReadOnly(destination)) return;
        NBTTagList sourcePages = source.getTagList("Pages", 10);
        for (int page = 0; page < Math.min(MAX_PAGES, sourcePages.tagCount()); page++) {
            NBTTagList sourceEntries = sourcePages.getCompoundTagAt(page).getTagList("Entries", 10);
            for (int row = 0; row < Math.min(ROWS, sourceEntries.tagCount()); row++) {
                NBTTagCompound entry = sourceEntries.getCompoundTagAt(row);
                String text = entry.getString("Text");
                ItemStack icon = entry.hasKey("Icon", 10) ? new ItemStack(entry.getCompoundTag("Icon")) : ItemStack.EMPTY;
                if (text.isEmpty() && icon.isEmpty()) continue;
                boolean duplicate = false;
                for (int targetPage = 0; targetPage < pageCount(destination) && !duplicate; targetPage++) {
                    String[] existing = lines(destination, targetPage);
                    ItemStack[] existingIcons = icons(destination, targetPage);
                    for (int i = 0; i < ROWS; i++) {
                        if ((!text.isEmpty() && text.equals(existing[i]))
                                || (text.isEmpty() && existing[i].isEmpty() && !existingIcons[i].isEmpty()
                                && ItemStack.areItemsEqual(icon, existingIcons[i])
                                && ItemStack.areItemStackTagsEqual(icon, existingIcons[i]))) {
                            duplicate = true;
                            break;
                        }
                    }
                }
                if (duplicate) continue;
                boolean added = false;
                for (int targetPage = 0; targetPage < MAX_PAGES && !added; targetPage++) {
                    String[] values = lines(destination, targetPage);
                    ItemStack[] valuesIcons = icons(destination, targetPage);
                    int flags = checked(destination, targetPage);
                    for (int i = 0; i < ROWS; i++) {
                        if (!values[i].isEmpty() || !valuesIcons[i].isEmpty()) continue;
                        values[i] = text;
                        valuesIcons[i] = icon;
                        if (entry.getBoolean("Checked")) flags |= 1 << i;
                        else flags &= ~(1 << i);
                        setPage(destination, targetPage, values, flags, valuesIcons);
                        added = true;
                        break;
                    }
                }
            }
        }
    }

    public static NBTTagCompound page(NBTTagCompound data, int page) {
        NBTTagList pages = data.getTagList("Pages", 10);
        return page >= 0 && page < pages.tagCount() ? pages.getCompoundTagAt(page).copy() : new NBTTagCompound();
    }

    public static void setPage(NBTTagCompound data, int index, String[] lines, int checked, ItemStack[] icons) {
        if (index < 0 || index >= MAX_PAGES || lines.length != ROWS || icons.length != ROWS) return;
        NBTTagList old = data.getTagList("Pages", 10);
        NBTTagList pages = new NBTTagList();
        for (int i = 0; i < Math.min(MAX_PAGES, Math.max(old.tagCount(), index + 1)); i++) {
            if (i != index) {
                pages.appendTag(i < old.tagCount() ? old.getCompoundTagAt(i).copy() : new NBTTagCompound());
                continue;
            }
            NBTTagCompound page = i < old.tagCount() ? old.getCompoundTagAt(i).copy() : new NBTTagCompound();
            NBTTagList oldEntries = page.getTagList("Entries", 10);
            NBTTagList entries = new NBTTagList();
            for (int row = 0; row < ROWS; row++) {
                NBTTagCompound entry = row < oldEntries.tagCount() ? oldEntries.getCompoundTagAt(row).copy() : new NBTTagCompound();
                String text = lines[row] == null ? "" : lines[row];
                text = text.replaceAll("[\\x00-\\x1f\\x7f]", "");
                if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
                entry.setString("Text", text);
                entry.setBoolean("Checked", (checked & (1 << row)) != 0);
                ItemStack icon = icons[row];
                if (icon == null || icon.isEmpty()) entry.removeTag("Icon");
                else {
                    ItemStack copy = icon.copy();
                    copy.setCount(1);
                    entry.setTag("Icon", copy.writeToNBT(new NBTTagCompound()));
                }
                entries.appendTag(entry);
            }
            page.setTag("Entries", entries);
            pages.appendTag(page);
        }
        data.setTag("Pages", pages);
        data.setInteger("LastPage", index);
    }

    public static String[] lines(NBTTagCompound data, int page) {
        String[] lines = new String[ROWS];
        NBTTagList entries = page(data, page).getTagList("Entries", 10);
        for (int row = 0; row < ROWS; row++) lines[row] = row < entries.tagCount() ? entries.getCompoundTagAt(row).getString("Text") : "";
        return lines;
    }

    public static int checked(NBTTagCompound data, int page) {
        int flags = 0;
        NBTTagList entries = page(data, page).getTagList("Entries", 10);
        for (int row = 0; row < Math.min(ROWS, entries.tagCount()); row++)
            if (entries.getCompoundTagAt(row).getBoolean("Checked")) flags |= 1 << row;
        return flags;
    }

    public static ItemStack[] icons(NBTTagCompound data, int page) {
        ItemStack[] icons = new ItemStack[ROWS];
        NBTTagList entries = page(data, page).getTagList("Entries", 10);
        for (int row = 0; row < ROWS; row++) {
            NBTTagCompound entry = row < entries.tagCount() ? entries.getCompoundTagAt(row) : new NBTTagCompound();
            icons[row] = entry.hasKey("Icon", 10) ? new ItemStack(entry.getCompoundTag("Icon")) : ItemStack.EMPTY;
        }
        return icons;
    }
}
