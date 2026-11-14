package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.glue.client.SuperGlueOutlineRenderer;
import com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform;
import com.cogwork.cogworkbridge.vendor.movingworld.client.render.MobileChunkRenderer;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunkClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Renders the MovingWorld mobile chunk under the bearing transform. */
public class BearingContraptionRenderer extends Render<BearingContraptionEntity> {

    private final FloatBuffer light0Position = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer light1Position = BufferUtils.createFloatBuffer(4);
    private final TrainControlsRenderer trainControls = new TrainControlsRenderer();

    public BearingContraptionRenderer(RenderManager renderManager) {
        super(renderManager);
        shadowSize = 0.0F;
    }

    @Override
    public void doRender(BearingContraptionEntity entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        if (!(entity.getMovingWorldChunk() instanceof MobileChunkClient mobileChunk)) {
            return;
        }
        if (entity.isNested() && entity.getParentContraption() == null) {
            return;
        }
        light0Position.clear(); light1Position.clear();
        GL11.glGetLight(GL11.GL_LIGHT0, GL11.GL_POSITION, light0Position);
        GL11.glGetLight(GL11.GL_LIGHT1, GL11.GL_POSITION, light1Position);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean light0 = GL11.glIsEnabled(GL11.GL_LIGHT0);
        boolean light1 = GL11.glIsEnabled(GL11.GL_LIGHT1);
        boolean colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);
        float oldLightX = OpenGlHelper.lastBrightnessX, oldLightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        try {
        java.util.List<ContraptionTransform.Point> pivots =
                entity.renderTransformChain(partialTicks);
        java.util.List<Float> angles = entity.renderAngleChain(partialTicks);
        java.util.List<ContraptionTransform.Axis> axes =
                entity.renderAxisChain();
        if (entity.isNested()) {
            double renderX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
            double renderY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
            double renderZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
            GlStateManager.translate(x - renderX, y - renderY, z - renderZ);
            for (int i = 0; i < pivots.size(); i++) {
                ContraptionTransform.Point chainPivot = pivots.get(i);
                GlStateManager.translate(chainPivot.x() - 0.5D,
                        chainPivot.y() - 0.5D, chainPivot.z() - 0.5D);
                GlStateManager.translate(0.5D, 0.5D, 0.5D);
                rotate(axes.get(i), angles.get(i));
                GlStateManager.translate(-0.5D, -0.5D, -0.5D);
            }
        } else {
            GlStateManager.translate((float) x, (float) y, (float) z);
            for (int i = 0; i < pivots.size(); i++) {
                ContraptionTransform.Point chainPivot = pivots.get(i);
                GlStateManager.translate(chainPivot.x() - entity.posX,
                        chainPivot.y() - entity.posY,
                        chainPivot.z() - entity.posZ);
                rotate(axes.get(i), angles.get(i));
                GlStateManager.translate(-(chainPivot.x() - entity.posX),
                        -(chainPivot.y() - entity.posY),
                        -(chainPivot.z() - entity.posZ));
            }
        }

        bindEntityTexture(entity);
        MobileChunkRenderer renderer = mobileChunk.getRenderer();
        renderer.render(partialTicks);
        trainControls.render(entity, mobileChunk, partialTicks);
        ContraptionBreakOverlayRenderer.render(entity, mobileChunk, partialTicks);
        if (entity.getContraption() != null) {
            SuperGlueOutlineRenderer.renderContraptionFrame(
                    entity.getContraption().capturedGlueBoxes());
        }
        } finally {
            GlStateManager.popMatrix();
            // OpenGL stores light positions in eye space; restore them under identity.
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            light0Position.rewind(); light1Position.rewind();
            GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, light0Position);
            GL11.glLight(GL11.GL_LIGHT1, GL11.GL_POSITION, light1Position);
            GlStateManager.popMatrix();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (light0) GlStateManager.enableLight(0); else GlStateManager.disableLight(0);
            if (light1) GlStateManager.enableLight(1); else GlStateManager.disableLight(1);
            if (colorMaterial) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldLightX, oldLightY);
        }
    }

    private static void rotate(ContraptionTransform.Axis axis, float angle) {
        switch (axis) {
            case X -> GlStateManager.rotate(angle, 1.0F, 0.0F, 0.0F);
            case Y -> GlStateManager.rotate(angle, 0.0F, 1.0F, 0.0F);
            case Z -> GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);
        }
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(BearingContraptionEntity entity) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
