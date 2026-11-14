package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

public final class PlacardBlock extends BlockContainer {
    public enum Mount implements IStringSerializable { FLOOR, WALL, CEILING; @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); } }
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Mount> MOUNT = PropertyEnum.create("mount", Mount.class);
    public PlacardBlock() { super(Material.CIRCUITS); setHardness(.5F); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(MOUNT, Mount.FLOOR)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING, MOUNT); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex() | state.getValue(MOUNT).ordinal() << 2; }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(MOUNT, Mount.values()[Math.min(2, (meta >> 2) & 3)]); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        Mount mount = side == EnumFacing.UP ? Mount.FLOOR : side == EnumFacing.DOWN ? Mount.CEILING : Mount.WALL;
        EnumFacing facing = side.getAxis().isHorizontal() ? side : mount == Mount.FLOOR ? placer.getHorizontalFacing() : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(MOUNT, mount).withProperty(FACING, facing);
    }
    public static EnumFacing outward(IBlockState state) { return state.getValue(MOUNT) == Mount.FLOOR ? EnumFacing.UP : state.getValue(MOUNT) == Mount.CEILING ? EnumFacing.DOWN : state.getValue(FACING); }
    @Override public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        BlockPos support = pos.offset(side.getOpposite());
        return world.getBlockState(support).getCollisionBoundingBox(world, support) != Block.NULL_AABB;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && !canPlaceBlockOnSide(world, pos, outward(state))) { dropBlockAsItem(world, pos, state, 0); world.setBlockToAir(pos); }
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new PlacardTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (outward(state)) {
            case UP -> new AxisAlignedBB(.125, 0, .125, .875, .1875, .875);
            case DOWN -> new AxisAlignedBB(.125, .8125, .125, .875, 1, .875);
            case NORTH -> new AxisAlignedBB(.125, .125, .8125, .875, .875, 1);
            case SOUTH -> new AxisAlignedBB(.125, .125, 0, .875, .875, .1875);
            case WEST -> new AxisAlignedBB(.8125, .125, .125, 1, .875, .875);
            case EAST -> new AxisAlignedBB(0, .125, .125, .1875, .875, .875);
        };
    }
    @Override public boolean canProvidePower(IBlockState state) { return true; }
    @Override public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return world.getTileEntity(pos) instanceof PlacardTileEntity tile && tile.isPowered() ? 15 : 0; }
    @Override public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return side == outward(state) ? getWeakPower(state, world, pos, side) : 0; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking() || player.getHeldItem(hand).getItem() == AllItems.WRENCH || !(world.getTileEntity(pos) instanceof PlacardTileEntity tile)) return false;
        ItemStack held = player.getHeldItem(hand), shown = tile.getItem();
        if (held.isEmpty()) return false;
        if (world.isRemote) return true;
        if (shown.isEmpty()) {
            if (!player.canPlayerEdit(pos, side, held)) return false;
            ItemStack copy = held.copy(); copy.setCount(1); tile.setItem(copy);
            if (!player.capabilities.isCreativeMode) held.shrink(1);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM, SoundCategory.BLOCKS, 1, 1);
        } else if (!tile.isPowered() && (ListFilterItem.isFilterItem(shown) ? ListFilterItem.matchesItem(shown, held)
                : ItemStack.areItemsEqual(shown, held) && ItemStack.areItemStackTagsEqual(shown, held))) {
            tile.pulse();
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_PLING, SoundCategory.BLOCKS, .5F, 1.5F);
        }
        return true;
    }
    @Override public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof PlacardTileEntity tile && !tile.getItem().isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, tile.getItem()); tile.setItem(ItemStack.EMPTY);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1, 1);
        }
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof PlacardTileEntity tile) {
            ItemStack item = tile.getItem(); if (!item.isEmpty()) InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), item);
            if (tile.isPowered()) world.notifyNeighborsOfStateChange(pos, this, false);
        }
        super.breakBlock(world, pos, state);
    }
}
