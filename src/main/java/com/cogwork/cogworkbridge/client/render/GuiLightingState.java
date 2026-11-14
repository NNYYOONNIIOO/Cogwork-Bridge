package com.cogwork.cogworkbridge.client.render;

import java.nio.FloatBuffer;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Saves the lighting and render flags changed while drawing a GUI block model. */
public final class GuiLightingState implements AutoCloseable {
    private final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
    private final boolean light0 = GL11.glIsEnabled(GL11.GL_LIGHT0);
    private final boolean light1 = GL11.glIsEnabled(GL11.GL_LIGHT1);
    private final boolean colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean rescale = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);
    private final float lightX = OpenGlHelper.lastBrightnessX;
    private final float lightY = OpenGlHelper.lastBrightnessY;
    private final FloatBuffer light0Position = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer light1Position = BufferUtils.createFloatBuffer(4);

    public GuiLightingState() {
        GL11.glGetLight(GL11.GL_LIGHT0, GL11.GL_POSITION, light0Position);
        GL11.glGetLight(GL11.GL_LIGHT1, GL11.GL_POSITION, light1Position);
    }

    @Override public void close() {
        // Light positions returned by OpenGL are already in eye space.
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        light0Position.rewind();
        light1Position.rewind();
        GL11.glLight(GL11.GL_LIGHT0, GL11.GL_POSITION, light0Position);
        GL11.glLight(GL11.GL_LIGHT1, GL11.GL_POSITION, light1Position);
        GlStateManager.popMatrix();
        if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (light0) GlStateManager.enableLight(0); else GlStateManager.disableLight(0);
        if (light1) GlStateManager.enableLight(1); else GlStateManager.disableLight(1);
        if (colorMaterial) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
        if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (rescale) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
    }
}
