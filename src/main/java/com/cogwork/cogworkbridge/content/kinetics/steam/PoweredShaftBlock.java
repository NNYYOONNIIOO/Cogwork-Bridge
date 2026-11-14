package com.cogwork.cogworkbridge.content.kinetics.steam;

import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.Random;

public final class PoweredShaftBlock extends ShaftBlock {
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new PoweredShaftTileEntity(); }
    @Override public Item getItemDropped(IBlockState state, Random random, int fortune) { return Item.getItemFromBlock(AllBlocks.SHAFT); }
    @Override public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult hit, World world, BlockPos pos, net.minecraft.entity.player.EntityPlayer player) { return new ItemStack(AllBlocks.SHAFT); }
    @Override public net.minecraft.util.math.AxisAlignedBB getBoundingBox(IBlockState state, net.minecraft.world.IBlockAccess world, BlockPos pos) {
        return switch (state.getValue(AXIS)) {
            case X -> new net.minecraft.util.math.AxisAlignedBB(0,.25,.25,1,.75,.75);
            case Y -> new net.minecraft.util.math.AxisAlignedBB(.25,0,.25,.75,1,.75);
            case Z -> new net.minecraft.util.math.AxisAlignedBB(.25,.25,0,.75,.75,1);
        };
    }
}
