package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FluidTankConnectivity {
    private static final int MAX_WIDTH = 3;
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);

    private FluidTankConnectivity() { }

    static void markAround(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        mark(world, pos);
        for (EnumFacing side : EnumFacing.values()) mark(world, pos.offset(side));
    }

    private static void mark(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return;
        if (world.getTileEntity(pos) instanceof FluidTankTileEntity tank
                && world.getBlockState(pos).getBlock() instanceof FluidTankBlock) {
            tank.requestConnectivityUpdate();
        }
    }

    static void rebuild(FluidTankTileEntity seed) {
        World world = seed.getWorld();
        if (world == null || world.isRemote) return;
        Block block = world.getBlockState(seed.getPos()).getBlock();
        if (!(block instanceof FluidTankBlock)) return;

        Map<BlockPos, FluidTankTileEntity> component = new LinkedHashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.getPos());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (component.containsKey(pos) || !world.isBlockLoaded(pos)
                    || world.getBlockState(pos).getBlock() != block) continue;
            TileEntity tile = world.getTileEntity(pos);
            if (!(tile instanceof FluidTankTileEntity part) || part.getClass() != seed.getClass()) continue;
            component.put(pos.toImmutable(), part);
            for (EnumFacing side : EnumFacing.values()) queue.addLast(pos.offset(side));
        }
        if (component.isEmpty() || !allExistingGroupsLoaded(world, component)) return;
        if (isUnchangedGroup(component)) {
            for (FluidTankTileEntity part : component.values()) part.finishConnectivityUpdate();
            return;
        }

        Map<BlockPos, List<FluidTankTileEntity>> oldGroups = new LinkedHashMap<>();
        for (FluidTankTileEntity part : component.values()) {
            BlockPos rootPos = part.getControllerPos();
            if (!component.containsKey(rootPos)) rootPos = part.getPos();
            oldGroups.computeIfAbsent(rootPos, ignored -> new ArrayList<>()).add(part);
        }
        for (Map.Entry<BlockPos, List<FluidTankTileEntity>> entry : oldGroups.entrySet()) {
            FluidTankTileEntity root = component.get(entry.getKey());
            if (root != null) split(root, entry.getValue(), null);
        }

        Set<BlockPos> available = new HashSet<>(component.keySet());
        List<BlockPos> origins = new ArrayList<>(component.keySet());
        origins.sort(POSITION_ORDER);
        while (!available.isEmpty()) {
            Shape best = null;
            for (BlockPos origin : origins) {
                if (!available.contains(origin)) continue;
                for (int width = 1; width <= MAX_WIDTH; width++) {
                    int height = heightFor(component, available, origin, width);
                    if (height == 0) continue;
                    Shape candidate = new Shape(origin, width, height);
                    if (best == null || candidate.size() > best.size()
                            || candidate.size() == best.size() && width > best.width()) best = candidate;
                }
            }
            if (best == null) break;
            form(component, available, best);
        }
        for (FluidTankTileEntity part : component.values()) {
            part.setRestructuring(false);
            part.syncStructure();
        }
        for (FluidTankTileEntity part : component.values()) {
            if (part.isController()) part.refreshLighting();
        }
    }

    private static boolean isUnchangedGroup(Map<BlockPos, FluidTankTileEntity> component) {
        FluidTankTileEntity first = component.values().iterator().next();
        FluidTankTileEntity root = first.getControllerTile();
        if (root == null || root.getHeight() > FluidConfig.fluidTankMaxHeight) return false;
        int width = root.getWidth();
        int height = root.getHeight();
        int size = width * width * height;
        if (component.size() != size || root.getCapacity() != FluidTankTileEntity.capacityPerBlock() * size) {
            return false;
        }
        FluidStack fluid = root.getFluid();
        if (fluid != null && fluid.amount > root.getCapacity()) return false;
        BlockPos origin = root.getPos();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    FluidTankTileEntity part = component.get(origin.add(x, y, z));
                    if (part == null || !part.getControllerPos().equals(origin)
                            || part.getWidth() != width || part.getHeight() != height
                            || part.hasWindows() != root.hasWindows()) return false;
                }
            }
        }
        return true;
    }

    private static boolean allExistingGroupsLoaded(World world, Map<BlockPos, FluidTankTileEntity> component) {
        Set<BlockPos> checkedRoots = new HashSet<>();
        for (FluidTankTileEntity part : component.values()) {
            BlockPos rootPos = part.getControllerPos();
            if (!world.isBlockLoaded(rootPos)) return false;
            FluidTankTileEntity root = part.getControllerTile();
            if (root == null) {
                // Block states can load before their tile entities. Do not split a saved group
                // until its still-present controller is ready, or its contents can be discarded.
                if (world.getBlockState(rootPos).getBlock() == world.getBlockState(part.getPos()).getBlock()
                        && world.getTileEntity(rootPos) == null) return false;
                continue;
            }
            if (!checkedRoots.add(rootPos)) continue;
            for (int y = 0; y < root.getHeight(); y++) {
                for (int x = 0; x < root.getWidth(); x++) {
                    for (int z = 0; z < root.getWidth(); z++) {
                        if (!world.isBlockLoaded(rootPos.add(x, y, z))) return false;
                        BlockPos expected = rootPos.add(x, y, z);
                        // Loaded block sections can become visible before their tile entities are installed.
                        if (world.getBlockState(expected).getBlock() instanceof FluidTankBlock
                                && world.getTileEntity(expected) == null) return false;
                    }
                }
            }
        }
        return true;
    }

    static void beforeRemoved(World world, BlockPos removed) {
        if (world == null || world.isRemote || !(world.getTileEntity(removed) instanceof FluidTankTileEntity part)) return;
        BlockPos controller = part.getControllerPos();
        if (!world.isBlockLoaded(controller)) world.getChunk(controller);
        FluidTankTileEntity root = part.getControllerTile();
        if (root == null || root.getWidth() == 1 && root.getHeight() == 1) return;
        List<FluidTankTileEntity> members = new ArrayList<>();
        BlockPos origin = root.getPos();
        // A tank spans at most four chunks; load its members before redistributing the controller's contents.
        for (int x = 0; x < root.getWidth(); x++) {
            for (int z = 0; z < root.getWidth(); z++) {
                BlockPos column = origin.add(x, 0, z);
                if (!world.isBlockLoaded(column)) world.getChunk(column);
            }
        }
        for (int y = 0; y < root.getHeight(); y++) {
            for (int x = 0; x < root.getWidth(); x++) {
                for (int z = 0; z < root.getWidth(); z++) {
                    BlockPos at = origin.add(x, y, z);
                    if (!world.isBlockLoaded(at)) continue;
                    if (world.getTileEntity(at) instanceof FluidTankTileEntity member
                            && member.getControllerPos().equals(origin)) members.add(member);
                }
            }
        }
        split(root, members, removed);
        for (FluidTankTileEntity member : members) {
            member.setRestructuring(false);
            if (!member.getPos().equals(removed)) {
                member.requestConnectivityUpdate();
                member.syncStructure();
                member.refreshLighting();
            }
        }
    }

    private static void split(FluidTankTileEntity root, List<FluidTankTileEntity> members,
                              @Nullable BlockPos excluded) {
        FluidStack original = rawFluid(root);
        if (original != null) original = original.copy();
        int remaining = original == null ? 0 : original.amount;
        boolean creative = root instanceof CreativeFluidTankTileEntity;
        boolean windows = root.hasWindows();
        members.sort(Comparator.comparing(FluidTankTileEntity::getPos, POSITION_ORDER));
        for (FluidTankTileEntity member : members) {
            member.setRestructuring(true);
            member.setStructure(null, 1, 1, windows);
            member.tank.setCapacity(FluidTankTileEntity.capacityPerBlock());
            member.tank.setFluid(null);
            boolean keep = excluded == null || !member.getPos().equals(excluded);
            if (creative) {
                ((CreativeFluidTankTileEntity) member).setRawSelected(keep ? original : null);
            } else if (keep && remaining > 0) {
                int amount = Math.min(FluidTankTileEntity.capacityPerBlock(), remaining);
                FluidStack portion = original.copy();
                portion.amount = amount;
                member.tank.setFluid(portion);
                remaining -= amount;
            }
        }
    }

    private static int heightFor(Map<BlockPos, FluidTankTileEntity> parts, Set<BlockPos> available,
                                 BlockPos origin, int width) {
        FluidStack chosen = null;
        int height = 0;
        for (int y = 0; y < FluidConfig.fluidTankMaxHeight; y++) {
            boolean complete = true;
            FluidStack layerFluid = chosen;
            for (int x = 0; x < width && complete; x++) {
                for (int z = 0; z < width; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    FluidTankTileEntity part = parts.get(pos);
                    if (part == null || !available.contains(pos)) { complete = false; break; }
                    FluidStack fluid = rawFluid(part);
                    if (fluid != null && fluid.amount > 0) {
                        if (layerFluid != null && !layerFluid.isFluidEqual(fluid)) { complete = false; break; }
                        layerFluid = fluid;
                    }
                }
            }
            if (!complete) break;
            chosen = layerFluid;
            height++;
        }
        return height;
    }

    private static void form(Map<BlockPos, FluidTankTileEntity> parts, Set<BlockPos> available, Shape shape) {
        FluidTankTileEntity root = parts.get(shape.origin());
        FluidStack selected = null;
        int amount = 0;
        boolean windows = false;
        List<FluidTankTileEntity> members = new ArrayList<>(shape.size());
        for (int y = 0; y < shape.height(); y++) {
            for (int x = 0; x < shape.width(); x++) {
                for (int z = 0; z < shape.width(); z++) {
                    BlockPos pos = shape.origin().add(x, y, z);
                    FluidTankTileEntity member = parts.get(pos);
                    FluidStack fluid = rawFluid(member);
                    if (fluid != null && fluid.amount > 0) {
                        if (selected == null) selected = fluid.copy();
                        amount += fluid.amount;
                    }
                    windows |= member.hasWindows();
                    members.add(member);
                    available.remove(pos);
                }
            }
        }
        for (FluidTankTileEntity member : members) {
            member.tank.setFluid(null);
            member.tank.setCapacity(member == root
                    ? FluidTankTileEntity.capacityPerBlock() * shape.size()
                    : FluidTankTileEntity.capacityPerBlock());
            member.setStructure(shape.origin(), shape.width(), shape.height(), windows);
            if (member instanceof CreativeFluidTankTileEntity creative) creative.setRawSelected(null);
        }
        if (root instanceof CreativeFluidTankTileEntity creative) {
            creative.setRawSelected(selected);
        } else if (selected != null) {
            selected.amount = Math.min(amount, root.tank.getCapacity());
            root.tank.setFluid(selected);
        }
    }

    @Nullable
    private static FluidStack rawFluid(FluidTankTileEntity tank) {
        return tank instanceof CreativeFluidTankTileEntity creative ? creative.getRawSelected() : tank.tank.getFluid();
    }

    private record Shape(BlockPos origin, int width, int height) {
        int size() { return width * width * height; }
    }
}
