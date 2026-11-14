package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

/** Lava filling followed by the two press strokes used for a sturdy sheet. */
public final class ObsidianSheetSequence {
    public static final String STEP = "ObsidianSheetPresses";
    private ObsidianSheetSequence() { }

    public static boolean isPowder(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (int id : OreDictionary.getOreIDs(stack)) if ("dustObsidian".equals(OreDictionary.getOreName(id))) return true;
        return false;
    }

    public static ItemStack filled() {
        ItemStack result = new ItemStack(AllItems.UNPROCESSED_OBSIDIAN_SHEET);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(STEP, 0);
        result.setTagCompound(tag);
        return result;
    }

    public static ItemStack pressed(ItemStack input) {
        if (input.isEmpty() || input.getItem() != AllItems.UNPROCESSED_OBSIDIAN_SHEET) return ItemStack.EMPTY;
        int presses = input.hasTagCompound() ? input.getTagCompound().getInteger(STEP) : 0;
        if (presses >= 1) return new ItemStack(AllItems.STURDY_SHEET);
        ItemStack result = new ItemStack(AllItems.UNPROCESSED_OBSIDIAN_SHEET);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(STEP, presses + 1);
        result.setTagCompound(tag);
        return result;
    }
}
