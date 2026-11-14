package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Tracks moving emitters without replacing any real-world block state. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ContraptionDynamicLight {
    private static final Map<World, Sources> WORLDS = new IdentityHashMap<>();

    private ContraptionDynamicLight() {
    }

    public static int combineRawLight(int vanilla, Object world, Object pos,
                                      Object type) {
        if (!(world instanceof World realWorld) || !(pos instanceof BlockPos blockPos)
                || type != EnumSkyBlock.BLOCK) {
            return vanilla;
        }
        synchronized (WORLDS) {
            Sources sources = WORLDS.get(realWorld);
            return sources == null ? vanilla
                    : Math.max(vanilla, sources.levels.getOrDefault(blockPos, 0));
        }
    }

    static void update(BearingContraptionEntity owner, Map<BlockPos, Integer> next) {
        World world = owner.world;
        Set<BlockPos> changed;
        synchronized (WORLDS) {
            Sources sources = WORLDS.get(world);
            if (sources == null) {
                if (next.isEmpty()) return;
                sources = new Sources();
                WORLDS.put(world, sources);
            }
            Map<BlockPos, Integer> old = sources.owners.get(owner);
            if (old != null && old.equals(next) || old == null && next.isEmpty()) return;
            Set<BlockPos> affected = new HashSet<>(next.keySet());
            if (old != null) affected.addAll(old.keySet());
            if (next.isEmpty()) sources.owners.remove(owner);
            else sources.owners.put(owner, new HashMap<>(next));
            Set<BlockPos> removed = new LinkedHashSet<>();
            Set<BlockPos> added = new LinkedHashSet<>();
            for (BlockPos pos : affected) {
                int oldLevel = sources.levels.getOrDefault(pos, 0);
                int level = 0;
                for (Map<BlockPos, Integer> values : sources.owners.values()) {
                    level = Math.max(level, values.getOrDefault(pos, 0));
                }
                if (level == oldLevel) continue;
                if (level == 0) sources.levels.remove(pos);
                else sources.levels.put(pos, level);
                (level < oldLevel ? removed : added).add(pos.toImmutable());
            }
            changed = removed;
            changed.addAll(added);
        }
        synchronized (WORLDS) {
            Sources sources = WORLDS.get(world);
            if (sources != null) sources.pending.addAll(changed);
        }
    }

    private static void relight(World world, Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (world.isBlockLoaded(pos)
                    && world.checkLightFor(EnumSkyBlock.BLOCK, pos)) {
                synchronized (WORLDS) {
                    Sources sources = WORLDS.get(world);
                    if (sources != null) sources.pending.remove(pos);
                }
            } else {
                synchronized (WORLDS) {
                    Sources sources = WORLDS.get(world);
                    if (sources != null) sources.pending.add(pos.toImmutable());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        flushPending(event.world, 16);
    }

    public static void tickClient(World world) {
        if (world != null && world.isRemote) flushPending(world, 16);
    }

    private static void flushPending(World world, int limit) {
        Set<BlockPos> pending = new LinkedHashSet<>();
        synchronized (WORLDS) {
            Sources sources = WORLDS.get(world);
            if (sources == null) return;
            Iterator<BlockPos> iterator = sources.pending.iterator();
            while (iterator.hasNext() && pending.size() < limit) {
                BlockPos pos = iterator.next();
                if (world.isAreaLoaded(pos, 16, false)) {
                    pending.add(pos);
                    iterator.remove();
                }
            }
        }
        relight(world, pending);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        World world = event.getWorld();
        Set<BearingContraptionEntity> removed = new HashSet<>();
        int chunkX = event.getChunk().x;
        int chunkZ = event.getChunk().z;
        synchronized (WORLDS) {
            Sources sources = WORLDS.get(world);
            if (sources == null) return;
            for (Map.Entry<BearingContraptionEntity, Map<BlockPos, Integer>> entry
                    : sources.owners.entrySet()) {
                BearingContraptionEntity owner = entry.getKey();
                if (owner.chunkCoordX == chunkX && owner.chunkCoordZ == chunkZ) {
                    removed.add(owner);
                    continue;
                }
                for (BlockPos pos : entry.getValue().keySet()) {
                    if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ) {
                        removed.add(owner);
                        break;
                    }
                }
            }
        }
        for (BearingContraptionEntity owner : removed) update(owner, Map.of());
        flushPending(world, Integer.MAX_VALUE);
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        Set<BearingContraptionEntity> owners;
        synchronized (WORLDS) {
            Sources sources = WORLDS.get(world);
            if (sources == null) return;
            owners = new HashSet<>(sources.owners.keySet());
        }
        for (BearingContraptionEntity owner : owners) update(owner, Map.of());
        flushPending(world, Integer.MAX_VALUE);
        synchronized (WORLDS) {
            WORLDS.remove(world);
        }
    }

    private static final class Sources {
        private final Map<BearingContraptionEntity, Map<BlockPos, Integer>> owners =
                new IdentityHashMap<>();
        private final Map<BlockPos, Integer> levels = new HashMap<>();
        private final Set<BlockPos> pending = new LinkedHashSet<>();
    }
}
