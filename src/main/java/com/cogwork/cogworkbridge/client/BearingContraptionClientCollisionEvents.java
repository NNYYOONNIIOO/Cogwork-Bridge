package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class BearingContraptionClientCollisionEvents {
    private BearingContraptionClientCollisionEvents() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null || minecraft.world == null) {
            return;
        }
        // A large structure is indexed by its anchor chunk, not every chunk it spans.
        for (BearingContraptionEntity contraption : minecraft.world.getEntities(
                BearingContraptionEntity.class, candidate -> !candidate.isDead)) {
            contraption.applyPendingClientElevatorMovement();
            AxisAlignedBB nearby = player.getEntityBoundingBox().grow(2.5D);
            if (contraption.getEntityBoundingBox().intersects(nearby))
                contraption.resolveClientEntityCollisions(player);
        }
    }
}
