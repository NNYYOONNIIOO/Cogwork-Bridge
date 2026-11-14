package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A bearing whose two independent hands follow the world's time. */
public final class ClockworkBearingBlock extends KineticBlock {
    public ClockworkBearingBlock() {
        super(Material.WOOD); setHardness(1F); setResistance(8F);
        setDefaultState(blockState.getBaseState().withProperty(MechanicalBearingBlock.FACING, EnumFacing.NORTH));
    }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, MechanicalBearingBlock.FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(MechanicalBearingBlock.FACING, EnumFacing.byIndex(Math.floorMod(meta, 6))); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(MechanicalBearingBlock.FACING).getIndex(); }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(MechanicalBearingBlock.FACING).getAxis(); }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side == state.getValue(MechanicalBearingBlock.FACING).getOpposite(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(MechanicalBearingBlock.FACING,
                preferred == null ? getPlacementFacing(placer, placer.isSneaking()) : preferred.getOpposite());
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ClockworkBearingTileEntity(); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                              EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (player.isSneaking() || !player.getHeldItem(hand).isEmpty()) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof ClockworkBearingTileEntity clock) clock.toggle();
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ClockworkBearingTileEntity clock) clock.disassemble();
        super.breakBlock(world, pos, state);
    }
}
