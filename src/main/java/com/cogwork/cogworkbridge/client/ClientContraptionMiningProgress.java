package com.cogwork.cogworkbridge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client copy of server-authoritative carried-block breaking stages. */
public final class ClientContraptionMiningProgress {

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static WorldClient currentWorld;

    private ClientContraptionMiningProgress() {
    }

    public static void update(int playerId, int sequence, int entityId,
                              BlockPos localPos, int stage) {
        WorldClient world = Minecraft.getMinecraft().world;
        if (world != currentWorld) {
            ENTRIES.clear();
            currentWorld = world;
        }
        if (world == null) {
            return;
        }
        Entry old = ENTRIES.get(playerId);
        if (stage < 0) {
            if (old != null && old.sequence() == sequence) {
                ENTRIES.remove(playerId);
            }
            ContraptionInteractionClientEvents.serverMiningResult(
                    playerId, sequence, entityId, localPos, stage);
        } else if (stage <= 9) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null && mc.player.getEntityId() == playerId
                    && !ContraptionInteractionClientEvents.isCurrentMining(
                            sequence, entityId, localPos)) {
                return;
            }
            ENTRIES.put(playerId, new Entry(sequence, entityId,
                    localPos.toImmutable(), stage, world.getTotalWorldTime() + 60L));
        }
    }

    public static List<Entry> forEntity(int entityId) {
        WorldClient world = Minecraft.getMinecraft().world;
        if (world != currentWorld) {
            ENTRIES.clear();
            currentWorld = world;
        }
        List<Entry> result = new ArrayList<>();
        if (world == null) {
            return result;
        }
        long now = world.getTotalWorldTime();
        ENTRIES.values().removeIf(entry -> entry.expiresAt() < now);
        for (Entry entry : ENTRIES.values()) {
            if (entry.entityId() == entityId) {
                result.add(entry);
            }
        }
        return result;
    }

    public static void clear() {
        ENTRIES.clear();
        currentWorld = null;
    }

    public static void clearSession(int playerId, int sequence) {
        Entry entry = ENTRIES.get(playerId);
        if (entry != null && entry.sequence() == sequence) {
            ENTRIES.remove(playerId);
        }
    }

    public record Entry(int sequence, int entityId, BlockPos localPos,
                        int stage, long expiresAt) {
    }
}
