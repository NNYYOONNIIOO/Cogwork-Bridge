package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.List;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public final class FluidValveBlock extends KineticBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    public static final PropertyBool AXIS_ALONG_FIRST_COORDINATE = PropertyBool.create("axis_along_first");
    public static final IUnlistedProperty<Boolean> OPEN = new IUnlistedProperty<Boolean>() {
        @Override public String getName() { return "valve_open"; }
        @Override public boolean isValid(Boolean value) { return value != null; }
        @Override public Class<Boolean> getType() { return Boolean.class; }
        @Override public String valueToString(Boolean value) { return value.toString(); }
    };

    public FluidValveBlock() {
        super(net.minecraft.block.material.Material.IRON);
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getDefaultState().withProperty(FACING, EnumFacing.SOUTH)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, true));
    }

    @Override public boolean hasAxis() { return true; }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FACING, AXIS_ALONG_FIRST_COORDINATE},
                new IUnlistedProperty<?>[]{OPEN});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState extended)) return state;
        TileEntity tile = world.getTileEntity(pos);
        return extended.withProperty(OPEN, tile instanceof FluidValveTileEntity valve && valve.isOpen());
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB[] shapes = collisionShapes(getPipeAxis(state));
        return shapes[0].union(shapes[1]);
    }

    @Override public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return getBoundingBox(state, world, pos);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB query,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean actualState) {
        for (AxisAlignedBB local : collisionShapes(getPipeAxis(state))) {
            AxisAlignedBB box = local.offset(pos);
            if (query.intersects(box)) boxes.add(box);
        }
    }

    private static AxisAlignedBB[] collisionShapes(Axis axis) {
        return switch (axis) {
            case X -> new AxisAlignedBB[]{
                    new AxisAlignedBB(0, 3 / 16D, 3 / 16D, 1, 13 / 16D, 13 / 16D),
                    new AxisAlignedBB(2 / 16D, 2 / 16D, 2 / 16D, 14 / 16D, 14 / 16D, 14 / 16D)};
            case Y -> new AxisAlignedBB[]{
                    new AxisAlignedBB(3 / 16D, 0, 3 / 16D, 13 / 16D, 1, 13 / 16D),
                    new AxisAlignedBB(2 / 16D, 2 / 16D, 2 / 16D, 14 / 16D, 14 / 16D, 14 / 16D)};
            case Z -> new AxisAlignedBB[]{
                    new AxisAlignedBB(3 / 16D, 3 / 16D, 0, 13 / 16D, 13 / 16D, 1),
                    new AxisAlignedBB(2 / 16D, 2 / 16D, 2 / 16D, 14 / 16D, 14 / 16D, 14 / 16D)};
        };
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(Math.floorMod(meta % 6, 6)))
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, meta >= 6);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex() + (state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? 6 : 0);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX, float hitY,
                                            float hitZ, int meta, EntityLivingBase placer) {
        EnumFacing facing = KineticBlock.getPlacementFacing(placer, placer.isSneaking()).getOpposite();
        Axis facingAxis = facing.getAxis();
        boolean alongFirst;
        if (facingAxis.isHorizontal()) {
            alongFirst = facingAxis == Axis.Z;
            EnumFacing perpendicular = facingAxis == Axis.X ? EnumFacing.SOUTH : EnumFacing.EAST;
            if (prefersShaft(world, pos, EnumFacing.UP) || prefersShaft(world, pos, EnumFacing.DOWN)
                    || prefersPipe(world, pos, perpendicular)
                    || prefersPipe(world, pos, perpendicular.getOpposite())) {
                alongFirst = facingAxis == Axis.X;
            }
        } else {
            alongFirst = placer.getHorizontalFacing().getAxis() == Axis.X;
            EnumFacing preferred = null;
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                if (!prefersShaft(world, pos, direction) && !prefersPipe(world, pos, direction.rotateY())) continue;
                if (preferred != null && preferred.getAxis() != direction.getAxis()) {
                    preferred = null;
                    break;
                }
                preferred = direction;
            }
            if (preferred != null) alongFirst = preferred.getAxis() == Axis.X;
        }
        return getDefaultState().withProperty(FACING, facing)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, alongFirst);
    }

    @Override
    public Axis getAxis(IBlockState state) {
        Axis facing = state.getValue(FACING).getAxis();
        return state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? firstPipeAxis(facing) : secondPipeAxis(facing);
    }

    private static boolean prefersShaft(World world, BlockPos pos, EnumFacing direction) {
        BlockPos neighbor = pos.offset(direction);
        if (!world.isBlockLoaded(neighbor)) return false;
        IBlockState state = world.getBlockState(neighbor);
        return state.getBlock() instanceof KineticBlock kinetic
                && kinetic.canConnectShaftTowards(world, neighbor, state, direction.getOpposite());
    }

    private static boolean prefersPipe(World world, BlockPos pos, EnumFacing direction) {
        BlockPos neighbor = pos.offset(direction);
        if (!world.isBlockLoaded(neighbor)) return false;
        IBlockState state = world.getBlockState(neighbor);
        if (state.getBlock() instanceof FluidPipeBlock pipe) return pipe.allowsAxis(state, direction.getAxis());
        if (state.getBlock() instanceof FluidValveBlock) return getPipeAxis(state) == direction.getAxis();
        if (state.getBlock() instanceof MechanicalPumpBlock) {
            return state.getValue(MechanicalPumpBlock.FACING).getAxis() == direction.getAxis();
        }
        TileEntity tile = world.getTileEntity(neighbor);
        return tile != null && tile.hasCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler
                .FLUID_HANDLER_CAPABILITY, direction.getOpposite());
    }

    public static Axis getPipeAxis(IBlockState state) {
        return state.getValue(AXIS_ALONG_FIRST_COORDINATE)
                ? secondPipeAxis(state.getValue(FACING).getAxis())
                : firstPipeAxis(state.getValue(FACING).getAxis());
    }

    private static Axis firstPipeAxis(Axis shaft) {
        for (Axis axis : Axis.values()) if (axis != shaft) return axis;
        throw new IllegalStateException("No perpendicular pipe axis");
    }

    private static Axis secondPipeAxis(Axis shaft) {
        boolean skippedFirst = false;
        for (Axis axis : Axis.values()) {
            if (axis == shaft) continue;
            if (!skippedFirst) { skippedFirst = true; continue; }
            return axis;
        }
        throw new IllegalStateException("No perpendicular pipe axis");
    }

    public static IBlockState rotateAround(IBlockState state, Axis axis) {
        EnumFacing facing = state.getValue(FACING);
        EnumFacing pipe = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, getPipeAxis(state));
        if (facing.getAxis() != axis) facing = facing.rotateAround(axis);
        if (pipe.getAxis() != axis) pipe = pipe.rotateAround(axis);
        return state.withProperty(FACING, facing)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, pipe.getAxis() == secondPipeAxis(facing.getAxis()));
    }

    public boolean canConnectTowards(World world, BlockPos pos, EnumFacing side) {
        return side.getAxis() == getPipeAxis(world.getBlockState(pos));
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        Axis pipeBefore = getPipeAxis(state);
        EnumFacing facing = rotation.rotate(state.getValue(FACING));
        Axis pipeAfter = rotation.rotate(EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, pipeBefore)).getAxis();
        return state.withProperty(FACING, facing)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, pipeAfter == secondPipeAxis(facing.getAxis()));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        Axis pipeBefore = getPipeAxis(state);
        EnumFacing facing = mirror.mirror(state.getValue(FACING));
        Axis pipeAfter = mirror.mirror(EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, pipeBefore)).getAxis();
        return state.withProperty(FACING, facing)
                .withProperty(AXIS_ALONG_FIRST_COORDINATE, pipeAfter == secondPipeAxis(facing.getAxis()));
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) { return new FluidValveTileEntity(); }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        FluidTransferManager.registerPipe(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block,
                                BlockPos neighborPos) {
        super.neighborChanged(state, world, pos, block, neighborPos);
        FluidTransferManager.markDirty(world);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        FluidTransferManager.unregisterPipe(world, pos);
        super.breakBlock(world, pos, state);
    }
}
