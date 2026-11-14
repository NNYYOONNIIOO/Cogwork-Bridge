package com.cogwork.cogworkbridge.content.display;

import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;

/** Resolves vanilla and modded dyes using the standard Forge dye names. */
final class DisplayDye {
    private static final String[] NAMES = {"dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue",
            "dyeYellow", "dyeLime", "dyePink", "dyeGray", "dyeLightGray", "dyeCyan",
            "dyePurple", "dyeBlue", "dyeBrown", "dyeGreen", "dyeRed", "dyeBlack"};

    private DisplayDye() { }

    @Nullable static EnumDyeColor color(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() == Items.DYE) return EnumDyeColor.byDyeDamage(stack.getMetadata());
        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equals(name)) return EnumDyeColor.byMetadata(i);
            }
        }
        return null;
    }
}
