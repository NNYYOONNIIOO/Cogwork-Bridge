package com.cogwork.cogworkbridge.content.processing.millstone;

import com.cogwork.cogworkbridge.content.processing.RecipeControl;

import net.minecraft.block.BlockStone;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/** Initial 1.12-compatible milling results; the full recipe system remains separate work. */
public final class MillstoneRecipes {
    private MillstoneRecipes() {
    }

    @Nullable
    public static Recipe find(ItemStack input) {
        if (input.isEmpty()) return null;
        var upstream = com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("milling", input);
        if (upstream != null) return new Recipe(upstream.outputs().get(0).stack().copy(), upstream.duration());
        Recipe builtin = builtin(input);
        return builtin != null && RecipeControl.allow("milling", input, ItemStack.EMPTY, builtin.result()) ? builtin : null;
    }
    private static Recipe builtin(ItemStack input) {
        Item item = input.getItem();
        int meta = input.getMetadata();
        if (item == Item.getItemFromBlock(Blocks.COBBLESTONE)) return new Recipe(new ItemStack(Blocks.GRAVEL), 250);
        if (item == Item.getItemFromBlock(Blocks.GRAVEL)) return new Recipe(new ItemStack(Items.FLINT), 250);
        if (item == Item.getItemFromBlock(Blocks.SANDSTONE) && meta == 0) return new Recipe(new ItemStack(Blocks.SAND), 150);
        if (item == Item.getItemFromBlock(Blocks.STONE) && meta == BlockStone.EnumType.ANDESITE.getMetadata()) {
            return new Recipe(new ItemStack(Blocks.COBBLESTONE), 200);
        }
        if (item == Item.getItemFromBlock(Blocks.STONE) && meta == BlockStone.EnumType.GRANITE.getMetadata()) {
            return new Recipe(new ItemStack(Blocks.SAND, 1, 1), 200);
        }
        if (item == Item.getItemFromBlock(Blocks.WOOL)) return new Recipe(new ItemStack(Items.STRING), 100);
        return null;
    }

    public static java.util.List<ItemStack> possible(ItemStack input) {
        var upstream = com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("milling", input);
        if (upstream == null) {
            Recipe recipe = find(input);
            return recipe == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(recipe.result().copy());
        }
        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        for (var output : upstream.outputs()) result.add(output.stack().copy());
        return result;
    }
    public static java.util.List<ItemStack> roll(ItemStack input, java.util.Random random) {
        var upstream = com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("milling", input);
        return upstream == null ? possible(input) : upstream.roll(random);
    }

    public record Recipe(ItemStack result, int duration) {
    }
}
