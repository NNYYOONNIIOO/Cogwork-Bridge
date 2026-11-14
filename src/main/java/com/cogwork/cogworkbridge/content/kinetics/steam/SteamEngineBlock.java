package com.cogwork.cogworkbridge.content.kinetics.steam;

import com.cogwork.cogworkbridge.content.fluids.FluidTankBlock;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SteamEngineBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public SteamEngineBlock() { super(Material.IRON); setHardness(2F); setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.UP)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new SteamEngineTileEntity(); }
    @Override public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return super.canPlaceBlockAt(world, pos) && world.getBlockState(pos.offset(side.getOpposite())).getBlock() instanceof FluidTankBlock;
    }
    @Override public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) { return net.minecraft.util.EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> new net.minecraft.util.math.AxisAlignedBB(0,1D/16,1D/16,1,15D/16,15D/16);
            case Y -> new net.minecraft.util.math.AxisAlignedBB(1D/16,0,1D/16,15D/16,1,15D/16);
            case Z -> new net.minecraft.util.math.AxisAlignedBB(1D/16,1D/16,0,15D/16,15D/16,1);
        };
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(AllBlocks.SHAFT)) return false;
        EnumFacing facing = state.getValue(FACING);
        BlockPos target = pos.offset(facing, 2);
        if (!world.isBlockLoaded(target) || !world.getBlockState(target).getBlock().isReplaceable(world, target)
                || !player.canPlayerEdit(target, facing, stack)) return false;
        if (!world.isRemote) {
            EnumFacing.Axis axis = KineticBlock.getPlacementAxis(player);
            if (axis == facing.getAxis()) axis = axis == EnumFacing.Axis.Y ? EnumFacing.Axis.X : EnumFacing.Axis.Y;
            world.setBlockState(target, AllBlocks.POWERED_SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis), 3);
            if (!player.capabilities.isCreativeMode) stack.shrink(1);
        }
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos from) {
        if (world.isRemote || !from.equals(pos.offset(state.getValue(FACING).getOpposite()))) return;
        if (!(world.getBlockState(from).getBlock() instanceof FluidTankBlock)) world.destroyBlock(pos, true);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            BlockPos target = pos.offset(state.getValue(FACING), 2);
            if (world.isBlockLoaded(target) && world.getTileEntity(target) instanceof PoweredShaftTileEntity shaft) shaft.release(pos);
        }
        super.breakBlock(world, pos, state);
    }
}
