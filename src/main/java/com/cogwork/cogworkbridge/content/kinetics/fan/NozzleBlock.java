package com.cogwork.cogworkbridge.content.kinetics.fan;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A nozzle attaches to the open face of a fan. */
public final class NozzleBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public NozzleBlock() {
        super(Material.ROCK);
        setHardness(0.8F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.UP));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new NozzleTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face,
                                            float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, face);
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return super.canPlaceBlockOnSide(world, pos, side) && supported(world, pos, side);
    }

    private boolean supported(World world, BlockPos pos, EnumFacing facing) {
        IBlockState behind = world.getBlockState(pos.offset(facing.getOpposite()));
        return behind.getBlock() instanceof EncasedFanBlock && behind.getValue(EncasedFanBlock.FACING) == facing;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos changed) {
        EnumFacing facing = state.getValue(FACING);
        if (!world.isRemote && changed.equals(pos.offset(facing.getOpposite()))
                && !supported(world, pos, facing)) {
            world.destroyBlock(pos, true);
        }
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case UP -> new AxisAlignedBB(0.0625D, 0, 0.0625D, 0.9375D, 0.9375D, 0.9375D);
            case DOWN -> new AxisAlignedBB(0.0625D, 0.0625D, 0.0625D, 0.9375D, 1, 0.9375D);
            case NORTH -> new AxisAlignedBB(0.0625D, 0.0625D, 0.0625D, 0.9375D, 0.9375D, 1);
            case SOUTH -> new AxisAlignedBB(0.0625D, 0.0625D, 0, 0.9375D, 0.9375D, 0.9375D);
            case WEST -> new AxisAlignedBB(0.0625D, 0.0625D, 0.0625D, 1, 0.9375D, 0.9375D);
            case EAST -> new AxisAlignedBB(0, 0.0625D, 0.0625D, 0.9375D, 0.9375D, 0.9375D);
        };
    }
}
