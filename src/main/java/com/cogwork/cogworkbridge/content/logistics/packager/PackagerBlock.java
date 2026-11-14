package com.cogwork.cogworkbridge.content.logistics.packager;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Output points away from the inventory attached behind the packager. */
public class PackagerBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Integer> STATUS = new IUnlistedProperty<Integer>() {
        @Override public String getName() { return "packager_status"; }
        @Override public boolean isValid(Integer value) { return value != null && value >= 0 && value <= 2; }
        @Override public Class<Integer> getType() { return Integer.class; }
        @Override public String valueToString(Integer value) { return value.toString(); }
    };
    public PackagerBlock() { super(Material.IRON); setHardness(2F); setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH)); }
    @Override protected BlockStateContainer createBlockState() { return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{STATUS}); }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        int status = tile instanceof PackagerTileEntity packager ? packager.linked() ? 2 : packager.isPowered() ? 1 : 0 : 0;
        if (tile != null && tile.getTileData().getCompoundTag("TutorialState").getString("linked").equals("true")) status = 2;
        return ((IExtendedBlockState) state).withProperty(STATUS, status);
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        for (EnumFacing facing : EnumFacing.values()) {
            var tile = world.getTileEntity(pos.offset(facing));
            if (tile != null && !(tile instanceof PackagerTileEntity)
                    && tile.hasCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite()))
                return getDefaultState().withProperty(FACING, facing.getOpposite());
        }
        EnumFacing facing = KineticBlock.getPlacementFacing(placer);
        return getDefaultState().withProperty(FACING, placer.isSneaking() ? facing : facing.getOpposite());
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new PackagerTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() { return net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == AllItems.WRENCH || held.getItem() instanceof StockLinkItem
                || held.getItem() instanceof ItemBlock item && item.getBlock() == com.cogwork.cogworkbridge.registry.AllBlocks.PACKAGE_FROGPORT
                || !(world.getTileEntity(pos) instanceof PackagerTileEntity tile)) return false;
        if (world.isRemote) return true;
        if (!tile.canInteract()) return true;
        if (!tile.getBox().isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, tile.takeBox());
        else if (com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem.isPackage(held)) {
            ItemStack remaining = tile.acceptPackage(held, false);
            player.setHeldItem(hand, remaining);
        }
        return true;
    }
    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof PackagerTileEntity tile) tile.updateRedstone();
    }
    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof PackagerTileEntity tile) tile.updateRedstone();
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof PackagerTileEntity tile) {
            ItemStack box = tile.takeBox();
            if (!box.isEmpty()) InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), box);
            tile.dropQueued();
        }
        super.breakBlock(world, pos, state);
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof PackagerTileEntity tile && (!tile.getBox().isEmpty() || tile.getAnimation() > 0) ? 15 : 0;
    }
}
