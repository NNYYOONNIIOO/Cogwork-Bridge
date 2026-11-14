package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorFloorSelectionPacket;
import com.cogwork.cogworkbridge.network.ModNetwork;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ElevatorControlsClient {
    private ElevatorControlsClient() { }
    @SubscribeEvent public static void scroll(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getDwheel() == 0 || mc.currentScreen != null || mc.player == null || mc.player.isSpectator()) return;
        ContraptionInteractionClientEvents.ContraptionHit hit = ContraptionInteractionClientEvents.currentHit();
        if (hit == null || !hit.entity().isElevator()
                || hit.hit().snapshot().state().getBlock() != AllBlocks.CONTRAPTION_CONTROLS) return;
        ModNetwork.CHANNEL.sendToServer(new ElevatorFloorSelectionPacket(hit.entity().getEntityId(),
                hit.hit().localPos(), event.getDwheel()));
        event.setCanceled(true);
    }
}
