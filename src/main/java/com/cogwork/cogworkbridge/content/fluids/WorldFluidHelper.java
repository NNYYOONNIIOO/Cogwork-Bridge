package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;

import javax.annotation.Nullable;

/** Resolves the registered fluid, including 1.12's unregistered flowing vanilla blocks. */
final class WorldFluidHelper {
    private WorldFluidHelper() { }

    @Nullable
    static Fluid fluidOf(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof IFluidBlock fluidBlock) return fluidBlock.getFluid();
        Fluid registered = FluidRegistry.lookupFluidForBlock(block);
        if (registered != null) return registered;
        if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) return FluidRegistry.WATER;
        if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) return FluidRegistry.LAVA;
        return null;
    }
}
