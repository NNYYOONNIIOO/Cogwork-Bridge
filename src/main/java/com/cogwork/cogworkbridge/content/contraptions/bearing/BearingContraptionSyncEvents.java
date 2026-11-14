package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class BearingContraptionSyncEvents {
    private static final Map<WorldServer, Map<UUID, PendingSnapshot>> PENDING =
            new WeakHashMap<>();

    private BearingContraptionSyncEvents() {
    }

    @SubscribeEvent
    public static void entityJoined(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote
                || !(event.getEntity() instanceof BearingContraptionEntity entity)) {
            return;
        }
        queue(entity, null);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP player)
                || !(event.getTarget() instanceof BearingContraptionEntity entity)
                || !entity.hasContraptionData()) {
            return;
        }
        queue(entity, player);
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.world instanceof WorldServer world)) {
            return;
        }
        Map<UUID, PendingSnapshot> pending;
        synchronized (PENDING) {
            pending = PENDING.remove(world);
        }
        if (pending == null) return;

        for (PendingSnapshot request : pending.values()) {
            BearingContraptionEntity entity = request.entity;
            if (entity.isDead || entity.world != world || !entity.hasContraptionData()
                    || world.getEntityFromUuid(entity.getUniqueID()) != entity) {
                continue;
            }
            NBTTagCompound snapshot = entity.snapshotNbt();
            if (request.broadcast) {
                ModNetwork.CHANNEL.sendToAllAround(
                        new ContraptionSnapshotPacket(entity.getEntityId(), snapshot),
                        new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                                entity.posX, entity.posY, entity.posZ, 96.0D));
            }
            for (EntityPlayerMP player : request.players) {
                if (!player.isDead && player.world == world && player.connection != null
                        && (!request.broadcast || player.getDistanceSq(entity) >= 96D * 96D)) {
                    ModNetwork.CHANNEL.sendTo(
                            new ContraptionSnapshotPacket(entity.getEntityId(), snapshot),
                            player);
                }
            }
        }
    }

    private static void queue(BearingContraptionEntity entity, EntityPlayerMP player) {
        if (!(entity.world instanceof WorldServer world)) return;
        // Serialization can call block/tile code. Never do it inside a Forge
        // tracking callback while EntityTracker is traversing its live set.
        synchronized (PENDING) {
            PendingSnapshot pending = PENDING
                    .computeIfAbsent(world, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(entity.getUniqueID(), ignored -> new PendingSnapshot(entity));
            if (player == null) pending.broadcast = true;
            else pending.players.add(player);
        }
    }

    @SubscribeEvent
    public static void worldUnloaded(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            BearingContraptionEntity.clearPendingClientSnapshots();
        } else if (event.getWorld() instanceof WorldServer world) {
            synchronized (PENDING) {
                PENDING.remove(world);
            }
        }
    }

    private static final class PendingSnapshot {
        private final BearingContraptionEntity entity;
        private final Set<EntityPlayerMP> players = new LinkedHashSet<>();
        private boolean broadcast;

        private PendingSnapshot(BearingContraptionEntity entity) {
            this.entity = entity;
        }
    }
}
