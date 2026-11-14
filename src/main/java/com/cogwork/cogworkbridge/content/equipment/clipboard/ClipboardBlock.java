package com.cogwork.cogworkbridge.content.equipment.clipboard;

import com.cogwork.cogworkbridge.CogworkBridge;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
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
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

public final class ClipboardBlock extends BlockContainer {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final IUnlistedProperty<Boolean> WRITTEN = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "written"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };
    public ClipboardBlock() {
        super(Material.WOOD); setHardness(.8F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }
    @Override protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING}, new IUnlistedProperty<?>[]{WRITTEN});
    }
    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return ((IExtendedBlockState) state).withProperty(WRITTEN,
                tile instanceof ClipboardTileEntity && ((ClipboardTileEntity) tile).isWritten());
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y,
                                                       float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side);
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new ClipboardTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(FACING)) {
            case UP -> new AxisAlignedBB(.1875, 0, .0625, .8125, .125, .9375);
            case DOWN -> new AxisAlignedBB(.1875, .875, .0625, .8125, 1, .9375);
            case NORTH -> new AxisAlignedBB(.1875, .0625, .875, .8125, .9375, 1);
            case SOUTH -> new AxisAlignedBB(.1875, .0625, 0, .8125, .9375, .125);
            case WEST -> new AxisAlignedBB(.875, .0625, .1875, 1, .9375, .8125);
            case EAST -> new AxisAlignedBB(0, .0625, .1875, .125, .9375, .8125);
        };
    }
    @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof ClipboardTileEntity clipboard)
            clipboard.setContent(ClipboardData.fromItem(stack));
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                               EnumHand hand, EnumFacing side, float x, float y, float z) {
        if (player.isSneaking()) {
            if (!world.isRemote && player.canPlayerEdit(pos, side, player.getHeldItem(hand))) {
                ItemStack drop = savedStack(world.getTileEntity(pos));
                if (world.setBlockToAir(pos)) ItemHandlerHelper.giveItemToPlayer(player, drop);
            }
        } else if (world.isRemote) CogworkBridge.proxy.openClipboardScreen(pos, -1);
        return true;
    }
    private ItemStack savedStack(@Nullable TileEntity tile) {
        ItemStack result = new ItemStack(this);
        if (tile instanceof ClipboardTileEntity clipboard) ClipboardData.toItem(result, clipboard.content());
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
}
