package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import javax.annotation.Nullable;

public final class PackageEntityRenderer extends Render<PackageEntity> {
    public PackageEntityRenderer(RenderManager manager) { super(manager); shadowSize = .3F; }
    @Override public void doRender(PackageEntity entity, double x, double y, double z, float yaw, float partial) {
        if (entity.getItem().isEmpty()) return;
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y + entity.height * .5, z);
        GlStateManager.rotate(-yaw, 0, 1, 0); GlStateManager.scale(1.49F, 1.49F, 1.49F);
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(entity.getItem(), ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, yaw, partial);
    }
    @Nullable @Override protected ResourceLocation getEntityTexture(PackageEntity entity) { return null; }
}
