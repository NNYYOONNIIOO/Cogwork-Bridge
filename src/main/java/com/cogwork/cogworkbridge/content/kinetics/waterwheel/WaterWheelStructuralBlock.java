package com.cogwork.cogworkbridge.content.kinetics.waterwheel;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import java.util.Random;

public final class WaterWheelStructuralBlock extends Block {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    private static final ThreadLocal<RemovedPart> REMOVED_PART = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DROP_CENTRE = ThreadLocal.withInitial(() -> true);
    private record RemovedPart(IBlockAccess world, BlockPos pos, IBlockState state) { }
    public WaterWheelStructuralBlock() { super(Material.WOOD); setHardness(2F); setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH)); }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }
    @Override public EnumBlockRenderType getRenderType(IBlockState state) { return EnumBlockRenderType.INVISIBLE; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public ItemStack getPickBlock(IBlockState state, net.minecraft.util.math.RayTraceResult target, World world, BlockPos pos, net.minecraft.entity.player.EntityPlayer player) { return new ItemStack(com.cogwork.cogworkbridge.registry.AllBlocks.LARGE_WATER_WHEEL); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
            net.minecraft.entity.player.EntityPlayer player, net.minecraft.util.EnumHand hand, EnumFacing side, float x, float y, float z) {
        BlockPos center = master(world, pos, state);
        return center != null && world.getBlockState(center).getBlock().onBlockActivated(world, center,
                world.getBlockState(center), player, hand, side, x, y, z);
    }
    static BlockPos master(IBlockAccess world, BlockPos pos, IBlockState state) {
        BlockPos next = masterPosition(world, pos, state);
        return next != null && world.getBlockState(next).getBlock() instanceof LargeWaterWheelBlock ? next : null;
    }
    static BlockPos masterPosition(IBlockAccess world, BlockPos pos, IBlockState state) {
        BlockPos next = pos;
        for (int i = 0; i < 2; i++) {
            RemovedPart removed = REMOVED_PART.get();
            IBlockState part = i == 0 ? state : removed != null && removed.world() == world && removed.pos().equals(next)
                    ? removed.state() : world.getBlockState(next);
            if (!(part.getBlock() instanceof WaterWheelStructuralBlock)) break;
            next = next.offset(part.getValue(FACING));
        }
        return next;
    }

    @Override public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (!world.isRemote && !LargeWaterWheelBlock.REMOVING.get()) world.scheduleUpdate(pos, this, 1);
    }

    @Override public void updateTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (world.isRemote) return;
        BlockPos next = pos;
        for (int i = 0; i < 2; i++) {
            if (!world.isBlockLoaded(next)) return;
            IBlockState part = world.getBlockState(next);
            if (!(part.getBlock() instanceof WaterWheelStructuralBlock)) break;
            next = next.offset(part.getValue(FACING));
        }
        // Do not load a chunk or destroy valid parts while their centre is unloaded.
        if (world.isBlockLoaded(next) && !(world.getBlockState(next).getBlock() instanceof LargeWaterWheelBlock)) {
            world.setBlockToAir(pos);
        }
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote && !LargeWaterWheelBlock.REMOVING.get()) {
            BlockPos master = master(world, pos, state);
            if (master != null) {
                // The removed part may be the intermediate link for corner parts.
                // Keep its old state available until the centre has gathered them.
                RemovedPart previous = REMOVED_PART.get();
                REMOVED_PART.set(new RemovedPart(world, pos.toImmutable(), state));
                try {
                    world.destroyBlock(master, DROP_CENTRE.get());
                } finally {
                    if (previous == null) REMOVED_PART.remove(); else REMOVED_PART.set(previous);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override public boolean removedByPlayer(IBlockState state, World world, BlockPos pos,
                                             EntityPlayer player, boolean willHarvest) {
        boolean previous = DROP_CENTRE.get();
        DROP_CENTRE.set(!player.capabilities.isCreativeMode);
        try {
            return super.removedByPlayer(state, world, pos, player, willHarvest);
        } finally {
            if (previous) DROP_CENTRE.remove(); else DROP_CENTRE.set(false);
        }
    }
}
