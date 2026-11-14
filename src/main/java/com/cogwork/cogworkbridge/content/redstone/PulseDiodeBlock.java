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

/** Direction is stored in metadata; electrical state and timing live in the tile. */
public final class PulseDiodeBlock extends BlockContainer {
    public enum Kind { EXTENDER, REPEATER, TIMER }
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final IUnlistedProperty<Integer> MODEL = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "diode_model"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value < 4; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    private final Kind kind;
    public PulseDiodeBlock(Kind kind) {
        super(Material.CIRCUITS);
        this.kind = kind;
        setHardness(.5F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    public Kind kind() { return kind; }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{MODEL});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        int model = tile instanceof PulseDiodeTileEntity diode ? (diode.isInput() ? 1 : 0) | (diode.isOutput() ? 2 : 0) : 0;
        return ((IExtendedBlockState) state).withProperty(MODEL, model);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new PulseDiodeTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return new AxisAlignedBB(0, 0, 0, 1, .1875D, 1); }
    @Override public boolean canPlaceBlockAt(World world, BlockPos pos) { return world.isSideSolid(pos.down(), EnumFacing.UP); }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public boolean shouldCheckWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return false; }
    @Override public boolean canConnectRedstone(IBlockState state, IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) {
        return side != null && side.getAxis() == state.getValue(FACING).getAxis();
    }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == state.getValue(FACING) && world.getTileEntity(pos) instanceof PulseDiodeTileEntity diode && diode.isOutput() ? 15 : 0;
    }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return getWeakPower(state, world, pos, side); }
    static boolean readInput(World world, BlockPos pos, EnumFacing facing) {
        BlockPos source = pos.offset(facing);
        IBlockState state = world.getBlockState(source);
        return world.getRedstonePower(source, facing) > 0 || state.getBlock() == Blocks.REDSTONE_WIRE && state.getValue(net.minecraft.block.BlockRedstoneWire.POWER) > 0;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && !canPlaceBlockAt(world, pos)) { dropBlockAsItem(world, pos, state, 0); world.setBlockToAir(pos); }
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || player.getHeldItem(hand).getItem() == AllItems.WRENCH || !player.canPlayerEdit(pos, side, player.getHeldItem(hand))) return false;
        if (!(world.getTileEntity(pos) instanceof PulseDiodeTileEntity diode)) return false;
        if (!world.isRemote) {
            diode.toggleInverted();
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, .3F, diode.isInverted() ? .6F : .5F);
        }
        return true;
    }
}
