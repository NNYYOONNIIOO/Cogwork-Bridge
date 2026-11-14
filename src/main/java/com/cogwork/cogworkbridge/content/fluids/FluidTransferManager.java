package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Server-side fluid transport, shared by regular and contraption worlds. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class FluidTransferManager {
    private static final WeakHashMap<World, FluidTransferManager> WORLDS = new WeakHashMap<>();
    private static final float PRESSURE_EPSILON = 0.0001F;

    private final Set<BlockPos> pipePositions = new HashSet<>();
    private final Set<BlockPos> pumpPositions = new HashSet<>();
    private List<Network> networks = Collections.emptyList();
    private long lastTick = Long.MIN_VALUE;
    private boolean dirty = true;

    private FluidTransferManager() { }

    public static void registerPipe(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        FluidTransferManager manager = manager(world);
        if (manager.pipePositions.add(pos.toImmutable())) manager.dirty = true;
    }

    public static void unregisterPipe(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        FluidTransferManager manager = manager(world);
        if (manager.pipePositions.remove(pos)) manager.dirty = true;
    }

    public static void registerPump(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        FluidTransferManager manager = manager(world);
        if (manager.pumpPositions.add(pos.toImmutable())) manager.dirty = true;
    }

    public static void unregisterPump(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        FluidTransferManager manager = manager(world);
        if (manager.pumpPositions.remove(pos)) manager.dirty = true;
    }

    public static void markDirty(World world) {
        if (world != null && !world.isRemote) manager(world).dirty = true;
    }

    public static void tick(World world) {
        if (world == null || world.isRemote) return;
        FluidTransferManager manager = manager(world);
        long time = world.getTotalWorldTime();
        if (manager.lastTick == time) return;
        manager.lastTick = time;
        if (manager.dirty) manager.rebuild(world);
        for (Network network : manager.networks) network.tick(world);
    }

    /** Scene-only pressure/flow visualization. Sampling never transfers or places fluid. */
    public static void renderTutorialFlows(World world, java.util.Collection<BlockPos> positions) {
        if (world == null || !world.isRemote) return;
        FluidTransferManager isolated = new FluidTransferManager();
        for (BlockPos pos : positions) {
            if (world.getTileEntity(pos) instanceof MechanicalPumpTileEntity) isolated.pumpPositions.add(pos);
            else if (isPipe(world.getBlockState(pos))) isolated.pipePositions.add(pos);
        }
        isolated.rebuild(world);
        for (Network network : isolated.networks) network.tick(world, true);
    }

    private static FluidTransferManager manager(World world) {
        synchronized (WORLDS) {
            return WORLDS.computeIfAbsent(world, ignored -> new FluidTransferManager());
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        synchronized (WORLDS) {
            WORLDS.remove(event.getWorld());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        invalidateNearChunk(event);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        invalidateNearChunk(event);
    }

    /** An endpoint can appear or disappear without a neighboring block update at chunk boundaries. */
    private static void invalidateNearChunk(ChunkEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        synchronized (WORLDS) {
            FluidTransferManager manager = WORLDS.get(world);
            if (manager == null) return;
            int chunkX = event.getChunk().x;
            int chunkZ = event.getChunk().z;
            for (BlockPos pos : manager.pipePositions) {
                if (Math.abs((pos.getX() >> 4) - chunkX) <= 1
                        && Math.abs((pos.getZ() >> 4) - chunkZ) <= 1) {
                    manager.dirty = true;
                    return;
                }
            }
            for (BlockPos pos : manager.pumpPositions) {
                if (Math.abs((pos.getX() >> 4) - chunkX) <= 1
                        && Math.abs((pos.getZ() >> 4) - chunkZ) <= 1) {
                    manager.dirty = true;
                    return;
                }
            }
        }
    }

    private void rebuild(World world) {
        dirty = false;
        pipePositions.removeIf(pos -> !world.isBlockLoaded(pos) || !isPipe(world.getBlockState(pos)));
        pumpPositions.removeIf(pos -> !world.isBlockLoaded(pos)
                || !(world.getTileEntity(pos) instanceof MechanicalPumpTileEntity));
        Set<BlockPos> remaining = new HashSet<>(pipePositions);
        remaining.addAll(pumpPositions);
        List<Network> rebuilt = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Network network = new Network();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(remaining.iterator().next());
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                if (!remaining.remove(pos)) continue;
                IBlockState state = world.getBlockState(pos);
                network.nodes.add(pos);
                if (state.getBlock() instanceof MechanicalPumpBlock) network.pumps.add(pos);
                for (EnumFacing side : EnumFacing.values()) {
                    BlockPos next = pos.offset(side);
                    if (remaining.contains(next) && connects(world, pos, state, side)
                            && connects(world, next, world.getBlockState(next), side.getOpposite())) {
                        queue.addLast(next);
                    }
                }
            }
            rebuilt.add(network);
        }
        networks = rebuilt;
    }

    private static final class Network {
        private final Set<BlockPos> nodes = new HashSet<>();
        private final Set<BlockPos> pumps = new HashSet<>();
        private Map<BlockPos, PumpPressure> previousPumps = Collections.emptyMap();
        private Map<Face, Float> cachedPressure = Collections.emptyMap();
        private boolean pressureInitialized;
        private int previousPumpRange;

        private void tick(World world) {
            tick(world, false);
        }

        private void tick(World world, boolean visualOnly) {
            Map<BlockPos, PumpPressure> currentPumps = new HashMap<>();
            for (BlockPos pos : pumps) {
                TileEntity tile = world.getTileEntity(pos);
                IBlockState state = world.getBlockState(pos);
                if (!(tile instanceof MechanicalPumpTileEntity pump)
                        || !(state.getBlock() instanceof MechanicalPumpBlock)) continue;
                float strength = Math.abs(pump.getSpeed());
                if (strength <= PRESSURE_EPSILON) continue;
                EnumFacing front = state.getValue(MechanicalPumpBlock.FACING);
                currentPumps.put(pos, new PumpPressure(front, strength));
            }
            if (!pressureInitialized || previousPumpRange != FluidConfig.mechanicalPumpRange
                    || !currentPumps.equals(previousPumps)) {
                Map<Face, Float> recalculated = new HashMap<>();
                for (Map.Entry<BlockPos, PumpPressure> entry : currentPumps.entrySet()) {
                    PumpPressure pump = entry.getValue();
                    distribute(world, recalculated, entry.getKey(), pump.front, pump.strength, false);
                    distribute(world, recalculated, entry.getKey(), pump.front.getOpposite(), pump.strength, true);
                }
                previousPumps = currentPumps;
                cachedPressure = recalculated;
                previousPumpRange = FluidConfig.mechanicalPumpRange;
                pressureInitialized = true;
            }
            Map<Face, Float> pressure = cachedPressure;
            if (!visualOnly) for (BlockPos pos : nodes) {
                TileEntity tile = world.getTileEntity(pos);
                if (!(tile instanceof OpenEndProvider provider)) continue;
                for (EnumFacing side : EnumFacing.values()) {
                    if (pressure.getOrDefault(new Face(pos, side), 0.0F) < -PRESSURE_EPSILON) {
                        provider.getOpenEnds().flush(side);
                    }
                }
            }
            Map<BlockPos, FluidStack> displayed = new HashMap<>();
            Map<Face, Integer> remainingCapacity = new HashMap<>();
            List<Endpoint> sources = new ArrayList<>();
            boolean hasPortableSink = false;
            for (BlockPos pos : nodes) {
                IBlockState state = world.getBlockState(pos);
                for (EnumFacing side : EnumFacing.values()) {
                    if (!connects(world, pos, state, side)) continue;
                    float amount = pressure.getOrDefault(new Face(pos, side), 0.0F);
                    if (amount < -PRESSURE_EPSILON && !nodes.contains(pos.offset(side))
                            && world.isBlockLoaded(pos.offset(side))
                            && world.getTileEntity(pos.offset(side)) instanceof PortableFluidInterfaceTileEntity) {
                        hasPortableSink = true;
                    }
                    if (amount <= PRESSURE_EPSILON) continue;
                    Endpoint endpoint = endpoint(world, pos, side, amount);
                    if (endpoint != null) sources.add(endpoint);
                }
            }

            Map<Endpoint, FluidStack> sourceFluids = new HashMap<>();
            for (Endpoint source : sources) {
                int limit = remaining(pressure, remainingCapacity, new Face(source.pos, source.side));
                FluidStack fluid = availableFluid(source.handler, limit);
                if (fluid == null) continue;
                sourceFluids.put(source, fluid);
            }

            for (BlockPos pos : nodes) {
                if (flipReversedFlowsAt(world, pressure, pos)
                        && world.getBlockState(pos).getBlock() instanceof GlassFluidPipeBlock) {
                    IBlockState state = world.getBlockState(pos);
                    world.notifyBlockUpdate(pos, state, state, 2);
                }
            }
            if (!advanceFlows(world, pressure, sourceFluids, displayed)) return;

            if (!visualOnly) for (Endpoint source : sources) {
                int limit = remaining(pressure, remainingCapacity, new Face(source.pos, source.side));
                if (limit <= 0) continue;
                FluidStack sample = sourceFluids.get(source);
                if (sample == null) continue;
                if (source.tile instanceof PortableFluidInterfaceTileEntity portable
                        && hasPendingFlow(world, pressure, source, sample)) {
                    portable.keepAlive();
                }
                if (hasPortableSink) keepPendingPortableSinksAlive(world, pressure, source, sample);
                int remaining = limit;
                while (remaining > 0) {
                    List<Route> sinks = findSinks(world, pressure, remainingCapacity, source, sample);
                    if (sinks.isEmpty()) break;
                    int before = remaining;
                    int share = remaining / sinks.size();
                    int extra = remaining % sinks.size();
                    for (Route route : sinks) {
                        int offered = share + (extra-- > 0 ? 1 : 0);
                        if (offered == 0) break;
                        for (Face face : route.faces) {
                            offered = Math.min(offered, remaining(pressure, remainingCapacity, face));
                        }
                        int moved = move(source.handler, route.sink.handler, sample, offered);
                        remaining -= moved;
                        for (Face face : route.faces) {
                            remainingCapacity.put(face, remaining(pressure, remainingCapacity, face) - moved);
                        }
                    }
                    if (remaining == before) break;
                }
            }
            for (BlockPos pos : nodes) {
                if (world.getBlockState(pos).getBlock() instanceof GlassFluidPipeBlock
                        && world.getTileEntity(pos) instanceof FluidPipeTileEntity pipe) {
                    pipe.setDisplayedFluid(displayed.get(pos));
                }
            }
        }

        /** Keep a mobile source docked while its fluid is still advancing through the network. */
        private boolean hasPendingFlow(World world, Map<Face, Float> pressure, Endpoint source, FluidStack fluid) {
            Set<BlockPos> visited = new HashSet<>();
            Deque<Face> frontier = new ArrayDeque<>();
            frontier.add(new Face(source.pos, source.side));
            while (!frontier.isEmpty()) {
                Face incoming = frontier.removeFirst();
                if (!visited.add(incoming.pos) || !world.isBlockLoaded(incoming.pos)) continue;
                IBlockState state = world.getBlockState(incoming.pos);
                TileEntity tile = world.getTileEntity(incoming.pos);
                if (tile instanceof SmartFluidPipeTileEntity smart && !smart.allows(fluid)) continue;
                if (tile instanceof FluidValveTileEntity valve && !valve.isOpen()) continue;
                if (state.getBlock() instanceof MechanicalPumpBlock
                        && (!(tile instanceof MechanicalPumpTileEntity pump) || pump.getSpeed() == 0.0F
                        || incoming.side != state.getValue(MechanicalPumpBlock.FACING).getOpposite())) continue;

                OpenEndStorage.Flow inFlow = flowAt(world, incoming.pos, incoming.side);
                if (inFlow == null) return true;
                if (!inFlow.inbound || !inFlow.fluid.isFluidEqual(fluid)) continue;
                if (!inFlow.complete()) return true;

                for (EnumFacing side : EnumFacing.values()) {
                    if (side == incoming.side || !connects(world, incoming.pos, state, side)) continue;
                    Face outgoing = new Face(incoming.pos, side);
                    if (pressure.getOrDefault(outgoing, 0.0F) >= -PRESSURE_EPSILON) continue;
                    OpenEndStorage.Flow outFlow = flowAt(world, incoming.pos, side);
                    if (outFlow == null) return true;
                    if (outFlow.inbound || !outFlow.fluid.isFluidEqual(fluid)) continue;
                    if (!outFlow.complete()) return true;
                    BlockPos next = incoming.pos.offset(side);
                    Face nextFace = new Face(next, side.getOpposite());
                    if (nodes.contains(next) && world.isBlockLoaded(next)
                            && pressure.getOrDefault(nextFace, 0.0F) > PRESSURE_EPSILON
                            && connects(world, next, world.getBlockState(next), side.getOpposite())) {
                        frontier.addLast(nextFace);
                    }
                }
            }
            return false;
        }

        /** A receiving interface must stay docked while fluid is still travelling towards it. */
        private void keepPendingPortableSinksAlive(World world, Map<Face, Float> pressure,
                                                   Endpoint source, FluidStack fluid) {
            Set<Face> visited = new HashSet<>();
            Deque<PendingStep> queue = new ArrayDeque<>();
            queue.add(new PendingStep(source.pos, source.side, false));
            while (!queue.isEmpty()) {
                PendingStep step = queue.removeFirst();
                Face incoming = new Face(step.pos, step.entered);
                if (!visited.add(incoming) || !world.isBlockLoaded(step.pos)) continue;
                IBlockState state = world.getBlockState(step.pos);
                TileEntity tile = world.getTileEntity(step.pos);
                if (tile instanceof SmartFluidPipeTileEntity smart && !smart.allows(fluid)) continue;
                if (tile instanceof FluidValveTileEntity valve && !valve.isOpen()) continue;
                if (state.getBlock() instanceof MechanicalPumpBlock
                        && (!(tile instanceof MechanicalPumpTileEntity pump) || pump.getSpeed() == 0.0F
                        || step.entered != state.getValue(MechanicalPumpBlock.FACING).getOpposite())) continue;

                boolean pending = step.pending || !completeFlow(world, step.pos, step.entered, fluid, true);
                for (EnumFacing side : EnumFacing.values()) {
                    if (side == step.entered || !connects(world, step.pos, state, side)) continue;
                    Face outgoing = new Face(step.pos, side);
                    float outward = -pressure.getOrDefault(outgoing, 0.0F);
                    if (outward <= PRESSURE_EPSILON) continue;
                    boolean routePending = pending || !completeFlow(world, step.pos, side, fluid, false);
                    BlockPos next = step.pos.offset(side);
                    if (nodes.contains(next)) {
                        if (world.isBlockLoaded(next)
                                && connects(world, next, world.getBlockState(next), side.getOpposite())
                                && pressure.getOrDefault(new Face(next, side.getOpposite()), 0.0F)
                                > PRESSURE_EPSILON) {
                            queue.addLast(new PendingStep(next, side.getOpposite(), routePending));
                        }
                        continue;
                    }
                    if (!routePending) continue;
                    Endpoint sink = endpoint(world, step.pos, side, outward);
                    if (sink == null || !(sink.tile instanceof PortableFluidInterfaceTileEntity portable)
                            || sameStorage(source, sink)) continue;
                    FluidStack offered = fluid.copy();
                    offered.amount = 1;
                    if (sink.handler.fill(offered, false) > 0) portable.keepAlive();
                }
            }
        }

        private boolean advanceFlows(World world, Map<Face, Float> pressure,
                                     Map<Endpoint, FluidStack> sources, Map<BlockPos, FluidStack> displayed) {
            Map<Face, FluidStack> offered = new HashMap<>();
            for (Map.Entry<Endpoint, FluidStack> source : sources.entrySet()) {
                offered.put(new Face(source.getKey().pos, source.getKey().side), source.getValue());
            }

            // First collect inbound faces from the previous tick, so iteration order cannot move fluid instantly.
            for (BlockPos pos : nodes) {
                IBlockState state = world.getBlockState(pos);
                TileEntity tile = world.getTileEntity(pos);
                for (EnumFacing side : EnumFacing.values()) {
                    Face face = new Face(pos, side);
                    if (pressure.getOrDefault(face, 0.0F) <= PRESSURE_EPSILON
                            || !connects(world, pos, state, side)) continue;
                    BlockPos neighbor = pos.offset(side);
                    FluidStack fluid = offered.get(face);
                    if (nodes.contains(neighbor)) {
                        OpenEndStorage.Flow adjacent = flowAt(world, neighbor, side.getOpposite());
                        fluid = connects(world, neighbor, world.getBlockState(neighbor), side.getOpposite())
                                && pressure.getOrDefault(new Face(neighbor, side.getOpposite()), 0.0F)
                                < -PRESSURE_EPSILON && adjacent != null && !adjacent.inbound && adjacent.complete()
                                ? adjacent.fluid : null;
                    }
                    if (tile instanceof SmartFluidPipeTileEntity smart && !smart.allows(fluid)) fluid = null;
                    if (tile instanceof FluidValveTileEntity valve && !valve.isOpen()) fluid = null;
                    if (state.getBlock() instanceof MechanicalPumpBlock
                            && (!(tile instanceof MechanicalPumpTileEntity pump) || pump.getSpeed() == 0.0F
                            || side != state.getValue(MechanicalPumpBlock.FACING).getOpposite())) fluid = null;
                    if (fluid == null) offered.remove(face);
                    else offered.put(face, fluid);
                }
            }

            for (BlockPos pos : nodes) {
                IBlockState state = world.getBlockState(pos);
                TileEntity tile = world.getTileEntity(pos);
                FluidStack inside = null;
                for (EnumFacing side : EnumFacing.values()) {
                    Face face = new Face(pos, side);
                    FluidStack next = offered.get(face);
                    OpenEndStorage.Flow flow = flowAt(world, pos, side);
                    if (next == null || flow == null || !flow.inbound || !flow.complete()
                            || !next.isFluidEqual(flow.fluid)) continue;
                    if (inside == null) inside = flow.fluid;
                    else if (!inside.isFluidEqual(flow.fluid)) {
                        com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "cross_streams");
                        world.destroyBlock(pos, true);
                        if (inside.getFluid() == FluidRegistry.WATER && flow.fluid.getFluid() == FluidRegistry.LAVA
                                || inside.getFluid() == FluidRegistry.LAVA && flow.fluid.getFluid() == FluidRegistry.WATER) {
                            world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);
                        }
                        FluidTransferManager.markDirty(world);
                        return false;
                    }
                }
                if (inside == null) continue;
                for (EnumFacing side : EnumFacing.values()) {
                    Face face = new Face(pos, side);
                    if (pressure.getOrDefault(face, 0.0F) >= -PRESSURE_EPSILON
                            || !connects(world, pos, state, side)) continue;
                    if (state.getBlock() instanceof MechanicalPumpBlock
                            && (!(tile instanceof MechanicalPumpTileEntity pump) || pump.getSpeed() == 0.0F
                            || side != state.getValue(MechanicalPumpBlock.FACING))) continue;
                    offered.put(face, inside);
                }
            }

            for (BlockPos pos : nodes) {
                TileEntity tile = world.getTileEntity(pos);
                if (!(tile instanceof OpenEndProvider provider)) continue;
                OpenEndStorage storage = provider.getOpenEnds();
                boolean changed = false;
                for (EnumFacing side : EnumFacing.values()) {
                    Face face = new Face(pos, side);
                    float facePressure = pressure.getOrDefault(face, 0.0F);
                    FluidStack fluid = offered.get(face);
                    OpenEndStorage.Flow next = null;
                    if (fluid != null && Math.abs(facePressure) > PRESSURE_EPSILON) {
                        boolean inbound = facePressure > 0;
                        OpenEndStorage.Flow old = storage.getFlow(side);
                        float progress = old != null && old.inbound == inbound && old.fluid.isFluidEqual(fluid)
                                ? old.progress : 0.0F;
                        float speed = 1.0F / 32.0F
                                + Math.min(1.0F, Math.abs(facePressure) / 128.0F) * 31.0F / 32.0F;
                        next = new OpenEndStorage.Flow(fluid, inbound, Math.min(1.0F, progress + speed), speed);
                        if (world.getBlockState(pos).getBlock() instanceof GlassFluidPipeBlock) {
                            displayed.put(pos, fluid);
                        }
                    }
                    changed |= storage.setFlow(side, next);
                }
                if (changed && world.getBlockState(pos).getBlock() instanceof GlassFluidPipeBlock) {
                    IBlockState state = world.getBlockState(pos);
                    world.notifyBlockUpdate(pos, state, state, 2);
                }
            }
            return true;
        }

        private void distribute(World world, Map<Face, Float> pressure, BlockPos pumpPos,
                                EnumFacing side, float strength, boolean pull) {
            addPressure(pressure, new Face(pumpPos, side), pull ? strength : -strength);
            BlockPos first = pumpPos.offset(side);
            if (!nodes.contains(first)
                    || !connects(world, first, world.getBlockState(first), side.getOpposite())) return;
            if (pumps.contains(first)) return;

            Map<BlockPos, PressureNode> graph = new HashMap<>();
            PressureNode root = new PressureNode(first, 1);
            graph.put(first, root);
            Deque<PressureNode> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty()) {
                PressureNode current = queue.removeFirst();
                IBlockState state = world.getBlockState(current.pos);
                for (EnumFacing direction : EnumFacing.values()) {
                    if (!connects(world, current.pos, state, direction)) continue;
                    BlockPos next = current.pos.offset(direction);
                    if (!world.isBlockLoaded(next)) continue;
                    if (nodes.contains(next)) {
                        if (next.equals(pumpPos) || !connects(world, next, world.getBlockState(next),
                                direction.getOpposite())) continue;
                        if (pumps.contains(next)) {
                            EnumFacing front = world.getBlockState(next).getValue(MechanicalPumpBlock.FACING);
                            if (front == (pull ? direction.getOpposite() : direction)) {
                                current.branches.add(new PressureBranch(direction, null));
                            }
                            continue;
                        }
                        if (current.distance + 1 >= FluidConfig.mechanicalPumpRange) {
                            PressureNode known = graph.get(next);
                            if (known == null || known.distance > current.distance) {
                                current.branches.add(new PressureBranch(direction, null));
                            }
                            continue;
                        }
                        PressureNode child = graph.get(next);
                        if (child == null) {
                            child = new PressureNode(next, current.distance + 1);
                            graph.put(next, child);
                            queue.addLast(child);
                        } else if (child.distance != current.distance + 1) {
                            continue;
                        }
                        current.branches.add(new PressureBranch(direction, child));
                    } else if (endpoint(world, current.pos, direction, strength) != null) {
                        current.branches.add(new PressureBranch(direction, null));
                    }
                }
            }

            Map<Integer, Map<Face, Boolean>> validFaces = new HashMap<>();
            collectPressureFaces(root, side.getOpposite(), validFaces, new HashMap<>());
            for (Map<Face, Boolean> faces : validFaces.values()) {
                float divided = strength / Math.max(1, faces.size() - 1);
                for (Map.Entry<Face, Boolean> entry : faces.entrySet()) {
                    addPressure(pressure, entry.getKey(), (entry.getValue() == pull ? -divided : divided));
                }
            }
        }

        private boolean collectPressureFaces(PressureNode node, EnumFacing entered,
                                             Map<Integer, Map<Face, Boolean>> validFaces,
                                             Map<BlockPos, Boolean> reachable) {
            Map<Face, Boolean> faces = validFaces.computeIfAbsent(node.distance, ignored -> new HashMap<>());
            Boolean cached = reachable.get(node.pos);
            boolean reachesEndpoint = cached != null && cached;
            if (cached == null) {
                for (PressureBranch branch : node.branches) {
                    if (branch.child != null && !collectPressureFaces(branch.child, branch.side.getOpposite(),
                            validFaces, reachable)) continue;
                    faces.put(new Face(node.pos, branch.side), false);
                    reachesEndpoint = true;
                }
                reachable.put(node.pos, reachesEndpoint);
            }
            if (reachesEndpoint) faces.put(new Face(node.pos, entered), true);
            return reachesEndpoint;
        }

        private List<Route> findSinks(World world, Map<Face, Float> pressure,
                                      Map<Face, Integer> remainingCapacity,
                                      Endpoint source, FluidStack fluid) {
            List<Route> sinks = new ArrayList<>();
            Set<BlockPos> visited = new HashSet<>();
            Deque<RouteStep> queue = new ArrayDeque<>();
            List<Face> start = new ArrayList<>();
            start.add(new Face(source.pos, source.side));
            queue.add(new RouteStep(source.pos, source.side, source.pressure, start));
            while (!queue.isEmpty()) {
                RouteStep step = queue.removeFirst();
                if (visited.contains(step.pos) || !world.isBlockLoaded(step.pos)) continue;
                if (!completeFlow(world, step.pos, step.entered, fluid, true)) continue;
                IBlockState state = world.getBlockState(step.pos);
                TileEntity tile = world.getTileEntity(step.pos);
                if (tile instanceof SmartFluidPipeTileEntity smart && !smart.allows(fluid)) continue;
                if (tile instanceof FluidValveTileEntity valve && !valve.isOpen()) continue;
                if (state.getBlock() instanceof MechanicalPumpBlock) {
                    if (!(tile instanceof MechanicalPumpTileEntity pump) || pump.getSpeed() == 0.0F
                            || step.entered != state.getValue(MechanicalPumpBlock.FACING).getOpposite()) continue;
                }
                visited.add(step.pos);
                for (EnumFacing side : EnumFacing.values()) {
                    if (side == step.entered || !connects(world, step.pos, state, side)) continue;
                    Face outgoing = new Face(step.pos, side);
                    if (!completeFlow(world, step.pos, side, fluid, false)) continue;
                    float outward = -pressure.getOrDefault(outgoing, 0.0F);
                    if (outward <= PRESSURE_EPSILON) continue;
                    if (remaining(pressure, remainingCapacity, outgoing) <= 0) continue;
                    float routePressure = Math.min(step.pressure, outward);
                    if (tile instanceof MechanicalPumpTileEntity pump) {
                        routePressure = Math.min(routePressure, Math.abs(pump.getSpeed()));
                    }
                    BlockPos next = step.pos.offset(side);
                    if (nodes.contains(next)) {
                        if (!world.isBlockLoaded(next) || visited.contains(next)
                                || !connects(world, next, world.getBlockState(next), side.getOpposite())) continue;
                        Face incoming = new Face(next, side.getOpposite());
                        float inward = pressure.getOrDefault(incoming, 0.0F);
                        if (inward > PRESSURE_EPSILON && remaining(pressure, remainingCapacity, incoming) > 0) {
                            List<Face> faces = new ArrayList<>(step.faces);
                            faces.add(outgoing);
                            faces.add(incoming);
                            queue.addLast(new RouteStep(next, side.getOpposite(), Math.min(routePressure, inward), faces));
                        }
                    } else {
                        Endpoint sink = endpoint(world, step.pos, side, routePressure);
                        if (sink != null && !sameStorage(source, sink)) {
                            List<Face> faces = new ArrayList<>(step.faces);
                            faces.add(outgoing);
                            sinks.add(new Route(sink, faces));
                        }
                    }
                }
            }
            return sinks;
        }
    }

    private static void addPressure(Map<Face, Float> pressure, Face face, float amount) {
        pressure.merge(face, amount, Float::sum);
    }

    private static boolean flipReversedFlowsAt(World world, Map<Face, Float> pressure, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof OpenEndProvider provider)) return false;
        OpenEndStorage storage = provider.getOpenEnds();
        boolean changed = false;
        for (EnumFacing side : EnumFacing.values()) {
            float value = pressure.getOrDefault(new Face(pos, side), 0.0F);
            OpenEndStorage.Flow flow = storage.getFlow(side);
            if (flow == null || Math.abs(value) <= PRESSURE_EPSILON || flow.inbound == (value > 0)) continue;
            changed |= storage.setFlow(side, flow.complete()
                    ? new OpenEndStorage.Flow(flow.fluid, value > 0, 1.0F, flow.speed) : null);
        }
        return changed;
    }

    @Nullable
    private static OpenEndStorage.Flow flowAt(World world, BlockPos pos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof OpenEndProvider provider ? provider.getOpenEnds().getFlow(side) : null;
    }

    private static boolean completeFlow(World world, BlockPos pos, EnumFacing side,
                                        FluidStack fluid, boolean inbound) {
        OpenEndStorage.Flow flow = flowAt(world, pos, side);
        return flow != null && flow.inbound == inbound && flow.complete() && flow.fluid.isFluidEqual(fluid);
    }

    private static int remaining(Map<Face, Float> pressure, Map<Face, Integer> capacities, Face face) {
        return capacities.computeIfAbsent(face, key -> Math.max(1,
                (int) (Math.abs(pressure.getOrDefault(key, 0.0F)) / 2.0F)));
    }

    @Nullable
    private static Endpoint endpoint(World world, BlockPos pos, EnumFacing side, float pressure) {
        BlockPos target = pos.offset(side);
        if (!world.isBlockLoaded(target)) return null;
        IBlockState targetState = world.getBlockState(target);
        if (isPipe(targetState) || targetState.getBlock() instanceof MechanicalPumpBlock) return null;
        TileEntity tile = world.getTileEntity(target);
        IFluidHandler handler = tile == null ? null : tile.getCapability(
                CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
        if (handler == null) {
            TileEntity owner = world.getTileEntity(pos);
            handler = owner instanceof OpenEndProvider provider ? provider.getOpenEnds().get(side) : null;
            tile = null;
        }
        return handler == null ? null : new Endpoint(pos, side, tile, handler, pressure);
    }

    private static int move(IFluidHandler source, IFluidHandler sink, FluidStack fluid, int limit) {
        if (limit <= 0) return 0;
        FluidStack request = fluid.copy();
        request.amount = limit;
        FluidStack available = source.drain(request, false);
        boolean generic = false;
        if (available == null || available.amount <= 0) {
            available = source.drain(limit, false);
            generic = true;
        }
        if (available == null || available.amount <= 0 || !fluid.isFluidEqual(available)) return 0;
        int accepted = Math.min(available.amount, sink.fill(available.copy(), false));
        if (accepted <= 0) {
            OpenEndStorage.reactWithWorld(sink, available);
            return 0;
        }
        request.amount = accepted;
        FluidStack extracted = generic ? source.drain(accepted, true) : source.drain(request, true);
        if (extracted == null || extracted.amount <= 0) return 0;
        int inserted = Math.max(0, Math.min(extracted.amount, sink.fill(extracted, true)));
        if (inserted < extracted.amount) {
            FluidStack remainder = extracted.copy();
            remainder.amount -= inserted;
            source.fill(remainder, true);
        }
        return inserted;
    }

    @Nullable
    private static FluidStack availableFluid(IFluidHandler handler, int limit) {
        if (limit <= 0) return null;
        FluidStack available = handler.drain(limit, false);
        if (available != null && available.amount > 0) return available;
        FluidStack stored = firstStoredFluid(handler, limit);
        if (stored == null) return null;
        available = handler.drain(stored, false);
        return available == null || available.amount <= 0 ? null : available;
    }

    @Nullable
    private static FluidStack firstStoredFluid(IFluidHandler handler, int limit) {
        IFluidTankProperties[] tanks = handler.getTankProperties();
        if (tanks == null) return null;
        for (IFluidTankProperties tank : tanks) {
            FluidStack fluid = tank.getContents();
            if (fluid == null || fluid.amount <= 0) continue;
            fluid = fluid.copy();
            fluid.amount = Math.min(limit, fluid.amount);
            return fluid;
        }
        return null;
    }

    private static boolean sameStorage(Endpoint first, Endpoint second) {
        if (first.handler == second.handler) return true;
        if (first.tile == null || second.tile == null) return false;
        if (first.tile == second.tile) return true;
        if (first.tile instanceof PortableFluidInterfaceTileEntity a
                && second.tile instanceof PortableFluidInterfaceTileEntity b
                && a.sharesConnectedStorageWith(b)) return true;
        if (first.tile instanceof FluidTankTileEntity a && second.tile instanceof FluidTankTileEntity b) {
            return a.getWorld() == b.getWorld() && a.getControllerPos().equals(b.getControllerPos());
        }
        return false;
    }

    private static boolean isPipe(IBlockState state) {
        return state.getBlock() instanceof FluidPipeBlock || state.getBlock() instanceof FluidValveBlock;
    }

    private static boolean connects(World world, BlockPos pos, IBlockState state, EnumFacing side) {
        if (state.getBlock() instanceof FluidPipeBlock pipe) return pipe.canConnectTowards(world, pos, side);
        if (state.getBlock() instanceof FluidValveBlock valve) return valve.canConnectTowards(world, pos, side);
        return state.getBlock() instanceof MechanicalPumpBlock
                && state.getValue(MechanicalPumpBlock.FACING).getAxis() == side.getAxis();
    }

    private static final class Endpoint {
        private final BlockPos pos;
        private final EnumFacing side;
        @Nullable private final TileEntity tile;
        private final IFluidHandler handler;
        private final float pressure;

        private Endpoint(BlockPos pos, EnumFacing side, @Nullable TileEntity tile,
                         IFluidHandler handler, float pressure) {
            this.pos = pos;
            this.side = side;
            this.tile = tile;
            this.handler = handler;
            this.pressure = pressure;
        }
    }

    private record Face(BlockPos pos, EnumFacing side) { }
    private record PumpPressure(EnumFacing front, float strength) { }
    private static final class PressureNode {
        private final BlockPos pos;
        private final int distance;
        private final List<PressureBranch> branches = new ArrayList<>();

        private PressureNode(BlockPos pos, int distance) {
            this.pos = pos;
            this.distance = distance;
        }
    }

    private record PressureBranch(EnumFacing side, @Nullable PressureNode child) { }
    private record RouteStep(BlockPos pos, EnumFacing entered, float pressure, List<Face> faces) { }
    private record PendingStep(BlockPos pos, EnumFacing entered, boolean pending) { }
    private record Route(Endpoint sink, List<Face> faces) { }
}
