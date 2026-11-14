package com.cogwork.cogworkbridge.content.contraptions.glue.client;

import com.cogwork.cogworkbridge.content.contraptions.glue.GlueBox;
import com.cogwork.cogworkbridge.content.contraptions.glue.ClientGlueAreas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Matches Create's translucent glue overlay and thick edge frame. */
public final class SuperGlueOutlineRenderer {

    private static final ResourceLocation GLUE_TEXTURE =
            new ResourceLocation("create", "textures/special/glue.png");
    private static final float[] PASSIVE = rgb(0x4D9162);
    private static final float[] HIGHLIGHT = rgb(0x68C586);
    private static final float[] FAIL = rgb(0xC5B548);
    private static final float SELECTED_EDGE_THICKNESS = 1.0F / 16.0F;
    private static final float PASSIVE_EDGE_THICKNESS = 1.0F / 48.0F;
    private static final float SURFACE_OFFSET = 1.0F / 128.0F;

    private SuperGlueOutlineRenderer() {
    }

    /** Draws camera-relative cuboid edges with the same geometry as glue. */
    public static void renderFrame(AxisAlignedBB box, float red, float green, float blue,
                                   float alpha, float thickness) {
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);
            float[] color = {red, green, blue};
            double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
            double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
            double tx = Math.min(thickness, (x1 - x0) / 2D);
            double ty = Math.min(thickness, (y1 - y0) / 2D);
            double tz = Math.min(thickness, (z1 - z0) / 2D);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            edgeBar(buffer, x0, y0, z0, x1, y0 + ty, z0 + tz, color, alpha);
            edgeBar(buffer, x0, y0, z1 - tz, x1, y0 + ty, z1, color, alpha);
            edgeBar(buffer, x0, y1 - ty, z0, x1, y1, z0 + tz, color, alpha);
            edgeBar(buffer, x0, y1 - ty, z1 - tz, x1, y1, z1, color, alpha);
            edgeBar(buffer, x0, y0, z0, x0 + tx, y0 + ty, z1, color, alpha);
            edgeBar(buffer, x1 - tx, y0, z0, x1, y0 + ty, z1, color, alpha);
            edgeBar(buffer, x0, y1 - ty, z0, x0 + tx, y1, z1, color, alpha);
            edgeBar(buffer, x1 - tx, y1 - ty, z0, x1, y1, z1, color, alpha);
            edgeBar(buffer, x0, y0, z0, x0 + tx, y1, z0 + tz, color, alpha);
            edgeBar(buffer, x1 - tx, y0, z0, x1, y1, z0 + tz, color, alpha);
            edgeBar(buffer, x0, y0, z1 - tz, x0 + tx, y1, z1, color, alpha);
            edgeBar(buffer, x1 - tx, y0, z1 - tz, x1, y1, z1, color, alpha);
            tessellator.draw();
        } finally {
            GlStateManager.depthMask(depthMask);
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    public static void render(SuperGlueSelectionHandler handler, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!holdingGlue(mc)) {
            return;
        }
        GlueBox selection = handler.getSelectionBox();
        GlueBox confirmed = handler.getConfirmedBox();

        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }

        double cameraX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double cameraY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double cameraZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.enableDepth();
        GlStateManager.disableCull();
        Minecraft.getMinecraft().getTextureManager().bindTexture(GLUE_TEXTURE);

        if (selection != null) {
            drawBox(selection, cameraX, cameraY, cameraZ,
                    HIGHLIGHT, 0.42F, SELECTED_EDGE_THICKNESS, 0.92F);
        }
        for (java.util.Map.Entry<Integer, GlueBox> entry : ClientGlueAreas.snapshot()) {
            drawBox(entry.getValue(), cameraX, cameraY, cameraZ,
                    PASSIVE, 0.22F, PASSIVE_EDGE_THICKNESS, 0.53F);
        }
        if (confirmed != null) {
            drawBox(confirmed, cameraX, cameraY, cameraZ,
                    FAIL, 0.42F, SELECTED_EDGE_THICKNESS, 0.92F);
        }

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /**
     * Draws local contraption glue boxes inside the caller's already-applied
     * parent transform chain. The box coordinates are relative to the anchor.
     */
    public static void renderContraptionFrame(List<GlueBox> boxes) {
        if (boxes == null || boxes.isEmpty() || !holdingGlue(Minecraft.getMinecraft())) {
            return;
        }
        GlStateManager.pushMatrix();
        prepareGlueState();
        for (GlueBox box : boxes) {
            drawLocalBox(box, PASSIVE, 0.22F, PASSIVE_EDGE_THICKNESS, 0.53F);
        }
        restoreGlueState();
        GlStateManager.popMatrix();
    }

    private static void prepareGlueState() {
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.enableDepth();
        GlStateManager.disableCull();
        Minecraft.getMinecraft().getTextureManager().bindTexture(GLUE_TEXTURE);
    }

    private static void restoreGlueState() {
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
    }

    private static boolean holdingGlue(Minecraft mc) {
        return mc.player != null
                && SuperGlueSelectionHandler.isGlue(
                mc.player.getHeldItemMainhand());
    }

    private static void drawLocalBox(GlueBox box, float[] color,
                                     float surfaceAlpha, float edgeThickness,
                                     float edgeAlpha) {
        drawBoxFaces(box, color, surfaceAlpha);
        drawWireframe(box, color, edgeAlpha, edgeThickness);
    }

    private static void drawBox(GlueBox box, double cx, double cy, double cz,
                                float[] color, float surfaceAlpha,
                                float edgeThickness, float edgeAlpha) {
        drawLocalBoxAt(box, -cx, -cy, -cz, color, surfaceAlpha,
                edgeThickness, edgeAlpha);
    }

    private static void drawBoxFaces(GlueBox box, float[] color,
                                     float surfaceAlpha) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        drawFaces(buffer, box, 0.0D, 0.0D, 0.0D, color, surfaceAlpha);

        tessellator.draw();
    }

    private static void drawLocalBoxAt(
            GlueBox box, double ox, double oy, double oz, float[] color,
            float surfaceAlpha, float edgeThickness, float edgeAlpha) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        drawFaces(buffer, box, ox, oy, oz, color, surfaceAlpha);
        tessellator.draw();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        double x0 = box.minX() + ox - SURFACE_OFFSET;
        double y0 = box.minY() + oy - SURFACE_OFFSET;
        double z0 = box.minZ() + oz - SURFACE_OFFSET;
        double x1 = box.maxX() + ox + SURFACE_OFFSET;
        double y1 = box.maxY() + oy + SURFACE_OFFSET;
        double z1 = box.maxZ() + oz + SURFACE_OFFSET;
        edgeBar(buffer, x0, y0, z0, x1, y0 + edgeThickness, z0 + edgeThickness, color, edgeAlpha);
        edgeBar(buffer, x0, y0, z1 - edgeThickness, x1, y0 + edgeThickness, z1, color, edgeAlpha);
        edgeBar(buffer, x0, y1 - edgeThickness, z0, x1, y1, z0 + edgeThickness, color, edgeAlpha);
        edgeBar(buffer, x0, y1 - edgeThickness, z1 - edgeThickness, x1, y1, z1, color, edgeAlpha);
        edgeBar(buffer, x0, y0, z0, x0 + edgeThickness, y0 + edgeThickness, z1, color, edgeAlpha);
        edgeBar(buffer, x1 - edgeThickness, y0, z0, x1, y0 + edgeThickness, z1, color, edgeAlpha);
        edgeBar(buffer, x0, y1 - edgeThickness, z0, x0 + edgeThickness, y1, z1, color, edgeAlpha);
        edgeBar(buffer, x1 - edgeThickness, y1 - edgeThickness, z0, x1, y1, z1, color, edgeAlpha);
        edgeBar(buffer, x0, y0, z0, x0 + edgeThickness, y1, z0 + edgeThickness, color, edgeAlpha);
        edgeBar(buffer, x1 - edgeThickness, y0, z0, x1, y1, z0 + edgeThickness, color, edgeAlpha);
        edgeBar(buffer, x0, y0, z1 - edgeThickness, x0 + edgeThickness, y1, z1, color, edgeAlpha);
        edgeBar(buffer, x1 - edgeThickness, y0, z1 - edgeThickness, x1, y1, z1, color, edgeAlpha);
        tessellator.draw();
    }

    private static void drawWireframe(GlueBox box, float[] color,
                                      float alpha, float thickness) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        drawEdgeBars(buffer, box, 0.0D, 0.0D, 0.0D, color, alpha, thickness);
        tessellator.draw();
    }

    private static void drawFaces(BufferBuilder buffer, GlueBox box,
                                  double ox, double oy, double oz,
                                  float[] color, float alpha) {
        double x0 = box.minX() + ox - SURFACE_OFFSET * 1.5D;
        double y0 = box.minY() + oy - SURFACE_OFFSET * 1.5D;
        double z0 = box.minZ() + oz - SURFACE_OFFSET * 1.5D;
        double x1 = box.maxX() + ox + SURFACE_OFFSET * 1.5D;
        double y1 = box.maxY() + oy + SURFACE_OFFSET * 1.5D;
        double z1 = box.maxZ() + oz + SURFACE_OFFSET * 1.5D;
        double xs = Math.max(1.0D, x1 - x0);
        double ys = Math.max(1.0D, y1 - y0);
        double zs = Math.max(1.0D, z1 - z0);

        texturedFace(buffer, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
                0.0D, xs, 0.0D, zs, color, alpha);
        texturedFace(buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                0.0D, xs, 0.0D, zs, color, alpha);
        texturedFace(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0,
                0.0D, xs, 0.0D, ys, color, alpha);
        texturedFace(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1,
                0.0D, xs, 0.0D, ys, color, alpha);
        texturedFace(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1,
                0.0D, zs, 0.0D, ys, color, alpha);
        texturedFace(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0,
                0.0D, zs, 0.0D, ys, color, alpha);
    }

    private static void texturedFace(BufferBuilder buffer,
                                     double x0, double y0, double z0,
                                     double x1, double y1, double z1,
                                     double x2, double y2, double z2,
                                     double x3, double y3, double z3,
                                     double minU, double maxU,
                                     double minV, double maxV,
                                     float[] color, float alpha) {
        addTexturedVertex(buffer, x0, y0, z0, maxU, minV, color, alpha);
        addTexturedVertex(buffer, x1, y1, z1, minU, minV, color, alpha);
        addTexturedVertex(buffer, x2, y2, z2, minU, maxV, color, alpha);
        addTexturedVertex(buffer, x3, y3, z3, maxU, maxV, color, alpha);
    }

    private static void addTexturedVertex(BufferBuilder buffer,
                                          double x, double y, double z,
                                          double u, double v, float[] color,
                                          float alpha) {
        buffer.pos(x, y, z).tex(u, v)
                .color(color[0], color[1], color[2], alpha).endVertex();
    }

    private static void drawEdgeBars(BufferBuilder buffer, GlueBox box,
                                     double ox, double oy, double oz,
                                     float[] color, float alpha,
                                     float thickness) {
        double x0 = box.minX() + ox - SURFACE_OFFSET;
        double y0 = box.minY() + oy - SURFACE_OFFSET;
        double z0 = box.minZ() + oz - SURFACE_OFFSET;
        double x1 = box.maxX() + ox + SURFACE_OFFSET;
        double y1 = box.maxY() + oy + SURFACE_OFFSET;
        double z1 = box.maxZ() + oz + SURFACE_OFFSET;
        edgeBar(buffer, x0, y0, z0, x1, y0 + thickness, z0 + thickness, color, alpha);
        edgeBar(buffer, x0, y0, z1 - thickness, x1, y0 + thickness, z1, color, alpha);
        edgeBar(buffer, x0, y1 - thickness, z0, x1, y1, z0 + thickness, color, alpha);
        edgeBar(buffer, x0, y1 - thickness, z1 - thickness, x1, y1, z1, color, alpha);
        edgeBar(buffer, x0, y0, z0, x0 + thickness, y0 + thickness, z1, color, alpha);
        edgeBar(buffer, x1 - thickness, y0, z0, x1, y0 + thickness, z1, color, alpha);
        edgeBar(buffer, x0, y1 - thickness, z0, x0 + thickness, y1, z1, color, alpha);
        edgeBar(buffer, x1 - thickness, y1 - thickness, z0, x1, y1, z1, color, alpha);
        edgeBar(buffer, x0, y0, z0, x0 + thickness, y1, z0 + thickness, color, alpha);
        edgeBar(buffer, x1 - thickness, y0, z0, x1, y1, z0 + thickness, color, alpha);
        edgeBar(buffer, x0, y0, z1 - thickness, x0 + thickness, y1, z1, color, alpha);
        edgeBar(buffer, x1 - thickness, y0, z1 - thickness, x1, y1, z1, color, alpha);
    }

    private static void edgeBar(BufferBuilder buffer,
                                double x0, double y0, double z0,
                                double x1, double y1, double z1,
                                float[] color, float alpha) {
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
            return;
        }
        colFace(buffer, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, color, alpha);
        colFace(buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color, alpha);
        colFace(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, color, alpha);
        colFace(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, color, alpha);
        colFace(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, color, alpha);
        colFace(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, color, alpha);
    }

    private static void colFace(BufferBuilder buffer,
                                double x0, double y0, double z0,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                double x3, double y3, double z3,
                                float[] color, float alpha) {
        addColorVertex(buffer, x0, y0, z0, color, alpha);
        addColorVertex(buffer, x1, y1, z1, color, alpha);
        addColorVertex(buffer, x2, y2, z2, color, alpha);
        addColorVertex(buffer, x3, y3, z3, color, alpha);
    }

    private static void addColorVertex(BufferBuilder buffer,
                                       double x, double y, double z,
                                       float[] color, float alpha) {
        buffer.pos(x, y, z).color(color[0], color[1], color[2], alpha).endVertex();
    }

    private static float[] rgb(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F
        };
    }
}
