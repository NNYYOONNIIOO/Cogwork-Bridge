package com.cogwork.cogworkbridge.content.logistics.packager;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Holding a requester at a stock ticker programs it through the stock list. */
public final class RedstoneRequesterItem extends StockLinkItem {
    public RedstoneRequesterItem(Block block) { super(block); }

    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        boolean tuned = stack.hasTagCompound() && stack.getTagCompound().hasUniqueId("LogisticsFrequency");
        ActionResult<ItemStack> result = super.onItemRightClick(world, player, hand);
        if (tuned && !world.isRemote && result.getType() == EnumActionResult.SUCCESS && stack.hasTagCompound()) {
            stack.getTagCompound().removeTag("AutoRequest");
            if (stack.getTagCompound().isEmpty()) stack.setTagCompound(null);
        }
        return result;
    }

    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
            EnumFacing face, float x, float y, float z, EnumHand hand) {
        if (!player.isSneaking() && hand == EnumHand.MAIN_HAND && world.getTileEntity(pos) instanceof StockTickerTileEntity) {
            if (!world.isRemote && world.getTileEntity(pos) instanceof StockTickerTileEntity ticker
                    && !LogisticsNetworkSecurity.mayInteract(world, ticker.getNetwork(), player)) {
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation("cogworkbridge.stock_ticker.locked"), true); return EnumActionResult.SUCCESS;
            }
            if (!world.isRemote) player.openGui(CogworkBridge.instance, ListFilterGuiHandler.STOCK_TICKER_GUI,
                    world, pos.getX(), pos.getY(), pos.getZ());
            return EnumActionResult.SUCCESS;
        }
        return super.onItemUseFirst(player, world, pos, face, x, y, z, hand);
    }
}
