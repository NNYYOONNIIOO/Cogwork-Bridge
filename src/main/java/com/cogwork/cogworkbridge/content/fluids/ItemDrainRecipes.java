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
        return DrainProcessingRecipes.preview(input);
    }
}
