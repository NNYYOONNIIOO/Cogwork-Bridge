package com.cogwork.cogworkbridge.content.contraptions.gantry;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class GantryCarriageBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyBool AXIS_ALONG_FIRST = PropertyBool.create("axis_along_first");
    public GantryCarriageBlock() {
        super(Material.IRON); setHardness(2F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP).withProperty(AXIS_ALONG_FIRST, false));
    }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, AXIS_ALONG_FIRST); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6))).withProperty(AXIS_ALONG_FIRST, meta >= 6); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex() + (state.getValue(AXIS_ALONG_FIRST) ? 6 : 0); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        IBlockState state = getDefaultState().withProperty(FACING, side);
        return withShaftAxis(state, world.getBlockState(pos.offset(side.getOpposite())));
    }
    @Override public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        IBlockState shaft = world.getBlockState(pos.offset(side.getOpposite()));
        return shaft.getBlock() instanceof GantryShaftBlock && shaft.getValue(GantryShaftBlock.FACING).getAxis() != side.getAxis();
    }
    @Override public EnumFacing.Axis getAxis(IBlockState state) {
        EnumFacing.Axis facing = state.getValue(FACING).getAxis();
        int index = 0;
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            if (axis == facing) continue;
            if ((index++ == 0) == state.getValue(AXIS_ALONG_FIRST)) return axis;
        }
        return EnumFacing.Axis.X;
    }
    private IBlockState withShaftAxis(IBlockState state, IBlockState shaft) {
        if (!(shaft.getBlock() instanceof GantryShaftBlock)) return state;
        EnumFacing.Axis mount = state.getValue(FACING).getAxis();
        EnumFacing.Axis rail = shaft.getValue(GantryShaftBlock.FACING).getAxis();
        if (rail == mount) return state;
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            if (axis != mount && axis != rail) {
                boolean first = true;
                for (EnumFacing.Axis candidate : EnumFacing.Axis.values()) {
                    if (candidate == mount) continue;
                    first = candidate == axis;
                    break;
                }
                return state.withProperty(AXIS_ALONG_FIRST, first);
            }
        }
        return state;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, block, fromPos);
        BlockPos support = pos.offset(state.getValue(FACING).getOpposite());
        if (world.isRemote || !fromPos.equals(support) || !world.isBlockLoaded(support)) return;
        IBlockState shaft = world.getBlockState(support);
        if (!(shaft.getBlock() instanceof GantryShaftBlock) || shaft.getValue(GantryShaftBlock.FACING).getAxis() == state.getValue(FACING).getAxis()) {
            world.destroyBlock(pos, true);
            return;
        }
        IBlockState adjusted = withShaftAxis(state, shaft);
        if (adjusted != state) world.setBlockState(pos, adjusted, 3);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                              EnumHand hand, EnumFacing side, float x, float y, float z) {
        return !player.isSneaking() && player.getHeldItem(hand).isEmpty();
    }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) { return side.getAxis() == getAxis(state); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new GantryCarriageTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
}
