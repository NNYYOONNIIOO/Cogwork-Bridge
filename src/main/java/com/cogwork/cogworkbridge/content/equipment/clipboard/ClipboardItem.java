package com.cogwork.cogworkbridge.content.equipment.clipboard;

import com.cogwork.cogworkbridge.CogworkBridge;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ClipboardItem extends ItemBlock {
    public ClipboardItem(Block block) {
        super(block);
        setMaxStackSize(1);
        addPropertyOverride(new ResourceLocation("cogworkbridge", "written"),
                (stack, world, entity) -> ClipboardData.isWritten(ClipboardData.fromItem(stack)) ? 1F : 0F);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                                EnumFacing side, float x, float y, float z) {
        if (player.isSneaking()) return super.onItemUse(player, world, pos, hand, side, x, y, z);
        return onItemRightClick(world, player, hand).getType();
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (hand == EnumHand.OFF_HAND) return new ActionResult<>(EnumActionResult.PASS, stack);
        if (world.isRemote) CogworkBridge.proxy.openClipboardScreen(null, player.inventory.currentItem);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
}
