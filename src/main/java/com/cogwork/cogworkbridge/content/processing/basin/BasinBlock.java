package com.cogwork.cogworkbridge.content.processing.basin;

import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerBlock;
import com.cogwork.cogworkbridge.content.processing.press.MechanicalPressBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.List;

public final class BasinBlock extends BlockContainer {
    public static final IUnlistedProperty<EnumFacing> OUTPUT = new IUnlistedProperty<EnumFacing>() {
        @Override public String getName() { return "output"; }
        @Override public boolean isValid(EnumFacing value) { return value != null && value != EnumFacing.UP; }
        @Override public Class<EnumFacing> getType() { return EnumFacing.class; }
        @Override public String valueToString(EnumFacing value) { return value.getName(); }
    };
    private static final AxisAlignedBB BOTTOM = new AxisAlignedBB(0.125D, 0, 0.125D, 0.875D, 0.125D, 0.875D);
    private static final AxisAlignedBB FLOOR = new AxisAlignedBB(0, 0.125D, 0, 1, 0.3125D, 1);
    private static final AxisAlignedBB NORTH = new AxisAlignedBB(0, 0.125D, 0, 1, 1, 0.125D);
    private static final AxisAlignedBB SOUTH = new AxisAlignedBB(0, 0.125D, 0.875D, 1, 1, 1);
    private static final AxisAlignedBB WEST = new AxisAlignedBB(0, 0.125D, 0.125D, 0.125D, 1, 0.875D);
    private static final AxisAlignedBB EAST = new AxisAlignedBB(0.875D, 0.125D, 0.125D, 1, 1, 0.875D);
    private static final AxisAlignedBB CONTENTS = new AxisAlignedBB(0.125D, 0.125D, 0.125D, 0.875D, 1, 0.875D);

    public BasinBlock() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 0);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) { return new BasinTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.MODEL; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        Block above = world.getBlockState(pos.up()).getBlock();
        return super.canPlaceBlockAt(world, pos)
                && !(above instanceof MechanicalMixerBlock)
                && !(above instanceof MechanicalPressBlock);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{OUTPUT});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        EnumFacing output = tile instanceof BasinTileEntity basin ? basin.getOutputSide() : EnumFacing.DOWN;
        return ((IExtendedBlockState) state).withProperty(OUTPUT, output);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB mask,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, mask, boxes, BOTTOM);
        if (entity instanceof EntityItem) addCollisionBoxToList(pos, mask, boxes, FLOOR);
        double height = entity instanceof EntityItem ? 0.8125D : 1.0D;
        addCollisionBoxToList(pos, mask, boxes, NORTH.setMaxY(height));
        addCollisionBoxToList(pos, mask, boxes, SOUTH.setMaxY(height));
        addCollisionBoxToList(pos, mask, boxes, WEST.setMaxY(height));
        addCollisionBoxToList(pos, mask, boxes, EAST.setMaxY(height));
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (world.isRemote || !(entity instanceof EntityItem item) || item.isDead
                || !CONTENTS.offset(pos).intersects(item.getEntityBoundingBox())) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof BasinTileEntity basin) {
            ItemStack before = item.getItem();
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(basin.getInputItems(), before, false);
            if (remaining.isEmpty()) {
                item.setDead();
            } else if (remaining.getCount() != before.getCount()) {
                item.setItem(remaining);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof BasinTileEntity basin)) {
            return false;
        }
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) {
            if (basin.isItemsEmpty()) {
                return false;
            }
            if (!world.isRemote) {
                basin.giveItemsTo(player);
            }
            return true;
        }
        if (com.cogwork.cogworkbridge.content.fluids.PotionFluid.isPotionItem(held)
                || held.getItem() == net.minecraft.init.Items.GLASS_BOTTLE) {
            if (!com.cogwork.cogworkbridge.content.fluids.FluidContainerHelper.canInteractWithPotionBottle(held, basin.getFluidHandler())) return false;
            if (world.isRemote) return true;
            ItemStack result = com.cogwork.cogworkbridge.content.fluids.FluidContainerHelper.interactWithPotionBottle(held, basin.getFluidHandler());
            if (result == null) return false;
            if (!player.capabilities.isCreativeMode) {
                if (held.getCount() == 1) player.setHeldItem(hand, result);
                else {
                    held.shrink(1);
                    if (!result.isEmpty() && !player.inventory.addItemStackToInventory(result)) player.dropItem(result, false);
                }
            }
            return true;
        }
        if (FluidUtil.getFluidHandler(held) == null) {
            return false;
        }
        return world.isRemote || FluidUtil.interactWithFluidHandler(player, hand, basin.getFluidHandler());
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof BasinTileEntity basin) {
            basin.dropItems(world, pos);
        }
        super.breakBlock(world, pos, state);
    }
}
