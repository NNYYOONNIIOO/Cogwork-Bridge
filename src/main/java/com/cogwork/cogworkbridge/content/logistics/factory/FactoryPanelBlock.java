package com.cogwork.cogworkbridge.content.logistics.factory;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.logistics.packager.StockLinkItem;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class FactoryPanelBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public FactoryPanelBlock() { super(Material.IRON); setHardness(2); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) { return getDefaultState().withProperty(FACING, side); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new FactoryPanelTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case UP -> new AxisAlignedBB(0, 0, 0, 1, .125, 1);
            case DOWN -> new AxisAlignedBB(0, .875, 0, 1, 1, 1);
            case NORTH -> new AxisAlignedBB(0, 0, .875, 1, 1, 1);
            case SOUTH -> new AxisAlignedBB(0, 0, 0, 1, 1, .125);
            case WEST -> new AxisAlignedBB(.875, 0, 0, 1, 1, 1);
            case EAST -> new AxisAlignedBB(0, 0, 0, .125, 1, 1);
        };
    }
    public static int slot(EnumFacing facing, float x, float y, float z) {
        return switch (facing) {
            case UP -> (x >= .5F ? 1 : 0) + (z >= .5F ? 2 : 0);
            case DOWN -> (x >= .5F ? 1 : 0) + (z < .5F ? 2 : 0);
            case NORTH -> (x >= .5F ? 1 : 0) + (y >= .5F ? 2 : 0);
            case SOUTH -> (x >= .5F ? 1 : 0) + (y < .5F ? 2 : 0);
            case EAST -> (y < .5F ? 1 : 0) + (z >= .5F ? 2 : 0);
            case WEST -> (y >= .5F ? 1 : 0) + (z >= .5F ? 2 : 0);
        };
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (!(world.getTileEntity(pos) instanceof FactoryPanelTileEntity panel)) return false;
        ItemStack held = player.getHeldItem(hand); int slot = slot(state.getValue(FACING), x, y, z);
        java.util.UUID selectedNetwork = panel.network(slot);
        if (!world.isRemote && selectedNetwork != null && !com.cogwork.cogworkbridge.content.logistics.packager.LogisticsNetworkSecurity.mayInteract(world, selectedNetwork, player)) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.stock_ticker.locked"), true); return true;
        }
        if (held.getItem() == AllItems.WRENCH && panel.active(slot) && !player.isSneaking()) {
            if (!world.isRemote) panel.cycleOutgoingPaths(slot);
            return true;
        }
        if (held.getItem() instanceof FactoryPanelItem) {
            if (held.hasTagCompound() && held.getTagCompound().hasUniqueId("LogisticsFrequency")) {
                if (!world.isRemote && !com.cogwork.cogworkbridge.content.logistics.packager.LogisticsNetworkSecurity.mayInteract(world, held.getTagCompound().getUniqueId("LogisticsFrequency"), player)) return true;
                if (!world.isRemote && panel.add(slot, held.getTagCompound().getUniqueId("LogisticsFrequency")) && !player.capabilities.isCreativeMode) held.shrink(1);
            }
            return true;
        }
        if (!panel.active(slot) || player.isSneaking() || held.getItem() instanceof StockLinkItem) return false;
        if (!held.isEmpty()) { if (!world.isRemote) panel.setFilter(slot, held); return true; }
        if (world.isRemote) CogworkBridge.proxy.openFactoryPanelScreen(pos, slot);
        return true;
    }
    @Override @javax.annotation.Nullable public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return NULL_AABB; }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
            java.util.List<AxisAlignedBB> boxes, @javax.annotation.Nullable net.minecraft.entity.Entity entity, boolean actual) {
        if (entity == null) addCollisionBoxToList(pos, query, boxes, getBoundingBox(state, world, pos));
    }
}
