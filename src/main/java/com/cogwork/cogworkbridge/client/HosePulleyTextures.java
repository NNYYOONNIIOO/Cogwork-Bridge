package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Materials shared by the pulley housing and its separately rendered parts. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class HosePulleyTextures {
    private HosePulleyTextures() { }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (String texture : new String[]{"hose_pulley", "pump", "fluid_tank_inner", "hose_pulley_coil"}) {
            event.getMap().registerSprite(new ResourceLocation("create", "block/" + texture));
        }
    }
}
