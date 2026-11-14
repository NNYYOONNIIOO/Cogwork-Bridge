package com.cogwork.cogworkbridge.content.contraptions.glue;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.BlockEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Replays persistent glue areas to clients after login or dimension changes. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class GlueSyncEvents {

    private static final Map<net.minecraft.world.World, Map<BlockPos, Boolean>> PENDING =
            new WeakHashMap<>();

    private GlueSyncEvents() {
    }

    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        syncNearby((EntityPlayerMP) event.player);
    }

    /**
     * Both events run before vanilla commits a block mutation. Reconcile on the
     * next server tick so a later cancelled event or a replace operation cannot
     * leave glue membership describing a block that was never placed/removed.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPlace(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote || event.isCanceled()) {
            return;
        }
        queue(event.getWorld(), event.getPos(), true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote || event.isCanceled()
                || GlueSelectionRules.areasAffecting(
                event.getWorld(), event.getPos()).isEmpty()) {
            return;
        }
        queue(event.getWorld(), event.getPos(), false);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.world.isRemote) {
            return;
        }
        Map<BlockPos, Boolean> pending;
        synchronized (PENDING) {
            pending = PENDING.remove(event.world);
        }
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, Boolean> entry : pending.entrySet()) {
            BlockPos pos = entry.getKey();
            boolean present = GlueSelectionRules.isMovable(
                    event.world.getBlockState(pos));
            reconcile(event.world, pos, present);
        }
    }

    private static void queue(net.minecraft.world.World world, BlockPos pos,
                              boolean expectedPresent) {
        synchronized (PENDING) {
            PENDING.computeIfAbsent(world, ignored -> new LinkedHashMap<>())
                    .put(pos.toImmutable(), expectedPresent);
        }
    }

    private static void reconcile(net.minecraft.world.World world, BlockPos pos,
                                  boolean present) {
        GridPos grid = GlueSelectionRules.grid(pos);
        for (SuperGlueEntity glue : GlueSelectionRules.areasAffecting(world, pos)) {
            boolean changed = present
                    ? glue.addMember(grid)
                    : glue.removeMember(grid);
            if (!changed) {
                continue;
            }
            ModNetwork.CHANNEL.sendToAllAround(
                    new GlueAreaUpdatePacket(glue.getEntityId(),
                            glue.getGlueBox(), glue.getGlueVolume()),
                    new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(
                            world.provider.getDimension(), glue.posX, glue.posY,
                            glue.posZ, 64.0D));
        }
    }

    private static void syncNearby(EntityPlayerMP player) {
        AxisAlignedBB area = new AxisAlignedBB(player.posX - 64.0D, player.posY - 64.0D,
                player.posZ - 64.0D, player.posX + 64.0D, player.posY + 64.0D,
                player.posZ + 64.0D);
        for (SuperGlueEntity glue : player.world.getEntitiesWithinAABB(SuperGlueEntity.class, area)) {
            ModNetwork.CHANNEL.sendTo(
                    new GlueAreaUpdatePacket(glue.getEntityId(),
                            glue.getGlueBox(), glue.getGlueVolume()), player);
        }
    }
}
