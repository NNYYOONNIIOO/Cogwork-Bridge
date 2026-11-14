package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Upstream filling recipes using items and fluids present in 1.12. */
final class FillingRecipes {
    private static final int GRASS_WATER = 500;
    private FillingRecipes() { }

    static boolean isCandidate(ItemStack stack) {
        return !stack.isEmpty() && (com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("filling", stack) != null
                || com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.isPowder(stack)
                || stack.getItem() == Item.getItemFromBlock(Blocks.DIRT) && stack.getMetadata() == 0);
    }

    private static com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.Recipe find(ItemStack stack, IFluidHandler source) {
        for (var recipe : com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.all("filling")) {
            if (!recipe.matches(stack) || recipe.outputs().size() != 1 || recipe.outputs().get(0).chance() < 1F) continue;
            FluidStack available = source.drain(recipe.fluid(), false);
            if (available != null && available.amount >= recipe.fluid().amount && available.isFluidEqual(recipe.fluid())) return recipe;
        }
        return null;
    }

    static boolean canFill(ItemStack stack, IFluidHandler source) {
        if (com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.isPowder(stack)) {
            FluidStack lava = source.drain(new FluidStack(FluidRegistry.LAVA, 500), false);
            return lava != null && lava.getFluid() == FluidRegistry.LAVA && lava.amount >= 500;
        }
        if (find(stack, source) != null) return true;
        if (!isCandidate(stack)) return false;
        if (stack.getItem() != Item.getItemFromBlock(Blocks.DIRT) || stack.getMetadata() != 0) return false;
        FluidStack fluid = source.drain(GRASS_WATER, false);
        return fluid != null && fluid.getFluid() == FluidRegistry.WATER && fluid.amount >= GRASS_WATER;
    }

    static ItemStack fill(ItemStack stack, IFluidHandler source) {
        if (com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.isPowder(stack)) {
            if (!canFill(stack, source)) return ItemStack.EMPTY;
            FluidStack lava = source.drain(new FluidStack(FluidRegistry.LAVA, 500), true);
            return lava != null && lava.getFluid() == FluidRegistry.LAVA && lava.amount == 500
                    ? com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.filled() : ItemStack.EMPTY;
        }
        var recipe = find(stack, source);
        if (recipe != null) {
            FluidStack drained = source.drain(recipe.fluid(), true);
            return drained != null && drained.amount == recipe.fluid().amount
                    ? recipe.outputs().get(0).stack().copy() : ItemStack.EMPTY;
        }
        if (!canFill(stack, source)) return ItemStack.EMPTY;
        FluidStack fluid = source.drain(GRASS_WATER, true);
        return fluid != null && fluid.getFluid() == FluidRegistry.WATER && fluid.amount == GRASS_WATER
                ? new ItemStack(Blocks.GRASS) : ItemStack.EMPTY;
    }
}
