package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.content.fluids.ProcessingFluids;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.BlockFluidFinite;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xy177.farmersfuturedelight.api.WaterloggedBlockApi;
import xy177.farmersfuturedelight.common.fluid.FFDStoredFluidStates;
import javax.annotation.Nullable;

/** Maps a saved fluid block to its actual fluid and material requirement. */
public final class SchematicFluids {
    private SchematicFluids() { }

    @Nullable public static Fluid fluidOf(IBlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) return FluidRegistry.WATER;
        if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) return FluidRegistry.LAVA;
        if (block instanceof IFluidBlock fluidBlock) return fluidBlock.getFluid();
        return FluidRegistry.lookupFluidForBlock(block);
    }

    /** Flowing vanilla/classic liquid is reproduced without creating another source. */
    public static int requiredAmount(IBlockState state) {
        Block block = state.getBlock();
        if (fluidOf(state) == null) return 0;
        if (block instanceof BlockLiquid) return state.getValue(BlockLiquid.LEVEL) == 0 ? Fluid.BUCKET_VOLUME : 0;
        if (block instanceof BlockFluidFinite finite) {
            int capacity = Math.max(1, finite.getMaxRenderHeightMeta() + 1);
            return Math.max(1, (state.getValue(BlockFluidBase.LEVEL) + 1) * Fluid.BUCKET_VOLUME / capacity);
        }
        if (block instanceof BlockFluidBase fluidBlock)
            return state.getValue(BlockFluidBase.LEVEL) == fluidBlock.getMaxRenderHeightMeta() ? Fluid.BUCKET_VOLUME : 0;
        return Fluid.BUCKET_VOLUME;
    }

    public static boolean alreadyPlaced(IBlockState existing, IBlockState desired) {
        Fluid fluid = fluidOf(desired);
        if (fluid == null || fluidOf(existing) != fluid) return false;
        int required = requiredAmount(desired);
        return required == 0 || requiredAmount(existing) >= required;
    }

    /** FFD stores waterlogged fluids outside the vanilla structure's block palette. */
    public static boolean alreadyContained(World world, BlockPos at, IBlockState desired) {
        if (!WaterloggedBlockApi.isWaterlogged(world, at)) return false;
        IBlockState existing = WaterloggedBlockApi.getFluidState(world, at);
        return existing != null && alreadyPlaced(existing, desired);
    }

    public static boolean placeContained(World world, BlockPos at, IBlockState expectedHost,
                                         IBlockState desired, boolean stored) {
        Fluid fluid = fluidOf(desired);
        if (fluid == null || fluid == FluidRegistry.WATER && world.provider.doesWaterVaporize()) return false;
        IBlockState host = world.getBlockState(at);
        if (host.getBlock() != expectedHost.getBlock()) return false;
        if (alreadyContained(world, at, desired)) return true;
        if (WaterloggedBlockApi.isWaterlogged(world, at)) return false;
        if (stored || requiredAmount(desired) == 0) {
            FFDStoredFluidStates.set(world, at, desired);
            world.notifyNeighborsOfStateChange(at, host.getBlock(), false);
            world.scheduleUpdate(at, host.getBlock(), WaterloggedBlockApi.fluidTickRate(world, fluid));
            return alreadyContained(world, at, desired);
        }
        return WaterloggedBlockApi.setContainedFluid(world, at, host, fluid, 3)
                && alreadyContained(world, at, desired);
    }

    public static ItemStack displayContainer(Fluid fluid) {
        FluidStack contents = new FluidStack(fluid, Fluid.BUCKET_VOLUME);
        Item local = ProcessingFluids.bucketFor(contents);
        return local != null ? new ItemStack(local) : FluidUtil.getFilledBucket(contents);
    }
}
