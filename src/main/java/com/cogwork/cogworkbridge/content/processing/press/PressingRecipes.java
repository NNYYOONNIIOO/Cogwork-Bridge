package com.cogwork.cogworkbridge.content.processing.press;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Pressing results whose input and output exist in this 1.12 port. */
public final class PressingRecipes {
    private PressingRecipes() { }

    public static ItemStack result(ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        ItemStack track = com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.press(input);
        if (!track.isEmpty()) return track;
        ItemStack sturdy = com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.pressed(input);
        if (!sturdy.isEmpty()) return sturdy;
        var upstream = com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("pressing", input);
        if (upstream != null && upstream.outputs().size() == 1 && upstream.outputs().get(0).chance() >= 1F)
            return upstream.outputs().get(0).stack().copy();
        if (input.getItem() == AllItems.PULP) return new ItemStack(AllItems.CARDBOARD);
        if (input.getItem() == Items.REEDS) return new ItemStack(Items.PAPER);
        if (input.getItem() == Items.IRON_INGOT) return new ItemStack(AllItems.IRON_SHEET);
        if (input.getItem() == AllItems.BRASS_INGOT) return new ItemStack(AllItems.BRASS_SHEET);
        if (input.getItem() == Items.GOLD_INGOT) return new ItemStack(AllItems.GOLDEN_SHEET);
        for (int id : OreDictionary.getOreIDs(input)) {
            String name = OreDictionary.getOreName(id);
            if ("ingotGold".equals(name)) return new ItemStack(AllItems.GOLDEN_SHEET);
            if ("ingotIron".equals(name)) return new ItemStack(AllItems.IRON_SHEET);
            if ("ingotCopper".equals(name)) return new ItemStack(AllItems.COPPER_SHEET);
            if ("ingotBrass".equals(name)) return new ItemStack(AllItems.BRASS_SHEET);
        }
        return ItemStack.EMPTY;
    }
}
