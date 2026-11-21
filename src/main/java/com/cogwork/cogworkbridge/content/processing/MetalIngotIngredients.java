package com.cogwork.cogworkbridge.content.processing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Treat plain ingot ingredients in imported recipes as their ore-dictionary groups. */
final class MetalIngotIngredients {
    private MetalIngotIngredients() { }

    static List<ItemStack> expand(List<ItemStack> ingredients) {
        // Script ingredients already carry their own live ore/NBT/condition predicate.
        if (ingredients instanceof RecipeControl.Choices) return ingredients;
        Set<String> ores = new LinkedHashSet<>();
        for (ItemStack ingredient : ingredients) {
            if (ingredient.isEmpty() || ingredient.hasTagCompound()) continue;
            for (int id : OreDictionary.getOreIDs(ingredient)) {
                String name = OreDictionary.getOreName(id);
                if (name.startsWith("ingot") && name.length() > 5 && Character.isUpperCase(name.charAt(5))) ores.add(name);
            }
        }
        if (ores.isEmpty()) return ingredients;
        List<ItemStack> examples = new ArrayList<>(ingredients);
        for (String ore : ores) for (ItemStack alternative : OreDictionary.getOres(ore, false)) {
            if (alternative.isEmpty() || examples.stream().anyMatch(existing ->
                    ItemStack.areItemsEqual(existing, alternative) && ItemStack.areItemStackTagsEqual(existing, alternative))) continue;
            ItemStack copy = alternative.copy(); copy.setCount(1); examples.add(copy);
        }
        return new RecipeControl.Choices(examples, stack -> {
            if (RecipeControl.matches(ingredients, stack)) return true;
            for (int id : OreDictionary.getOreIDs(stack)) if (ores.contains(OreDictionary.getOreName(id))) return true;
            return false;
        });
    }
}
