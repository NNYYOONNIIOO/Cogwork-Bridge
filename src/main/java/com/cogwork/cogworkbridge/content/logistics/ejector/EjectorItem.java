package com.cogwork.cogworkbridge.content.logistics.ejector;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Sneak-use an intended landing block, then place the ejector on the same horizontal line. */
public final class EjectorItem extends ItemBlock {
    private static final String TARGET = "CogworkBridgeEjectorTarget";
    public EjectorItem(Block block) { super(block); }
    @javax.annotation.Nullable public static BlockPos selectedTarget(ItemStack stack, int dimension) {
        if (!stack.hasTagCompound() || !stack.getTagCompound().hasKey(TARGET, 10)) return null;
        NBTTagCompound target = stack.getTagCompound().getCompoundTag(TARGET);
        return target.getInteger("Dimension") == dimension
                ? new BlockPos(target.getInteger("X"), target.getInteger("Y"), target.getInteger("Z")) : null;
    }
    @javax.annotation.Nullable public static EnumFacing targetFacing(ItemStack stack, World world, BlockPos pos) {
        BlockPos target = selectedTarget(stack, world.provider.getDimension());
        if (target == null) return null;
        BlockPos delta = target.subtract(pos);
        int distance = Math.abs(delta.getX()) + Math.abs(delta.getZ());
        if ((delta.getX() != 0 && delta.getZ() != 0) || distance < 1 || distance > 32
                || Math.abs(delta.getY()) > 32) return null;
        return delta.getX() > 0 ? EnumFacing.EAST : delta.getX() < 0 ? EnumFacing.WEST
                : delta.getZ() > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }
    @Override public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
            EnumFacing side, float x, float y, float z, EnumHand hand) {
        if (!player.isSneaking()) return EnumActionResult.PASS;
        if (!world.isRemote) {
            ItemStack stack = player.getHeldItem(hand);
            NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
            NBTTagCompound target = new NBTTagCompound();
            target.setInteger("X", pos.getX()); target.setInteger("Y", pos.getY()); target.setInteger("Z", pos.getZ());
            target.setInteger("Dimension", world.provider.getDimension());
            root.setTag(TARGET, target); stack.setTagCompound(root);
        }
        return EnumActionResult.SUCCESS;
    }
    @Override public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
            EnumFacing side, float x, float y, float z, IBlockState state) {
        NBTTagCompound selected = stack.hasTagCompound() && stack.getTagCompound().hasKey(TARGET, 10)
                ? stack.getTagCompound().getCompoundTag(TARGET).copy() : null;
        BlockPos delta = null;
        if (selected != null && selected.getInteger("Dimension") == world.provider.getDimension()) {
            BlockPos target = new BlockPos(selected.getInteger("X"), selected.getInteger("Y"), selected.getInteger("Z"));
            BlockPos candidate = target.subtract(pos);
            int distance = Math.abs(candidate.getX()) + Math.abs(candidate.getZ());
            if ((candidate.getX() == 0 || candidate.getZ() == 0) && distance >= 1 && distance <= 32
                    && Math.abs(candidate.getY()) <= 32) {
                delta = candidate;
                EnumFacing facing = delta.getX() > 0 ? EnumFacing.EAST : delta.getX() < 0 ? EnumFacing.WEST
                        : delta.getZ() > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH;
                state = state.withProperty(EjectorBlock.FACING, facing);
            }
        }
        if (!super.placeBlockAt(stack, player, world, pos, side, x, y, z, state)) return false;
        if (!world.isRemote && selected != null && selected.getInteger("Dimension") == world.provider.getDimension()) {
            if (delta != null) {
                IBlockState actual = world.getBlockState(pos);
                if (actual.getBlock() == getBlock() && actual.getValue(EjectorBlock.FACING) != state.getValue(EjectorBlock.FACING)) {
                    world.setBlockState(pos, actual.withProperty(EjectorBlock.FACING, state.getValue(EjectorBlock.FACING)), 3);
                    com.cogwork.cogworkbridge.kinetic.KineticNetworkManager.discard(world);
                }
                if (world.getTileEntity(pos) instanceof EjectorTileEntity ejector)
                    ejector.setTarget(Math.abs(delta.getX()) + Math.abs(delta.getZ()), delta.getY());
            }
            stack.getTagCompound().removeTag(TARGET);
        }
        return true;
    }
}
