package com.cogwork.cogworkbridge.content.contraptions.mounted;

import net.minecraft.block.BlockRailBase;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Directional rail whose redstone level sets the minecart's target speed. */
public final class ControllerRailBlock extends BlockRailBase {
    public static final PropertyEnum<EnumRailDirection> SHAPE = PropertyEnum.create("shape", EnumRailDirection.class,
            EnumRailDirection.NORTH_SOUTH, EnumRailDirection.EAST_WEST, EnumRailDirection.ASCENDING_EAST,
            EnumRailDirection.ASCENDING_WEST, EnumRailDirection.ASCENDING_NORTH, EnumRailDirection.ASCENDING_SOUTH);
    public static final PropertyBool BACKWARDS = PropertyBool.create("backwards");
    public ControllerRailBlock() {
        super(true);
        setHardness(.7F);
        setDefaultState(blockState.getBaseState().withProperty(SHAPE, EnumRailDirection.NORTH_SOUTH).withProperty(BACKWARDS, false));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, SHAPE, BACKWARDS); }
    @Override public PropertyEnum<EnumRailDirection> getShapeProperty() { return SHAPE; }
    @Override public IBlockState getStateFromMeta(int meta) {
        int shape = Math.min(5, meta & 7);
        return getDefaultState().withProperty(SHAPE, EnumRailDirection.byMetadata(shape)).withProperty(BACKWARDS, (meta & 8) != 0);
    }
    @Override public int getMetaFromState(IBlockState state) {
        return state.getValue(SHAPE).getMetadata() | (state.getValue(BACKWARDS) ? 8 : 0);
    }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face, float x, float y, float z, int meta, EntityLivingBase placer) {
        EnumFacing facing = placer.getHorizontalFacing();
        return getDefaultState().withProperty(SHAPE, facing.getAxis() == EnumFacing.Axis.X ? EnumRailDirection.EAST_WEST : EnumRailDirection.NORTH_SOUTH)
                .withProperty(BACKWARDS, facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }
    @Override public TileEntity createTileEntity(World world, IBlockState state) { return new ControllerRailTileEntity(); }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public BlockRenderLayer getRenderLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }

    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) return state;
        BlockRailBase rail = (BlockRailBase) Blocks.GOLDEN_RAIL;
        EnumRailDirection shape = rail.getDefaultState().withProperty(rail.getShapeProperty(), state.getValue(SHAPE))
                .withRotation(rotation).getValue(rail.getShapeProperty());
        IBlockState rotated = state.withProperty(SHAPE, shape);
        if (rotation == Rotation.CLOCKWISE_180
                || (pointingTowards(rotated).getAxis() == EnumFacing.Axis.Z) == (rotation == Rotation.COUNTERCLOCKWISE_90))
            rotated = rotated.cycleProperty(BACKWARDS);
        return rotated;
    }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        BlockRailBase rail = (BlockRailBase) Blocks.GOLDEN_RAIL;
        EnumRailDirection shape = rail.getDefaultState().withProperty(rail.getShapeProperty(), state.getValue(SHAPE))
                .withMirror(mirror).getValue(rail.getShapeProperty());
        IBlockState mirrored = state.withProperty(SHAPE, shape);
        if ((pointingTowards(mirrored).getAxis() == EnumFacing.Axis.Z) == (mirror == Mirror.LEFT_RIGHT))
            mirrored = mirrored.cycleProperty(BACKWARDS);
        return mirrored;
    }

    public static EnumFacing direction(IBlockState state) {
        EnumRailDirection shape = state.getValue(SHAPE);
        EnumFacing base = pointingTowards(state);
        boolean reversedSlope = shape == EnumRailDirection.ASCENDING_EAST || shape == EnumRailDirection.ASCENDING_SOUTH;
        return state.getValue(BACKWARDS) ^ reversedSlope ? base.getOpposite() : base;
    }
    private static EnumFacing pointingTowards(IBlockState state) {
        return switch (state.getValue(SHAPE)) {
            case EAST_WEST, ASCENDING_WEST -> EnumFacing.WEST;
            case ASCENDING_EAST -> EnumFacing.EAST;
            case ASCENDING_SOUTH -> EnumFacing.SOUTH;
            default -> EnumFacing.NORTH;
        };
    }
    public static int power(World world, BlockPos pos) {
        if (world.isRemote && world.getTileEntity(pos) instanceof ControllerRailTileEntity rail)
            return rail.signalLevel();
        return calculatePower(world, pos);
    }
    static int calculatePower(World world, BlockPos pos) {
        int direct = world.getRedstonePowerFromNeighbors(pos);
        if (direct > 0) return direct;
        IBlockState original = world.getBlockState(pos);
        if (original.getBlock() instanceof ControllerRailBlock)
            return networkPower(world, pos, direction(original));
        if (!(original.getBlock() instanceof CartAssemblerBlock)
                || !(world.getTileEntity(pos) instanceof CartAssemblerTileEntity assembler)) return 0;
        IBlockState rail = assembler.getRailState(original);
        if (rail.getBlock() instanceof ControllerRailBlock)
            return networkPower(world, pos, direction(rail));
        // A station holding a non-directional rail can bridge controller rails
        // facing either way, without assigning a direction to that rail.
        EnumFacing first = original.getValue(CartAssemblerBlock.SHAPE) == EnumRailDirection.EAST_WEST
                ? EnumFacing.WEST : EnumFacing.NORTH;
        return Math.max(networkPower(world, pos, first), networkPower(world, pos, first.getOpposite()));
    }
    private static int networkPower(World world, BlockPos pos, EnumFacing direction) {
        int[] distances = {0, 0}, values = {0, 0};
        for (int branch = 0; branch < 2; branch++) {
            EnumFacing scan = branch == 0 ? direction : direction.getOpposite();
            BlockPos cursor = pos;
            for (int i = 1; i <= 15; i++) {
                BlockPos next = nextRail(world, cursor, scan, direction);
                if (next == null) break;
                distances[branch] = i;
                values[branch] = world.getRedstonePowerFromNeighbors(next);
                cursor = next;
                if (values[branch] > 0) break;
            }
        }
        if (distances[0] > 8 && distances[1] > 8) return 0;
        if (values[0] == 0 && distances[1] <= 8) return values[1];
        if (values[1] == 0 && distances[0] <= 8) return values[0];
        if (values[0] != 0 && values[1] != 0)
            return (int) Math.ceil((values[0] * distances[1] + values[1] * distances[0])
                    / (double) (distances[0] + distances[1]));
        return 0;
    }
    @Override protected void updateState(IBlockState state, World world, BlockPos pos, Block neighbour) {
        if (world.isRemote) return;
        state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ControllerRailBlock)) return;
        notifyPowerChanged(world, pos);
        EnumFacing forward = direction(state);
        for (EnumFacing scan : new EnumFacing[]{forward, forward.getOpposite()}) {
            BlockPos cursor = pos;
            for (int i = 0; i < 15; i++) {
                BlockPos next = nextRail(world, cursor, scan, forward);
                if (next == null) break;
                notifyPowerChanged(world, next);
                cursor = next;
            }
        }
    }
    private static void notifyPowerChanged(World world, BlockPos pos) {
        if (world.getTileEntity(pos) instanceof ControllerRailTileEntity rail) rail.recalculate();
    }

    private static EnumFacing uphill(EnumRailDirection shape) {
        return switch (shape) {
            case ASCENDING_EAST -> EnumFacing.EAST;
            case ASCENDING_WEST -> EnumFacing.WEST;
            case ASCENDING_NORTH -> EnumFacing.NORTH;
            case ASCENDING_SOUTH -> EnumFacing.SOUTH;
            default -> null;
        };
    }

    private static BlockPos nextRail(World world, BlockPos current, EnumFacing scan, EnumFacing direction) {
        IBlockState currentState = world.getBlockState(current);
        EnumRailDirection currentShape;
        if (currentState.getBlock() instanceof ControllerRailBlock) currentShape = currentState.getValue(SHAPE);
        else if (currentState.getBlock() instanceof CartAssemblerBlock) currentShape = currentState.getValue(CartAssemblerBlock.SHAPE);
        else return null;

        // Both ends must meet at the same height, including rising and descending rails.
        int endY = current.getY() + (uphill(currentShape) == scan ? 1 : 0);
        BlockPos horizontal = current.offset(scan);
        for (int offset : new int[]{0, -1}) {
            BlockPos candidate = new BlockPos(horizontal.getX(), endY + offset, horizontal.getZ());
            if (!world.isBlockLoaded(candidate)) continue;
            IBlockState state = world.getBlockState(candidate);
            EnumRailDirection shape;
            if (state.getBlock() instanceof ControllerRailBlock) {
                if (direction(state) != direction) continue;
                shape = state.getValue(SHAPE);
            } else if (state.getBlock() instanceof CartAssemblerBlock
                    && world.getTileEntity(candidate) instanceof CartAssemblerTileEntity assembler) {
                shape = state.getValue(CartAssemblerBlock.SHAPE);
                boolean sameAxis = (shape == EnumRailDirection.EAST_WEST) == (direction.getAxis() == EnumFacing.Axis.X);
                IBlockState rail = assembler.getRailState(state);
                if (!sameAxis || rail.getBlock() instanceof ControllerRailBlock && direction(rail) != direction) continue;
            } else continue;
            if (candidate.getY() + (uphill(shape) == scan.getOpposite() ? 1 : 0) == endY) return candidate;
        }
        return null;
    }

    @Override public void onMinecartPass(World world, EntityMinecart cart, BlockPos pos) {
        if (!world.isRemote) applyToMinecart(world, cart, pos, world.getBlockState(pos), power(world, pos));
    }

    static void applyToMinecart(World world, EntityMinecart cart, BlockPos pos, IBlockState rail, int signal) {
        EnumFacing direction = direction(rail);
        if (cart instanceof EntityMinecartFurnace furnace) {
            furnace.pushX = direction.getXOffset();
            furnace.pushZ = direction.getZOffset();
        }
        BlockRailBase actualRail = (BlockRailBase) world.getBlockState(pos).getBlock();
        double maxSpeed = Math.min(actualRail.getRailMaxSpeed(world, cart, pos), cart.getCurrentCartSpeedCapOnRail());
        double target = maxSpeed * Math.max(0, Math.min(15, signal)) / 15D;
        double dot = cart.motionX * direction.getXOffset() + cart.motionZ * direction.getZOffset();
        if ((dot >= 0 || cart.motionX * cart.motionX + cart.motionZ * cart.motionZ < .0001D) && target > 0) {
            cart.motionX = direction.getXOffset() * target;
            cart.motionZ = direction.getZOffset() * target;
        } else {
            if (cart instanceof EntityMinecartFurnace furnace) furnace.pushX = furnace.pushZ = 0D;
            cart.motionX = (pos.getX() + .5D - cart.posX) / 16D;
            cart.motionY = 0D;
            cart.motionZ = (pos.getZ() + .5D - cart.posZ) / 16D;
        }
    }
    public IBlockState wrenchState(IBlockState state, World world, BlockPos pos) {
        for (Rotation rotation : new Rotation[]{Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90}) {
            IBlockState candidate = withRotation(state, rotation);
            if (stable(candidate, world, pos)) return candidate;
        }
        IBlockState reversed = state.cycleProperty(BACKWARDS);
        return stable(reversed, world, pos) ? reversed : state;
    }
    private boolean stable(IBlockState state, World world, BlockPos pos) {
        if (!world.isSideSolid(pos.down(), EnumFacing.UP)) return false;
        return !state.getValue(SHAPE).isAscending()
                || world.isSideSolid(pos.offset(pointingTowards(state)), EnumFacing.UP);
    }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) { return power(world, pos); }
}
