package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import com.cogwork.cogworkbridge.content.processing.mixer.MixingRecipes;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionType;
import net.minecraft.potion.PotionUtils;
import net.minecraftforge.common.brewing.AbstractBrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uses the same registered brewing rules as the 1.12 brewing stand for heated mixing. */
public final class PotionMixingRecipes {
    public static final int AMOUNT = 1000;
    private static final Item[] BOTTLES = {Items.POTIONITEM, Items.SPLASH_POTION, Items.LINGERING_POTION};

    public record Preview(ItemStack ingredient, FluidStack input, FluidStack output) { }

    private PotionMixingRecipes() { }

    private static boolean supported(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Item bottle : BOTTLES) if (stack.getItem() == bottle) return true;
        return false;
    }

    @Nullable
    public static FluidStack brew(FluidStack input, ItemStack ingredient) {
        if (input == null || input.amount < AMOUNT || ingredient.isEmpty()
                || input.getFluid() != FluidRegistry.WATER && !PotionFluid.isPotionFluid(input)) return null;
        ItemStack bottle = PotionFluid.toBottle(input);
        ItemStack reagent = ingredient.copy();
        reagent.setCount(1);
        ItemStack output = BrewingRecipeRegistry.getOutput(bottle, reagent);
        if (!supported(output)) return null;
        FluidStack result = PotionFluid.fromPotion(output, AMOUNT);
        if (result.isFluidEqual(input)) return null;
        return com.cogwork.cogworkbridge.content.processing.RecipeControl.allowed(new com.cogwork.cogworkbridge.content.processing.RecipeControl.View(
                "mixing", "", List.of(List.of(ingredient)), List.of(), List.of(), List.of(input), result)) ? result : null;
    }

    @Nullable
    public static MixingRecipes.Plan find(BasinTileEntity basin) {
        for (FluidStack available : basin.getInputFluids()) {
            if (available.amount < AMOUNT) continue;
            for (int slot = 0; slot < basin.getInputItems().getSlots(); slot++) {
                ItemStack ingredient = basin.getInputItems().getStackInSlot(slot);
                if (ingredient.isEmpty()) continue;
                FluidStack result = brew(available, ingredient);
                if (result == null) continue;
                FluidStack input = available.copy();
                input.amount = AMOUNT;
                int[] counts = new int[basin.getInputItems().getSlots()];
                counts[slot] = 1;
                if (basin.fillOutput(result, false) != AMOUNT
                        || !basin.processBatch(counts, Collections.emptyList(), true)) continue;
                ItemStack reagent = ingredient.copy();
                reagent.setCount(1);
                String id = "brewing:" + input.writeToNBT(new NBTTagCompound()) + ':'
                        + reagent.writeToNBT(new NBTTagCompound()) + ':' + result.writeToNBT(new NBTTagCompound());
                return new MixingRecipes.Plan(id, counts, ItemStack.EMPTY, 0, result, Collections.singletonList(input));
            }
        }
        return null;
    }

    /** Includes registered potion types and specific NBT-bearing inputs provided by other mods. */
    public static List<ItemStack> potionVariants(Collection<ItemStack> knownItems) {
        Map<String, ItemStack> variants = new LinkedHashMap<>();
        for (PotionType type : ForgeRegistries.POTION_TYPES.getValuesCollection()) {
            for (Item bottle : BOTTLES) addStack(variants, PotionUtils.addPotionToItemStack(new ItemStack(bottle), type));
        }
        for (ItemStack item : knownItems) if (supported(item)) addStack(variants, item);
        for (var recipe : BrewingRecipeRegistry.getRecipes())
            if (recipe instanceof AbstractBrewingRecipe<?> concrete && supported(concrete.getInput()))
                addStack(variants, concrete.getInput());
        return new ArrayList<>(variants.values());
    }

    /** Build JEI entries from the same rules used by the mixer, including Forge recipes. */
    public static List<Preview> previews(Collection<ItemStack> knownItems, Collection<ItemStack> potionInputs) {
        Map<String, ItemStack> reagents = new LinkedHashMap<>();
        for (ItemStack item : knownItems) {
            if (!item.isEmpty() && BrewingRecipeRegistry.isValidIngredient(item)) addStack(reagents, item);
        }
        for (var recipe : BrewingRecipeRegistry.getRecipes()) {
            if (!(recipe instanceof AbstractBrewingRecipe<?> concrete)) continue;
            Object ingredient = concrete.getIngredient();
            if (ingredient instanceof ItemStack stack) addStack(reagents, stack);
            else if (ingredient instanceof List<?> list)
                for (Object option : list) if (option instanceof ItemStack stack) addStack(reagents, stack);
        }
        Map<String, Preview> results = new LinkedHashMap<>();
        for (ItemStack bottle : potionInputs) {
            if (!supported(bottle)) continue;
            FluidStack input = PotionFluid.fromPotion(bottle, AMOUNT);
            for (ItemStack reagent : reagents.values()) {
                FluidStack output = brew(input, reagent);
                if (output == null) continue;
                String key = input.writeToNBT(new NBTTagCompound()) + ":" + reagent.writeToNBT(new NBTTagCompound())
                        + ":" + output.writeToNBT(new NBTTagCompound());
                results.putIfAbsent(key, new Preview(reagent.copy(), input.copy(), output));
            }
        }
        return new ArrayList<>(results.values());
    }

    private static void addStack(Map<String, ItemStack> stacks, ItemStack original) {
        if (original == null || original.isEmpty()) return;
        ItemStack stack = original.copy();
        stack.setCount(1);
        stacks.putIfAbsent(stack.writeToNBT(new NBTTagCompound()).toString(), stack);
    }
}
