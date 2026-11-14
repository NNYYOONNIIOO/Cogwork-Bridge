package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import javax.annotation.Nullable;

/** A read-only preview using the same container rules as the item drain. */
public final class ItemDrainRecipes {
    public record Result(ItemStack container, FluidStack fluid) { }
    private ItemDrainRecipes() { }
    @Nullable public static Result preview(ItemStack input) {
        if (input.isEmpty()) return null;
        ItemStack one = input.copy(); one.setCount(1);
        FluidStack fluid = FluidContainerHelper.contents(one);
        if (fluid == null || fluid.amount <= 0 || fluid.amount > 1500) return null;
        FluidTank destination = new FluidTank(1500);
        ItemStack result = FluidContainerHelper.empty(one, destination, fluid);
        return result == null ? null : new Result(result.copy(), fluid.copy());
    }
}
