package com.cogwork.cogworkbridge.content.processing.millstone;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public final class MillstoneBlock extends KineticBlock {
    private static final AxisAlignedBB BASE = new AxisAlignedBB(0, 0, 0, 1, 6.0D / 16.0D, 1);
    private static final AxisAlignedBB TOP = new AxisAlignedBB(2.0D / 16.0D, 6.0D / 16.0D, 2.0D / 16.0D,
            14.0D / 16.0D, 1, 14.0D / 16.0D);

    public MillstoneBlock() {
        super(Material.ROCK, "cogworkbridge.millstone");
        setHardness(2.0F);
        setResistance(6.0F);
        setHarvestLevel("pickaxe", 0);
    }

    @Override public boolean hasAxis() { return false; }
    @Override public EnumFacing.Axis getAxis(IBlockState state) { return EnumFacing.Axis.Y; }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side == EnumFacing.DOWN;
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new MillstoneTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB mask,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        addCollisionBoxToList(pos, mask, boxes, BASE);
        addCollisionBoxToList(pos, mask, boxes, TOP);
    }

    @Override
    public void onLanded(World world, Entity entity) {
        super.onLanded(world, entity);
        if (world.isRemote || !(entity instanceof EntityItem)) return;
        BlockPos landed = entity.getPosition();
        if (world.getBlockState(landed).getBlock() != this) landed = landed.down();
        acceptItem(world, landed, entity);
    }

    private static void acceptItem(World world, BlockPos pos, Entity entity) {
        if (world.isRemote || !(entity instanceof EntityItem item) || item.isDead) return;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof MillstoneTileEntity millstone)) return;
        ItemStack original = item.getItem();
        ItemStack remainder = millstone.insertInput(original);
        if (remainder.isEmpty()) item.setDead();
        else if (remainder.getCount() != original.getCount()) item.setItem(remainder);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (!player.getHeldItem(hand).isEmpty()) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof MillstoneTileEntity millstone)) return false;
        if (!world.isRemote) millstone.giveItemsTo(player);
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof MillstoneTileEntity millstone) millstone.dropItems(world, pos);
        super.breakBlock(world, pos, state);
    }
}
