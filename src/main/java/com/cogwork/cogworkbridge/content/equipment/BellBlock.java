package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class BellBlock extends BlockContainer {
    public enum Mount implements IStringSerializable {
        FLOOR, CEILING, SINGLE_WALL, DOUBLE_WALL;
        @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Mount> MOUNT = PropertyEnum.create("mount", Mount.class);
    private final boolean haunted;
    public BellBlock(boolean haunted) {
        super(Material.IRON);
        this.haunted = haunted;
        setHardness(2F);
        setLightOpacity(0);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(MOUNT, Mount.FLOOR));
    }
    public boolean isHaunted() { return haunted; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, MOUNT); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex() | state.getValue(MOUNT).ordinal() << 2; }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(MOUNT, Mount.values()[(meta >> 2) & 3]); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        Mount mount = side == EnumFacing.UP ? Mount.FLOOR : side == EnumFacing.DOWN ? Mount.CEILING : Mount.SINGLE_WALL;
        EnumFacing facing = side.getAxis().isHorizontal() ? side : placer.getHorizontalFacing();
        if (mount == Mount.SINGLE_WALL && world.isSideSolid(pos.offset(side), side.getOpposite())) mount = Mount.DOUBLE_WALL;
        return getDefaultState().withProperty(FACING, facing).withProperty(MOUNT, mount);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new BellTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return new AxisAlignedBB(.1875D, 0, .1875D, .8125D, 1, .8125D); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (side.getAxis().isVertical() || y > .8124F) return false;
        boolean wall = state.getValue(MOUNT) == Mount.SINGLE_WALL || state.getValue(MOUNT) == Mount.DOUBLE_WALL;
        if ((side.getAxis() == state.getValue(FACING).getAxis()) == wall) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof BellTileEntity bell) { bell.ring(side); if (haunted) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "haunted_bell"); }
        return true;
    }
    @Override public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof BellTileEntity bell) { bell.ring(player.getHorizontalFacing()); if (haunted) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "haunted_bell"); }
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (world.isRemote) return;
        if (tryConvert(world, pos, state)) return;
        if (world.getTileEntity(pos) instanceof BellTileEntity bell) bell.updatePower(world.isBlockPowered(pos));
    }
    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote) tryConvert(world, pos, state);
    }
    private boolean tryConvert(World world, BlockPos pos, IBlockState state) {
        if (haunted) return false;
        net.minecraft.util.ResourceLocation below = world.getBlockState(pos.down()).getBlock().getRegistryName();
        if (below == null || !(below.getPath().equals("soul_fire") || below.getPath().equals("soul_campfire"))) return false;
        world.setBlockState(pos, AllBlocks.HAUNTED_BELL.getDefaultState().withProperty(FACING, state.getValue(FACING)).withProperty(MOUNT, state.getValue(MOUNT)), 3);
        world.playSound(null, pos, com.cogwork.cogworkbridge.registry.AllSounds.HAUNTED_BELL_CONVERT, net.minecraft.util.SoundCategory.BLOCKS, 1F, 1F);
        return true;
    }
}
