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
                || assembly(stack, null) != null
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
        if (assembly(stack, source) != null) return true;
        if (com.cogwork.cogworkbridge.content.processing.ScriptedAssemblyRecipes.inProgress(stack)) return false;
        if (find(stack, source) != null) return true;
        if (com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.isPowder(stack)) {
            FluidStack lava = source.drain(new FluidStack(FluidRegistry.LAVA, 500), false);
            return lava != null && lava.getFluid() == FluidRegistry.LAVA && lava.amount >= 500
                    && allowed(stack, com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.filled(), new FluidStack(FluidRegistry.LAVA, 500));
        }
        if (!isCandidate(stack)) return false;
        if (stack.getItem() != Item.getItemFromBlock(Blocks.DIRT) || stack.getMetadata() != 0) return false;
        FluidStack fluid = source.drain(GRASS_WATER, false);
        return fluid != null && fluid.getFluid() == FluidRegistry.WATER && fluid.amount >= GRASS_WATER
                && allowed(stack, new ItemStack(Blocks.GRASS), new FluidStack(FluidRegistry.WATER, GRASS_WATER));
    }

    static ItemStack fill(ItemStack stack, IFluidHandler source) {
        var assembly = assembly(stack, source);
        if (assembly != null) {
            FluidStack used = source.drain(assembly.step().fluid(), true);
            return used != null && used.amount == assembly.step().fluid().amount
                    ? assembly.result(java.util.concurrent.ThreadLocalRandom.current()) : ItemStack.EMPTY;
        }
        if (com.cogwork.cogworkbridge.content.processing.ScriptedAssemblyRecipes.inProgress(stack)) return ItemStack.EMPTY;
        var registered = find(stack, source);
        if (registered != null) {
            FluidStack drained = source.drain(registered.fluid(), true);
            return drained != null && drained.amount == registered.fluid().amount ? registered.outputs().get(0).stack().copy() : ItemStack.EMPTY;
        }
        if (com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.isPowder(stack)) {
            if (!canFill(stack, source)) return ItemStack.EMPTY;
            FluidStack lava = source.drain(new FluidStack(FluidRegistry.LAVA, 500), true);
            return lava != null && lava.getFluid() == FluidRegistry.LAVA && lava.amount == 500
                    ? com.cogwork.cogworkbridge.content.processing.ObsidianSheetSequence.filled() : ItemStack.EMPTY;
        }
        if (!canFill(stack, source)) return ItemStack.EMPTY;
        FluidStack fluid = source.drain(GRASS_WATER, true);
        return fluid != null && fluid.getFluid() == FluidRegistry.WATER && fluid.amount == GRASS_WATER
                ? new ItemStack(Blocks.GRASS) : ItemStack.EMPTY;
    }
    static com.cogwork.cogworkbridge.content.processing.ScriptedAssemblyRecipes.Match assembly(ItemStack input, IFluidHandler source) {
        return com.cogwork.cogworkbridge.content.processing.ScriptedAssemblyRecipes.find(
                com.cogwork.cogworkbridge.content.processing.AssemblyLineRecipes.Operation.FILL, input, ItemStack.EMPTY, source);
    }
    static boolean allowed(ItemStack input, ItemStack output, FluidStack fluid) {
        return com.cogwork.cogworkbridge.content.processing.RecipeControl.allowed(new com.cogwork.cogworkbridge.content.processing.RecipeControl.View(
                "filling", "", java.util.List.of(java.util.List.of(input)), java.util.List.of(), java.util.List.of(output), java.util.List.of(fluid), null));
    }
    static boolean containerAllowed(ItemStack input, IFluidHandler source) {
        for (var property : source.getTankProperties()) {
            FluidStack available = property.getContents();
            if (available == null) continue;
            var preview = SpoutRecipes.preview(input, available);
            if (preview != null) {
                FluidStack drained = source.drain(preview.fluid(), false);
                if (drained != null && drained.amount == preview.fluid().amount) return true;
            }
        }
        return false;
    }
}
