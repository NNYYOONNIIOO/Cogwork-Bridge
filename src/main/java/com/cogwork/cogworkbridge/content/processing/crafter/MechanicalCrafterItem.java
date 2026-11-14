package com.cogwork.cogworkbridge.content.processing.crafter;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Keeps the placement face available after the crafter's block entity is created. */
public final class MechanicalCrafterItem extends ItemBlock {
    public MechanicalCrafterItem(Block block) { super(block); }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
                                EnumFacing side, float hitX, float hitY, float hitZ, IBlockState state) {
        if (!super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, state)) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalCrafterTileEntity crafter) {
            EnumFacing facing = world.getBlockState(pos).getValue(MechanicalCrafterBlock.FACING);
            crafter.setPointing(MechanicalCrafterDirection.relativeTo(facing, side.getOpposite()));
        }
        return true;
    }
}
