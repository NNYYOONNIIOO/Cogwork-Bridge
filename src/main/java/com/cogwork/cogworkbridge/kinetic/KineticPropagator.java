package com.cogwork.cogworkbridge.kinetic;

import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerBlock;
import com.cogwork.cogworkbridge.content.processing.millstone.MillstoneBlock;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock;
import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerBlock;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingBlock;
import com.cogwork.cogworkbridge.content.fluids.MechanicalPumpBlock;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Direction-aware kinetic link rules following Create 1.21.1's rotation model.
 *
 * <p>This class is only the 1.12.2 adapter. The actual connection geometry lives
 * in {@link KineticGeometry} so every known mesh layout can be tested without a
 * running client.
 */
public final class KineticPropagator {

    private KineticPropagator() {
    }

    public static double speedModifier(KineticTileEntity from, KineticTileEntity to) {
        World world = from.getWorld();
        BlockPos diff = to.getPos().subtract(from.getPos());
        EnumFacing direction = directionOf(diff);
        IBlockState fromState = world.getBlockState(from.getPos());
        IBlockState toState = world.getBlockState(to.getPos());
        if (!(fromState.getBlock() instanceof KineticBlock)
                || !(toState.getBlock() instanceof KineticBlock)) {
            return 0.0D;
        }

        KineticBlock fromDefinition = (KineticBlock) fromState.getBlock();
        KineticBlock toDefinition = (KineticBlock) toState.getBlock();
        if (isSpeedControllerGearLink(from, to)) {
            SpeedControllerTileEntity controller = from instanceof SpeedControllerTileEntity
                    ? (SpeedControllerTileEntity) from : (SpeedControllerTileEntity) to;
            return controller.getTargetSpeed() == 0 ? 0.0D : 1.0D;
        }
        boolean connectedByAxis = false;
        if (direction != null) {
            boolean shaftFrom = fromDefinition.canConnectShaftTowards(
                    world, from.getPos(), fromState, direction);
            boolean shaftTo = toDefinition.canConnectShaftTowards(
                    world, to.getPos(), toState, direction.getOpposite());
            connectedByAxis = shaftFrom && shaftTo;
        }
        if (connectedByAxis) {
            return fromDefinition.shaftPortModifier(world, from.getPos(), fromState, direction)
                    * toDefinition.shaftPortModifier(world, to.getPos(), toState, direction.getOpposite());
        }
        // A redstone-locked gantry acts as a rack-and-pinion transmission.
        // An unlocked rack moves the carriage instead of transmitting power.
        if (direction != null) {
            com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftTileEntity shaft =
                    from instanceof com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftTileEntity s ? s
                    : to instanceof com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftTileEntity s ? s : null;
            com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageTileEntity carriage =
                    from instanceof com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageTileEntity c ? c
                    : to instanceof com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageTileEntity c ? c : null;
            if (shaft != null && carriage != null && shaft.isLocked()) {
                IBlockState shaftState = world.getBlockState(shaft.getPos());
                IBlockState carriageState = world.getBlockState(carriage.getPos());
                EnumFacing mount = carriageState.getValue(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock.FACING);
                if (shaft.getPos().offset(mount).equals(carriage.getPos())) {
                    EnumFacing facing = shaftState.getValue(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftBlock.FACING);
                    int sign = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1 : -1;
                    boolean reverse = switch (facing.getAxis()) {
                        case Y -> mount == EnumFacing.NORTH || mount == EnumFacing.EAST;
                        case X -> mount == EnumFacing.DOWN || mount == EnumFacing.SOUTH;
                        case Z -> mount == EnumFacing.UP || mount == EnumFacing.WEST;
                    };
                    return reverse ? -sign : sign;
                }
            }
        }
        if (from instanceof BeltTileEntity firstBelt && to instanceof BeltTileEntity secondBelt
                && firstBelt.connectedTo(secondBelt)) return 1.0D;
        if (from instanceof ChainConveyorTileEntity first && to instanceof ChainConveyorTileEntity second
                && first.connectedTo(second)) return 1.0D;
        if (direction != null && from instanceof ChainDriveTileEntity first
                && to instanceof ChainDriveTileEntity second) {
            return first.connects(direction) && second.connects(direction.getOpposite())
                    ? first.chainModifier() / second.chainModifier() : 0.0D;
        }
        if (fromDefinition instanceof MechanicalBearingBlock
                || toDefinition instanceof MechanicalBearingBlock) {
            return 0.0D;
        }

        // Port checks above are authoritative; geometry below only handles gear meshes.
        KineticGeometry.Block fromBlock = describe(fromDefinition, fromState);
        KineticGeometry.Block toBlock = describe(toDefinition, toState);
        double modifier = KineticGeometry.rotationModifier(
                fromBlock, position(from.getPos()), toBlock, position(to.getPos()));
        if (modifier != 0.0D) {
            return modifier;
        }
        return 0.0D;
    }

    /** The controller couples to a horizontal large cog directly above it. */
    public static boolean isSpeedControllerGearLink(
            KineticTileEntity first, KineticTileEntity second) {
        KineticTileEntity controllerTile;
        KineticTileEntity cogTile;
        if (first instanceof SpeedControllerTileEntity && second.getPos().getY() == first.getPos().getY() + 1) {
            controllerTile = first;
            cogTile = second;
        } else if (second instanceof SpeedControllerTileEntity
                && first.getPos().getY() == second.getPos().getY() + 1) {
            controllerTile = second;
            cogTile = first;
        } else {
            return false;
        }

        World world = controllerTile.getWorld();
        BlockPos controllerPos = controllerTile.getPos();
        BlockPos cogPos = cogTile.getPos();
        if (world == null || !cogPos.equals(controllerPos.up())) {
            return false;
        }
        IBlockState controllerState = world.getBlockState(controllerPos);
        IBlockState cogState = world.getBlockState(cogPos);
        if (!(controllerState.getBlock() instanceof SpeedControllerBlock controller)
                || !(cogState.getBlock() instanceof CogwheelBlock cog) || !cog.isLarge()) {
            return false;
        }
        EnumFacing.Axis cogAxis = cog.getAxis(cogState);
        return cogAxis != EnumFacing.Axis.Y && cogAxis != controller.getAxis(controllerState);
    }

    public static java.util.List<BlockPos> candidatePositions(KineticTileEntity from) {
        IBlockState state = from.getWorld().getBlockState(from.getPos());
        if (!(state.getBlock() instanceof KineticBlock)) {
            return java.util.Collections.emptyList();
        }
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>();
        for (EnumFacing facing : EnumFacing.VALUES) {
            positions.add(from.getPos().offset(facing));
        }
        if (from instanceof BeltTileEntity belt) {
            for (BlockPos candidate : belt.beltNeighbors()) {
                if (!positions.contains(candidate)) positions.add(candidate);
            }
        }
        if (from instanceof ChainConveyorTileEntity chain) {
            for (BlockPos candidate : chain.connections()) if (!positions.contains(candidate)) positions.add(candidate);
        }
        KineticGeometry.Block block = describe((KineticBlock) state.getBlock(), state);
        if (block.isCog()) {
            for (KineticGeometry.Position offset : KineticGeometry.candidateOffsets(block)) {
                BlockPos candidate = from.getPos().add(offset.x(), offset.y(), offset.z());
                if (!positions.contains(candidate)) {
                    positions.add(candidate);
                }
            }
        }
        return positions;
    }

    private static KineticGeometry.Block describe(KineticBlock block, IBlockState state) {
        KineticGeometry.Axis axis = KineticGeometry.Axis.valueOf(block.getAxis(state).name());
        KineticGeometry.Kind kind = block instanceof CogwheelBlock cogwheel
                ? (cogwheel.isLarge() ? KineticGeometry.Kind.LARGE_COG : KineticGeometry.Kind.SMALL_COG)
                : block instanceof MechanicalMixerBlock || block instanceof MillstoneBlock
                || block instanceof MechanicalCrafterBlock || block instanceof MechanicalPumpBlock
                || block instanceof MechanicalArmBlock
                || block instanceof com.cogwork.cogworkbridge.content.display.DisplayBoardBlock
                ? KineticGeometry.Kind.SMALL_COG
                : KineticGeometry.Kind.SHAFT;

        return new KineticGeometry.Block(axis, kind,
                java.util.EnumSet.noneOf(KineticGeometry.Face.class));
    }

    private static EnumFacing directionOf(BlockPos diff) {
        int nonzero = 0;
        for (int coordinate : new int[]{diff.getX(), diff.getY(), diff.getZ()}) {
            if (coordinate == 0) {
                continue;
            }
            nonzero++;
            if (Math.abs(coordinate) != 1) {
                return null;
            }
        }
        return nonzero == 1
                ? EnumFacing.getFacingFromVector(diff.getX(), diff.getY(), diff.getZ())
                : null;
    }

    private static KineticGeometry.Position position(BlockPos pos) {
        return new KineticGeometry.Position(pos.getX(), pos.getY(), pos.getZ());
    }

}
