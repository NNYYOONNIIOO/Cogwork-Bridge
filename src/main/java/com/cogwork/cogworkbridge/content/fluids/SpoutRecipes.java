package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import javax.annotation.Nullable;

/** A read-only recipe preview using the same filling rules as the spout. */
public final class SpoutRecipes {
    public record Result(ItemStack item, FluidStack fluid) { }
    private SpoutRecipes() { }
    public static boolean isContainer(ItemStack item) { return FluidContainerHelper.canPossiblyFill(item); }
    @Nullable public static Result preview(ItemStack input, FluidStack fluid) {
        if (input.isEmpty() || fluid == null || fluid.amount <= 0) return null;
        ItemStack one = input.copy(); one.setCount(1);
        FluidStack available = fluid.copy(); available.amount = 1000;
        FluidTank tank = new FluidTank(1000); tank.setFluid(available);
        ItemStack output = FillingRecipes.canFill(one, tank) ? FillingRecipes.fill(one, tank)
                : FluidContainerHelper.canFill(one, tank, 1000) ? FluidContainerHelper.fill(one, tank, 1000) : null;
        int used = 1000 - tank.getFluidAmount();
        if (output == null || output.isEmpty() || used <= 0) return null;
        available.amount = used;
        return new Result(output.copy(), available);
    }
}
