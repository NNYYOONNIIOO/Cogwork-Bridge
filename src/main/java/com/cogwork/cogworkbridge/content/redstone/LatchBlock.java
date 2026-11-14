package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import javax.annotation.Nullable;

/** The set/reset and pulse-toggle latches share their directional redstone interface. */
public final class LatchBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final IUnlistedProperty<Integer> MODEL = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "latch_model"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 4; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    private final boolean toggle;
    public LatchBlock(boolean toggle) { super(Material.CIRCUITS); this.toggle = toggle; setHardness(.5F); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)); }
    public boolean isToggle() { return toggle; }
    @Override protected BlockStateContainer createBlockState() { return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{MODEL}); }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        int model = tile instanceof LatchTileEntity latch ? (latch.isOutput() ? 1 : 0) | (latch.isInput() ? 2 : 0) : 0;
        return ((IExtendedBlockState) state).withProperty(MODEL, model);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) { return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite()); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new LatchTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return new AxisAlignedBB(0, 0, 0, 1, .1875D, 1); }
    @Override public boolean canPlaceBlockAt(World world, BlockPos pos) { return world.isSideSolid(pos.down(), EnumFacing.UP); }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return false; }
    @Override public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) {
        return side != null && side.getAxis().isHorizontal() && (!toggle || side.getAxis() == state.getValue(FACING).getAxis());
    }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == state.getValue(FACING) && world.getTileEntity(pos) instanceof LatchTileEntity latch && latch.isOutput() ? 15 : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return getWeakPower(state, world, pos, side); }
    private static boolean input(World world, BlockPos pos, EnumFacing side) {
        BlockPos source = pos.offset(side);
        IBlockState state = world.getBlockState(source);
        return world.getRedstonePower(source, side) > 0 || state.getBlock() == Blocks.REDSTONE_WIRE && state.getValue(net.minecraft.block.BlockRedstoneWire.POWER) > 0;
    }
    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) world.scheduleUpdate(pos, this, 1);
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (world.isRemote) return;
        if (!canPlaceBlockAt(world, pos)) { dropBlockAsItem(world, pos, state, 0); world.setBlockToAir(pos); return; }
        if (!world.isUpdateScheduled(pos, this)) world.scheduleUpdate(pos, this, 1);
    }
    @Override public void updateTick(World world, BlockPos pos, IBlockState state, java.util.Random random) {
        if (!(world.getTileEntity(pos) instanceof LatchTileEntity latch)) return;
        EnumFacing facing = state.getValue(FACING);
        boolean back = input(world, pos, facing);
        boolean sides = !toggle && (input(world, pos, facing.rotateY()) || input(world, pos, facing.rotateYCCW()));
        latch.updateInput(back, sides, toggle);
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || player.getHeldItem(hand).getItem() == AllItems.WRENCH || !player.canPlayerEdit(pos, side, player.getHeldItem(hand))) return false;
        if (world.getTileEntity(pos) instanceof LatchTileEntity latch) {
            if (!toggle && latch.isInput() != latch.isSideInput()) return false;
            if (!world.isRemote) {
                latch.toggle();
                world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, .3F, latch.isOutput() ? .6F : .5F);
            }
            return true;
        }
        return false;
    }
}
