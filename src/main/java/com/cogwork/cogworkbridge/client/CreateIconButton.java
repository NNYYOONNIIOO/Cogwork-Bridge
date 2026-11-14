package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.CogworkBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Upstream 18px button with an isolated icon and the GUI texture unit restored. */
public final class CreateIconButton extends GuiButton {
    private static final ResourceLocation WIDGETS = new ResourceLocation("create", "textures/gui/widgets.png");
    private static final ResourceLocation ICONS = new ResourceLocation("create", "textures/gui/icons.png");
    private final int iconX, iconY;
    private ResourceLocation isolated;
    private boolean selected;

    public CreateIconButton(int id, int x, int y, int iconX, int iconY) {
        super(id, x, y, 18, 18, "");
        this.iconX = iconX; this.iconY = iconY;
        Minecraft mc = Minecraft.getMinecraft();
        try (var input = mc.getResourceManager().getResource(ICONS).getInputStream()) {
            BufferedImage atlas = ImageIO.read(input);
            if (atlas != null && atlas.getWidth() >= (iconX + 1) * 16 && atlas.getHeight() >= (iconY + 1) * 16)
                isolated = mc.getTextureManager().getDynamicTextureLocation("cogworkbridge_gui_icon",
                        new DynamicTexture(atlas.getSubimage(iconX * 16, iconY * 16, 16, 16)));
        } catch (IOException e) {
            CogworkBridge.LOGGER.warn("Could not load Create GUI icon", e);
        }
    }
    public void release() {
        if (isolated != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(isolated);
        isolated = null;
    }
    public void setSelected(boolean selected) { this.selected = selected; }
    @Override public void drawButton(Minecraft mc, int mouseX, int mouseY, float partial) {
        if (!visible) return;
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        mc.entityRenderer.disableLightmap();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL13.glActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D(); GlStateManager.disableLighting(); GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, .1F); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); GlStateManager.color(1, 1, 1, 1);
        GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        bind(mc, WIDGETS);
        int background = !enabled ? 90 : hovered ? (Mouse.isButtonDown(0) ? 36 : 18) : selected ? 72 : 0;
        Gui.drawModalRectWithCustomSizedTexture(x, y, background, 0, 18, 18, 256, 256);
        ResourceLocation texture = isolated == null ? ICONS : isolated;
        bind(mc, texture);
        if (isolated == null) Gui.drawModalRectWithCustomSizedTexture(x + 1, y + 1, iconX * 16, iconY * 16, 16, 16, 256, 256);
        else Gui.drawModalRectWithCustomSizedTexture(x + 1, y + 1, 0, 0, 16, 16, 16, 16);
        GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }
    private static void bind(Minecraft mc, ResourceLocation texture) {
        mc.getTextureManager().bindTexture(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mc.getTextureManager().getTexture(texture).getGlTextureId());
    }
}
