package com.cogwork.cogworkbridge.content.logistics.factory;

import com.cogwork.cogworkbridge.content.logistics.packager.StockLinkItem;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class FactoryPanelItem extends StockLinkItem {
    public FactoryPanelItem(Block block) { super(block); }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing face, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (!held.hasTagCompound() || !held.getTagCompound().hasUniqueId("LogisticsFrequency")) return EnumActionResult.FAIL;
        var frequency = held.getTagCompound().getUniqueId("LogisticsFrequency");
        if (!world.isRemote && !com.cogwork.cogworkbridge.content.logistics.packager.LogisticsNetworkSecurity.mayInteract(world, frequency, player)) return EnumActionResult.FAIL;
        BlockPos placedPos = world.getBlockState(pos).getBlock().isReplaceable(world, pos) ? pos : pos.offset(face);
        EnumActionResult result = super.onItemUse(player, world, pos, hand, face, x, y, z);
        if (result == EnumActionResult.SUCCESS && !world.isRemote
                && world.getBlockState(placedPos).getBlock() == block
                && world.getTileEntity(placedPos) instanceof FactoryPanelTileEntity panel) {
            EnumFacing facing = world.getBlockState(placedPos).getValue(FactoryPanelBlock.FACING);
            panel.add(FactoryPanelBlock.slot(facing, x, y, z), frequency);
        }
        return result;
    }
}
