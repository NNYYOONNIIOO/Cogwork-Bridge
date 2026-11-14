package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

/** Provides the Forge 1.12 fluid that the vanilla milk bucket does not expose. */
public final class MilkFluid {
    private static final String NAME = "milk";
    private static Fluid registered;

    private MilkFluid() { }

    public static void register() {
        Fluid existing = FluidRegistry.getFluid(NAME);
        if (existing == null) {
            FluidRegistry.registerFluid(new Fluid(NAME,
                    new ResourceLocation("create", "fluid/milk_still"),
                    new ResourceLocation("create", "fluid/milk_flow")));
            existing = FluidRegistry.getFluid(NAME);
        }
        registered = existing;
    }

    @Nullable
    static FluidStack bucket() {
        return registered == null ? null : new FluidStack(registered, 1000);
    }

    static boolean isMilk(@Nullable FluidStack fluid) {
        return fluid != null && registered != null && fluid.getFluid() == registered;
    }
}
