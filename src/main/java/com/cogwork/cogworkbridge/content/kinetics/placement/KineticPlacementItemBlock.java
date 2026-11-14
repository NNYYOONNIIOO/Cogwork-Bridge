package com.cogwork.cogworkbridge.content.kinetics.placement;

import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class KineticPlacementItemBlock extends ItemBlock {
    public KineticPlacementItemBlock(Block block) {
        super(block);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world,
                                BlockPos pos, EnumFacing side, float hitX, float hitY,
                                float hitZ, IBlockState newState) {
        if (getBlock() instanceof CogwheelBlock cog
                && !KineticPlacementRules.validCogwheelPosition(world, pos,
                        cog.getAxis(newState), cog.isLarge())) {
            return false;
        }
        return super.placeBlockAt(stack, player, world, pos, side, hitX, hitY,
                hitZ, newState);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos clickedPos,
                                          EnumFacing face, float hitX, float hitY, float hitZ,
                                          EnumHand hand) {
        KineticPlacementPlan plan = KineticPlacementRules.plan(
                world, clickedPos, face, hitX, hitY, hitZ, player, hand, getBlock());
        if (!plan.assisted() || !plan.valid()) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        ItemStack stack = player.getHeldItem(hand);
        int originalCount = stack.getCount();
        EnumActionResult result = stack.onItemUse(player, world, clickedPos, hand,
                face, hitX, hitY, hitZ);
        if (player.capabilities.isCreativeMode) {
            stack.setCount(originalCount);
        }
        return result;
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world,
                                     BlockPos clickedPos, EnumHand hand,
                                     EnumFacing face, float hitX, float hitY,
                                     float hitZ) {
        KineticPlacementPlan plan = KineticPlacementRules.plan(
                world, clickedPos, face, hitX, hitY, hitZ, player, hand, getBlock());
        if (!plan.assisted()) {
            return super.onItemUse(player, world, clickedPos, hand, face,
                    hitX, hitY, hitZ);
        }
        if (!plan.valid()) {
            return EnumActionResult.FAIL;
        }
        KineticPlacementContext.setForcedAxis(plan.axis());
        try {
            return super.onItemUse(player, world, plan.target(), hand,
                    face, hitX, hitY, hitZ);
        } finally {
            KineticPlacementContext.clear();
        }
    }

    public static KineticPlacementPlan preview(World world, BlockPos clickedPos,
                                               EnumFacing face, float hitX,
                                               float hitY, float hitZ,
                                               EntityPlayer player, EnumHand hand,
                                               Block block) {
        return KineticPlacementRules.plan(world, clickedPos, face, hitX, hitY,
                hitZ, player, hand, block);
    }
}
