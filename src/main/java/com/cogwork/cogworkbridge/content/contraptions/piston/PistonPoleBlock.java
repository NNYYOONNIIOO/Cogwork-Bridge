package com.cogwork.cogworkbridge.content.contraptions.piston;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** The rod and two internal head variants have orientation-only metadata. */
public final class PistonPoleBlock extends Block {
    private static final int MAX_POLES = 64;
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    private final boolean head;

    public PistonPoleBlock(boolean head) {
        super(Material.WOOD);
        this.head = head;
        setHardness(.5F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    public boolean isHead() { return head; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6))); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, side.getOpposite());
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (head) return FULL_BLOCK_AABB;
        return switch (state.getValue(FACING).getAxis()) {
            case X -> new AxisAlignedBB(0, .375, .375, 1, .625, .625);
            case Y -> new AxisAlignedBB(.375, 0, .375, .625, 1, .625);
            case Z -> new AxisAlignedBB(.375, .375, 0, .625, .625, 1);
        };
    }
    @Override public Item getItemDropped(IBlockState state, java.util.Random random, int fortune) {
        return head ? Item.getItemFromBlock(com.cogwork.cogworkbridge.registry.AllBlocks.PISTON_EXTENSION_POLE)
                : super.getItemDropped(state, random, fortune);
    }

    /** Breaking a connected rod dismantles the section between the head and the base. */
    @Override public void onBlockHarvested(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (!world.isRemote) {
            EnumFacing.Axis axis = state.getValue(FACING).getAxis();
            EnumFacing positive = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis);
            BlockPos base = null, headPos = head ? pos : null;
            for (EnumFacing direction : new EnumFacing[]{positive, positive.getOpposite()}) {
                for (int i = 1; i <= MAX_POLES; i++) {
                    BlockPos candidate = pos.offset(direction, i);
                    if (!world.isBlockLoaded(candidate)) break;
                    IBlockState next = world.getBlockState(candidate);
                    if (next.getBlock() instanceof PistonPoleBlock pole && !pole.head
                            && next.getValue(FACING).getAxis() == axis) continue;
                    if (next.getBlock() instanceof MechanicalPistonBlock
                            && next.getValue(MechanicalPistonBlock.FACING).getAxis() == axis) base = candidate;
                    if (next.getBlock() instanceof PistonPoleBlock pole && pole.head
                            && next.getValue(FACING).getAxis() == axis) headPos = candidate;
                    break;
                }
            }
            if (base != null && headPos != null
                    && world.getBlockState(base).getValue(MechanicalPistonBlock.FACING)
                    == world.getBlockState(headPos).getValue(FACING)) {
                EnumFacing facing = world.getBlockState(base).getValue(MechanicalPistonBlock.FACING);
                BlockPos difference = headPos.subtract(base);
                int distance = difference.getX() * facing.getXOffset() + difference.getY() * facing.getYOffset()
                        + difference.getZ() * facing.getZOffset();
                if (distance > 0 && distance <= MAX_POLES) {
                    for (int i = 1; i <= distance; i++) {
                        BlockPos cursor = base.offset(facing, i);
                        if (!cursor.equals(pos)) world.destroyBlock(cursor, !player.capabilities.isCreativeMode);
                    }
                    if (world.getTileEntity(base) instanceof MechanicalPistonTileEntity piston) piston.onLengthBroken();
                }
            }
        }
        super.onBlockHarvested(world, pos, state, player);
    }

    /** Called before a piston base is removed; stops at unloaded or unrelated blocks. */
    public static void dismantleFromBase(World world, BlockPos base, IBlockState state, @Nullable EntityPlayer player) {
        EnumFacing facing = state.getValue(MechanicalPistonBlock.FACING);
        boolean drop = player == null || !player.capabilities.isCreativeMode;
        BlockPos headPos = null;
        for (int i = 1; i <= MAX_POLES; i++) {
            BlockPos candidate = base.offset(facing, i);
            if (!world.isBlockLoaded(candidate)) break;
            IBlockState next = world.getBlockState(candidate);
            if (next.getBlock() instanceof PistonPoleBlock pole && !pole.head
                    && next.getValue(FACING).getAxis() == facing.getAxis()) continue;
            if (next.getBlock() instanceof PistonPoleBlock pole && pole.head && next.getValue(FACING) == facing)
                headPos = candidate;
            break;
        }
        if (headPos != null) {
            for (BlockPos cursor = base.offset(facing); !cursor.equals(headPos.offset(facing)); cursor = cursor.offset(facing))
                world.destroyBlock(cursor, drop);
        }
        for (int i = 1; i <= MAX_POLES; i++) {
            BlockPos candidate = base.offset(facing.getOpposite(), i);
            if (!world.isBlockLoaded(candidate)) break;
            IBlockState next = world.getBlockState(candidate);
            if (!(next.getBlock() instanceof PistonPoleBlock pole) || pole.head
                    || next.getValue(FACING).getAxis() != facing.getAxis()) break;
            world.destroyBlock(candidate, drop);
        }
    }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                              EnumHand hand, EnumFacing face, float x, float y, float z) {
        ItemStack held = player.getHeldItem(hand);
        if (player.isSneaking() || held.getItem() != Item.getItemFromBlock(com.cogwork.cogworkbridge.registry.AllBlocks.PISTON_EXTENSION_POLE)) return false;
        EnumFacing.Axis axis = state.getValue(FACING).getAxis();
        double look = axis == EnumFacing.Axis.X ? player.getLookVec().x
                : axis == EnumFacing.Axis.Y ? player.getLookVec().y : player.getLookVec().z;
        EnumFacing direction = face.getAxis() == axis ? face : EnumFacing.getFacingFromAxis(
                look >= 0 ? EnumFacing.AxisDirection.POSITIVE : EnumFacing.AxisDirection.NEGATIVE, axis);
        BlockPos target = pos;
        for (int i = 0; i < 64; i++) {
            target = target.offset(direction);
            if (!world.isBlockLoaded(target)) return false;
            IBlockState next = world.getBlockState(target);
            if (next.getBlock() instanceof PistonPoleBlock pole && !pole.isHead() && next.getValue(FACING).getAxis() == axis) continue;
            if (!next.getBlock().isReplaceable(world, target) || !player.canPlayerEdit(target, direction, held)
                    || !world.mayPlace(com.cogwork.cogworkbridge.registry.AllBlocks.PISTON_EXTENSION_POLE, target, false, direction, player)) return false;
            if (!world.isRemote && world.setBlockState(target, com.cogwork.cogworkbridge.registry.AllBlocks.PISTON_EXTENSION_POLE.getDefaultState().withProperty(FACING, direction), 3))
                if (!player.capabilities.isCreativeMode) held.shrink(1);
            return true;
        }
        return false;
    }
}
