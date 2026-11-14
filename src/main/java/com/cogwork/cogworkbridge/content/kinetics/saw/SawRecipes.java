package com.cogwork.cogworkbridge.content.kinetics.saw;

import com.cogwork.cogworkbridge.content.processing.RecipeControl;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.Recipe;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.Output;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class SawRecipes {
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
    private SawRecipes() { }
    public static ItemStack result(ItemStack input) {
        var recipes = matching(input, ItemStack.EMPTY);
        return recipes.isEmpty() ? ItemStack.EMPTY : recipes.get(0).outputs().get(0).stack().copy();
    }

    /** Keep recipe order stable so each new stack advances to the next matching output. */
    public static java.util.List<Recipe> matching(ItemStack input, ItemStack filter) {
        java.util.List<Recipe> matches = new java.util.ArrayList<>();
        if (input.isEmpty()) return matches;
        for (Recipe recipe : UpstreamProcessingRecipes.all("cutting"))
            if (recipe.matches(input) && !recipe.outputs().isEmpty()) matches.add(recipe);
        if (matches.isEmpty()) {
            ItemStack result = fallback(input);
            if (!result.isEmpty() && RecipeControl.allow("cutting", input, ItemStack.EMPTY, result)) matches.add(new Recipe("cutting", java.util.List.of(input.copy()),
                    java.util.List.of(), java.util.List.of(new Output(result, 1F)), 100, false, null));
        }
        var single = input.copy(); single.setCount(1);
        var ingredients = java.util.Collections.singletonList(single);
        for (var recipe : xy177.farmersfuturedelight.api.WorkstationRecipes.getRecipes(
                xy177.farmersfuturedelight.api.WorkstationRecipes.Type.STONECUTTING)) {
            if (!recipe.matches(ingredients)) continue;
            ItemStack output = recipe.assemble(java.util.Collections.singletonList(single.copy()));
            if (!output.isEmpty() && RecipeControl.allow("cutting", input, ItemStack.EMPTY, output)) matches.add(new Recipe("stonecutting", java.util.List.of(single.copy()),
                    java.util.List.of(), java.util.List.of(new Output(output, 1F)), 50, false, null));
        }
        // Upstream recipe filtering tests the principal output, not incidental by-products.
        matches.removeIf(recipe -> !ListFilterItem.matchesItem(filter, recipe.outputs().get(0).stack()));
        return matches;
    }

    private static ItemStack fallback(ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        if (input.getItem() == Item.getItemFromBlock(Blocks.LOG) || input.getItem() == Item.getItemFromBlock(Blocks.LOG2)) {
            int kind = (input.getMetadata() & 3) + (input.getItem() == Item.getItemFromBlock(Blocks.LOG2) ? 4 : 0);
            if (kind >= WOODS.length) return ItemStack.EMPTY;
            Item stripped = ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmers_future_delight", "stripped_" + WOODS[kind] + "_log"));
            return stripped == null ? ItemStack.EMPTY : new ItemStack(stripped);
        }
        if (input.getItem().getRegistryName() != null) {
            String id = input.getItem().getRegistryName().toString();
            for (int i = 0; i < WOODS.length; i++)
                if (id.equals("farmers_future_delight:stripped_" + WOODS[i] + "_log") || id.equals("farmers_future_delight:stripped_" + WOODS[i] + "_wood"))
                    return new ItemStack(Blocks.PLANKS, 6, i);
        }
        return ItemStack.EMPTY;
    }
}
