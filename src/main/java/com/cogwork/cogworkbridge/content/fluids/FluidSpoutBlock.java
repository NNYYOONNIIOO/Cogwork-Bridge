package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public final class FluidSpoutBlock extends BlockContainer {
    private static final AxisAlignedBB BODY = new AxisAlignedBB(1.0D / 16, 2.0D / 16, 1.0D / 16,
            15.0D / 16, 14.0D / 16, 15.0D / 16);
    private static final AxisAlignedBB CENTER = new AxisAlignedBB(2.0D / 16, 0, 2.0D / 16,
            14.0D / 16, 1, 14.0D / 16);
    public FluidSpoutBlock() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setLightOpacity(0);
    }

    @Override public TileEntity createNewTileEntity(World world, int meta) { return new FluidSpoutTileEntity(); }
    @Override public net.minecraft.util.EnumBlockRenderType getRenderType(IBlockState state) {
        return net.minecraft.util.EnumBlockRenderType.MODEL;
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return new AxisAlignedBB(1.0D / 16, 0, 1.0D / 16, 15.0D / 16, 1, 15.0D / 16);
    }
    @Override public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                                 AxisAlignedBB mask, List<AxisAlignedBB> boxes,
                                                 @Nullable Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, mask, boxes, BODY);
        addCollisionBoxToList(pos, mask, boxes, CENTER);
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof FluidSpoutTileEntity spout) || spout.getFluid() == null) return 0;
        return Math.min(15, 1 + (int) (14.0D * spout.getFluid().amount / spout.getCapacity()));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof FluidSpoutTileEntity spout)) return false;
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) return false;
        if (world.isRemote) return spout.canInteractWithContainer(held);
        ItemStack result = spout.interactWithContainer(held);
        if (result == null) return false;
        if (player.capabilities.isCreativeMode) return true;
        if (held.getCount() == 1) player.setHeldItem(hand, result);
        else {
            held.shrink(1);
            if (!player.inventory.addItemStackToInventory(result)) player.dropItem(result, false);
        }
        return true;
    }

}
