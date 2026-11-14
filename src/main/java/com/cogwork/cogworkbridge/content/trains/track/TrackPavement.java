package com.cogwork.cogworkbridge.content.trains.track;

import com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock;
import com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks;
import net.minecraft.block.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.util.*;

/** Paving footprint adapted from upstream TrackPaver, without modifying occupied blocks. */
final class TrackPavement {
    record Placement(IBlockState state, int cost) { }
    private static final Map<IBlockState, IBlockState> DOUBLE_SLABS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private TrackPavement() { }

    static Map<BlockPos, Placement> plan(World world, Map<BlockPos, TrackShape> tracks, TrackCurve curve, ItemStack material) {
        Map<BlockPos, Placement> result = new LinkedHashMap<>();
        if (material.isEmpty() || !(material.getItem() instanceof ItemBlock item)) return result;
        Block block = item.getBlock();
        IBlockState single = block.getStateFromMeta(item.getMetadata(material.getMetadata()));
        BlockPos sample = tracks.keySet().iterator().next().down();
        if (block instanceof TrackBlock || block instanceof FakeTrackBlock || block.hasTileEntity(single)
                || single.getCollisionBoundingBox(world, sample) == null) return result;
        IBlockState full = doubleSlab(single, material);
        boolean slab = full != single;
        boolean wall = block instanceof BlockWall || block instanceof GirderBlock;
        for (Map.Entry<BlockPos, TrackShape> entry : tracks.entrySet()) {
            Vec3d axis = entry.getValue().axis(), normal = axis.crossProduct(UP);
            IBlockState straightState = full;
            if (block instanceof GirderBlock && !entry.getValue().slope() && (entry.getValue().x == 0 || entry.getValue().z == 0)) {
                EnumFacing.Axis girderAxis = entry.getValue().x != 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
                straightState = single.withProperty(GirderBlock.AXIS, girderAxis).withProperty(GirderBlock.TOP, false)
                        .withProperty(GirderBlock.BOTTOM, false).withProperty(girderAxis == EnumFacing.Axis.X ? GirderBlock.X : GirderBlock.Z, true);
            }
            Vec3d center = new Vec3d(entry.getKey().down()).add(.5, .5, .5);
            add(world, result, new BlockPos(center.add(normal)), straightState, slab ? 2 : 1);
            add(world, result, new BlockPos(center.subtract(normal)), straightState, slab ? 2 : 1);
            if (wall) continue;
            add(world, result, new BlockPos(center), straightState, slab ? 2 : 1);
            // Fill diagonal seams only towards another planned track, not past a terminus.
            for (int sign : new int[]{-1, 1}) {
                BlockPos next = entry.getKey().add((int) axis.x * sign, (int) axis.y * sign, (int) axis.z * sign);
                if (!tracks.containsKey(next)) continue;
                for (int side : new int[]{-1, 1}) add(world, result,
                        new BlockPos(center.add(normal.normalize().scale(side * .45)).add(axis.normalize().scale(sign * .45))),
                        straightState, slab ? 2 : 1);
            }
        }
        if (curve != null && !wall) {
            Map<Column, Double> levels = new LinkedHashMap<>();
            int segments = Math.max(8, (int) Math.ceil(curve.length() * 2));
            for (int i = 0; i < segments; i++) {
                double t = curve.parameterAtFraction((i + .5) / segments);
                Vec3d point = curve.point(t).add(0, -.9375, 0);
                Vec3d normal = UP.crossProduct(curve.tangent(t)).normalize().scale(.97);
                for (Vec3d p : new Vec3d[]{point, point.add(normal), point.subtract(normal)}) {
                    Column column = new Column(MathHelper.floor(p.x), MathHelper.floor(p.z));
                    levels.merge(column, p.y, Math::min);
                }
            }
            for (Map.Entry<Column, Double> entry : levels.entrySet()) {
                int floor = MathHelper.floor(entry.getValue());
                boolean half = slab && entry.getValue() - floor >= .5;
                BlockPos target = new BlockPos(entry.getKey().x, floor + (half ? 1 : 0), entry.getKey().z);
                add(world, result, target, half ? half(single, false) : full, slab && !half ? 2 : 1);
                if (half) add(world, result, target.down(), half(single, true), 1);
            }
        }
        // A cell cannot contain both a real/fake rail and pavement in the legacy world.
        tracks.keySet().forEach(result::remove);
        if (curve != null) TrackCurveSupport.positions(curve).forEach(result::remove);
        return result;
    }
    private record Column(int x, int z) { }
    private static void add(World world, Map<BlockPos, Placement> result, BlockPos pos, IBlockState state, int cost) {
        if (!world.isBlockLoaded(pos)) { result.putIfAbsent(pos, new Placement(state, cost)); return; }
        IBlockState existing = world.getBlockState(pos);
        if (existing.getBlock() != state.getBlock() && !existing.getBlock().hasTileEntity(existing)
                && existing.getBlock().isReplaceable(world, pos)) result.putIfAbsent(pos, new Placement(state, cost));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState half(IBlockState state, boolean top) {
        for (IProperty property : state.getPropertyKeys()) if (property.getName().equals("half") || property.getName().equals("type")) {
            var value = property.parseValue(top ? "top" : "bottom");
            if (value.isPresent()) return state.withProperty(property, (Comparable) value.get());
        }
        return state;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState doubleSlab(IBlockState single, ItemStack material) {
        if (single.getBlock() instanceof PaletteBlocks.Slab slab && slab.getDoubleSlab() != null)
            return slab.getDoubleSlab().getDefaultState();
        for (IProperty property : single.getPropertyKeys()) if (property.getName().equals("type")) {
            var value = property.parseValue("double");
            if (value.isPresent()) return single.withProperty(property, (Comparable) value.get());
        }
        if (!(single.getBlock() instanceof BlockSlab slab) || slab.isDouble()) return single;
        if (DOUBLE_SLABS.containsKey(single)) return DOUBLE_SLABS.get(single);
        Random random = new Random(0);
        for (Block block : ForgeRegistries.BLOCKS) if (block instanceof BlockSlab candidate && candidate.isDouble()) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                if (block.getItemDropped(state, random, 0) == material.getItem() && block.damageDropped(state) == material.getMetadata()) {
                    DOUBLE_SLABS.put(single, state); return state;
                }
            }
        }
        DOUBLE_SLABS.put(single, single); return single;
    }
}
