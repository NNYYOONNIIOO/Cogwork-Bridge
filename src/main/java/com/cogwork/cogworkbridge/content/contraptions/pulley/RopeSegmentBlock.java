package com.cogwork.cogworkbridge.content.contraptions.pulley;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Stationary rope and magnet left by a stopped pulley. */
public final class RopeSegmentBlock extends Block {
    private static final ThreadLocal<Boolean> INTERNAL_CHANGE = ThreadLocal.withInitial(() -> false);
    private final boolean magnet;
    public RopeSegmentBlock(boolean magnet) { super(magnet ? Material.IRON : Material.CLOTH); this.magnet = magnet; setHardness(.5F); }
    public static boolean isSegment(IBlockState state) { return state.getBlock() instanceof RopeSegmentBlock; }
    public static void setInternal(boolean value) { INTERNAL_CHANGE.set(value); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return magnet ? new AxisAlignedBB(.25D, 0, .25D, .75D, .5D, .75D) : new AxisAlignedBB(.375D, 0, .375D, .625D, 1, .625D);
    }
    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) { return NULL_AABB; }
    @Override public boolean isLadder(IBlockState state, IBlockAccess world, BlockPos pos, EntityLivingBase entity) { return true; }
    @Override public Item getItemDropped(IBlockState state, java.util.Random rand, int fortune) { return net.minecraft.init.Items.AIR; }
    @Override public ItemStack getItem(World world, BlockPos pos, IBlockState state) { return new ItemStack(AllBlocks.ROPE_PULLEY); }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && !INTERNAL_CHANGE.get()) {
            BlockPos cursor = pos.up();
            while (cursor.getY() < world.getHeight() && world.isBlockLoaded(cursor) && isSegment(world.getBlockState(cursor))) cursor = cursor.up();
            if (world.getTileEntity(cursor) instanceof RopePulleyTileEntity pulley) pulley.onRopeBroken(pos);
        }
        super.breakBlock(world, pos, state);
    }
}
