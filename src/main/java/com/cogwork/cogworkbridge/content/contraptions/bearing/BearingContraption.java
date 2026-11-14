package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.content.contraptions.glue.GlueBox;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueSelectionRules;
import com.cogwork.cogworkbridge.content.contraptions.glue.GridPos;
import com.cogwork.cogworkbridge.content.contraptions.glue.SuperGlueEntity;
import com.cogwork.cogworkbridge.content.contraptions.actors.AttachedActorBlock;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisBlock;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisConnectivity;
import com.cogwork.cogworkbridge.content.contraptions.chassis.StickerBlock;
import com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock;
import com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftBlock;
import com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyBlock;
import com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock;
import com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartAnchorBlock;
import com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock;
import com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.piston.PistonPoleBlock;
import com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyBlock;
import com.cogwork.cogworkbridge.content.contraptions.pulley.RopePulleyTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.pulley.RopeSegmentBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidTankBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueAreaRemovedPacket;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueAreaUpdatePacket;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Contraption capture rooted at the block in front of a bearing.
 *
 * <p>Structure connectivity follows the same broad rules as Create's assembly
 * traversal: attached blocks and glued neighbours can be consumed, while the
 * bearing itself remains in the world. The root block is always part of the
 * structure; unrelated face-adjacent blocks are not.</p>
 */
public final class BearingContraption {

    public static final int MAX_BLOCKS = 2048;
    private static final int AIR_FLAGS = 3;
    private static final int GLUE_FORMAT_CELLS_RELATIVE = 3;

    private final Map<BlockPos, Snapshot> blocks = new LinkedHashMap<>();
    private final Map<Integer, CapturedGlue> capturedGlue = new LinkedHashMap<>();
    private Set<GlueEdge> retainedGlueEdges;
    private BlockPos anchor;
    private float capturedStress;
    private long contentVersion;
    private NBTTagCompound actorControls;
    private EnumFacing forcedMovement;

    private BearingContraption() {
    }

    NBTTagCompound actorControls() { return actorControls == null ? null : actorControls.copy(); }
    void setActorControls(NBTTagCompound data) { actorControls = data == null ? null : data.copy(); }

    public static BearingContraption capture(World world, BlockPos origin, BlockPos anchor) {
        return capture(world, origin, anchor, selected -> {
        });
    }

    public static BearingContraption capture(
            World world, BlockPos origin, BlockPos anchor,
            Consumer<Set<BlockPos>> beforeSnapshot) {
        return capture(world, origin, anchor, Set.of(), beforeSnapshot);
    }

    static BearingContraption capture(
            World world, BlockPos origin, BlockPos anchor, Set<BlockPos> excluded,
            Consumer<Set<BlockPos>> beforeSnapshot) {
        if (origin.equals(anchor) || excluded.contains(anchor)
                || !world.isBlockLoaded(anchor) || world.isAirBlock(anchor)) {
            return null;
        }
        List<SuperGlueEntity> glueAreas = GlueSelectionRules.loadedAreas(world);
        Set<BlockPos> selection = connectedFrom(world, origin, anchor, glueAreas, excluded, null, false);
        if (selection.isEmpty() || selection.size() > MAX_BLOCKS || !selection.contains(anchor)) {
            return null;
        }
        return captureSelection(world, anchor, selection, glueAreas, beforeSnapshot, false, false);
    }

    /** A mounted structure can include a second cart assembler as an anchor. */
    public static BearingContraption captureMounted(World world, BlockPos origin, BlockPos anchor,
                                                    Consumer<Set<BlockPos>> beforeSnapshot) {
        if (!world.isBlockLoaded(anchor) || world.isAirBlock(anchor)) return null;
        List<SuperGlueEntity> glueAreas = GlueSelectionRules.loadedAreas(world);
        Set<BlockPos> selection = connectedFrom(world, origin, anchor, glueAreas, Set.of(), null, true);
        if (selection.isEmpty() || selection.size() > MAX_BLOCKS || !selection.contains(anchor)) return null;
        return captureSelection(world, anchor, selection, glueAreas, beforeSnapshot, false, true);
    }

    /** Captures each contacted component while keeping a separate mechanical anchor. */
    public static BearingContraption captureLinear(World world, BlockPos controller, BlockPos anchor,
                                                   java.util.Collection<BlockPos> seeds,
                                                   Consumer<Set<BlockPos>> beforeSnapshot) {
        return captureLinear(world, controller, anchor, seeds, null, beforeSnapshot);
    }

    public static BearingContraption captureLinear(World world, BlockPos controller, BlockPos anchor,
                                                   java.util.Collection<BlockPos> seeds, EnumFacing forcedMovement,
                                                   Consumer<Set<BlockPos>> beforeSnapshot) {
        if (!world.isBlockLoaded(anchor)) return null;
        List<SuperGlueEntity> glueAreas = GlueSelectionRules.loadedAreas(world);
        Set<BlockPos> selected = new LinkedHashSet<>();
        for (BlockPos seed : seeds) {
            if (selected.contains(seed)) continue;
            if (!world.isBlockLoaded(seed)) return null;
            if (!world.isAirBlock(seed)) selected.addAll(connectedFrom(world, controller, seed, glueAreas, Set.of(), forcedMovement, false));
            if (selected.size() > MAX_BLOCKS) return null;
        }
        BearingContraption result = captureSelection(world, anchor, selected, glueAreas, beforeSnapshot, true, false);
        if (result != null) result.forcedMovement = forcedMovement;
        return result;
    }

    private static BearingContraption captureSelection(World world, BlockPos anchor, Set<BlockPos> selection,
                                                       List<SuperGlueEntity> glueAreas,
                                                       Consumer<Set<BlockPos>> beforeSnapshot, boolean allowEmpty,
                                                       boolean includeCartAssemblers) {
        // Reject the entire assembly before closing containers or removing anything.
        // Hardness and piston reaction are the 1.12 equivalents of the upstream
        // movement-allowed checks for unbreakable or immovable blocks.
        for (BlockPos selected : selection) {
            IBlockState state = world.getBlockState(selected);
            if (!movementAllowed(world, selected, state)) return null;
        }
        if (beforeSnapshot != null) {
            beforeSnapshot.accept(selection);
        }

        // A suspended chain cannot remain attached to a wheel left behind.
        // Detach those links before snapshotting so their items are not copied.
        for (BlockPos selected : selection) {
            TileEntity tile = world.getTileEntity(selected);
            if (tile instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity wheel) {
                for (BlockPos target : new ArrayList<>(wheel.connections()))
                    if (!selection.contains(target)) wheel.disconnect(target, null);
            } else if (tile instanceof com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity port
                    && port.chainTarget() != null && (!selection.contains(port.chainTarget())
                        || port.chainEnd() != null && !selection.contains(port.chainEnd()))) {
                port.unbindChain();
            }
        }

        BearingContraption result = new BearingContraption();
        result.anchor = anchor.toImmutable();
        Set<GridPos> selectedGrid = new java.util.LinkedHashSet<>();
        for (BlockPos selected : selection) {
            selectedGrid.add(GlueSelectionRules.grid(selected));
        }
        for (SuperGlueEntity glue : glueAreas) {
            java.util.Set<GridPos> occupied = new java.util.LinkedHashSet<>();
            for (GridPos pos : glue.getGlueVolume()) {
                if (selectedGrid.contains(pos)) {
                    occupied.add(pos);
                }
            }
            GlueBox box = glue.getGlueBox().minimizeTo(occupied);
            if (box == null) {
                continue;
            }
            Set<GridPos> relativeCells = new java.util.LinkedHashSet<>();
            for (GridPos pos : occupied) {
                relativeCells.add(new GridPos(
                        pos.x() - anchor.getX(),
                        pos.y() - anchor.getY(),
                        pos.z() - anchor.getZ()));
            }
            GlueBox relativeBox = GlueBox.of(
                    box.minX() - anchor.getX(),
                    box.minY() - anchor.getY(),
                    box.minZ() - anchor.getZ(),
                    box.maxX() - anchor.getX(),
                    box.maxY() - anchor.getY(),
                    box.maxZ() - anchor.getZ());
            if (relativeBox.minimizeTo(relativeCells) == null) {
                continue;
            }
            result.capturedGlue.put(glue.getEntityId(),
                    new CapturedGlue(relativeBox, relativeCells));
        }
        for (BlockPos pos : selection) {
            IBlockState state = world.getBlockState(pos);
            if (!isMovable(state, includeCartAssemblers)) {
                continue;
            }
            NBTTagCompound tile = null;
            TileEntity te = world.getTileEntity(pos);
            if (te != null) {
                tile = te.writeToNBT(new NBTTagCompound());
            }
            float stressImpact = 0.0F;
            if (te instanceof com.cogwork.cogworkbridge.kinetic.KineticTileEntity kinetic) {
                stressImpact = kinetic.getStressImpact();
                result.capturedStress += stressImpact;
            }
            result.blocks.put(pos.toImmutable(),
                    new Snapshot(pos.toImmutable(), state, tile, stressImpact));
        }
        return result.blocks.isEmpty() && !allowEmpty ? null : result;
    }

    private static Set<BlockPos> connectedFrom(World world, BlockPos origin, BlockPos anchor,
                                                List<SuperGlueEntity> glueAreas,
                                                Set<BlockPos> excluded, EnumFacing forcedMovement,
                                                boolean includeCartAssemblers) {
        IBlockState rootState = world.getBlockState(anchor);
        BlockPos bearingFront = rootState.getBlock() instanceof MechanicalBearingBlock
                && rootState.getPropertyKeys().contains(MechanicalBearingBlock.FACING)
                ? anchor.offset(rootState.getValue(MechanicalBearingBlock.FACING)) : null;
        ChassisConnectivity.Access access = new ChassisConnectivity.Access() {
            @Override public IBlockState state(BlockPos candidate) {
                return candidate.equals(origin) || candidate.equals(bearingFront) || excluded.contains(candidate)
                        || !world.isBlockLoaded(candidate) ? null : world.getBlockState(candidate);
            }
            @Override public NBTTagCompound tileData(BlockPos candidate) {
                TileEntity tile = world.getTileEntity(candidate);
                return tile == null ? null : tile.writeToNBT(new NBTTagCompound());
            }
            @Override public boolean terminal(BlockPos candidate) {
                IBlockState state = state(candidate);
                return !candidate.equals(anchor) && state != null
                        && (state.getBlock() instanceof MechanicalBearingBlock
                        || includeCartAssemblers && state.getBlock() instanceof CartAssemblerBlock);
            }
            @Override public EnumFacing forcedMovement() { return forcedMovement; }
        };
        Set<BlockPos> result = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> queued = new LinkedHashSet<>();
        frontier.add(anchor);
        queued.add(anchor);
        while (!frontier.isEmpty() && result.size() <= MAX_BLOCKS) {
            BlockPos current = frontier.removeFirst();
            IBlockState currentState = access.state(current);
            if (!isMovable(currentState, includeCartAssemblers) || !result.add(current) || access.terminal(current)) continue;
            if (currentState.getBlock() instanceof ChassisBlock) {
                ChassisConnectivity.collect(access, current, (first, second) -> {
                    if (queued.add(second)) frontier.addLast(second);
                });
            }
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos next = current.offset(side);
                IBlockState nextState = access.state(next);
                if (!isMovable(nextState, includeCartAssemblers) || queued.contains(next)) continue;
                if (!movementAllowed(world, next, nextState)) {
                    if (side == forcedMovement) result.add(next);
                    continue;
                }
                if (isGluedWorldPair(glueAreas, current, next)
                        || isConnectedTankPair(world, current, next)
                        || isStickerPair(access, current, currentState, next, nextState)
                        || isStickerPair(access, next, nextState, current, currentState)
                        || isStickyBlockPair(access, current, currentState, next, nextState, side)
                        || forcedMovement == side && !ChassisConnectivity.isNotSupportive(access, current, currentState, side)
                        || isAttachedPair(current, currentState, next, nextState)
                        || isMechanicalAttachment(current, currentState, next, nextState,
                                access::state, includeCartAssemblers)) {
                    queued.add(next);
                    frontier.addLast(next);
                }
            }
        }
        return result;
    }

    private static boolean movementAllowed(World world, BlockPos pos, IBlockState state) {
        if (state.getBlockHardness(world, pos) < 0F
                || state.getPushReaction() == net.minecraft.block.material.EnumPushReaction.BLOCK) return false;
        TileEntity tile = world.getTileEntity(pos);
        return !(tile instanceof MechanicalPistonTileEntity piston && piston.visualState() == 2)
                && !(tile instanceof ClockworkBearingTileEntity clock && clock.isRunning())
                && !(tile instanceof RopePulleyTileEntity pulley && pulley.isRunning())
                && !(tile instanceof ElevatorPulleyTileEntity elevator && elevator.isRunning());
    }

    private static boolean isMovable(IBlockState state, boolean includeCartAssemblers) {
        return state != null && state.getBlock() != Blocks.AIR
                && (state.getMaterial().blocksMovement() || includeCartAssemblers
                && state.getBlock() instanceof CartAssemblerBlock);
    }

    private static boolean isGluedWorldPair(List<SuperGlueEntity> glueAreas,
                                            BlockPos first, BlockPos second) {
        for (SuperGlueEntity glue : glueAreas) {
            if (glue.canBondAt(first) && glue.canBondAt(second)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAttachedPair(BlockPos first, IBlockState firstState,
                                          BlockPos second, IBlockState secondState) {
        return firstState.getBlock() instanceof SailBlock && secondState.getBlock() instanceof SailBlock
                || isActorAttachedTo(first, firstState, second)
                || isActorAttachedTo(second, secondState, first);
    }

    private static boolean isStickyBlockPair(ChassisConnectivity.Access access,
                                             BlockPos first, IBlockState state,
                                             BlockPos second, IBlockState other, EnumFacing side) {
        if (!state.getBlock().isStickyBlock(state) && !other.getBlock().isStickyBlock(other)) return false;
        if (state.getPushReaction() == net.minecraft.block.material.EnumPushReaction.PUSH_ONLY
                || other.getPushReaction() == net.minecraft.block.material.EnumPushReaction.PUSH_ONLY) return false;
        return !ChassisConnectivity.isNotSupportive(access, first, state, side)
                && !ChassisConnectivity.isNotSupportive(access, second, other, side.getOpposite());
    }

    /** Directional links mirror the extra neighbours visited during upstream assembly. */
    private static boolean isMechanicalAttachment(BlockPos first, IBlockState state,
                                                   BlockPos second, IBlockState other,
                                                   Function<BlockPos, IBlockState> stateAt,
                                                   boolean includeCartAssemblers) {
        EnumFacing side = null;
        for (EnumFacing candidate : EnumFacing.VALUES) {
            if (first.offset(candidate).equals(second)) {
                side = candidate;
                break;
            }
        }
        if (side == null) return false;
        // A train bogey carries the block above it and can also be joined along its axle.
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.trains.track.BogeyBlock
                && (side == EnumFacing.UP || side.getAxis() == state.getValue(com.cogwork.cogworkbridge.content.trains.track.BogeyBlock.AXIS))) return true;
        if (other.getBlock() instanceof com.cogwork.cogworkbridge.content.trains.track.BogeyBlock
                && (side == EnumFacing.DOWN || side.getAxis() == other.getValue(com.cogwork.cogworkbridge.content.trains.track.BogeyBlock.AXIS))) return true;
        if (side == EnumFacing.DOWN && (other.getBlock() instanceof MinecartAnchorBlock
                || includeCartAssemblers && other.getBlock() instanceof CartAssemblerBlock)) return true;
        if (side == EnumFacing.DOWN && (isPulley(state) || state.getBlock() instanceof RopeSegmentBlock
                && hasPulleyAbove(first, stateAt))) return true;

        if (state.getBlock() instanceof MechanicalPistonBlock piston) {
            EnumFacing facing = state.getValue(MechanicalPistonBlock.FACING);
            if (side == facing.getOpposite() && other.getBlock() instanceof PistonPoleBlock pole
                    && !pole.isHead() && other.getValue(PistonPoleBlock.FACING).getAxis() == facing.getAxis()) return true;
            return side == facing && (piston.isSticky() || pistonExtended(first, facing, stateAt));
        }
        if (state.getBlock() instanceof PistonPoleBlock pole) {
            EnumFacing facing = state.getValue(PistonPoleBlock.FACING);
            if (pole.isHead()) {
                if (side == facing && state.getBlock() == AllBlocks.STICKY_PISTON_HEAD) return true;
                if (side != facing.getOpposite()) return false;
                if (other.getBlock() instanceof PistonPoleBlock adjacent && !adjacent.isHead())
                    return other.getValue(PistonPoleBlock.FACING).getAxis() == facing.getAxis();
                return other.getBlock() instanceof MechanicalPistonBlock
                        && other.getValue(MechanicalPistonBlock.FACING) == facing
                        && pistonExtended(second, facing, stateAt);
            }
            if (side.getAxis() != facing.getAxis()) return false;
            if (other.getBlock() instanceof PistonPoleBlock)
                return other.getValue(PistonPoleBlock.FACING).getAxis() == facing.getAxis();
            if (other.getBlock() instanceof MechanicalPistonBlock) {
                EnumFacing pistonFacing = other.getValue(MechanicalPistonBlock.FACING);
                return pistonFacing == side || pistonFacing == side.getOpposite()
                        && pistonExtended(second, pistonFacing, stateAt);
            }
        }
        if (state.getBlock() instanceof GantryShaftBlock) {
            EnumFacing facing = state.getValue(GantryShaftBlock.FACING);
            return side.getAxis() == facing.getAxis() && other.getBlock() instanceof GantryShaftBlock
                    && other.getValue(GantryShaftBlock.FACING) == facing
                    || other.getBlock() instanceof GantryCarriageBlock
                    && other.getValue(GantryCarriageBlock.FACING) == side;
        }
        if (state.getBlock() instanceof GantryCarriageBlock carriage) {
            EnumFacing facing = state.getValue(GantryCarriageBlock.FACING);
            if (side == facing.getOpposite()) return other.getBlock() instanceof GantryShaftBlock;
            return side.getAxis() == carriage.getAxis(state) && other.getBlock() instanceof GantryShaftBlock
                    && other.getValue(GantryShaftBlock.FACING).getAxis() == side.getAxis();
        }
        return false;
    }

    private static boolean pistonExtended(BlockPos base, EnumFacing facing,
                                           Function<BlockPos, IBlockState> stateAt) {
        IBlockState front = stateAt.apply(base.offset(facing));
        return front != null && front.getBlock() instanceof PistonPoleBlock
                && front.getValue(PistonPoleBlock.FACING).getAxis() == facing.getAxis();
    }

    private static boolean isPulley(IBlockState state) {
        return state != null && (state.getBlock() instanceof RopePulleyBlock
                || state.getBlock() instanceof ElevatorPulleyBlock);
    }

    private static boolean hasPulleyAbove(BlockPos segment, Function<BlockPos, IBlockState> stateAt) {
        BlockPos cursor = segment;
        for (int i = 0; i < 384; i++) {
            IBlockState above = stateAt.apply(cursor = cursor.up());
            if (isPulley(above)) return true;
            if (above == null || !(above.getBlock() instanceof RopeSegmentBlock)) return false;
        }
        return false;
    }

    private static boolean isStickerPair(ChassisConnectivity.Access access,
                                         BlockPos stickerPos, IBlockState stickerState,
                                         BlockPos otherPos, IBlockState otherState) {
        if (!(stickerState.getBlock() instanceof StickerBlock)) return false;
        NBTTagCompound tile = access.tileData(stickerPos);
        NBTTagCompound otherTile = otherState.getBlock() instanceof StickerBlock
                ? access.tileData(otherPos) : null;
        return isStickerPair(stickerPos, stickerState, tile, otherPos, otherState, otherTile);
    }

    private static boolean isStickerPair(BlockPos stickerPos, IBlockState stickerState,
                                         NBTTagCompound stickerTile, BlockPos otherPos,
                                         IBlockState otherState, NBTTagCompound otherTile) {
        if (!(stickerState.getBlock() instanceof StickerBlock) || stickerTile == null
                || !stickerTile.getBoolean("Extended")) return false;
        EnumFacing facing = stickerState.getValue(StickerBlock.FACING);
        if (!stickerPos.offset(facing).equals(otherPos)) return false;
        EnumFacing targetSide = facing.getOpposite();
        if (otherState.getBlock() instanceof StickerBlock && otherTile != null
                && !otherTile.getBoolean("Extended")
                && otherState.getValue(StickerBlock.FACING) == targetSide) return false;
        if (otherState.getBlock() instanceof MechanicalBearingBlock
                && otherState.getValue(MechanicalBearingBlock.FACING) == targetSide) return false;
        if (otherState.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillBlock
                && otherState.getValue(com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillBlock.FACING) == targetSide) return false;
        if (otherState.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock
                && otherState.getValue(com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock.FACING) == targetSide) return false;
        return !(otherState.getBlock() instanceof AttachedActorBlock)
                || otherState.getValue(AttachedActorBlock.FACING) != targetSide;
    }


    private static boolean isConnectedTankPair(World world, BlockPos first, BlockPos second) {
        TileEntity firstTile = world.getTileEntity(first);
        TileEntity secondTile = world.getTileEntity(second);
        return firstTile instanceof FluidTankTileEntity firstTank
                && secondTile instanceof FluidTankTileEntity secondTank
                && world.getBlockState(first).getBlock() instanceof FluidTankBlock
                && world.getBlockState(second).getBlock() == world.getBlockState(first).getBlock()
                && firstTank.getControllerPos().equals(secondTank.getControllerPos());
    }

    private static boolean isCapturedTankPair(Snapshot first, Snapshot second) {
        if (first == null || second == null || first.tile == null || second.tile == null
                || !(first.state.getBlock() instanceof FluidTankBlock)
                || first.state.getBlock() != second.state.getBlock()) return false;
        return first.pos.add(FluidTankTileEntity.controllerOffset(first.tile)).equals(
                second.pos.add(FluidTankTileEntity.controllerOffset(second.tile)));
    }

    private static boolean isActorAttachedTo(BlockPos actor, IBlockState state, BlockPos support) {
        return state.getBlock() instanceof AttachedActorBlock
                && actor.offset(state.getValue(AttachedActorBlock.FACING).getOpposite()).equals(support);
    }

    private static BlockPos toBlockPos(GridPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    public Map<BlockPos, Snapshot> blocks() {
        return blocks;
    }

    public long contentVersion() {
        return contentVersion;
    }

    public NBTTagCompound tileDataAt(BlockPos pos) {
        Snapshot snapshot = blocks.get(pos);
        return snapshot == null || snapshot.tile == null
                ? null : snapshot.tile.copy();
    }


    public BlockPos anchor() {
        return anchor == null ? BlockPos.ORIGIN : anchor;
    }

    public void alignAnchor(BlockPos canonicalAnchor) {
        if (canonicalAnchor == null) {
            return;
        }
        BlockPos next = canonicalAnchor.toImmutable();
        if (!next.equals(anchor)) {
            retainedGlueEdges = null;
            contentVersion++;
        }
        anchor = next;
    }

    public float capturedStress() {
        return capturedStress;
    }

    void preserveControllerGlue(World world, BlockPos controller) {
        GridPos localController = GlueSelectionRules.grid(controller.subtract(anchor()));
        for (Map.Entry<Integer, CapturedGlue> entry : capturedGlue.entrySet()) {
            Entity entity = world.getEntityByID(entry.getKey());
            if (!(entity instanceof SuperGlueEntity glue)
                    || !glue.canBondAt(controller) || !glue.canBondAt(anchor())) {
                continue;
            }
            CapturedGlue captured = entry.getValue();
            Set<GridPos> cells = new LinkedHashSet<>(captured.cells);
            cells.add(localController);
            GlueBox box = captured.box;
            GlueBox expanded = GlueBox.of(
                    Math.min(box.minX(), localController.x()),
                    Math.min(box.minY(), localController.y()),
                    Math.min(box.minZ(), localController.z()),
                    Math.max(box.maxX(), localController.x() + 1),
                    Math.max(box.maxY(), localController.y() + 1),
                    Math.max(box.maxZ(), localController.z() + 1));
            entry.setValue(new CapturedGlue(expanded, Set.copyOf(cells)));
        }
    }

    /** Immutable copies of glue boxes stored relative to the contraption anchor. */
    public List<GlueBox> capturedGlueBoxes() {
        List<GlueBox> boxes = new ArrayList<>(capturedGlue.size());
        for (CapturedGlue captured : capturedGlue.values()) {
            GlueBox box = captured.box;
            boxes.add(GlueBox.of(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()));
        }
        return boxes;
    }

    public BlockPos localPos(Snapshot snapshot, BlockPos bearing) {
        return snapshot.pos.subtract(anchor());
    }

    public BlockPos localPos(Snapshot snapshot) {
        return snapshot.pos.subtract(anchor());
    }

    public boolean contains(BlockPos pos) {
        return blocks.containsKey(pos);
    }

    public Optional<Snapshot> snapshotAtLocal(BlockPos localPos) {
        return Optional.ofNullable(blocks.get(anchor().add(localPos)));
    }

    /**
     * Removes one captured block, then detaches every remaining block that is
     * no longer connected to the bearing anchor through captured glue.
     */
    public RemovalResult removeAtLocal(BlockPos localPos) {
        // The bearing cannot retain any part once its anchor is gone. Keep
        // the surviving snapshot intact for the entity's full disassembly.
        if (BlockPos.ORIGIN.equals(localPos) || !blocks.containsKey(anchor())) {
            Snapshot removed = removeSingleAtLocal(localPos);
            return removed == null ? null
                    : new RemovalResult(removed, List.of(), List.of());
        }
        BlockPos absolute = anchor().add(localPos);
        Snapshot removed = blocks.remove(absolute);
        if (removed == null) {
            return null;
        }
        contentVersion++;

        Set<BlockPos> retained = connectedFromAnchor();
        Set<BlockPos> detachedPositions = new LinkedHashSet<>(blocks.keySet());
        detachedPositions.removeAll(retained);
        List<CapturedGlue> detachedGlue = detachedGlueFor(detachedPositions);
        Set<GlueEdge> glueEdges = retainedGlueEdges();
        List<Snapshot> detached = new ArrayList<>();
        for (BlockPos position : detachedPositions) {
            Snapshot snapshot = blocks.get(position);
            if (snapshot != null) {
                detached.add(snapshot);
            }
        }
        Set<GlueEdge> retainedGlue = new LinkedHashSet<>();
        for (BlockPos first : retained) {
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos second = first.offset(facing);
                if (retained.contains(second)
                        && glueEdges.contains(canonicalPair(first, second))) {
                    retainedGlue.add(canonicalPair(first, second));
                }
            }
        }

        blocks.keySet().retainAll(retained);
        retainedGlueEdges = retainedGlue;
        capturedStress = 0.0F;
        for (Snapshot snapshot : blocks.values()) {
            capturedStress += snapshot.stressImpact;
        }
        capturedGlue.replaceAll((id, captured) -> {
            Set<GridPos> cells = new LinkedHashSet<>(captured.cells);
            cells.remove(GlueSelectionRules.grid(
                    absolute.subtract(anchor())));
            GlueBox box = captured.box.minimizeTo(cells);
            return box == null ? null : new CapturedGlue(box, Set.copyOf(cells));
        });
        capturedGlue.values().removeIf(java.util.Objects::isNull);
        return new RemovalResult(removed, List.copyOf(detached),
                List.copyOf(detachedGlue));
    }

    /**
     * Removes one block without cascading. The server uses this for an anchor
     * removal before full disassembly; clients apply authoritative block packets.
     */
    public Snapshot removeSingleAtLocal(BlockPos localPos) {
        retainedGlueEdges = null;
        BlockPos absolute = anchor().add(localPos);
        Snapshot removed = blocks.remove(absolute);
        if (removed == null) {
            return null;
        }
        contentVersion++;
        capturedStress = 0.0F;
        for (Snapshot snapshot : blocks.values()) {
            capturedStress += snapshot.stressImpact;
        }
        capturedGlue.replaceAll((id, captured) -> {
            Set<GridPos> cells = new LinkedHashSet<>(captured.cells);
            cells.remove(GlueSelectionRules.grid(
                    absolute.subtract(anchor())));
            GlueBox box = captured.box.minimizeTo(cells);
            return box == null ? null : new CapturedGlue(box, Set.copyOf(cells));
        });
        capturedGlue.values().removeIf(java.util.Objects::isNull);
        return removed;
    }

    private Set<BlockPos> connectedFromAnchor() {
        Set<GridPos> graph = new LinkedHashSet<>();
        for (BlockPos pos : blocks.keySet()) {
            graph.add(GlueSelectionRules.grid(pos));
        }
        Set<BlockPos> result = new LinkedHashSet<>();
        GridPos anchor = GlueSelectionRules.grid(anchor());
        if (graph.contains(anchor)) {
            collectConnected(anchor, graph, result);
        }
        return result;
    }

    private void collectConnected(GridPos root, Set<GridPos> graph,
                                  Set<BlockPos> result) {
        collectConnected(Set.of(root), graph, result);
    }

    private void collectConnected(Set<GridPos> roots, Set<GridPos> graph,
                                  Set<BlockPos> result) {
        Set<GlueEdge> chassisEdges = new LinkedHashSet<>();
        ChassisConnectivity.Access capturedAccess = new ChassisConnectivity.Access() {
            @Override public IBlockState state(BlockPos at) {
                Snapshot snapshot = blocks.get(at);
                return snapshot == null ? null : snapshot.state;
            }
            @Override public NBTTagCompound tileData(BlockPos at) {
                Snapshot snapshot = blocks.get(at);
                return snapshot == null ? null : snapshot.tile;
            }
            @Override public boolean terminal(BlockPos at) { return false; }
            @Override public EnumFacing forcedMovement() { return forcedMovement; }
        };
        for (BlockPos chassisPos : blocks.keySet()) {
            if (!(blocks.get(chassisPos).state.getBlock() instanceof ChassisBlock)) continue;
            ChassisConnectivity.collect(capturedAccess, chassisPos, (first, second) -> {
                if (blocks.containsKey(first) && blocks.containsKey(second))
                    chassisEdges.add(canonicalPair(first, second));
            });
        }
        Set<GridPos> connected = ContraptionConnectivity.connectedFrom(
                roots, graph::contains,
                (first, second) -> {
                    BlockPos firstPos = toBlockPos(first);
                    BlockPos secondPos = toBlockPos(second);
                    EnumFacing side = EnumFacing.getFacingFromVector(secondPos.getX() - firstPos.getX(),
                            secondPos.getY() - firstPos.getY(), secondPos.getZ() - firstPos.getZ());
                    return isCapturedGluePair(firstPos, secondPos)
                            || isCapturedTankPair(blocks.get(firstPos), blocks.get(secondPos))
                            || isCapturedStickerPair(blocks.get(firstPos), blocks.get(secondPos))
                            || isStickyBlockPair(capturedAccess, firstPos, blocks.get(firstPos).state(),
                                    secondPos, blocks.get(secondPos).state(), side)
                            || forcedMovement == side && !ChassisConnectivity.isNotSupportive(capturedAccess,
                                    firstPos, blocks.get(firstPos).state(), side)
                            || chassisEdges.contains(canonicalPair(firstPos, secondPos))
                            || isAttachedPair(firstPos, blocks.get(firstPos).state(),
                                    secondPos, blocks.get(secondPos).state())
                            || isMechanicalAttachment(firstPos, blocks.get(firstPos).state(),
                                    secondPos, blocks.get(secondPos).state(), capturedAccess::state, false);
                },
                ignored -> false, MAX_BLOCKS);
        for (GridPos pos : connected) {
            result.add(toBlockPos(pos));
        }
    }

    private boolean isCapturedGluePair(BlockPos first, BlockPos second) {
        return retainedGlueEdges().contains(canonicalPair(first, second));
    }

    private static boolean isCapturedStickerPair(Snapshot first, Snapshot second) {
        return first != null && second != null
                && (isStickerPair(first.pos, first.state, first.tile, second.pos, second.state, second.tile)
                || isStickerPair(second.pos, second.state, second.tile, first.pos, first.state, first.tile));
    }


    private Set<GlueEdge> retainedGlueEdges() {
        if (retainedGlueEdges != null) {
            return retainedGlueEdges;
        }
        Set<GlueEdge> edges = new LinkedHashSet<>();
        for (BlockPos first : blocks.keySet()) {
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos second = first.offset(facing);
                if (!blocks.containsKey(second)) {
                    continue;
                }
                if (isCapturedGluePairWithoutCache(first, second)) {
                    edges.add(canonicalPair(first, second));
                }
            }
        }
        retainedGlueEdges = edges;
        return retainedGlueEdges;
    }

    private boolean isCapturedGluePairWithoutCache(
            BlockPos first, BlockPos second) {
        GridPos firstLocal = GlueSelectionRules.grid(
                first.subtract(anchor()));
        GridPos secondLocal = GlueSelectionRules.grid(
                second.subtract(anchor()));
        for (CapturedGlue captured : capturedGlue.values()) {
            if (captured.cells.contains(firstLocal)
                    && captured.cells.contains(secondLocal)) {
                return true;
            }
        }
        return false;
    }

    private static GlueEdge canonicalPair(BlockPos first, BlockPos second) {
        return new GlueEdge(pointKey(first.getX(), first.getY(), first.getZ()),
                pointKey(second.getX(), second.getY(), second.getZ()));
    }

    private static long pointKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | ((long) y & 0xFFFL);
    }

    private record GlueEdge(long first, long second) {
        private GlueEdge {
            if (first > second) {
                long swap = first;
                first = second;
                second = swap;
            }
        }
    }

    private List<CapturedGlue> detachedGlueFor(Set<BlockPos> detachedPositions) {
        List<CapturedGlue> result = new ArrayList<>();
        Set<BlockPos> unvisited = new LinkedHashSet<>(detachedPositions);
        while (!unvisited.isEmpty()) {
            BlockPos root = unvisited.iterator().next();
            Set<BlockPos> component = new LinkedHashSet<>();
            Deque<BlockPos> frontier = new ArrayDeque<>();
            frontier.add(root);
            while (!frontier.isEmpty()) {
                BlockPos current = frontier.removeFirst();
                if (!unvisited.remove(current)) {
                    continue;
                }
                component.add(current);
                for (EnumFacing facing : EnumFacing.values()) {
                    BlockPos next = current.offset(facing);
                    if (unvisited.contains(next)
                            && isCapturedGluePair(current, next)) {
                        frontier.addLast(next);
                    }
                }
            }
            for (CapturedGlue captured : capturedGlue.values()) {
                GlueBox box = captured.box;
                Set<GridPos> cells = new LinkedHashSet<>();
                for (BlockPos block : component) {
                    GridPos local = GlueSelectionRules.grid(
                            block.subtract(anchor()));
                    if (captured.cells.contains(local)) {
                        cells.add(local);
                    }
                }
                GlueBox cropped = box.minimizeTo(cells);
                if (cropped != null) {
                    result.add(new CapturedGlue(cropped, Set.copyOf(cells)));
                }
            }
        }
        return result;
    }

    public Snapshot replaceAtLocal(BlockPos localPos, IBlockState state,
                                   NBTTagCompound tile) {
        retainedGlueEdges = null;
        BlockPos absolute = anchor().add(localPos);
        Snapshot previous = blocks.get(absolute);
        float replacementStress = previous == null ? 0.0F : previous.stressImpact;
        if (previous != null) {
            capturedStress = Math.max(0.0F, capturedStress - previous.stressImpact);
        }
        Snapshot replacement = new Snapshot(absolute.toImmutable(), state,
                tile == null ? null : tile.copy(), replacementStress);
        blocks.put(absolute.toImmutable(), replacement);
        contentVersion++;
        return previous;
    }

    public boolean persistBlockData(BlockPos localPos, IBlockState state,
                                    NBTTagCompound tile) {
        BlockPos absolute = anchor().add(localPos);
        Snapshot snapshot = blocks.get(absolute);
        if (snapshot == null) {
            return false;
        }
        NBTTagCompound nextTile = tile == null ? null : tile.copy();
        if (snapshot.state == state
                && java.util.Objects.equals(snapshot.tile, nextTile)) {
            return false;
        }
        blocks.put(absolute, new Snapshot(absolute, state,
                nextTile, snapshot.stressImpact));
        contentVersion++;
        return true;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public void removeFromWorld(World world, boolean hideGlue) {
        removeFromWorld(world, hideGlue, Set.of());
    }

    public void removeFromWorld(World world, boolean hideGlue, Set<BlockPos> ignoredPositions) {
        for (int glueId : new java.util.ArrayList<>(capturedGlue.keySet())) {
            Entity glue = world.getEntityByID(glueId);
            if (glue instanceof SuperGlueEntity && glue.isEntityAlive()) {
                ModNetwork.CHANNEL.sendToAllAround(
                        new GlueAreaRemovedPacket(glueId),
                        new NetworkRegistry.TargetPoint(
                                glue.dimension, glue.posX, glue.posY, glue.posZ, 64.0D));
                if (hideGlue) {
                    glue.setDead();
                } else {
                    glue.setInvisible(true);
                }
            }
            if (!world.isRemote) {
                ModNetwork.CHANNEL.sendToAllAround(
                        new GlueAreaRemovedPacket(glueId),
                        new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                                anchor().getX(), anchor().getY(),
                                anchor().getZ(), 96.0D));
            }
        }
        for (BlockPos pos : blocks.keySet()) {
            if (ignoredPositions.contains(pos)) continue;
            world.removeTileEntity(pos);
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), AIR_FLAGS);
        }
    }

    public java.util.List<SuperGlueEntity> restore(
            World world, BlockPos anchorPos, EnumFacing.Axis axis, float angle) {
        return restore(world, anchorPos, axis, angle, ignored -> false);
    }

    public java.util.List<SuperGlueEntity> restore(
            World world, BlockPos anchorPos, EnumFacing.Axis axis, float angle,
            java.util.function.Predicate<BlockPos> ignorePosition) {
        for (int glueId : new ArrayList<>(capturedGlue.keySet())) {
            Entity existing = world.getEntityByID(glueId);
            if (existing instanceof SuperGlueEntity && existing.isEntityAlive()) {
                existing.setDead();
            }
        }
        List<RestoredBlock> restored = rotatedBlocks(
                anchorPos, axis, angle, blocks.values());
        restored.removeIf(block -> ignorePosition.test(block.pos));
        placeRestoredBlocks(world, restored);
        List<SuperGlueEntity> restoredEntities = spawnGlueBoxes(
                world, anchorPos, axis, angle, capturedGlue.values());
        capturedGlue.clear();
        return restoredEntities;
    }

    /**
     * Restores only the disconnected part of a previous removal. Retained
     * blocks continue to be owned by the live contraption entity.
     */
    public List<SuperGlueEntity> restoreDetached(
            World world, BlockPos anchorPos, EnumFacing.Axis axis, float angle,
            RemovalResult removal) {
        if (removal == null || removal.detachedBlocks().isEmpty()) {
            return List.of();
        }
        List<RestoredBlock> restored = rotatedBlocks(
                anchorPos, axis, angle, removal.detachedBlocks());
        placeRestoredBlocks(world, restored);
        return spawnGlueBoxes(
                world, anchorPos, axis, angle, removal.detachedGlue());
    }

    private List<RestoredBlock> rotatedBlocks(
            BlockPos anchorPos, EnumFacing.Axis axis, float angle,
            Iterable<Snapshot> snapshots) {
        int turns = Math.floorMod(Math.round(angle / 90.0F), 4);
        Map<BlockPos, RestoredBlock> restored = new LinkedHashMap<>();
        Map<BlockPos, List<TankRestorePart>> tanks = new LinkedHashMap<>();
        for (Snapshot snapshot : snapshots) {
            BlockPos local = snapshot.pos.subtract(anchor());
            BlockPos target = rotateBlock(anchorPos, local, axis, turns);
            IBlockState targetState = rotateState(snapshot.state, axis, turns);
            NBTTagCompound tile = rotateTileData(snapshot, axis, turns);
            RestoredBlock block = new RestoredBlock(target, targetState, tile);
            restored.put(target, block);
            if (tile != null && snapshot.state.getBlock() instanceof FluidTankBlock) {
                BlockPos originalController = snapshot.pos.add(FluidTankTileEntity.controllerOffset(tile));
                tanks.computeIfAbsent(originalController, ignored -> new ArrayList<>())
                        .add(new TankRestorePart(snapshot, block));
            }
        }
        for (Map.Entry<BlockPos, List<TankRestorePart>> entry : tanks.entrySet()) {
            restoreTankGroup(entry.getKey(), entry.getValue());
        }
        return new ArrayList<>(restored.values());
    }

    private static void restoreTankGroup(BlockPos oldController, List<TankRestorePart> parts) {
        TankRestorePart oldRoot = null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (TankRestorePart part : parts) {
            if (part.source.pos.equals(oldController)) oldRoot = part;
            BlockPos pos = part.restored.pos;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        BlockPos newController = new BlockPos(minX, minY, minZ);
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        NBTTagCompound original = oldRoot == null ? null : oldRoot.restored.tile;
        int oldWidth = original == null ? 1 : Math.max(1, Math.min(3, original.getInteger("TankWidth")));
        int oldHeight = original == null ? 1 : Math.max(1, Math.min(256, original.getInteger("TankHeight")));
        boolean complete = original != null && oldWidth * oldWidth * oldHeight == parts.size()
                && width == depth && width <= 3 && height <= 256
                && width * width * height == parts.size();

        NBTTagCompound savedTank = original != null && original.hasKey("Tank", 10)
                ? original.getCompoundTag("Tank").copy() : new FluidTank(0).writeToNBT(new NBTTagCompound());
        NBTTagCompound savedCreative = original != null && original.hasKey("CreativeFluid", 10)
                ? original.getCompoundTag("CreativeFluid").copy() : null;
        boolean windows = original == null || !original.hasKey("TankWindows", 1)
                || original.getBoolean("TankWindows");

        if (complete) {
            for (TankRestorePart part : parts) {
                NBTTagCompound tag = part.restored.tile;
                BlockPos target = part.restored.pos;
                FluidTankTileEntity.relocateTag(tag, target);
                FluidTankTileEntity.setControllerOffset(tag, newController.subtract(target));
                tag.setInteger("TankWidth", width);
                tag.setInteger("TankHeight", height);
                tag.setBoolean("TankWindows", windows);
                tag.setTag("Tank", target.equals(newController) ? savedTank.copy()
                        : new FluidTank(FluidTankTileEntity.capacityPerBlock()).writeToNBT(new NBTTagCompound()));
                tag.removeTag("CreativeFluid");
                if (target.equals(newController) && savedCreative != null) {
                    tag.setTag("CreativeFluid", savedCreative.copy());
                }
            }
            return;
        }

        FluidTank stored = new FluidTank(FluidTankTileEntity.capacityPerBlock() * oldWidth * oldWidth * oldHeight);
        stored.readFromNBT(savedTank);
        FluidStack fluid = stored.getFluid();
        int remaining = fluid == null ? 0 : fluid.amount;
        parts.sort(java.util.Comparator.comparingInt((TankRestorePart part) -> part.restored.pos.getY())
                .thenComparingInt(part -> part.restored.pos.getX())
                .thenComparingInt(part -> part.restored.pos.getZ()));
        for (TankRestorePart part : parts) {
            NBTTagCompound tag = part.restored.tile;
            FluidTankTileEntity.relocateTag(tag, part.restored.pos);
            FluidTankTileEntity.setControllerOffset(tag, BlockPos.ORIGIN);
            tag.setInteger("TankWidth", 1);
            tag.setInteger("TankHeight", 1);
            tag.setBoolean("TankWindows", windows);
            FluidTank individual = new FluidTank(FluidTankTileEntity.capacityPerBlock());
            if (remaining > 0) {
                FluidStack portion = fluid.copy();
                portion.amount = Math.min(remaining, FluidTankTileEntity.capacityPerBlock());
                individual.setFluid(portion);
                remaining -= portion.amount;
            }
            tag.setTag("Tank", individual.writeToNBT(new NBTTagCompound()));
            tag.removeTag("CreativeFluid");
            if (savedCreative != null) tag.setTag("CreativeFluid", savedCreative.copy());
        }
    }

    private record TankRestorePart(Snapshot source, RestoredBlock restored) { }

    private static NBTTagCompound rotateTileData(Snapshot snapshot, EnumFacing.Axis axis, int turns) {
        NBTTagCompound tile = snapshot.tile == null ? null : snapshot.tile.copy();
        if (tile == null) return null;
        if (snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorBlock) {
            NBTTagList links = tile.getTagList("ChainConnections", 10);
            for (int i = 0; i < links.tagCount(); i++) rotateOffset(links.getCompoundTagAt(i), "Offset", axis, turns);
            NBTTagList packages = tile.getTagList("ChainPackages", 10);
            for (int i = 0; i < packages.tagCount(); i++) {
                NBTTagCompound box = packages.getCompoundTagAt(i);
                if (box.hasKey("TargetOffset")) rotateOffset(box, "TargetOffset", axis, turns);
                else if (axis == EnumFacing.Axis.Y) box.setFloat("Progress", box.getFloat("Progress") + turns * 90F);
            }
        } else if (snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock) {
            rotateOffset(tile, "ChainTargetOffset", axis, turns); rotateOffset(tile, "ChainEndOffset", axis, turns);
            if (!tile.hasKey("ChainEndOffset") && axis == EnumFacing.Axis.Y) tile.setFloat("ChainAngle", tile.getFloat("ChainAngle") + turns * 90F);
        }
        if (snapshot.state.getBlock() instanceof ChassisBlock) {
            int previous = tile.getInteger("StickyMask");
            int rotated = 0;
            for (EnumFacing side : EnumFacing.VALUES)
                if ((previous & 1 << side.getIndex()) != 0)
                    rotated |= 1 << rotateFacing(side, axis, turns).getIndex();
            tile.setInteger("StickyMask", rotated);
        }
        if (snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock) {
            QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(new QuarterTurnGrid.GridPos3(
                    tile.getInteger("StepX"), tile.getInteger("StepY"), tile.getInteger("StepZ")),
                    QuarterTurnGrid.Axis.valueOf(axis.name()), turns);
            tile.setInteger("StepX", rotated.x()); tile.setInteger("StepY", rotated.y()); tile.setInteger("StepZ", rotated.z());
        } else if (snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.steam.PoweredShaftBlock
                && tile.hasKey("EngineOffset", 4)) {
            BlockPos offset = BlockPos.fromLong(tile.getLong("EngineOffset"));
            QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(new QuarterTurnGrid.GridPos3(
                    offset.getX(), offset.getY(), offset.getZ()), QuarterTurnGrid.Axis.valueOf(axis.name()), turns);
            tile.setLong("EngineOffset", new BlockPos(rotated.x(), rotated.y(), rotated.z()).toLong());
        } else if (snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock) {
            EnumFacing oldFacing = snapshot.state.getValue(
                    com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterBlock.FACING);
            var oldPointing = com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity.readPointing(tile);
            EnumFacing rotatedFacing = rotateFacing(oldFacing, axis, turns);
            EnumFacing rotatedOutput = rotateFacing(oldPointing.relativeTo(oldFacing), axis, turns);
            var rotatedPointing = com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterDirection
                    .relativeTo(rotatedFacing, rotatedOutput);
            tile.setByte(com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity.POINTING_NBT,
                    (byte) rotatedPointing.ordinal());
            NBTTagList entries = tile.getTagList("CrafterPausedIngredients", 10);
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                BlockPos offset = entry.hasKey("OriginOffset", 4)
                        ? BlockPos.fromLong(entry.getLong("OriginOffset"))
                        : BlockPos.fromLong(entry.getLong("Origin")).subtract(snapshot.pos);
                QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(new QuarterTurnGrid.GridPos3(
                        offset.getX(), offset.getY(), offset.getZ()),
                        QuarterTurnGrid.Axis.valueOf(axis.name()), turns);
                entry.setLong("OriginOffset", new BlockPos(rotated.x(), rotated.y(), rotated.z()).toLong());
                entry.removeTag("Origin");
                if (entry.hasKey("RenderX", 99) && entry.hasKey("RenderY", 99) && entry.hasKey("RenderZ", 99)) {
                    double x = entry.getDouble("RenderX"), y = entry.getDouble("RenderY"), z = entry.getDouble("RenderZ");
                    EnumFacing east = rotateFacing(EnumFacing.EAST, axis, turns);
                    EnumFacing up = rotateFacing(EnumFacing.UP, axis, turns);
                    EnumFacing south = rotateFacing(EnumFacing.SOUTH, axis, turns);
                    entry.setDouble("RenderX", x * east.getXOffset() + y * up.getXOffset() + z * south.getXOffset());
                    entry.setDouble("RenderY", x * east.getYOffset() + y * up.getYOffset() + z * south.getYOffset());
                    entry.setDouble("RenderZ", x * east.getZOffset() + y * up.getZOffset() + z * south.getZOffset());
                }
            }
        } else if (snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmBlock) {
            net.minecraft.nbt.NBTTagList points = tile.getTagList("Points", 10);
            for (int i = 0; i < points.tagCount(); i++) {
                NBTTagCompound point = points.getCompoundTagAt(i);
                BlockPos offset = BlockPos.fromLong(point.getLong("Offset"));
                QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(new QuarterTurnGrid.GridPos3(
                        offset.getX(), offset.getY(), offset.getZ()), QuarterTurnGrid.Axis.valueOf(axis.name()), turns);
                point.setLong("Offset", new BlockPos(rotated.x(), rotated.y(), rotated.z()).toLong());
                point.setInteger("Side", rotateFacing(EnumFacing.byIndex(point.getInteger("Side")), axis, turns).getIndex());
            }
        } else if (snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelBlock) {
            net.minecraft.nbt.NBTTagList filters = tile.getTagList("Filters", 10);
            for (int i = 0; i < filters.tagCount(); i++) {
                NBTTagCompound filter = filters.getCompoundTagAt(i);
                filter.setByte("Side", (byte) rotateFacing(EnumFacing.byIndex(filter.getByte("Side")), axis, turns).getIndex());
            }
        } else if (snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.logistics.vault.ItemVaultBlock) {
            QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(new QuarterTurnGrid.GridPos3(
                    tile.getInteger("ControllerX"), tile.getInteger("ControllerY"), tile.getInteger("ControllerZ")),
                    QuarterTurnGrid.Axis.valueOf(axis.name()), turns);
            tile.setInteger("ControllerX", rotated.x()); tile.setInteger("ControllerY", rotated.y()); tile.setInteger("ControllerZ", rotated.z());
        }
        if (!(snapshot.state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelBlock)) return tile;
        String key = com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelTileEntity.PORTS_KEY;
        int oldPorts = tile.getInteger(key);
        int newPorts = 0;
        for (EnumFacing face : EnumFacing.VALUES) {
            if ((oldPorts & (1 << face.ordinal())) != 0) {
                newPorts |= 1 << rotateFacing(face, axis, turns).ordinal();
            }
        }
        tile.setInteger(key, newPorts);
        return tile;
    }

    private static void rotateOffset(NBTTagCompound tag, String key, EnumFacing.Axis axis, int turns) {
        if (!tag.hasKey(key, 4)) return;
        BlockPos offset = BlockPos.fromLong(tag.getLong(key));
        QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(new QuarterTurnGrid.GridPos3(offset.getX(), offset.getY(), offset.getZ()),
                QuarterTurnGrid.Axis.valueOf(axis.name()), turns);
        tag.setLong(key, new BlockPos(rotated.x(), rotated.y(), rotated.z()).toLong());
    }

    private void placeRestoredBlocks(
            World world, List<RestoredBlock> restored) {
        for (RestoredBlock block : restored) {
            world.removeTileEntity(block.pos);
            world.setBlockState(block.pos, Blocks.AIR.getDefaultState(), AIR_FLAGS);
        }
        for (RestoredBlock block : restored) {
            world.setBlockState(block.pos, block.state, AIR_FLAGS);
            if (block.tile == null) {
                continue;
            }
            NBTTagCompound tag = block.tile.copy();
            if (block.state.getBlock() instanceof FluidTankBlock) {
                FluidTankTileEntity.relocateTag(tag, block.pos);
            } else {
                tag.setInteger("x", block.pos.getX());
                tag.setInteger("y", block.pos.getY());
                tag.setInteger("z", block.pos.getZ());
            }
            TileEntity restoredTile = TileEntity.create(world, tag);
            if (restoredTile != null) {
                restoredTile.setWorld(world);
                restoredTile.validate();
                world.setTileEntity(block.pos, restoredTile);
                restoredTile.markDirty();
            }
        }
    }

    private List<SuperGlueEntity> spawnGlueBoxes(
            World world, BlockPos anchorPos, EnumFacing.Axis axis, float angle,
            Iterable<CapturedGlue> glueBoxes) {
        List<SuperGlueEntity> restoredEntities = new ArrayList<>();
        for (CapturedGlue raw : glueBoxes) {
            GlueBox movedBox = rotateGlueBox(raw.box, anchorPos, axis, angle);
            SuperGlueEntity restoredGlue = new SuperGlueEntity(world, movedBox,
                    QuarterTurnGrid.rotateGlueCells(raw.cells,
                            anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(),
                            QuarterTurnGrid.Axis.valueOf(axis.name()), angle));
            world.spawnEntity(restoredGlue);
            restoredEntities.add(restoredGlue);
            ModNetwork.CHANNEL.sendToAllAround(
                        new GlueAreaUpdatePacket(restoredGlue.getEntityId(),
                                movedBox, restoredGlue.getGlueVolume()),
                    new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                            restoredGlue.posX, restoredGlue.posY, restoredGlue.posZ, 64.0D));
        }
        return restoredEntities;
    }

    static GlueBox rotateGlueBox(GlueBox relative, BlockPos anchor,
                                 EnumFacing.Axis axis, float angle) {
        return QuarterTurnGrid.rotateGlueBox(relative,
                anchor.getX(), anchor.getY(), anchor.getZ(),
                QuarterTurnGrid.Axis.valueOf(axis.name()), angle);
    }

    private static BlockPos rotateBlock(BlockPos anchor, BlockPos local, EnumFacing.Axis axis, int turns) {
        if (turns == 0) {
            return anchor.add(local);
        }
        QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(
                new QuarterTurnGrid.GridPos3(local.getX(), local.getY(), local.getZ()),
                QuarterTurnGrid.Axis.valueOf(axis.name()), turns);
        return anchor.add(rotated.x(), rotated.y(), rotated.z());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState rotateState(IBlockState state, EnumFacing.Axis axis, int turns) {
        if (turns == 0) {
            return state;
        }
        if (state.getBlock() instanceof SequencedGearshiftBlock) {
            return SequencedGearshiftBlock.transformState(state, axis, turns);
        }
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock piston) {
            EnumFacing originalFacing = state.getValue(com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock.FACING);
            EnumFacing rotatedFacing = rotateFacing(originalFacing, axis, turns);
            EnumFacing.Axis rotatedShaft = rotateFacing(EnumFacing.getFacingFromAxis(
                    EnumFacing.AxisDirection.POSITIVE, piston.getAxis(state)), axis, turns).getAxis();
            IBlockState rotated = state.withProperty(com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock.FACING, rotatedFacing)
                    .withProperty(com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock.AXIS_ALONG_FIRST, false);
            return piston.getAxis(rotated) == rotatedShaft ? rotated : rotated.withProperty(
                    com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock.AXIS_ALONG_FIRST, true);
        }
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock carriage) {
            EnumFacing originalFacing = state.getValue(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock.FACING);
            EnumFacing rotatedFacing = rotateFacing(originalFacing, axis, turns);
            EnumFacing.Axis rotatedShaft = rotateFacing(EnumFacing.getFacingFromAxis(
                    EnumFacing.AxisDirection.POSITIVE, carriage.getAxis(state)), axis, turns).getAxis();
            IBlockState rotated = state.withProperty(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock.FACING, rotatedFacing)
                    .withProperty(com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock.AXIS_ALONG_FIRST, false);
            return carriage.getAxis(rotated) == rotatedShaft ? rotated : rotated.withProperty(
                    com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock.AXIS_ALONG_FIRST, true);
        }
        IBlockState result = state;
        for (net.minecraft.block.properties.IProperty<?> property : state.getPropertyKeys()) {
            String name = property.getName();
            Comparable<?> value = result.getValue(property);
            if (value instanceof EnumFacing
                    && ("facing".equals(name) || "horizontal_facing".equals(name))) {
                EnumFacing rotated = (EnumFacing) value;
                for (int i = 0; i < turns; i++) {
                    rotated = rotateAround(rotated, axis);
                }
                if (property.getAllowedValues().contains(rotated)) {
                    result = result.withProperty((net.minecraft.block.properties.IProperty) property, rotated);
                }
            } else if (value instanceof net.minecraft.block.BlockLog.EnumAxis
                    && ("axis".equals(name) || "log_axis".equals(name))) {
                net.minecraft.block.BlockLog.EnumAxis logAxis =
                        (net.minecraft.block.BlockLog.EnumAxis) value;
                EnumFacing.Axis facingAxis = EnumFacing.Axis.byName(logAxis.getName());
                if (facingAxis == null) {
                    continue;
                }
                EnumFacing rotated = EnumFacing.getFacingFromAxis(
                        EnumFacing.AxisDirection.POSITIVE, facingAxis);
                for (int i = 0; i < turns; i++) {
                    rotated = rotateAround(rotated, axis);
                }
                result = result.withProperty((net.minecraft.block.properties.IProperty) property,
                        net.minecraft.block.BlockLog.EnumAxis.fromFacingAxis(rotated.getAxis()));
            } else if (value instanceof EnumFacing.Axis && "axis".equals(name)) {
                EnumFacing rotated = EnumFacing.getFacingFromAxis(
                        EnumFacing.AxisDirection.POSITIVE, (EnumFacing.Axis) value);
                for (int i = 0; i < turns; i++) {
                    rotated = rotateAround(rotated, axis);
                }
                result = result.withProperty((net.minecraft.block.properties.IProperty) property,
                        rotated.getAxis());
            }
        }
        if (axis == EnumFacing.Axis.Y && blockUsesRotationProperty(state)) {
            result = result.withRotation(rotationForTurns(turns));
        }
        return result;
    }

    private static boolean blockUsesRotationProperty(IBlockState state) {
        for (net.minecraft.block.properties.IProperty<?> property : state.getPropertyKeys()) {
            if ("facing".equals(property.getName()) || "axis".equals(property.getName())) {
                return false;
            }
        }
        return true;
    }

    private static EnumFacing rotateAround(EnumFacing facing, EnumFacing.Axis axis) {
        net.minecraft.util.math.Vec3i vec = facing.getDirectionVec();
        double radians = Math.toRadians(90.0D);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int x = vec.getX();
        int y = vec.getY();
        int z = vec.getZ();
        int rotatedX;
        int rotatedY;
        int rotatedZ;
        switch (axis) {
            case X:
                rotatedX = x;
                rotatedY = (int) Math.round(y * cos - z * sin);
                rotatedZ = (int) Math.round(y * sin + z * cos);
                break;
            case Z:
                rotatedX = (int) Math.round(x * cos - y * sin);
                rotatedY = (int) Math.round(x * sin + y * cos);
                rotatedZ = z;
                break;
            case Y:
            default:
                rotatedX = (int) Math.round(x * cos + z * sin);
                rotatedY = y;
                rotatedZ = (int) Math.round(-x * sin + z * cos);
                break;
        }
        return EnumFacing.getFacingFromVector(rotatedX, rotatedY, rotatedZ);
    }

    static EnumFacing rotateFacing(EnumFacing facing, EnumFacing.Axis axis, int turns) {
        EnumFacing rotated = facing;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            rotated = rotateAround(rotated, axis);
        }
        return rotated;
    }

    private static net.minecraft.util.Rotation rotationForTurns(int turns) {
        switch (Math.floorMod(turns, 4)) {
            case 1:
                return net.minecraft.util.Rotation.COUNTERCLOCKWISE_90;
            case 2:
                return net.minecraft.util.Rotation.CLOCKWISE_180;
            case 3:
                return net.minecraft.util.Rotation.CLOCKWISE_90;
            default:
                return net.minecraft.util.Rotation.NONE;
        }
    }

    private static final class RestoredBlock {
        private final BlockPos pos;
        private final IBlockState state;
        private final NBTTagCompound tile;

        private RestoredBlock(BlockPos pos, IBlockState state, NBTTagCompound tile) {
            this.pos = pos;
            this.state = state;
            this.tile = tile;
        }
    }

    private record CapturedGlue(GlueBox box, Set<GridPos> cells) {
    }

    public record RemovalResult(Snapshot removedBlock,
                                List<Snapshot> detachedBlocks,
                                List<CapturedGlue> detachedGlue) {
    }

    public void syncAnchor(BlockPos bearing, EnumFacing facing) {
        alignAnchor(bearing.offset(facing));
    }

    /**
     * Rebases a captured snapshot onto a new anchor without changing its
     * structure-local geometry. Nested bearings pass the child snapshot into a
     * synthetic coordinate grid rooted at the child bearing in the parent.
     */
    public BearingContraption translatedTo(BlockPos newAnchor) {
        BlockPos target = newAnchor.toImmutable();
        BlockPos delta = target.subtract(anchor());
        BearingContraption translated = new BearingContraption();
        translated.anchor = target;
        translated.capturedStress = capturedStress;
        translated.forcedMovement = forcedMovement;
        translated.setActorControls(actorControls);
        for (Map.Entry<BlockPos, Snapshot> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey().add(delta);
            Snapshot snapshot = entry.getValue();
            NBTTagCompound tile = snapshot.tile == null ? null : snapshot.tile.copy();
            if (tile != null && snapshot.state.getBlock() instanceof FluidTankBlock) {
                FluidTankTileEntity.relocateTag(tile, pos);
            }
            translated.blocks.put(pos, new Snapshot(
                    pos, snapshot.state, tile, snapshot.stressImpact));
        }
        for (Map.Entry<Integer, CapturedGlue> entry : capturedGlue.entrySet()) {
            CapturedGlue glue = entry.getValue();
            translated.capturedGlue.put(entry.getKey(), new CapturedGlue(
                    glue.box, glue.cells));
        }
        translated.retainedGlueEdges = null;
        return translated;
    }

    BearingContraption rotatedTo(BlockPos newAnchor, EnumFacing.Axis axis, int turns) {
        BearingContraption rotated = new BearingContraption();
        rotated.anchor = newAnchor.toImmutable();
        rotated.capturedStress = capturedStress;
        rotated.setActorControls(actorControls);
        int normalizedTurns = Math.floorMod(turns, 4);
        rotated.forcedMovement = forcedMovement == null ? null : rotateFacing(forcedMovement, axis, normalizedTurns);
        Map<BlockPos, RestoredBlock> transformedBlocks = new LinkedHashMap<>();
        for (RestoredBlock block : rotatedBlocks(rotated.anchor, axis, normalizedTurns * 90.0F, blocks.values())) {
            transformedBlocks.put(block.pos, block);
        }
        for (Snapshot snapshot : blocks.values()) {
            BlockPos local = snapshot.pos.subtract(anchor());
            BlockPos pos = rotateBlock(rotated.anchor, local, axis, normalizedTurns);
            RestoredBlock block = transformedBlocks.get(pos);
            rotated.blocks.put(pos, new Snapshot(pos, block.state, block.tile, snapshot.stressImpact));
        }
        QuarterTurnGrid.Axis gridAxis = QuarterTurnGrid.Axis.valueOf(axis.name());
        float degrees = normalizedTurns * 90.0F;
        for (Map.Entry<Integer, CapturedGlue> entry : capturedGlue.entrySet()) {
            CapturedGlue glue = entry.getValue();
            rotated.capturedGlue.put(entry.getKey(), new CapturedGlue(
                    QuarterTurnGrid.rotateGlueBox(glue.box, 0, 0, 0, gridAxis, degrees),
                    QuarterTurnGrid.rotateGlueCells(glue.cells, 0, 0, 0, gridAxis, degrees)));
        }
        return rotated;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (Snapshot snapshot : blocks.values()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("X", snapshot.pos.getX());
            entry.setInteger("Y", snapshot.pos.getY());
            entry.setInteger("Z", snapshot.pos.getZ());
            entry.setInteger("Meta", snapshot.state.getBlock().getMetaFromState(snapshot.state));
            entry.setString("Block", snapshot.state.getBlock().getRegistryName().toString());
            entry.setFloat("StressImpact", snapshot.stressImpact);
            if (snapshot.tile != null) {
                entry.setTag("Tile", snapshot.tile);
            }
            list.appendTag(entry);
        }
        compound.setTag("Blocks", list);
        if (forcedMovement != null) compound.setByte("ForcedMovement", (byte) forcedMovement.getIndex());
        else compound.removeTag("ForcedMovement");
        if (actorControls != null) compound.setTag("ActorControls", actorControls.copy());
        compound.setInteger("CaptureCount", blocks.size());
        compound.setFloat("CapturedStress", capturedStress);
        compound.setInteger("StressFormat", 1);
        NBTTagList glueList = new NBTTagList();
        for (CapturedGlue glue : capturedGlue.values()) {
            GlueBox box = glue.box;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("MinX", box.minX());
            entry.setInteger("MinY", box.minY());
            entry.setInteger("MinZ", box.minZ());
            entry.setInteger("MaxX", box.maxX());
            entry.setInteger("MaxY", box.maxY());
            entry.setInteger("MaxZ", box.maxZ());
            NBTTagList positions = new NBTTagList();
            for (GridPos pos : glue.cells) {
                NBTTagCompound cell = new NBTTagCompound();
                cell.setInteger("X", pos.x());
                cell.setInteger("Y", pos.y());
                cell.setInteger("Z", pos.z());
                positions.appendTag(cell);
            }
            entry.setTag("Cells", positions);
            glueList.appendTag(entry);
        }
        compound.setTag("Glue", glueList);
        compound.setInteger("GlueFormat", GLUE_FORMAT_CELLS_RELATIVE);
        if (anchor != null) {
            compound.setInteger("AnchorX", anchor.getX());
            compound.setInteger("AnchorY", anchor.getY());
            compound.setInteger("AnchorZ", anchor.getZ());
        }
        return compound;
    }

    public static BearingContraption readFromNBT(NBTTagCompound compound) {
        BearingContraption result = new BearingContraption();
        if (compound.hasKey("ForcedMovement")) result.forcedMovement = EnumFacing.byIndex(compound.getByte("ForcedMovement"));
        if (compound.hasKey("ActorControls", 10)) result.setActorControls(compound.getCompoundTag("ActorControls"));
        NBTTagList list = compound.getTagList("Blocks", 10);
        boolean preciseStress = compound.getInteger("StressFormat") >= 1;
        float reconstructedStress = 0.0F;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            Block block = Block.getBlockFromName(entry.getString("Block"));
            if (block == null) {
                continue;
            }
            BlockPos pos = new BlockPos(entry.getInteger("X"), entry.getInteger("Y"), entry.getInteger("Z"));
            IBlockState state = block.getStateFromMeta(entry.getInteger("Meta"));
            NBTTagCompound tile = entry.hasKey("Tile") ? entry.getCompoundTag("Tile") : null;
            float stressImpact = entry.getFloat("StressImpact");
            result.blocks.put(pos, new Snapshot(pos, state, tile, stressImpact));
            reconstructedStress += stressImpact;
        }
        result.capturedStress = preciseStress
                ? compound.getFloat("CapturedStress") : reconstructedStress;
        if (compound.hasKey("AnchorX")) {
            result.anchor = new BlockPos(compound.getInteger("AnchorX"),
                    compound.getInteger("AnchorY"), compound.getInteger("AnchorZ"));
        }
        int glueFormat = compound.getInteger("GlueFormat");
        NBTTagList glueList = compound.getTagList("Glue", 10);
        for (int i = 0; i < glueList.tagCount(); i++) {
            NBTTagCompound entry = glueList.getCompoundTagAt(i);
            GlueBox box = GlueBox.of(
                    entry.getInteger("MinX"), entry.getInteger("MinY"),
                    entry.getInteger("MinZ"), entry.getInteger("MaxX"),
                    entry.getInteger("MaxY"), entry.getInteger("MaxZ"));
            if (glueFormat == 0 && result.anchor != null) {
                box = GlueBox.of(
                        box.minX() - result.anchor.getX(),
                        box.minY() - result.anchor.getY(),
                        box.minZ() - result.anchor.getZ(),
                        box.maxX() - result.anchor.getX(),
                        box.maxY() - result.anchor.getY(),
                        box.maxZ() - result.anchor.getZ());
            } else if (glueFormat == 1 && result.anchor != null) {
                box = box.translated(result.anchor.getX(), result.anchor.getY(),
                        result.anchor.getZ());
            }
            Set<GridPos> cells = new LinkedHashSet<>();
            NBTTagList cellList = entry.getTagList("Cells", 10);
            for (int j = 0; j < cellList.tagCount(); j++) {
                NBTTagCompound cell = cellList.getCompoundTagAt(j);
                cells.add(new GridPos(cell.getInteger("X"),
                        cell.getInteger("Y"), cell.getInteger("Z")));
            }
            if (cells.isEmpty()) {
                cells.addAll(box.positions());
            }
            result.capturedGlue.put(i,
                    new CapturedGlue(box, Set.copyOf(cells)));
        }
        return result;
    }

    /**
     * Reconstructs the server-side snapshot from MovingWorld's display chunk
     * when the separate custom snapshot packet was not delivered.
     */
    public static BearingContraption fromLocalChunk(
            com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunk mobileChunk,
            BlockPos anchor) {
        BearingContraption result = new BearingContraption();
        result.anchor = anchor.toImmutable();
        for (int x = mobileChunk.minX(); x < mobileChunk.maxX(); x++) {
            for (int y = mobileChunk.minY(); y < mobileChunk.maxY(); y++) {
                for (int z = mobileChunk.minZ(); z < mobileChunk.maxZ(); z++) {
                    BlockPos local = new BlockPos(x, y, z);
                    IBlockState state = mobileChunk.getBlockState(local);
                    if (state == null || state.getBlock() == Blocks.AIR) {
                        continue;
                    }
                    TileEntity tile = mobileChunk.getTileEntity(local);
                    NBTTagCompound tileData = tile == null
                            ? null : tile.writeToNBT(new NBTTagCompound());
                    BlockPos worldPos = anchor.add(local);
                    float stressImpact = 0.0F;
                    if (tile instanceof com.cogwork.cogworkbridge.kinetic.KineticTileEntity kinetic) {
                        stressImpact = kinetic.getStressImpact();
                    }
                    result.blocks.put(worldPos,
                            new Snapshot(worldPos, state, tileData, stressImpact));
                }
            }
        }
        return result;
    }

    public static final class Snapshot {
        private final BlockPos pos;
        private final IBlockState state;
        private final NBTTagCompound tile;
        private final float stressImpact;

        private Snapshot(BlockPos pos, IBlockState state, NBTTagCompound tile,
                         float stressImpact) {
            this.pos = pos;
            this.state = state;
            this.tile = tile;
            this.stressImpact = stressImpact;
        }

        public BlockPos pos() {
            return pos;
        }

        public IBlockState state() {
            return state;
        }

        public NBTTagCompound tileData() {
            return tile == null ? null : tile.copy();
        }

        public float stressImpact() {
            return stressImpact;
        }
    }
}
