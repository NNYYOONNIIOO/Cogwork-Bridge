package com.cogwork.cogworkbridge.content.logistics.factory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.world.World;
import net.minecraftforge.common.crafting.IShapedRecipe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/** Finds a vanilla-size crafting arrangement from the connected panel filters. */
final class FactoryPanelRecipes {
    record Match(List<ItemStack> pattern, int output, int[] amounts) { }
    private FactoryPanelRecipes() { }

    @Nullable static Match find(World world, List<ItemStack> inputs, ItemStack output) {
        if (inputs.isEmpty() || output.isEmpty()) return null;
        for (IRecipe recipe : CraftingManager.REGISTRY) {
            if (!recipe.canFit(3, 3) || recipe.getRecipeOutput().isEmpty()
                    || !ItemStack.areItemsEqual(recipe.getRecipeOutput(), output)) continue;
            List<Ingredient> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty() || ingredients.size() > 9) continue;
            int width = recipe instanceof IShapedRecipe shaped ? shaped.getRecipeWidth() : Math.min(3, ingredients.size());
            int height = recipe instanceof IShapedRecipe shaped ? shaped.getRecipeHeight() : (ingredients.size() + width - 1) / width;
            if (width < 1 || width > 3 || height < 1 || height > 3 || ingredients.size() > width * height) continue;
            List<ItemStack> pattern = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
            int[] amounts = new int[inputs.size()];
            boolean valid = true;
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ingredient = ingredients.get(i);
                if (ingredient == Ingredient.EMPTY) continue;
                int chosen = -1;
                for (int j = 0; j < inputs.size(); j++) {
                    if (inputs.get(j).isEmpty() || !ingredient.apply(inputs.get(j))) continue;
                    if (chosen < 0 || amounts[j] < amounts[chosen]) chosen = j;
                }
                if (chosen < 0) { valid = false; break; }
                int x = i % width + (3 - width) / 2, y = i / width + (3 - height) / 2;
                ItemStack sample = inputs.get(chosen).copy(); sample.setCount(1);
                pattern.set(y * 3 + x, sample); amounts[chosen]++;
            }
            if (!valid) continue;
            for (int amount : amounts) if (amount == 0) { valid = false; break; }
            if (!valid) continue;
            InventoryCrafting grid = grid(pattern);
            if (!recipe.matches(grid, world)) continue;
            ItemStack result = recipe.getCraftingResult(grid);
            if (!result.isEmpty() && ItemStack.areItemsEqual(result, output) && ItemStack.areItemStackTagsEqual(result, output))
                return new Match(pattern, result.getCount(), amounts);
        }
        return null;
    }

    static boolean matches(World world, List<ItemStack> pattern, ItemStack output) {
        if (pattern.size() != 9 || output.isEmpty()) return false;
        InventoryCrafting grid = grid(pattern);
        IRecipe recipe = CraftingManager.findMatchingRecipe(grid, world);
        if (recipe == null) return false;
        ItemStack result = recipe.getCraftingResult(grid);
        return !result.isEmpty() && ItemStack.areItemsEqual(result, output) && ItemStack.areItemStackTagsEqual(result, output);
    }

    private static InventoryCrafting grid(List<ItemStack> pattern) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        for (int i = 0; i < Math.min(9, pattern.size()); i++) inventory.setInventorySlotContents(i, pattern.get(i).copy());
        return inventory;
    }
}
