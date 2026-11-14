package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.crate.CreativeCrateTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

public final class CreativeCrateRenderer extends TileEntitySpecialRenderer<CreativeCrateTileEntity> {
    @Override public void render(CreativeCrateTileEntity tile, double x, double y, double z,
                                 float partialTicks, int destroyStage, float alpha) {
        ItemStack selected = tile.getSelection();
        if (selected.isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 14.05D / 16.0D, z + 0.5D);
        GlStateManager.rotate(90.0F, 1, 0, 0);
        GlStateManager.scale(0.45F, 0.45F, 0.45F);
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(selected, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
