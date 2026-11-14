package com.cogwork.cogworkbridge.content.logistics.portable;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class PortableStorageInterfaceBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public PortableStorageInterfaceBlock() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6)));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
                                            float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        EnumFacing facing = com.cogwork.cogworkbridge.kinetic.KineticBlock.getPlacementFacing(placer);
        if (!placer.isSneaking()) facing = facing.getOpposite();
        return getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new PortableStorageInterfaceTileEntity();
    }

    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case DOWN -> new AxisAlignedBB(0, 0.125D, 0, 1, 1, 1);
            case UP -> new AxisAlignedBB(0, 0, 0, 1, 0.875D, 1);
            case NORTH -> new AxisAlignedBB(0, 0, 0.125D, 1, 1, 1);
            case SOUTH -> new AxisAlignedBB(0, 0, 0, 1, 1, 0.875D);
            case WEST -> new AxisAlignedBB(0.125D, 0, 0, 1, 1, 1);
            case EAST -> new AxisAlignedBB(0, 0, 0, 0.875D, 1, 1);
        };
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof PortableStorageInterfaceTileEntity portable) {
            portable.stopConnection();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                net.minecraft.block.Block block, BlockPos neighborPos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof PortableStorageInterfaceTileEntity portable) {
            portable.refreshConnection();
        }
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof PortableStorageInterfaceTileEntity portable && portable.isConnected() ? 15 : 0;
    }
}
