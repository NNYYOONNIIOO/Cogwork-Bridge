package com.cogwork.cogworkbridge.kinetic;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Per-world kinetic rebuild scheduling and network snapshots. */
public final class KineticNetworkManager {
    private static final Object LOCK = new Object();
    private static final Map<World, Map<BlockPos, KineticNetwork>> NETWORKS = new IdentityHashMap<>();

    private KineticNetworkManager() {
    }

    public static KineticNetwork networkFor(KineticTileEntity tile) {
        synchronized (LOCK) {
            World world = tile.getWorld();
            Map<BlockPos, KineticNetwork> map =
                    NETWORKS.computeIfAbsent(world, ignored -> new HashMap<>());
            KineticNetwork resolved = map.get(tile.getPos());
            if (resolved != null) {
                return resolved;
            }

            // A downstream TE may tick first. Discover the complete component
            // from it, then rebuild from the strongest source so propagation
            // starts in the right direction regardless of update order.
            KineticNetwork network = resolveComponent(tile);
            for (BlockPos member : network.getPositions()) {
                map.put(member, network);
            }
            return network;
        }
    }

    /** Rebuilds one component from its strongest source, never from an arbitrary downstream TE. */
    public static KineticNetwork resolveComponent(KineticTileEntity tile) {
        NetworkParticipant root = tile;
        Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<NetworkParticipant> queue = new java.util.ArrayDeque<>();
        queue.add(tile);
        seen.add(tile.getPos());
        float strongest = 0.0F;
        while (!queue.isEmpty()) {
            NetworkParticipant member = queue.removeFirst();
            if (member.isNetworkSource() && Math.abs(member.generatedSpeed()) > Math.abs(strongest)) {
                root = member;
                strongest = member.generatedSpeed();
            }
            for (NetworkParticipant next : member.kineticNeighbours()) {
                if (seen.add(next.getNetworkPos())) {
                    queue.addLast(next);
                }
            }
        }
        return KineticNetwork.agent().resolve(root);
    }

    public static void endServerTick(World world) {
        synchronized (LOCK) {
            NETWORKS.remove(world);
        }
    }

    public static void discard(World world) {
        if (world == null) {
            return;
        }
        synchronized (LOCK) {
            NETWORKS.remove(world);
            for (java.util.Iterator<Map.Entry<World, Map<BlockPos, KineticNetwork>>> iterator =
                    NETWORKS.entrySet().iterator(); iterator.hasNext(); ) {
                World key = iterator.next().getKey();
                if (key instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fakeWorld
                        && fakeWorld.getParentWorld() == world) {
                    iterator.remove();
                }
            }
        }
    }
}
