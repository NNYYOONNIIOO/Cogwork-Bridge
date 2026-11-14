package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.equipment.PotatoCannonItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class PotatoCannonRenderHandler {
    private static float mainRecoil, offRecoil, oldMain, oldOff;
    public static void fire(EnumHand hand) { if (hand == EnumHand.MAIN_HAND) mainRecoil = 1F; else offRecoil = 1F; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        oldMain = mainRecoil; oldOff = offRecoil; mainRecoil *= .65F; offRecoil *= .65F;
    }
    @SubscribeEvent public static void hand(RenderSpecificHandEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || !(event.getItemStack().getItem() instanceof PotatoCannonItem)) return;
        float recoil = event.getHand() == EnumHand.MAIN_HAND ? oldMain + (mainRecoil - oldMain) * event.getPartialTicks()
                : oldOff + (offRecoil - oldOff) * event.getPartialTicks();
        int sign = event.getHand() == EnumHand.MAIN_HAND ? 1 : -1;
        event.setCanceled(true);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(-.1F * sign, 0F, .14F + recoil * .08F);
            GlStateManager.scale(.75F, .75F, .75F); GlStateManager.rotate(recoil * 80F, 1F, 0F, 0F);
            mc.getItemRenderer().renderItemInFirstPerson(mc.player, event.getPartialTicks(), event.getInterpolatedPitch(),
                    event.getHand(), event.getSwingProgress(), event.getItemStack(), event.getEquipProgress());
        } finally { GlStateManager.popMatrix(); }
    }
}
