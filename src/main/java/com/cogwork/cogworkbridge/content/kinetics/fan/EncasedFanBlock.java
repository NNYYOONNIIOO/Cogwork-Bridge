package com.cogwork.cogworkbridge.content.kinetics.fan;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A directional fan driven through the shaft at its back. */
public final class EncasedFanBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public EncasedFanBlock() {
        super(Material.ROCK);
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(FACING).getAxis(); }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        EnumFacing facing = preferred == null
                ? getPlacementFacing(placer, placer.isSneaking()) : preferred.getOpposite();
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override public EnumFacing getShaftFacing(World world, BlockPos pos, IBlockState state) {
        return state.getValue(FACING).getOpposite();
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new EncasedFanTileEntity(); }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
}
