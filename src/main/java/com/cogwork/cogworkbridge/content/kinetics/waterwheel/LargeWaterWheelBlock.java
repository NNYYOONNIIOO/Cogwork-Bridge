package com.cogwork.cogworkbridge.content.kinetics.waterwheel;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumHand;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.Random;

/** Centre of the three by three occupied wheel plane. */
public final class LargeWaterWheelBlock extends KineticBlock {
    static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    public LargeWaterWheelBlock() { super(Material.WOOD); setHardness(2F); setDefaultState(getDefaultState().withProperty(AXIS, EnumFacing.Axis.X)); }
    @Override public boolean hasAxis() { return true; }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing preferred = placer.isSneaking() ? null : getPreferredShaftFacing(world, pos);
        return getDefaultState().withProperty(AXIS, preferred == null ? getPlacementAxis(placer) : preferred.getAxis());
    }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new LargeWaterWheelTileEntity(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.ENTITYBLOCK_ANIMATED; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float x, float y, float z) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() != Item.getItemFromBlock(Blocks.PLANKS)) return false;
        if (!world.isRemote && world.getTileEntity(pos) instanceof LargeWaterWheelTileEntity wheel) wheel.setWood(stack.getMetadata());
        return true;
    }
    @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote) world.scheduleUpdate(pos, this, 1);
    }
    @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.isRemote) return;
        EnumFacing.Axis axis = getAxis(state);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            BlockPos offset = offset(axis, a, b);
            BlockPos target = pos.add(offset);
            IBlockState occupied = world.getBlockState(target);
            if (occupied.getBlock() == AllBlocks.WATER_WHEEL_STRUCTURAL
                    ? !pos.equals(WaterWheelStructuralBlock.master(world, target, occupied))
                    : !occupied.getBlock().isReplaceable(world, target)) {
                world.destroyBlock(pos, true); return;
            }
        }
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            BlockPos offset = offset(axis, a, b);
            BlockPos target = pos.add(offset);
            if (world.getBlockState(target).getBlock() == AllBlocks.WATER_WHEEL_STRUCTURAL) continue;
            BlockPos next = a == 0 ? offset(axis, a, b - Integer.signum(b)) : offset(axis, a - Integer.signum(a), b);
            EnumFacing toward = EnumFacing.getFacingFromVector(next.getX() - offset.getX(), next.getY() - offset.getY(), next.getZ() - offset.getZ());
            world.setBlockState(target, AllBlocks.WATER_WHEEL_STRUCTURAL.getDefaultState().withProperty(WaterWheelStructuralBlock.FACING, toward), 3);
        }
    }
    static BlockPos offset(EnumFacing.Axis axis, int a, int b) {
        return switch (axis) { case X -> new BlockPos(0, a, b); case Y -> new BlockPos(a, 0, b); case Z -> new BlockPos(a, b, 0); };
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            REMOVING.set(true);
            try {
                java.util.List<BlockPos> parts = new java.util.ArrayList<>();
                for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    BlockPos part = pos.add(offset(getAxis(state), a, b));
                    IBlockState partState = world.getBlockState(part);
                    if (partState.getBlock() == AllBlocks.WATER_WHEEL_STRUCTURAL
                            && pos.equals(WaterWheelStructuralBlock.masterPosition(world, part, partState))) parts.add(part);
                }
                for (BlockPos part : parts) world.setBlockToAir(part);
            } finally { REMOVING.remove(); }
        }
        super.breakBlock(world, pos, state);
    }
}
