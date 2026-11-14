package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.glue.client.SuperGlueOutlineRenderer;
import com.cogwork.cogworkbridge.content.contraptions.glue.client.SuperGlueSelectionHandler;
import com.cogwork.cogworkbridge.content.contraptions.glue.ClientGlueAreas;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SuperGlueClientEvents {

    private static final SuperGlueSelectionHandler SELECTION = new SuperGlueSelectionHandler();

    private SuperGlueClientEvents() {
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (Minecraft.getMinecraft().world != null) {
                for (int i = 0; i < 8; i++) {
                    com.cogwork.cogworkbridge.content.contraptions.glue.GlueBox box =
                            ClientGlueAreas.pollCreated();
                    if (box == null) {
                        break;
                    }
                    com.cogwork.cogworkbridge.content.contraptions.glue.GlueFeedback
                            .playCreation(Minecraft.getMinecraft().world, box);
                }
            }
            SELECTION.tick();
        }
    }

    @SubscribeEvent
    public static void mouse(MouseEvent event) {
        if (event.getButton() != 0 && event.getButton() != 1) {
            return;
        }
        if (!event.isButtonstate()) {
            return;
        }
        boolean rightClick = event.getButton() == 1;
        if (SELECTION.onMouseInput(!rightClick)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void renderWorld(RenderWorldLastEvent event) {
        SuperGlueOutlineRenderer.render(SELECTION, event.getPartialTicks());
    }

    @SubscribeEvent
    public static void worldUnload(PlayerEvent.PlayerLoggedOutEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            ClientGlueAreas.clear();
            SELECTION.discard(false);
        });
    }

    @SubscribeEvent
    public static void worldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote) {
            ClientGlueAreas.clear();
            SELECTION.discard(false);
        }
    }
}
