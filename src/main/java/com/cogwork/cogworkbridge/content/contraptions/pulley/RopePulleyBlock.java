package com.cogwork.cogworkbridge.content.contraptions.pulley;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class RopePulleyBlock extends KineticBlock {
    public static final PropertyEnum<EnumFacing.Axis> HORIZONTAL_AXIS = PropertyEnum.create(
            "axis", EnumFacing.Axis.class, axis -> axis.isHorizontal());
    public RopePulleyBlock() {
        super(Material.IRON);
        setHardness(2F); setResistance(6F); setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(HORIZONTAL_AXIS, EnumFacing.Axis.X));
    }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, HORIZONTAL_AXIS); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(HORIZONTAL_AXIS, (meta & 1) == 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(HORIZONTAL_AXIS) == EnumFacing.Axis.X ? 0 : 1; }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(HORIZONTAL_AXIS); }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side.getAxis() == getAxis(state); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = getPreferredShaftFacing(world, pos);
        EnumFacing.Axis axis = preferred != null && preferred.getAxis().isHorizontal()
                ? preferred.getAxis() : placer.getHorizontalFacing().rotateY().getAxis();
        return getDefaultState().withProperty(HORIZONTAL_AXIS, axis);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
            return state.withProperty(HORIZONTAL_AXIS, getAxis(state) == EnumFacing.Axis.X ? EnumFacing.Axis.Z : EnumFacing.Axis.X);
        return state;
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new RopePulleyTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || !player.getHeldItem(hand).isEmpty()) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof RopePulleyTileEntity pulley) pulley.requestAssembly();
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof RopePulleyTileEntity pulley) pulley.removePulley();
        super.breakBlock(world, pos, state);
    }
}
