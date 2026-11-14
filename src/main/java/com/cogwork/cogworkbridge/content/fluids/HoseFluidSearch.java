package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/** Incremental world-fluid searches used by a stationary hose endpoint. */
final class HoseFluidSearch {
    private static final int SEARCH_PER_TICK = 1024;

    private final HosePulleyTileEntity owner;
    private final Deque<Entry> frontier = new ArrayDeque<>();
    private final Set<BlockPos> visited = new HashSet<>();
    private final PriorityQueue<Entry> sources = new PriorityQueue<>(
            Comparator.<Entry>comparingInt(entry -> entry.pos().getY()).reversed()
                    .thenComparing(Comparator.comparingInt(Entry::distance).reversed()));
    private final PriorityQueue<Entry> fillQueue = new PriorityQueue<>(
            Comparator.<Entry>comparingInt(entry -> entry.pos().getY()).thenComparingInt(Entry::distance));
    private final Set<BlockPos> fillVisited = new HashSet<>();
    private final Deque<BlockPos> fillValidationFrontier = new ArrayDeque<>();
    private final Set<BlockPos> fillValidationVisited = new HashSet<>();
    @Nullable private BlockPos root;
    @Nullable private Fluid sourceFluid;
    @Nullable private Fluid fillFluid;
    private boolean searching;
    private boolean infinite;
    boolean isInfinite() { return infinite; }
    private boolean fillInfinite;
    private int fillValidationFluidCount;
    private int recheck;
    private int fillRecheck;

    HoseFluidSearch(HosePulleyTileEntity owner) { this.owner = owner; }

    void reset() {
        root = null;
        sourceFluid = null;
        fillFluid = null;
        frontier.clear();
        visited.clear();
        sources.clear();
        fillQueue.clear();
        fillVisited.clear();
        fillValidationFrontier.clear();
        fillValidationVisited.clear();
        searching = false;
        infinite = false;
        fillInfinite = false;
        fillValidationFluidCount = 0;
        recheck = 0;
        fillRecheck = 0;
    }

    void tick(BlockPos endpoint) {
        if (root == null || !root.equals(endpoint)) return;
        World world = owner.getWorld();
        if (world == null || world.isRemote) return;
        if (fillFluid != null && fillRecheck > 0 && --fillRecheck == 0) {
            fillVisited.clear();
            fillQueue.clear();
            fillQueue.add(new Entry(endpoint.toImmutable(), 0));
            startFillValidation(endpoint);
        }
        continueFillValidation();
        if (searching) {
            for (int count = 0; count < SEARCH_PER_TICK && !frontier.isEmpty(); count++) {
                Entry entry = frontier.removeFirst();
                BlockPos at = entry.pos();
                if (!visited.add(at)) continue;
                if (!world.isBlockLoaded(at)) {
                    abortSourceSearch();
                    return;
                }
                Fluid found = fluidAt(world, at);
                if (found == null || found != sourceFluid) continue;
                if (sourceAt(world, at) != null) sources.add(entry);
                if (FluidConfig.canDrainInfinitely(found) && visited.size() >= FluidConfig.hosePulleyBlockThreshold) {
                    infinite = true;
                    frontier.clear();
                    break;
                }
                for (EnumFacing side : EnumFacing.values()) {
                    if (side == EnumFacing.DOWN) continue;
                    BlockPos next = at.offset(side);
                    if (visited.contains(next) || !withinRange(next)) continue;
                    if (!world.isBlockLoaded(next)) {
                        abortSourceSearch();
                        return;
                    }
                    if (fluidAt(world, next) == sourceFluid) {
                        IBlockState neighbor = world.getBlockState(next);
                        if (side == EnumFacing.UP && (next.getX() != root.getX() || next.getZ() != root.getZ())
                                && neighbor.getPropertyKeys().contains(BlockLiquid.LEVEL)
                                && neighbor.getValue(BlockLiquid.LEVEL) != 0) continue;
                        frontier.addLast(new Entry(next.toImmutable(), entry.distance() + 1));
                    }
                }
            }
            if (frontier.isEmpty()) {
                searching = false;
                recheck = 160;
            }
        } else if (recheck > 0 && --recheck == 0) {
            startSourceSearch(endpoint);
        }
    }

    @Nullable FluidStack findSource(BlockPos endpoint) {
        ensureRoot(endpoint);
        World world = owner.getWorld();
        if (world == null || !world.isBlockLoaded(endpoint)) return null;
        // A completed or bottomless search is valid only while its hose end
        // is still immersed in the same fluid. The world can change without
        // the pulley moving, for example when a player removes the source.
        if (sourceFluid != null && fluidAt(world, endpoint) != sourceFluid) {
            resetSourceSearch();
        }
        if (sourceFluid == null) {
            startSourceSearch(endpoint);
            return null;
        }
        if (searching) return null;
        while (!sources.isEmpty()) {
            FluidStack source = sourceAt(world, sources.peek().pos());
            if (source != null && source.getFluid() == sourceFluid && source.amount == 1000) return source;
            sources.poll();
        }
        return null;
    }

    @Nullable FluidStack pull(BlockPos endpoint) {
        FluidStack available = findSource(endpoint);
        if (available == null || sources.isEmpty()) return null;
        if (infinite) {
            resetFillSearch();
            playTransferEffect(owner.getWorld(), endpoint, available.getFluid(), true);
            return available;
        }
        World world = owner.getWorld();
        BlockPos target = sources.poll().pos();
        IBlockState state = world.getBlockState(target);
        FluidStack drained = state.getBlock() instanceof IFluidBlock block
                ? block.drain(world, target, true)
                : world.setBlockState(target, Blocks.AIR.getDefaultState(), 3) ? available : null;
        if (drained != null && drained.amount > 0) {
            resetFillSearch();
            playTransferEffect(world, target, drained.getFluid(), true);
        }
        return drained;
    }

    @Nullable BlockPos findFillTarget(BlockPos endpoint, Fluid fluid) {
        ensureRoot(endpoint);
        World world = owner.getWorld();
        if (fluid.getBlock() == null) return null;
        if (fluid == FluidRegistry.WATER && world.provider.doesWaterVaporize()) return endpoint;
        if (!FluidConfig.fluidFillPlaceFluidSourceBlocks) return endpoint;
        if (fillFluid != fluid) {
            fillFluid = fluid;
            fillVisited.clear();
            fillQueue.clear();
            fillQueue.add(new Entry(endpoint.toImmutable(), 0));
            fillValidationFrontier.clear();
            fillValidationVisited.clear();
            fillInfinite = false;
            fillRecheck = 1;
        }
        if (fillInfinite && !FluidConfig.fillInfinite) return fluidAt(world, endpoint) == fluid ? endpoint : null;
        for (int count = 0; count < SEARCH_PER_TICK && !fillQueue.isEmpty(); count++) {
            Entry entry = fillQueue.peek();
            BlockPos at = entry.pos();
            if (!world.isBlockLoaded(at)) {
                fillFluid = null;
                fillVisited.clear();
                fillQueue.clear();
                return null;
            }
            if (fillVisited.contains(at)) {
                fillQueue.poll();
                continue;
            }
            IBlockState state = world.getBlockState(at);
            Fluid existing = fluidAt(world, at);
            if (existing == fluid && sourceAt(world, at) != null) {
                consumeFillEntry(entry);
                continue;
            }
            if (existing == null && state.getBlock().isReplaceable(world, at)
                    || existing == fluid && sourceAt(world, at) == null) return at;
            fillVisited.add(at);
            fillQueue.poll();
        }
        return null;
    }

    boolean deposit(BlockPos endpoint, Fluid fluid) {
        BlockPos target = findFillTarget(endpoint, fluid);
        if (target == null) return false;
        World world = owner.getWorld();
        if (fluid == FluidRegistry.WATER && world.provider.doesWaterVaporize()) {
            world.playEvent(1501, target, 0);
            playTransferEffect(world, target, fluid, false);
            resetSourceSearch();
            return true;
        }
        if (!FluidConfig.fluidFillPlaceFluidSourceBlocks
                || fillInfinite && !FluidConfig.fillInfinite && fluidAt(world, endpoint) == fluid) {
            playTransferEffect(world, target, fluid, false);
            resetSourceSearch();
            return true;
        }
        if (!world.setBlockState(target, fluid.getBlock().getDefaultState(), 3)) return false;
        playTransferEffect(world, target, fluid, false);
        Entry entry = fillQueue.peek();
        if (entry != null && entry.pos().equals(target)) consumeFillEntry(entry);
        resetSourceSearch();
        return true;
    }

    private void consumeFillEntry(Entry entry) {
        fillQueue.poll();
        fillVisited.add(entry.pos());
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos next = entry.pos().offset(side);
            if (!fillVisited.contains(next) && withinRange(next)) {
                fillQueue.add(new Entry(next.toImmutable(), entry.distance() + 1));
            }
        }
    }

    private void ensureRoot(BlockPos endpoint) {
        if (root != null && root.equals(endpoint)) return;
        reset();
        root = endpoint.toImmutable();
    }

    private void startSourceSearch(BlockPos endpoint) {
        World world = owner.getWorld();
        if (world == null || !world.isBlockLoaded(endpoint)) return;
        sourceFluid = fluidAt(world, endpoint);
        frontier.clear();
        visited.clear();
        sources.clear();
        infinite = false;
        searching = sourceFluid != null;
        if (searching) frontier.add(new Entry(endpoint.toImmutable(), 0));
        recheck = 160;
    }

    private void abortSourceSearch() {
        frontier.clear();
        sources.clear();
        visited.clear();
        searching = false;
        infinite = false;
        recheck = 160;
    }

    private boolean withinRange(BlockPos pos) {
        return root != null && pos.getY() >= 0 && pos.getY() < 256
                && root.distanceSq(pos) <= (double) FluidConfig.hosePulleyRange * FluidConfig.hosePulleyRange;
    }

    private void resetSourceSearch() {
        sourceFluid = null;
        frontier.clear();
        visited.clear();
        sources.clear();
        searching = false;
        infinite = false;
        recheck = 0;
    }

    private void resetFillSearch() {
        fillFluid = null;
        fillQueue.clear();
        fillVisited.clear();
        fillValidationFrontier.clear();
        fillValidationVisited.clear();
        fillInfinite = false;
        fillValidationFluidCount = 0;
        fillRecheck = 0;
    }

    private void startFillValidation(BlockPos endpoint) {
        fillValidationFrontier.clear();
        fillValidationVisited.clear();
        fillValidationFluidCount = 0;
        int threshold = FluidConfig.hosePulleyBlockThreshold;
        int interval = threshold < 0 ? 160 : Math.max(160, threshold / SEARCH_PER_TICK + 1);
        fillRecheck = interval;
        if (FluidConfig.fillInfinite || threshold < 0 || !owner.getWorld().isBlockLoaded(endpoint)
                || fluidAt(owner.getWorld(), endpoint) != fillFluid) {
            fillInfinite = false;
            return;
        }
        fillValidationFrontier.add(endpoint.toImmutable());
    }

    private void continueFillValidation() {
        if (fillFluid == null || fillValidationFrontier.isEmpty()) return;
        World world = owner.getWorld();
        for (int count = 0; count < SEARCH_PER_TICK && !fillValidationFrontier.isEmpty(); count++) {
            BlockPos at = fillValidationFrontier.removeFirst();
            if (!fillValidationVisited.add(at)) continue;
            if (!world.isBlockLoaded(at)) {
                fillValidationFrontier.clear();
                fillValidationVisited.clear();
                fillRecheck *= 2;
                return;
            }
            if (fluidAt(world, at) != fillFluid) continue;
            fillValidationFluidCount++;
            if (!FluidConfig.fillInfinite && FluidConfig.hosePulleyBlockThreshold >= 0
                    && fillValidationFluidCount >= FluidConfig.hosePulleyBlockThreshold) {
                fillInfinite = true;
                fillValidationFrontier.clear();
                fillValidationVisited.clear();
                return;
            }
            for (EnumFacing side : EnumFacing.values()) {
                BlockPos next = at.offset(side);
                if (withinRange(next) && !fillValidationVisited.contains(next)) {
                    fillValidationFrontier.addLast(next.toImmutable());
                }
            }
        }
        if (fillValidationFrontier.isEmpty()) {
            fillInfinite = false;
            fillValidationVisited.clear();
        }
    }

    @Nullable private static Fluid fluidAt(World world, BlockPos pos) {
        return WorldFluidHelper.fluidOf(world.getBlockState(pos));
    }

    @Nullable private static FluidStack sourceAt(World world, BlockPos pos) {
        if (world == null || !world.isBlockLoaded(pos)) return null;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof IFluidBlock fluidBlock) {
            return fluidBlock.canDrain(world, pos) ? fluidBlock.drain(world, pos, false) : null;
        }
        Fluid fluid = fluidAt(world, pos);
        if (fluid == null || state.getPropertyKeys().contains(BlockLiquid.LEVEL)
                && state.getValue(BlockLiquid.LEVEL) != 0) return null;
        return new FluidStack(fluid, 1000);
    }

    private static void playTransferEffect(World world, BlockPos pos, Fluid fluid, boolean pulling) {
        if (world == null || world.isRemote || fluid == null) return;
        FluidStack stack = new FluidStack(fluid, 1);
        SoundEvent sound = pulling ? fluid.getFillSound(stack) : fluid.getEmptySound(stack);
        if (sound == null) sound = pulling ? SoundEvents.ITEM_BUCKET_FILL : SoundEvents.ITEM_BUCKET_EMPTY;
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 0.3F, 1.0F);
        Block block = fluid.getBlock();
        if (block != null && world instanceof WorldServer server) {
            server.spawnParticle(EnumParticleTypes.BLOCK_CRACK,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    20, 0.25D, 0.25D, 0.25D, 0.25D,
                    Block.getStateId(block.getDefaultState()));
        }
    }

    private record Entry(BlockPos pos, int distance) { }
}
