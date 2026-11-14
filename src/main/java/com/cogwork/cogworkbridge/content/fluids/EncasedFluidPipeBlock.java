package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.Random;

public final class EncasedFluidPipeBlock extends FluidPipeBlock {
    @Override public boolean isOpaqueCube(IBlockState state) { return true; }
    @Override public boolean isFullCube(IBlockState state) { return true; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }
    @Override public void addCollisionBoxToList(IBlockState state, net.minecraft.world.World world,
            BlockPos pos, AxisAlignedBB query, java.util.List<AxisAlignedBB> boxes,
            net.minecraft.entity.Entity entity, boolean actualState) {
        AxisAlignedBB box = FULL_BLOCK_AABB.offset(pos);
        if (query.intersects(box)) boxes.add(box);
    }
    @Override public Item getItemDropped(IBlockState state, Random random, int fortune) {
        return Item.getItemFromBlock(AllBlocks.FLUID_PIPE);
    }
    @Override public net.minecraft.item.ItemStack getPickBlock(IBlockState state,
            net.minecraft.util.math.RayTraceResult target, net.minecraft.world.World world,
            BlockPos pos, net.minecraft.entity.player.EntityPlayer player) {
        return new net.minecraft.item.ItemStack(AllBlocks.FLUID_PIPE);
    }
}
