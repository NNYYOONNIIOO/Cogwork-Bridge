package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class HosePulleyBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public HosePulleyBlock() {
        super(net.minecraft.block.material.Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setLightOpacity(0);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override public boolean hasAxis() { return false; }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(Math.floorMod(meta, 4)));
    }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public TileEntity createNewTileEntity(World world, int meta) { return new HosePulleyTileEntity(); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX, float hitY,
                                                       float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing preferred = KineticBlock.getPreferredShaftFacing(world, pos);
        if (preferred != null && preferred.getAxis().isVertical()) preferred = null;
        if (preferred == null) {
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.offset(direction);
                if (!world.isBlockLoaded(neighborPos)) continue;
                IBlockState neighbor = world.getBlockState(neighborPos);
                boolean connectable = neighbor.getBlock() instanceof FluidPipeBlock pipe
                        && pipe.allowsAxis(neighbor, direction.getAxis())
                        || neighbor.getBlock() instanceof FluidValveBlock
                        && FluidValveBlock.getPipeAxis(neighbor) == direction.getAxis()
                        || neighbor.getBlock() instanceof MechanicalPumpBlock
                        && neighbor.getValue(MechanicalPumpBlock.FACING).getAxis() == direction.getAxis();
                TileEntity tile = world.getTileEntity(neighborPos);
                connectable |= tile != null && tile.hasCapability(
                        net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                        direction.getOpposite());
                if (!connectable) continue;
                EnumFacing shaftSide = direction.getOpposite();
                if (preferred != null && preferred.getAxis() != shaftSide.getAxis()) {
                    preferred = null;
                    break;
                }
                preferred = shaftSide;
            }
        }
        EnumFacing facing = preferred == null ? placer.getHorizontalFacing().getOpposite() : preferred.rotateYCCW();
        return getDefaultState().withProperty(FACING, facing);
    }
    @Override public net.minecraft.util.EnumFacing.Axis getAxis(IBlockState state) {
        return state.getValue(FACING).rotateY().getAxis();
    }
    @Override public boolean hasShaftTowards(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        return side == state.getValue(FACING).rotateY();
    }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
