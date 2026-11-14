package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StockLinkItem extends ItemBlock {
    public StockLinkItem(Block block) { super(block); }

    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
            EnumFacing face, float x, float y, float z, EnumHand hand) {
        java.util.UUID network = StockLinkNetwork.networkOf(world.getTileEntity(pos));
        if (player.isSneaking() || network == null) return EnumActionResult.PASS;
        if (!world.isRemote && !LogisticsNetworkSecurity.mayInteract(world, network, player)) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.stock_ticker.locked"), true);
            return EnumActionResult.SUCCESS;
        }
        if (!world.isRemote) {
            ItemStack stack = player.getHeldItem(hand);
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            tag.setUniqueId("LogisticsFrequency", network);
            stack.setTagCompound(tag);
            player.inventory.markDirty();
        }
        return EnumActionResult.SUCCESS;
    }

    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.hasTagCompound() && stack.getTagCompound().hasUniqueId("LogisticsFrequency")) {
            if (!world.isRemote) {
                stack.getTagCompound().removeTag("LogisticsFrequencyMost");
                stack.getTagCompound().removeTag("LogisticsFrequencyLeast");
                if (stack.getTagCompound().isEmpty()) stack.setTagCompound(null);
            }
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return super.onItemRightClick(world, player, hand);
    }

    @Override public boolean hasEffect(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasUniqueId("LogisticsFrequency");
    }
}
