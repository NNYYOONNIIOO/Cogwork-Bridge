package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.chute.ChuteTileEntity;
import com.cogwork.cogworkbridge.content.logistics.chute.SmartChuteTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

public final class ChuteRenderer extends TileEntitySpecialRenderer<ChuteTileEntity> {
    @Override public void render(ChuteTileEntity tile, double x, double y, double z,
                                 float partialTicks, int destroyStage, float alpha) {
        ItemStack item = tile.getItem();
        ItemStack filter = tile.getFilter();
        if (item.isEmpty() && filter.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        RenderHelper.enableStandardItemLighting();
        if (!item.isEmpty() && tile.hasWindow()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, tile.renderPosition(partialTicks), 0);
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            Minecraft.getMinecraft().getRenderItem().renderItem(item, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        if (!filter.isEmpty() && tile instanceof SmartChuteTileEntity) {
            for (int side = 0; side < 4; side++) {
                GlStateManager.pushMatrix();
                GlStateManager.rotate(side * 90.0F, 0, 1, 0);
                GlStateManager.translate(0, 11 / 16.0D, 0.505D);
                GlStateManager.scale(0.3F, 0.3F, 0.3F);
                Minecraft.getMinecraft().getRenderItem().renderItem(filter, ItemCameraTransforms.TransformType.FIXED);
                GlStateManager.popMatrix();
            }
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
