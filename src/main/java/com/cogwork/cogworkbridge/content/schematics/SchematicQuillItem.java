package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SchematicQuillItem extends Item {
    public SchematicQuillItem() { setMaxStackSize(1); setCreativeTab(ModCreativeTab.INSTANCE); }
    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
            EnumFacing face, float x, float y, float z, EnumHand hand) {
        return onItemUse(player, world, pos, hand, face, x, y, z);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing face, float x, float y, float z) {
        if (hand != EnumHand.MAIN_HAND) return EnumActionResult.PASS;
        if (world.isRemote) CogworkBridge.proxy.schematicSelect(player.isSneaking(), pos, face);
        return EnumActionResult.SUCCESS;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
        if (world.isRemote) CogworkBridge.proxy.schematicSelect(player.isSneaking(), null, null);
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
