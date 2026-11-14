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
        var assembly = FillingRecipes.assembly(one, tank);
        if (assembly == null && com.cogwork.cogworkbridge.content.processing.ScriptedAssemblyRecipes.inProgress(one)) return null;
        ItemStack output = assembly != null ? assembly.result(null) : FillingRecipes.canFill(one, tank) ? FillingRecipes.fill(one, tank) : null;
        if (assembly != null) tank.drain(assembly.step().fluid(), true);
        if (output == null && FluidContainerHelper.canFill(one, tank, 1000)) {
            output = FluidContainerHelper.fill(one, tank, 1000);
            FluidStack consumed = available.copy(); consumed.amount = 1000 - tank.getFluidAmount();
            if (output != null && !FillingRecipes.allowed(one, output, consumed)) return null;
        }
        int used = 1000 - tank.getFluidAmount();
        if (output == null || output.isEmpty() || used <= 0) return null;
        available.amount = used;
        return new Result(output.copy(), available);
    }
}
