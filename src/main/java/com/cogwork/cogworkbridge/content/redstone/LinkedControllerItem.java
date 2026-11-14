package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.fluids.ListFilterGuiHandler;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class LinkedControllerItem extends Item {
    public LinkedControllerItem() { setMaxStackSize(1); setCreativeTab(ModCreativeTab.INSTANCE); }
    public static void setFrequency(ItemStack stack, int slot, ItemStack frequency) {
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound freq = root.hasKey("Frequencies", 10) ? root.getCompoundTag("Frequencies") : new NBTTagCompound();
        freq.setTag("" + slot, frequency.writeToNBT(new NBTTagCompound())); root.setTag("Frequencies", freq); stack.setTagCompound(root);
    }
    public static ItemStack frequency(ItemStack stack, int slot) {
        NBTTagCompound root = stack.getTagCompound(); if (root == null || !root.hasKey("Frequencies", 10)) return ItemStack.EMPTY;
        NBTTagCompound freq = root.getCompoundTag("Frequencies"); return freq.hasKey("" + slot, 10) ? new ItemStack(freq.getCompoundTag("" + slot)) : ItemStack.EMPTY;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (hand == EnumHand.MAIN_HAND && !world.isRemote)
                player.openGui(CogworkBridge.instance, ListFilterGuiHandler.LINKED_CONTROLLER_GUI, world, player.inventory.currentItem, 0, 0);
            return new ActionResult<>(hand == EnumHand.MAIN_HAND ? EnumActionResult.SUCCESS : EnumActionResult.PASS, stack);
        }
        if (world.isRemote) CogworkBridge.proxy.toggleLinkedController(hand);
        player.getCooldownTracker().setCooldown(this, 2);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                                 net.minecraft.util.EnumFacing side, float x, float y, float z) {
        return onItemRightClick(world, player, hand).getType();
    }
    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                                      net.minecraft.util.EnumFacing side, float x, float y, float z,
                                                      EnumHand hand) {
        var state=world.getBlockState(pos);
        if (player.isSneaking() && state.getBlock() instanceof LecternControllerBlock && world.getTileEntity(pos) instanceof LecternControllerTileEntity tile) {
            if (player.isSpectator() || !player.canPlayerEdit(pos,side,player.getHeldItem(hand)) || !world.isBlockModifiable(player,pos)) return EnumActionResult.FAIL;
            if (!world.isRemote) tile.swap(player,hand); return EnumActionResult.SUCCESS;
        }
        if (!player.isSneaking() && LecternControllerBlock.isEmptyLectern(state)) {
            if (!world.isRemote && player.canPlayerEdit(pos,side,player.getHeldItem(hand)) && world.isBlockModifiable(player,pos)) {
                ItemStack controller=player.getHeldItem(hand).copy();controller.setCount(1);
                int meta=state.getBlock().getMetaFromState(state);net.minecraft.util.EnumFacing facing=LecternControllerBlock.facing(state);
                if(world.setBlockState(pos,com.cogwork.cogworkbridge.registry.AllBlocks.LECTERN_CONTROLLER.getDefaultState().withProperty(LecternControllerBlock.FACING,facing),3) && world.getTileEntity(pos) instanceof LecternControllerTileEntity tile) {
                    tile.setup(controller,meta);if(!player.capabilities.isCreativeMode)player.getHeldItem(hand).shrink(1);
                }
            }
            return EnumActionResult.SUCCESS;
        }
        if (!player.isSneaking() && state.getBlock() instanceof LecternControllerBlock) return EnumActionResult.PASS;
        if (!player.isSneaking() && world.getBlockState(pos).getBlock() instanceof RedstoneLinkBlock) {
            if (world.isRemote) CogworkBridge.proxy.bindLinkedController(pos, hand);
            player.getCooldownTracker().setCooldown(this, 2);
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }
}
