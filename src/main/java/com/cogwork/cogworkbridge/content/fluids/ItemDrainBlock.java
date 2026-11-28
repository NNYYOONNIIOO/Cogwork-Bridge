package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public final class ItemDrainBlock extends BlockContainer {
    private static final AxisAlignedBB SHAPE =
            new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.8125D, 1.0D);

    public ItemDrainBlock() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setLightOpacity(0);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new ItemDrainTileEntity();
    }

    @Override
    public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public void onLanded(World world, Entity entity) {
        super.onLanded(world, entity);
        if (world.isRemote || !(entity instanceof net.minecraft.entity.item.EntityItem item)) return;
        BlockPos landed = entity.getPosition();
        if (world.getBlockState(landed).getBlock() != this) landed = landed.down();
        if (world.getTileEntity(landed) instanceof ItemDrainTileEntity drain) drain.tryInsertItemEntity(item);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof ItemDrainTileEntity drain)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (FluidContainerHelper.contents(held) != null) {
            if (!world.isRemote) drain.tryDrainHeldItem(player, hand);
            return true;
        }
        if (!held.isEmpty() && held.getItem() instanceof ItemBlock) return false;
        if (drain.getHeldItemStack().isEmpty()) return false;
        if (!world.isRemote) drain.takeHeldItem(player);
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof ItemDrainTileEntity drain) drain.dropHeldItem();
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) { return true; }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof ItemDrainTileEntity drain ? drain.comparatorLevel() : 0;
    }
}
