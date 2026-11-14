package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public final class RepackagerBlock extends PackagerBlock {
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new RepackagerTileEntity(); }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED; }
}
