package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.content.processing.RecipeControl;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.List;

/** Machine-only rules: removing a drain recipe does not break hand-held fluid containers. */
final class DrainProcessingRecipes {
    private DrainProcessingRecipes() { }
    static ItemDrainRecipes.Result preview(ItemStack input) {
        if (input.isEmpty()) return null;
        var recipe = UpstreamProcessingRecipes.find("emptying", input);
        if (recipe != null) return new ItemDrainRecipes.Result(recipe.outputs().get(0).stack().copy(), recipe.fluidOutput().copy());
        FluidStack fluid = FluidContainerHelper.contents(input);
        if (fluid == null || fluid.amount <= 0 || fluid.amount > 1500) return null;
        FluidTank tank = new FluidTank(1500);
        ItemStack result = FluidContainerHelper.empty(input, tank, fluid);
        if (result == null || !RecipeControl.allowed(new RecipeControl.View("emptying", "", List.of(List.of(input)),
                List.of(), List.of(result), List.of(), fluid))) return null;
        return new ItemDrainRecipes.Result(result.copy(), fluid.copy());
    }
    static FluidStack contents(ItemStack input) {
        var plan = preview(input); return plan == null ? null : plan.fluid();
    }
    static ItemStack empty(ItemStack input, IFluidHandler destination, FluidStack expected) {
        var plan = preview(input);
        if (plan == null || expected == null || !plan.fluid().isFluidEqual(expected) || plan.fluid().amount != expected.amount
                || destination.fill(plan.fluid(), false) != plan.fluid().amount) return null;
        return destination.fill(plan.fluid(), true) == plan.fluid().amount ? plan.container().copy() : null;
    }
}
