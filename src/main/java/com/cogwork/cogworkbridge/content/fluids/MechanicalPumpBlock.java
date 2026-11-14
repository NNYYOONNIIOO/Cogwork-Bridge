package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MechanicalPumpBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public MechanicalPumpBlock() {
        super(Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override public boolean hasAxis() { return false; }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta, 6)));
    }

    @Override
    public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getIndex(); }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing look = getPlacementFacing(placer);
        EnumFacing target = placer.isSneaking() ? look : look.getOpposite();
        EnumFacing best = null;
        int bestScore = Integer.MIN_VALUE;
        for (EnumFacing candidate : EnumFacing.values()) {
            BlockPos neighborPos = pos.offset(candidate);
            if (!world.isBlockLoaded(neighborPos)) continue;
            IBlockState neighbor = world.getBlockState(neighborPos);
            boolean connectable = neighbor.getBlock() instanceof FluidPipeBlock pipe
                    && pipe.allowsAxis(neighbor, candidate.getAxis())
                    || neighbor.getBlock() instanceof FluidValveBlock
                    && FluidValveBlock.getPipeAxis(neighbor) == candidate.getAxis()
                    || neighbor.getBlock() instanceof MechanicalPumpBlock
                    && neighbor.getValue(FACING).getAxis() == candidate.getAxis();
            TileEntity tile = world.getTileEntity(neighborPos);
            connectable |= tile != null && tile.hasCapability(
                    net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                    candidate.getOpposite());
            if (!connectable) continue;
            int score = candidate.getXOffset() * target.getXOffset()
                    + candidate.getYOffset() * target.getYOffset()
                    + candidate.getZOffset() * target.getZOffset();
            if (score >= bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (!placer.isSneaking() && best != null && best.getAxis() != target.getAxis()) target = best;
        return getDefaultState().withProperty(FACING, target);
    }

    @Override
    public EnumFacing.Axis getAxis(IBlockState state) { return state.getValue(FACING).getAxis(); }

    @Override
    public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return false;
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) { return new MechanicalPumpTileEntity(); }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        FluidTransferManager.registerPump(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block,
                                BlockPos neighborPos) {
        super.neighborChanged(state, world, pos, block, neighborPos);
        FluidTransferManager.markDirty(world);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        FluidTransferManager.unregisterPump(world, pos);
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }
}
