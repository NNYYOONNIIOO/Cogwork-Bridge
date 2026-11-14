package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.content.processing.basin.BasinBlock;
import com.cogwork.cogworkbridge.content.logistics.depot.DepotBlock;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class AssemblyOperatorItemBlock extends ItemBlock {
    private static final ThreadLocal<Boolean> REDIRECTED = new ThreadLocal<>();

    public AssemblyOperatorItemBlock(Block block) {
        super(block);
    }

    /** Whether the item is currently placing above a processing surface. */
    public static boolean isRedirectedPlacement() {
        return Boolean.TRUE.equals(REDIRECTED.get());
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                          EnumFacing face, float hitX, float hitY, float hitZ,
                                          EnumHand hand) {
        if (!isAssemblyBase(world, pos, face)) {
            return EnumActionResult.PASS;
        }
        return placeAboveBase(player, world, pos, hand);
    }

    private EnumActionResult placeAboveBase(EntityPlayer player, World world, BlockPos pos, EnumHand hand) {
        BlockPos target = pos.up(2);
        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty() || !world.isBlockLoaded(target)
                || !world.getBlockState(target).getBlock().isReplaceable(world, target)
                || !player.canPlayerEdit(target, EnumFacing.UP, stack)
                || !world.mayPlace(getBlock(), target, false, EnumFacing.UP, player)) {
            return EnumActionResult.FAIL;
        }
        // Place against the redirected target directly; re-entering ItemStack.onItemUse
        // can invoke the base block interaction again instead of completing placement.
        int originalCount = stack.getCount();
        boolean wasRedirected = isRedirectedPlacement();
        REDIRECTED.set(true);
        try {
            return super.onItemUse(player, world, target, hand, EnumFacing.UP, .5F, 1F, .5F);
        } finally {
            if (wasRedirected) REDIRECTED.set(true); else REDIRECTED.remove();
            if (player.capabilities.isCreativeMode) stack.setCount(originalCount);
        }
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                     EnumHand hand, EnumFacing face,
                                     float hitX, float hitY, float hitZ) {
        if (isAssemblyBase(world, pos, face)) {
            return placeAboveBase(player, world, pos, hand);
        }
        return super.onItemUse(player, world, pos, hand, face, hitX, hitY, hitZ);
    }

    private static boolean isAssemblyBase(World world, BlockPos pos, EnumFacing face) {
        if (face != EnumFacing.UP) return false;
        Block block = world.getBlockState(pos).getBlock();
        if (block instanceof BeltBlock) {
            return world.getTileEntity(pos) instanceof BeltTileEntity belt
                    && belt.step().getY() == 0 && !belt.step().equals(BlockPos.ORIGIN)
                    && ((BeltBlock) block).getAxis(world.getBlockState(pos)) != EnumFacing.Axis.Y;
        }
        return block instanceof BasinBlock || block instanceof DepotBlock || block instanceof EjectorBlock;
    }
}
