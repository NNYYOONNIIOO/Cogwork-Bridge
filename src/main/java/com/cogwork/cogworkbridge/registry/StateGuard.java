package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidValveBlock;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmBlock;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime assertion that the "metadata is orientation only" rule holds.
 *
 * <p>1.12.2 caps a block at 16 metadata values, and Roughly Enough IDs extends
 * block IDs and chunk palettes but <em>not</em> per-block metadata. Rather than
 * depending on that mod, Cogwork Bridge keeps every block inside the vanilla budget:
 * at most 16 states, built from direction and axis properties or explicitly
 * recognized orientation flags.
 *
 * <p>{@link #verifyAll()} runs once during pre-init and throws on violation, so a
 * mistake fails fast in development instead of silently corrupting saves.
 */
public final class StateGuard {

    public static final int VANILLA_METADATA_LIMIT = 16;

    private StateGuard() {
    }

    /**
     * @return every violation found, empty when the invariant holds
     */
    public static List<String> findViolations() {
        List<String> problems = new ArrayList<>();

        for (Block block : BlockFamily.all()) {
            String name = String.valueOf(block.getRegistryName());

            int stateCount = block.getBlockState().getValidStates().size();
            // Farmer's Future Delight persists this injected property through
            // its own waterlogged state carrier, independently of our meta bits.
            for (IProperty<?> property : block.getBlockState().getProperties())
                if (isFutureDelightWaterlogged(property) || isPaletteActualState(block, property)) stateCount /= property.getAllowedValues().size();
            if (stateCount > VANILLA_METADATA_LIMIT) {
                problems.add(name + ": " + stateCount + " states exceeds the "
                        + VANILLA_METADATA_LIMIT + "-metadata budget");
            }

            for (IProperty<?> property : block.getBlockState().getProperties()) {
                if (isFutureDelightWaterlogged(property) || isPaletteActualState(block, property)) continue;
                boolean whistleMount = block instanceof com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock && property == com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock.WALL;
                boolean sequencerOrientation = block instanceof SequencedGearshiftBlock
                        && property == SequencedGearshiftBlock.VERTICAL;
                boolean valveOrientation = block instanceof FluidValveBlock
                        && property == FluidValveBlock.AXIS_ALONG_FIRST_COORDINATE;
                boolean smartPipeOrientation = block instanceof SmartFluidPipeBlock
                        && property == SmartFluidPipeBlock.ATTACHMENT;
                boolean armOrientation = block instanceof MechanicalArmBlock && property == MechanicalArmBlock.CEILING;
                boolean gaugeOrientation = block instanceof com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeBlock
                        && property == com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeBlock.AXIS_ALONG_FIRST;
                boolean pistonOrientation = block instanceof com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock
                        && property == com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock.AXIS_ALONG_FIRST;
                boolean gantryOrientation = block instanceof com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock
                        && property == com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock.AXIS_ALONG_FIRST;
                boolean trackOrientation = block instanceof com.cogwork.cogworkbridge.content.trains.track.TrackBlock
                        && property == com.cogwork.cogworkbridge.content.trains.track.TrackBlock.SHAPE;
                boolean railOrientation = block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock
                        && property == com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock.SHAPE;
                boolean controllerRailOrientation = block instanceof com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailBlock
                        && (property == com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailBlock.SHAPE
                            || property == com.cogwork.cogworkbridge.content.contraptions.mounted.ControllerRailBlock.BACKWARDS);
                boolean nixieOrientation = block instanceof com.cogwork.cogworkbridge.content.display.NixieTubeBlock
                        && property == com.cogwork.cogworkbridge.content.display.NixieTubeBlock.MOUNT;
                boolean leverOrientation = block instanceof com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock
                        && property == com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock.MOUNT;
                boolean detectorOrientation = block instanceof com.cogwork.cogworkbridge.content.redstone.DetectorBlock
                        && property == com.cogwork.cogworkbridge.content.redstone.DetectorBlock.TARGET;
                boolean placardOrientation = block instanceof com.cogwork.cogworkbridge.content.redstone.PlacardBlock
                        && property == com.cogwork.cogworkbridge.content.redstone.PlacardBlock.MOUNT;
                boolean scaffoldActualState = block instanceof com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock
                        && property == com.cogwork.cogworkbridge.content.decoration.MetalScaffoldingBlock.BOTTOM;
                boolean bellMount = block instanceof com.cogwork.cogworkbridge.content.equipment.BellBlock
                        && property == com.cogwork.cogworkbridge.content.equipment.BellBlock.MOUNT;
                boolean paletteHalf = (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Stairs
                        || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Slab)
                        && property == net.minecraft.block.BlockStairs.HALF
                        || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteDoor && property == net.minecraft.block.BlockDoor.HALF
                        || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteTrapdoor && property == net.minecraft.block.BlockTrapDoor.HALF
                        || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.CopycatBlock.Step && property == net.minecraft.block.BlockStairs.HALF;
                boolean bracketOrientation = block instanceof com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment.RenderBlock
                        && property == com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment.RenderBlock.FIRST;
                if (!isOrientationProperty(property) && !whistleMount && !detectorOrientation && !placardOrientation && !armOrientation && !sequencerOrientation && !valveOrientation
                        && !smartPipeOrientation && !gaugeOrientation && !nixieOrientation && !leverOrientation && !pistonOrientation && !railOrientation && !trackOrientation && !gantryOrientation && !controllerRailOrientation && !scaffoldActualState && !bellMount && !paletteHalf && !bracketOrientation) {
                    problems.add(name + ": property " + property.getName()
                            + " is not an orientation property; make it a separate registry name or TileEntity state");
                }
            }
        }

        return problems;
    }

    public static boolean isOrientationProperty(IProperty<?> property) {
        if (property instanceof PropertyDirection) {
            return true;
        }
        // Axis properties are PropertyEnum<EnumFacing.Axis>; accept only that value type.
        if (property instanceof PropertyEnum) {
            return ((PropertyEnum<?>) property).getValueClass() == EnumFacing.Axis.class;
        }
        return false;
    }

    private static boolean isFutureDelightWaterlogged(IProperty<?> property) {
        return "waterlogged".equals(property.getName()) && property.getAllowedValues().size() == 2
                && property.getClass().getName().startsWith("xy177.farmersfuturedelight.");
    }

    private static boolean isPaletteActualState(Block block, IProperty<?> property) {
        if (block instanceof com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock)
            return property == com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock.ACTIVE
                    || property == com.cogwork.cogworkbridge.content.trains.track.RailwayTargetBlock.CROSS;
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderShaftBlock)
            return property == com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock.TOP
                    || property == com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock.BOTTOM;
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteDoor)
            return property == net.minecraft.block.BlockDoor.OPEN || property == net.minecraft.block.BlockDoor.POWERED || property == net.minecraft.block.BlockDoor.HINGE;
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteTrapdoor)
            return property == net.minecraft.block.BlockTrapDoor.OPEN;
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock)
            return property == com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock.X
                    || property == com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock.Z
                    || property == com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock.TOP
                    || property == com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock.BOTTOM;
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Stairs)
            return property == net.minecraft.block.BlockStairs.SHAPE;
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Wall)
            return property == net.minecraft.block.BlockWall.VARIANT || property == net.minecraft.block.BlockWall.UP
                    || property == net.minecraft.block.BlockWall.NORTH || property == net.minecraft.block.BlockWall.SOUTH
                    || property == net.minecraft.block.BlockWall.EAST || property == net.minecraft.block.BlockWall.WEST;
        if (block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Pane)
            return property == net.minecraft.block.BlockPane.NORTH || property == net.minecraft.block.BlockPane.SOUTH
                    || property == net.minecraft.block.BlockPane.EAST || property == net.minecraft.block.BlockPane.WEST;
        return false;
    }

    /** Fails fast during pre-init when a block violates the contract. */
    public static void verifyAll() {
        List<String> problems = findViolations();
        if (problems.isEmpty()) {
            CogworkBridge.LOGGER.info("StateGuard: {} block(s) within the metadata budget", BlockFamily.count());
            return;
        }
        for (String problem : problems) {
            CogworkBridge.LOGGER.error("StateGuard violation -> {}", problem);
        }
        throw new IllegalStateException("Block metadata contract violated by " + problems.size() + " block(s)");
    }
}
