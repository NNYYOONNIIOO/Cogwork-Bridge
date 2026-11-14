package com.cogwork.cogworkbridge.content.decoration;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import javax.annotation.Nullable;

public final class MetalScaffoldingItem extends ItemBlock {
    public record Placement(BlockPos target, EnumFacing direction, IBlockState state) { }

    public MetalScaffoldingItem(Block block) { super(block); }

    @Nullable public Placement placement(World world, BlockPos pos, EntityPlayer player, EnumHand hand,
                                          EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.getBlockState(pos).getBlock() != block) return null;
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return null;
        boolean inside = new AxisAlignedBB(pos).contains(player.getPositionEyes(1F));
        EnumFacing direction = player.isSneaking() ? (inside ? side.getOpposite() : side)
                : side == EnumFacing.UP && !inside ? player.getHorizontalFacing() : EnumFacing.UP;
        BlockPos target = pos;
        int horizontalDistance = 0;
        while (horizontalDistance < 7) {
            target = target.offset(direction);
            if (!world.isBlockLoaded(target) || target.getY() < 0 || target.getY() >= world.getHeight()) return null;
            IBlockState existing = world.getBlockState(target);
            if (existing.getBlock() == block) {
                if (direction.getAxis().isHorizontal()) horizontalDistance++;
                continue;
            }
            if (!existing.getBlock().isReplaceable(world, target) || !player.canPlayerEdit(target, direction, stack)
                    || !world.mayPlace(block, target, false, direction, player)) return null;
            IBlockState placed = block.getStateForPlacement(world, target, direction, hitX, hitY, hitZ,
                    getMetadata(stack.getMetadata()), player, hand);
            return new Placement(target.toImmutable(), direction, placed);
        }
        return null;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.getBlockState(pos).getBlock() != block)
            return super.onItemUse(player, world, pos, hand, side, hitX, hitY, hitZ);
        Placement placement = placement(world, pos, player, hand, side, hitX, hitY, hitZ);
        if (placement == null) return EnumActionResult.FAIL;
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && placeBlockAt(stack, player, world, placement.target(), placement.direction(),
                hitX, hitY, hitZ, placement.state())) {
            net.minecraft.block.SoundType sound = block.getSoundType(placement.state(), world, placement.target(), player);
            world.playSound(null, placement.target(), sound.getPlaceSound(), SoundCategory.BLOCKS,
                    (sound.getVolume() + 1F) / 2F, sound.getPitch() * .8F);
            if (!player.capabilities.isCreativeMode) stack.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }
}
