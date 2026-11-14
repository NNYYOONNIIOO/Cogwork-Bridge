package com.cogwork.cogworkbridge.registry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumBlockRenderType;

/**
 * Unobtainable model carrier used by a TESR for a moving sub-part.
 *
 * <p>Keeping the sub-part as a normal baked model gives it the same texture atlas,
 * UV and lighting path as an ordinary block model.  It is registered without an
 * ItemBlock and is never placed in the world.</p>
 */
public final class RenderPartBlock extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public RenderPartBlock() {
        super(Material.ROCK);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.UP));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing[] values = EnumFacing.values();
        return getDefaultState().withProperty(FACING, values[Math.floorMod(meta, values.length)]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).ordinal();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
