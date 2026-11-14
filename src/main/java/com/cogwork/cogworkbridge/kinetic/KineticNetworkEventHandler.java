package com.cogwork.cogworkbridge.kinetic;

import com.cogwork.cogworkbridge.Reference;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Clears the one-tick kinetic snapshot after the serverside update pass. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class KineticNetworkEventHandler {

    private KineticNetworkEventHandler() {
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.world.isRemote) {
            KineticNetworkManager.endServerTick(event.world);
        }
    }
}
