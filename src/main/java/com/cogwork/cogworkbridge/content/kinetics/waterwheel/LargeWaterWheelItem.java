package com.cogwork.cogworkbridge.content.kinetics.waterwheel;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class LargeWaterWheelItem extends ItemBlock {
    public LargeWaterWheelItem(Block block) { super(block); }
    @Override public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float hitX, float hitY, float hitZ, IBlockState newState) {
        EnumFacing.Axis axis = newState.getValue(KineticBlock.AXIS);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            BlockPos target = pos.add(LargeWaterWheelBlock.offset(axis, a, b));
            if (!world.isBlockLoaded(target) || !world.getBlockState(target).getBlock().isReplaceable(world, target)
                    || !player.canPlayerEdit(target, side, stack)) return false;
        }
        return super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState);
    }
}
