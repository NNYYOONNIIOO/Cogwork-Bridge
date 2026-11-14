package com.cogwork.cogworkbridge.content.display;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/** Select a target first, then place the link against a source block. */
public final class DisplayLinkItem extends ItemBlock {
    private static final String TARGET = "CogworkBridgeDisplayTarget";
    public DisplayLinkItem(Block block) { super(block); }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                                EnumFacing side, float x, float y, float z) {
        ItemStack stack = player.getHeldItem(hand);
        NBTTagCompound tag = stack.getTagCompound();
        boolean selected = tag != null && tag.hasKey(TARGET, 10);
        if (player.isSneaking() && selected) {
            if (!world.isRemote) {
                tag.removeTag(TARGET);
                if (tag.isEmpty()) stack.setTagCompound(null);
                player.sendStatusMessage(new TextComponentTranslation("gui.cogworkbridge.display.cleared"), true);
            }
            return EnumActionResult.SUCCESS;
        }
        if (!selected) {
            TileEntity tile = world.getTileEntity(pos);
            if (!(tile instanceof DisplayTarget) && !(tile instanceof TileEntitySign)
                    && !LecternDisplayTarget.supports(tile)) return EnumActionResult.PASS;
            if (!world.isRemote) {
                if (tag == null) tag = new NBTTagCompound();
                NBTTagCompound target = new NBTTagCompound();
                target.setLong("Pos", pos.toLong());
                target.setInteger("Dimension", world.provider.getDimension());
                tag.setTag(TARGET, target);
                stack.setTagCompound(tag);
                player.sendStatusMessage(new TextComponentTranslation("gui.cogworkbridge.display.selected"), true);
            }
            return EnumActionResult.SUCCESS;
        }
        NBTTagCompound targetData = tag.getCompoundTag(TARGET);
        BlockPos target = BlockPos.fromLong(targetData.getLong("Pos"));
        BlockPos placed = world.getBlockState(pos).getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        if (targetData.getInteger("Dimension") != world.provider.getDimension() || target.distanceSq(placed) > 64D * 64D)
            return EnumActionResult.FAIL;
        EnumActionResult result = super.onItemUse(player, world, pos, hand, side, x, y, z);
        if (result == EnumActionResult.SUCCESS && !world.isRemote) {
            TileEntity tile = world.getTileEntity(placed);
            if (tile instanceof DisplayLinkTileEntity link) link.setTarget(target);
            if (!stack.isEmpty() && stack.hasTagCompound()) {
                stack.getTagCompound().removeTag(TARGET);
                if (stack.getTagCompound().isEmpty()) stack.setTagCompound(null);
            }
        }
        return result;
    }
}
