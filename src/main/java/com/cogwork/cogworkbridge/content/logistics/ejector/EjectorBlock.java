package com.cogwork.cogworkbridge.content.logistics.ejector;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class EjectorBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0, 0, 0, 1, 13 / 16.0D, 1);
    public EjectorBlock() {
        super(Material.IRON); setHardness(2); setResistance(6);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }
    @Override public boolean hasAxis() { return false; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(Math.floorMod(meta, 4)));
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(FACING).rotateY().getAxis(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, int meta, EntityLivingBase placer, EnumHand hand) {
        if (placer instanceof EntityPlayer player) {
            EnumFacing selected = EjectorItem.targetFacing(player.getHeldItem(hand), world, pos);
            if (selected != null) return getDefaultState().withProperty(FACING, selected);
        }
        return getStateForPlacement(world, pos, side, x, y, z, meta, placer);
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side.getAxis() == getAxis(state);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new EjectorTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return SHAPE; }
    @Override public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof EjectorTileEntity ejector) {
            if (entity instanceof EntityItem item) ejector.acceptEntity(item);
            else if (entity.posY >= pos.getY() + 0.75D) ejector.requestLaunch();
        }
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (side != EnumFacing.UP || player.getHeldItem(hand).getItem() == AllItems.WRENCH
                || !(world.getTileEntity(pos) instanceof EjectorTileEntity ejector)) return false;
        if (!world.isRemote) ejector.interact(player, hand);
        return true;
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof EjectorTileEntity ejector) {
            ItemTransfer.drop(world, pos, ejector.takeAll()); ejector.dropFlights();
        }
        super.breakBlock(world, pos, state);
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        if (!(world.getTileEntity(pos) instanceof EjectorTileEntity ejector)) return 0;
        ItemStack stack = ejector.getStackInSlot(0);
        return stack.isEmpty() ? 0 : 1 + (int) (14.0F * stack.getCount() / stack.getMaxStackSize());
    }
}
