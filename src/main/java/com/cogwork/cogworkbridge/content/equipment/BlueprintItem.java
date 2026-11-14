package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BlueprintItem extends Item {
    public BlueprintItem() { setCreativeTab(ModCreativeTab.INSTANCE); }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing face, float x, float y, float z) {
        ItemStack stack = player.getHeldItem(hand);
        if (!player.canPlayerEdit(pos.offset(face), face, stack) || !world.isSideSolid(pos, face)) return EnumActionResult.FAIL;
        BlueprintEntity blueprint = null;
        for (int size = 3; size >= 1; size--) {
            BlueprintEntity candidate = new BlueprintEntity(world, pos, face, player.getHorizontalFacing(), size);
            if (candidate.canPlace()) { blueprint = candidate; break; }
        }
        if (blueprint == null) return EnumActionResult.FAIL;
        if (!world.isRemote) {
            world.spawnEntity(blueprint);
            if (!player.capabilities.isCreativeMode) stack.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }
}
