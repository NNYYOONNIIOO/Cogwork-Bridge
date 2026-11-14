package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.equipment.DivingGearItem;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Upstream helmet visibility translated from linear fluid fog to 1.12's fog pipeline. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class DivingHelmetVision {
    private DivingHelmetVision() { }

    @SubscribeEvent public static void fluidFog(EntityViewRenderEvent.FogDensity event) {
        if (!(event.getEntity() instanceof EntityLivingBase entity)
                || entity instanceof EntityPlayer player && player.isSpectator()
                || entity.isPotionActive(MobEffects.BLINDNESS)) return;
        var helmet = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof DivingGearItem gear)
                || gear.armorType != EntityEquipmentSlot.HEAD) return;
        Material fluid = event.getState().getMaterial();
        // Upstream visibility is a helmet feature; it does not consume or require air.
        if (fluid == Material.WATER) {
            float density = entity.isPotionActive(MobEffects.WATER_BREATHING) ? .01F
                    : Math.max(.01F, .1F - EnchantmentHelper.getRespirationModifier(entity) * .03F);
            GlStateManager.setFog(GlStateManager.FogMode.EXP);
            event.setDensity(density / 6.25F);
            event.setCanceled(true);
        } else if (fluid == Material.LAVA && gear.isNetherite()) {
            GlStateManager.setFog(GlStateManager.FogMode.LINEAR);
            GlStateManager.setFogStart(-4F);
            GlStateManager.setFogEnd(20F);
            // Cancelling bypasses vanilla's lava density=2 branch. Density is ignored
            // by LINEAR fog, which uses the same near/far distances as upstream.
            event.setDensity(0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent public static void fireOverlay(RenderBlockOverlayEvent event) {
        if (event.getOverlayType() == RenderBlockOverlayEvent.OverlayType.FIRE
                && DivingGearItem.hasNetheriteSuit(event.getPlayer())) event.setCanceled(true);
    }
}
