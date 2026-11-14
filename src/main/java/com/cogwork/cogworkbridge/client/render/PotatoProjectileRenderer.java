package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.equipment.PotatoProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import javax.annotation.Nullable;

public final class PotatoProjectileRenderer extends Render<PotatoProjectileEntity> {
    public PotatoProjectileRenderer(RenderManager manager) { super(manager); }
    @Override public void doRender(PotatoProjectileEntity entity, double x, double y, double z, float yaw, float partial) {
        if (entity.getItem().isEmpty()) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        net.minecraft.item.Item item = entity.getItem().getItem();
        boolean pointed = item == net.minecraft.init.Items.CARROT || item == net.minecraft.init.Items.GOLDEN_CARROT
                || item == net.minecraft.init.Items.BEETROOT || item == net.minecraft.init.Items.FISH || item == net.minecraft.init.Items.COOKED_FISH;
        if (entity.isStuck()) GlStateManager.rotate(-renderManager.playerViewY, 0, 1, 0);
        else if (pointed) {
            GlStateManager.rotate(entity.rotationYaw, 0, 1, 0);
            GlStateManager.rotate(entity.rotationPitch + 90F, 1, 0, 0);
            GlStateManager.rotate((entity.ticksExisted + partial) * 20F, 0, 1, 0);
            GlStateManager.rotate(-140F, 0, 0, 1);
        } else {
            GlStateManager.rotate(-renderManager.playerViewY, 0, 1, 0);
            GlStateManager.rotate(renderManager.playerViewX, 1, 0, 0);
            GlStateManager.rotate((entity.ticksExisted + partial) * 20F, 0, 0, 1);
            GlStateManager.rotate((entity.ticksExisted + partial) * 11F, 1, 0, 0);
        }
        GlStateManager.scale(.5F, .5F, .5F);
        Minecraft.getMinecraft().getRenderItem().renderItem(entity.getItem(), ItemCameraTransforms.TransformType.GROUND);
        GlStateManager.popMatrix();
    }
    @Nullable @Override protected ResourceLocation getEntityTexture(PotatoProjectileEntity entity) { return null; }
}
