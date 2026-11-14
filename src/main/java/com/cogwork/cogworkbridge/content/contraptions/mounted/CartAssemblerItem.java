package com.cogwork.cogworkbridge.content.contraptions.mounted;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The assembler wraps an existing straight rail and retains its rail type. */
public final class CartAssemblerItem extends ItemBlock {
    public CartAssemblerItem(Block block) { super(block); }

    /** The assembler replaces the clicked rail, rather than the cell above it. */
    @Override
    @SideOnly(Side.CLIENT)
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side,
                                       EntityPlayer player, ItemStack stack) {
        return !stack.isEmpty() && player.canPlayerEdit(pos, side, stack)
                && world.isBlockModifiable(player, pos)
                && isSupportedRail(world, pos, world.getBlockState(pos));
    }

    private static boolean isSupportedRail(World world, BlockPos pos, IBlockState state) {
        if (!(state.getBlock() instanceof BlockRailBase rail)
                || CartAssemblerTileEntity.railTypeFor(state.getBlock()) < 0) return false;
        BlockRailBase.EnumRailDirection shape = rail.getRailDirection(world, pos, state, null);
        return shape == BlockRailBase.EnumRailDirection.NORTH_SOUTH
                || shape == BlockRailBase.EnumRailDirection.EAST_WEST;
    }

    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                                 EnumFacing face, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        IBlockState rail = world.getBlockState(pos);
        int type = CartAssemblerTileEntity.railTypeFor(rail.getBlock());
        boolean backwards = rail.getBlock() instanceof ControllerRailBlock
                ? rail.getValue(ControllerRailBlock.BACKWARDS)
                : player.getHorizontalFacing().getAxisDirection() == EnumFacing.AxisDirection.POSITIVE;
        if (!isSupportedRail(world, pos, rail) || !player.canPlayerEdit(pos, face, held)
                || !world.isBlockModifiable(player, pos))
            return EnumActionResult.FAIL;
        BlockRailBase railBlock = (BlockRailBase) rail.getBlock();
        BlockRailBase.EnumRailDirection shape = railBlock.getRailDirection(world, pos, rail, null);
        if (world.isRemote) return EnumActionResult.SUCCESS;
        IBlockState assembler = block.getDefaultState().withProperty(CartAssemblerBlock.SHAPE, shape);
        if (!world.setBlockState(pos, assembler, 3)) return EnumActionResult.FAIL;
        if (world.getTileEntity(pos) instanceof CartAssemblerTileEntity tile) {
            tile.setBackwards(backwards);
            tile.setRailType(type);
        }
        world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_STONE_PLACE,
                net.minecraft.util.SoundCategory.BLOCKS, 1F, 1F);
        if (!player.capabilities.isCreativeMode) held.shrink(1);
        return EnumActionResult.SUCCESS;
    }
}
