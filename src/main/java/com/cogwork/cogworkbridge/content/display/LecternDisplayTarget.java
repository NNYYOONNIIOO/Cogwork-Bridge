package com.cogwork.cogworkbridge.content.display;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import xy177.farmersfuturedelight.common.tile.TileEntityLectern;

/** The FFD lectern retains ownership of its book, page and redstone state. */
public final class LecternDisplayTarget {
    private LecternDisplayTarget() { }
    public static boolean supports(TileEntity tile) { return tile instanceof TileEntityLectern; }

    public static void accept(TileEntity tile, int page, String text) {
        if (!(tile instanceof TileEntityLectern lectern) || page < 0 || page >= 50) return;
        ItemStack old = lectern.getBook();
        if (old.isEmpty() || old.getItem() != Items.WRITABLE_BOOK && old.getItem() != Items.WRITTEN_BOOK) return;
        boolean signing = old.getItem() == Items.WRITABLE_BOOK;
        ItemStack book = signing ? new ItemStack(Items.WRITTEN_BOOK) : old.copy();
        NBTTagCompound tag = old.hasTagCompound() ? old.getTagCompound().copy() : new NBTTagCompound();
        NBTTagList pages = tag.getTagList("pages", 8).copy();
        if (signing) {
            for (int i = 0; i < pages.tagCount(); i++)
                pages.set(i, new NBTTagString(json(pages.getStringTagAt(i))));
            tag.setString("title", "Printed Book");
            tag.setString("author", "Data Gatherer");
            tag.setBoolean("resolved", true);
        }
        String value = json(text);
        if (!signing && page < pages.tagCount() && pages.getStringTagAt(page).equals(value)) return;
        while (pages.tagCount() <= page) pages.appendTag(new NBTTagString(json("")));
        pages.set(page, new NBTTagString(value));
        tag.setTag("pages", pages);
        book.setTagCompound(tag);
        int selected = lectern.getPage();
        lectern.setBook(book);
        lectern.setPage(Math.min(selected, pages.tagCount() - 1));
        lectern.markDirty();
    }
    private static String json(String text) {
        return ITextComponent.Serializer.componentToJson(new TextComponentString(text));
    }
}
