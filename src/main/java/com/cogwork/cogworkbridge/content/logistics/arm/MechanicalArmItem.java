package com.cogwork.cogworkbridge.content.logistics.arm;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/** Right click an inventory to cycle it between input, output and unselected. */
public final class MechanicalArmItem extends ItemBlock {
    private static final String POINTS = "CogworkBridgeArmPoints";
    public static NBTTagList selectedPoints(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getTagList(POINTS, 10).copy() : new NBTTagList();
    }
    public MechanicalArmItem(Block block) { super(block); }
    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
            float x, float y, float z, EnumHand hand) {
        if (ArmInteractionHandlers.at(world, pos, side) == null) return EnumActionResult.PASS;
        if (!world.isRemote) {
            ItemStack item = player.getHeldItem(hand);
            NBTTagCompound root = item.hasTagCompound() ? item.getTagCompound() : new NBTTagCompound();
            NBTTagList points = root.getTagList(POINTS, 10);
            int index = -1;
            for (int i = 0; i < points.tagCount(); i++) {
                NBTTagCompound point = points.getCompoundTagAt(i);
                if (point.getLong("Pos") == pos.toLong() && point.getInteger("Dimension") == world.provider.getDimension()) { index = i; break; }
            }
            int mode = ArmInteractionHandlers.depositOnly(world, pos) ? 1 : 0;
            if (index >= 0) { mode = points.getCompoundTagAt(index).getInteger("Mode") + 1; points.removeTag(index); }
            if (mode < 2) {
                NBTTagCompound point = new NBTTagCompound(); point.setLong("Pos", pos.toLong());
                point.setInteger("Dimension", world.provider.getDimension()); point.setInteger("Side", side.getIndex()); point.setInteger("Mode", mode);
                points.appendTag(point);
            }
            root.setTag(POINTS, points); item.setTagCompound(root);
            player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.arm.point." + (mode == 0 ? "input" : mode == 1 ? "output" : "removed")), true);
            player.inventory.markDirty();
        }
        return EnumActionResult.SUCCESS;
    }
    @Override public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
            EnumFacing side, float x, float y, float z, IBlockState state) {
        NBTTagList selected = stack.hasTagCompound() ? stack.getTagCompound().getTagList(POINTS, 10).copy() : new NBTTagList();
        if (!super.placeBlockAt(stack, player, world, pos, side, x, y, z, state)) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof MechanicalArmTileEntity arm) {
            arm.setPoints(selected, world.provider.getDimension());
            if (stack.hasTagCompound()) stack.getTagCompound().removeTag(POINTS);
        }
        return true;
    }
}
