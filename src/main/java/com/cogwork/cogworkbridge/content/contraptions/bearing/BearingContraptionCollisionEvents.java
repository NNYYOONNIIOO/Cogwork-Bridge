package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.Reference;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;

/**
 * Keeps moving structures out of the block collision table.
 *
 * <p>1.21.1 resolves contraption contacts against local block shapes with a
 * rotation-aware entity collider. Injecting an enclosing world AABB makes a
 * rotating structure occupy the empty corners of its bounds.</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class BearingContraptionCollisionEvents {

    private BearingContraptionCollisionEvents() {
    }

    @SubscribeEvent
    public static void guiClosed(net.minecraftforge.event.entity.player.PlayerContainerEvent.Close event) {
        ContraptionGuiProxy.onContainerClosed(event.getEntityPlayer(), event.getContainer());
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!event.world.isRemote) {
            for (net.minecraft.entity.player.EntityPlayerMP player :
                    event.world.getMinecraftServer().getPlayerList().getPlayers()) {
                if (player.world == event.world) {
                    BearingContraptionInteractionHandler
                            .keepCarriedGuiValid(player);
                }
            }
        }
    }

    /**
     * Supplies rotated per-block AABBs to vanilla Entity.move(). The entity
     * sweep remains as a final safety correction after movement.
     */
    @SubscribeEvent
    public static void collectCollisions(GetCollisionBoxesEvent event) {
        for (BearingContraptionEntity entity : event.getWorld().getEntitiesWithinAABB(
                BearingContraptionEntity.class, event.getAabb().grow(2.0D))) {
            if (entity.isDead || entity.getContraption() == null || entity.isElevatorCollisionSuppressed()
                    || entity.carriesSeatedPassenger(event.getEntity())) {
                continue;
            }
            for (net.minecraft.util.math.AxisAlignedBB box : entity.collisionBoxes()) {
                if (box.intersects(event.getAabb())) {
                    event.getCollisionBoxesList().add(box);
                }
            }
        }
    }
}
