package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.equipment.WorldshaperItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class WorldshaperPreview {
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.currentScreen != null || !mc.player.capabilities.isCreativeMode) return;
        ItemStack stack = mc.player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof WorldshaperItem)) stack = mc.player.getHeldItemOffhand();
        if (!(stack.getItem() instanceof WorldshaperItem)) return;
        RayTraceResult hit = WorldshaperItem.target(mc.player);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return;
        java.util.List<BlockPos> positions = WorldshaperItem.positions(mc.world, stack, hit);
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) return;
        float p = event.getPartialTicks();
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * p;
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * p;
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * p;
        GlStateManager.pushMatrix(); GlStateManager.enableBlend(); GlStateManager.disableTexture2D(); GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2F);
        for (BlockPos pos : positions) {
            AxisAlignedBB box = new AxisAlignedBB(pos).grow(.002D).offset(-x, -y, -z);
            RenderGlobal.drawSelectionBoundingBox(box, .4F, .85F, 1F, .65F);
        }
        GlStateManager.depthMask(true); GlStateManager.enableTexture2D(); GlStateManager.disableBlend(); GlStateManager.popMatrix();
    }
}
