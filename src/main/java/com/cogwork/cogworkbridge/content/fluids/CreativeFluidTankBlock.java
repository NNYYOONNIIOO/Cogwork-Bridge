package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class CreativeFluidTankBlock extends FluidTankBlock {
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new CreativeFluidTankTileEntity(); }
}
