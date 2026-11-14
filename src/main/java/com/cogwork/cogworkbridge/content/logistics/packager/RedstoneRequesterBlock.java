package com.cogwork.cogworkbridge.content.logistics.packager;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import javax.annotation.Nullable;

public final class RedstoneRequesterBlock extends BlockContainer {
    public static final PropertyEnum<EnumFacing.Axis> AXIS = PropertyEnum.create("axis", EnumFacing.Axis.class,
            axis -> axis != EnumFacing.Axis.Y);
    public RedstoneRequesterBlock() {
        super(Material.IRON); setHardness(2F);
        setDefaultState(blockState.getBaseState().withProperty(AXIS, EnumFacing.Axis.Z));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, AXIS); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(AXIS) == EnumFacing.Axis.X ? 1 : 0; }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(AXIS, (meta & 1) == 1 ? EnumFacing.Axis.X : EnumFacing.Axis.Z); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        EnumFacing facing = state.getValue(AXIS) == EnumFacing.Axis.X ? EnumFacing.EAST : EnumFacing.SOUTH;
        return state.withProperty(AXIS, rotation.rotate(facing).getAxis());
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(AXIS, placer.getHorizontalFacing().getAxis());
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof RedstoneRequesterTileEntity requester) {
            if (stack.hasTagCompound() && stack.getTagCompound().hasUniqueId("LogisticsFrequency")
                    && placer instanceof EntityPlayer player
                    && !LogisticsNetworkSecurity.mayInteract(world, stack.getTagCompound().getUniqueId("LogisticsFrequency"), player)) return;
            requester.readItem(stack);
        }
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new RedstoneRequesterTileEntity(); }
    private ItemStack savedStack(@Nullable TileEntity tile) {
        ItemStack result = new ItemStack(this);
        if (tile instanceof RedstoneRequesterTileEntity requester) requester.writeItem(result);
        return result;
    }
    @Override public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(savedStack(world.getTileEntity(pos)));
    }
    @Override public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state,
                                       @Nullable TileEntity tile, ItemStack tool) {
        if (!world.isRemote) spawnAsEntity(world, pos, savedStack(tile));
    }
    @Override public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return savedStack(world.getTileEntity(pos));
    }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.getHeldItem(hand).getItem() == AllItems.WRENCH || player.getHeldItem(hand).getItem() instanceof StockLinkItem) return false;
        if (!(world.getTileEntity(pos) instanceof RedstoneRequesterTileEntity requester)) return false;
        if (player.isSneaking()) return false;
        if (!world.isRemote && !LogisticsNetworkSecurity.mayInteract(world, requester.getNetwork(), player)) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.stock_ticker.locked"), true); return true;
        }
        if (!world.isRemote) player.openGui(CogworkBridge.instance, ListFilterGuiHandler.REDSTONE_REQUESTER_GUI,
                world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof RedstoneRequesterTileEntity requester) requester.updatePower();
    }
    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof RedstoneRequesterTileEntity requester) requester.updatePower();
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof RedstoneRequesterTileEntity requester && requester.succeeded() ? 15 : 0;
    }
}
