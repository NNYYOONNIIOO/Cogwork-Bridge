package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** Extends an existing multi-block tank by one whole horizontal layer. */
public final class FluidTankItem extends ItemBlock {
    public FluidTankItem(Block block) { super(block); }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos,
                                EnumFacing face, float hitX, float hitY, float hitZ, IBlockState state) {
        // ItemBlock applies BlockEntityTag before onBlockPlacedBy. A tank item must not import
        // the old multi-block controller or more fluid than one newly placed block can hold.
        ItemStack placed = stack.copy();
        NBTTagCompound data = placed.getSubCompound("BlockEntityTag");
        if (data != null) {
            data.removeTag("TankController");
            data.removeTag("TankControllerOffset");
            data.removeTag("TankWidth");
            data.removeTag("TankHeight");
            data.removeTag("TankLuminosity");
            if (data.hasKey("Tank", 10)) {
                NBTTagCompound tank = data.getCompoundTag("Tank");
                if (tank.hasKey("Amount", 99)) {
                    tank.setInteger("Amount", Math.max(0, Math.min(
                            FluidTankTileEntity.capacityPerBlock(), tank.getInteger("Amount"))));
                }
            }
        }
        return super.placeBlockAt(placed, player, world, pos, face, hitX, hitY, hitZ, state);
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing face, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        int originalCount = held.getCount();
        BlockPos origin = null;
        int width = 0;
        int layerY = 0;
        if (!player.isSneaking() && face.getAxis().isVertical()
                && world.getBlockState(pos).getBlock() == block) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof FluidTankTileEntity tank) {
                FluidTankTileEntity root = tank.getControllerTile();
                if (root != null && root.getWidth() > 1) {
                    origin = root.getPos();
                    width = root.getWidth();
                    layerY = face == EnumFacing.UP ? origin.getY() + root.getHeight() : origin.getY() - 1;
                }
            }
        }

        EnumActionResult result = super.onItemUse(player, world, pos, hand, face, hitX, hitY, hitZ);
        if (result != EnumActionResult.SUCCESS || world.isRemote || origin == null
                || pos.offset(face).getY() != layerY) return result;
        if (player.capabilities.isCreativeMode) held.setCount(originalCount);

        List<BlockPos> additional = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos target = new BlockPos(origin.getX() + x, layerY, origin.getZ() + z);
                if (!world.isBlockLoaded(target)) return result;
                if (world.getBlockState(target).getBlock() == block) continue;
                if (!world.getBlockState(target).getBlock().isReplaceable(world, target)
                        || !player.canPlayerEdit(target, face, held)
                        || !world.mayPlace(block, target, false, face, player)) return result;
                additional.add(target);
            }
        }
        if (!player.capabilities.isCreativeMode && held.getCount() < additional.size()) return result;
        for (BlockPos target : additional) {
            if (player.capabilities.isCreativeMode) held.setCount(originalCount);
            if (super.onItemUse(player, world, target, hand, face, 0.5F, 0.5F, 0.5F)
                    != EnumActionResult.SUCCESS) break;
        }
        if (player.capabilities.isCreativeMode) held.setCount(originalCount);
        return result;
    }
}
