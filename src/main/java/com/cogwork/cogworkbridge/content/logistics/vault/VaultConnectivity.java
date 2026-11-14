package com.cogwork.cogworkbridge.content.logistics.vault;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Forms the largest complete 1-3 wide square cross-section, with length at most three times its width. */
public final class VaultConnectivity {
    private static final Comparator<BlockPos> ORDER = Comparator.comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
    private VaultConnectivity() { }
    static void markAround(World world, BlockPos pos) {
        if (world == null || world.isRemote) return;
        mark(world, pos);
        for (EnumFacing side : EnumFacing.VALUES) mark(world, pos.offset(side));
    }

    /** Split the current vault group without moving or merging any member inventory. */
    public static void split(World world, BlockPos pos) {
        if (world == null || world.isRemote || !world.isBlockLoaded(pos)) return;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof ItemVaultTileEntity member)) return;
        BlockPos rootPos = member.controllerPos();
        TileEntity rootTile = world.getTileEntity(rootPos);
        if (!(rootTile instanceof ItemVaultTileEntity root)) return;
        int width = root.width();
        int length = root.length();
        EnumFacing.Axis axis = root.axis();
        for (int along = 0; along < length; along++) for (int y = 0; y < width; y++)
            for (int across = 0; across < width; across++) {
                BlockPos memberPos = position(rootPos, axis, along, y, across);
                if (world.getTileEntity(memberPos) instanceof ItemVaultTileEntity part
                        && part.controllerPos().equals(rootPos))
                    part.setStructure(memberPos, 1, 1);
            }
        markAround(world, rootPos);
    }
    private static void mark(World world, BlockPos pos) {
        if (world.isBlockLoaded(pos) && world.getTileEntity(pos) instanceof ItemVaultTileEntity vault) vault.requestRebuild();
    }
    static void rebuild(ItemVaultTileEntity seed) {
        World world = seed.getWorld();
        if (world == null || world.isRemote) return;
        EnumFacing.Axis axis = seed.axis();
        Map<BlockPos, ItemVaultTileEntity> members = new LinkedHashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.getPos());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (members.containsKey(pos) || !world.isBlockLoaded(pos)
                    || !(world.getTileEntity(pos) instanceof ItemVaultTileEntity tile) || tile.axis() != axis) continue;
            members.put(pos.toImmutable(), tile);
            for (EnumFacing side : EnumFacing.VALUES) queue.addLast(pos.offset(side));
        }
        if (members.isEmpty() || !loadedExistingGroups(world, members)) return;
        Set<BlockPos> available = new HashSet<>(members.keySet());
        List<BlockPos> origins = new ArrayList<>(members.keySet());
        origins.sort(ORDER);
        while (!available.isEmpty()) {
            Shape best = null;
            for (BlockPos origin : origins) {
                if (!available.contains(origin)) continue;
                for (int width = 1; width <= 3; width++) {
                    int length = lengthFor(available, origin, axis, width);
                    if (length == 0) continue;
                    Shape shape = new Shape(origin, width, length);
                    if (best == null || shape.volume() > best.volume()
                            || shape.volume() == best.volume() && width > best.width()) best = shape;
                }
            }
            if (best == null) return;
            for (int along = 0; along < best.length(); along++) for (int y = 0; y < best.width(); y++)
                for (int across = 0; across < best.width(); across++) {
                    BlockPos pos = position(best.origin(), axis, along, y, across);
                    ItemVaultTileEntity member = members.get(pos);
                    member.setStructure(best.origin(), best.width(), best.length());
                    available.remove(pos);
                }
        }
    }
    private static int lengthFor(Set<BlockPos> available, BlockPos origin, EnumFacing.Axis axis, int width) {
        int length = 0;
        for (int along = 0; along < width * 3; along++) {
            boolean complete = true;
            for (int y = 0; y < width && complete; y++) for (int across = 0; across < width; across++) {
                if (!available.contains(position(origin, axis, along, y, across))) { complete = false; break; }
            }
            if (!complete) break;
            length++;
        }
        return length;
    }
    private static BlockPos position(BlockPos origin, EnumFacing.Axis axis, int along, int y, int across) {
        return axis == EnumFacing.Axis.X ? origin.add(along, y, across) : origin.add(across, y, along);
    }
    private static boolean loadedExistingGroups(World world, Map<BlockPos, ItemVaultTileEntity> members) {
        Set<BlockPos> checked = new HashSet<>();
        for (ItemVaultTileEntity part : members.values()) {
            BlockPos rootPos = part.controllerPos();
            if (!world.isBlockLoaded(rootPos)) return false;
            TileEntity tile = world.getTileEntity(rootPos);
            if (!(tile instanceof ItemVaultTileEntity root)) {
                if (world.getBlockState(rootPos).getBlock() instanceof ItemVaultBlock && tile == null) return false;
                continue;
            }
            if (!checked.add(rootPos)) continue;
            for (int along = 0; along < root.length(); along++) for (int y = 0; y < root.width(); y++)
                for (int across = 0; across < root.width(); across++) {
                    BlockPos expected = position(rootPos, root.axis(), along, y, across);
                    if (!world.isBlockLoaded(expected)) return false;
                    if (world.getBlockState(expected).getBlock() instanceof ItemVaultBlock
                            && world.getTileEntity(expected) == null) return false;
                }
        }
        return true;
    }
    private record Shape(BlockPos origin, int width, int length) { int volume() { return width * width * length; } }
}
