package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Owns survival mining progress and publishes its breaking stage. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ContraptionMiningServer {

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final long TIMEOUT_TICKS = 40L;

    private ContraptionMiningServer() {
    }

    public static void handle(EntityPlayerMP player, int sequence, int entityId,
                              BlockPos localPos, EnumFacing side, float angle,
                              Vec3d hit, boolean cancel) {
        UUID playerId = player.getUniqueID();
        Session session = SESSIONS.get(playerId);
        if (cancel) {
            if (session != null && session.sequence == sequence) {
                stop(playerId, ContraptionMiningProgressPacket.CANCELLED);
            }
            return;
        }
        if (player.capabilities.isCreativeMode || !player.capabilities.allowEdit
                || player.isDead || !Float.isFinite(angle) || hit == null
                || !Double.isFinite(hit.x) || !Double.isFinite(hit.y) || !Double.isFinite(hit.z)) {
            reject(player, sequence, entityId, localPos);
            return;
        }
        Entity found = player.world.getEntityByID(entityId);
        if (!(found instanceof BearingContraptionEntity entity)
                || entity.isDead || !entity.hasContraptionData()
                || entity.getContraption() == null || entity.getMovingWorldChunk() == null) {
            reject(player, sequence, entityId, localPos);
            return;
        }
        BearingContraption.Snapshot snapshot = entity.getContraption()
                .snapshotAtLocal(localPos).orElse(null);
        if (snapshot == null) {
            reject(player, sequence, entityId, localPos);
            return;
        }
        BlockPos referencePos = new BlockPos(
                entity.localBlockCenterToWorldAtAngle(localPos, angle));
        if (!player.world.isBlockModifiable(player, referencePos)
                || !player.world.canMineBlockBody(player, referencePos)
                || !player.canPlayerEdit(referencePos, side, player.getHeldItemMainhand())) {
            reject(player, sequence, entityId, localPos);
            return;
        }
        if (session != null && (session.sequence != sequence || session.entity != entity
                || !session.localPos.equals(localPos)
                || session.block != snapshot.state().getBlock()
                || session.tool.getItem().shouldCauseBlockBreakReset(
                        session.tool, player.getHeldItemMainhand()))) {
            stop(playerId, ContraptionMiningProgressPacket.CANCELLED);
            session = null;
        }
        if (!BearingContraptionInteractionHandler.playerBreakIsReachable(
                player, entity, localPos, side, angle, hit)) {
            return;
        }
        Vec3d eyes = player.getPositionEyes(1.0F);
        double allowedReach = player.getEntityAttribute(
                net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE)
                .getAttributeValue() + 1.0D;
        if (eyes.squareDistanceTo(hit) > allowedReach * allowedReach) {
            reject(player, sequence, entityId, localPos);
            return;
        }
        long now = player.world.getTotalWorldTime();
        if (session == null) {
            session = new Session(player, sequence, entity, localPos,
                    snapshot.state().getBlock(), player.getHeldItemMainhand().copy());
            SESSIONS.put(playerId, session);
        }
        session.lastHeartbeat = now;
        if (session.lastAdvanceTick == now) {
            return;
        }
        session.lastAdvanceTick = now;
        float strength;
        try {
            World previousWorld = player.world;
            World interactionWorld = entity.getInteractionWorld();
            try {
                player.setWorld(interactionWorld);
                strength = snapshot.state().getPlayerRelativeBlockHardness(
                        player, interactionWorld, localPos);
            } finally {
                player.setWorld(previousWorld);
            }
        } catch (RuntimeException exception) {
            if (com.cogwork.cogworkbridge.util.LogOnce.first("contraption-mining", snapshot.state().getBlock(), exception)) {
                CogworkBridge.LOGGER.error("Could not calculate contraption mining strength for {} at {}",
                        snapshot.state(), localPos, exception);
            }
            stop(playerId, ContraptionMiningProgressPacket.CANCELLED);
            return;
        }
        if (Float.isNaN(strength) || strength <= 0.0F) {
            return;
        }
        session.progress += strength;
        if (session.progress >= 1.0F) {
            BearingContraptionInteractionHandler.handleBreak(
                    player, entityId, localPos, side, angle, hit);
            boolean removed = entity.getContraption() == null
                    || entity.getContraption().snapshotAtLocal(localPos).isEmpty();
            stop(playerId, removed ? ContraptionMiningProgressPacket.COMPLETED
                    : ContraptionMiningProgressPacket.CANCELLED);
            return;
        }
        int stage = Math.min(9, (int) Math.ceil(session.progress * 10.0F) - 1);
        if (stage != session.lastStage || now - session.lastBroadcast >= 10L) {
            session.lastStage = stage;
            session.lastBroadcast = now;
            broadcast(session, stage);
        }
    }

    private static void stop(UUID playerId, int result) {
        Session session = SESSIONS.remove(playerId);
        if (session != null) {
            broadcast(session, result);
        }
    }

    private static void reject(EntityPlayerMP player, int sequence,
                               int entityId, BlockPos localPos) {
        Session session = SESSIONS.get(player.getUniqueID());
        if (session != null && session.sequence == sequence) {
            stop(player.getUniqueID(), ContraptionMiningProgressPacket.CANCELLED);
        } else {
            ModNetwork.CHANNEL.sendTo(new ContraptionMiningProgressPacket(
                    player.getEntityId(), sequence, entityId, localPos,
                    ContraptionMiningProgressPacket.CANCELLED), player);
        }
    }

    private static void broadcast(Session session, int stage) {
        EntityPlayerMP player = session.player;
        ModNetwork.CHANNEL.sendToAllAround(
                new ContraptionMiningProgressPacket(player.getEntityId(), session.sequence,
                        session.entity.getEntityId(), session.localPos, stage),
                new NetworkRegistry.TargetPoint(session.world.provider.getDimension(),
                        player.posX, player.posY, player.posZ, 96.0D));
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = event.world.getTotalWorldTime();
        Iterator<Session> iterator = SESSIONS.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.world != event.world) {
                continue;
            }
            if (session.player.isDead || session.player.world != session.world
                    || session.entity.isDead || now - session.lastHeartbeat > TIMEOUT_TICKS) {
                iterator.remove();
                broadcast(session, ContraptionMiningProgressPacket.CANCELLED);
            }
        }
    }

    @SubscribeEvent
    public static void playerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP player) {
            stop(player.getUniqueID(), ContraptionMiningProgressPacket.CANCELLED);
        }
    }

    @SubscribeEvent
    public static void worldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            SESSIONS.values().removeIf(session -> session.world == event.getWorld());
        }
    }

    private static final class Session {
        private final EntityPlayerMP player;
        private final int sequence;
        private final BearingContraptionEntity entity;
        private final World world;
        private final BlockPos localPos;
        private final Block block;
        private final ItemStack tool;
        private float progress;
        private long lastAdvanceTick = Long.MIN_VALUE;
        private long lastHeartbeat;
        private long lastBroadcast;
        private int lastStage = -1;

        private Session(EntityPlayerMP player, int sequence,
                        BearingContraptionEntity entity, BlockPos localPos,
                        Block block, ItemStack tool) {
            this.player = player;
            this.sequence = sequence;
            this.entity = entity;
            this.world = player.world;
            this.localPos = localPos.toImmutable();
            this.block = block;
            this.tool = tool;
        }
    }
}
