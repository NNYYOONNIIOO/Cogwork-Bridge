package com.cogwork.cogworkbridge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import java.util.List;

/** Shared Create-style panel for goggles and in-world controls. */
public final class CreateOverlayTooltip {
    private CreateOverlayTooltip() { }

    public static void draw(List<String> lines, ScaledResolution screen, int desiredX, int desiredY, ItemStack icon) {
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.fontRenderer.getStringWidth(line));
        int height = lines.size() * 10 + 2;
        int x = Math.max(5, Math.min(desiredX, screen.getScaledWidth() - width - 8));
        int y = Math.max(icon.isEmpty() ? 5 : 18, Math.min(desiredY, screen.getScaledHeight() - height - 5));
        Gui.drawRect(x - 4, y - 4, x + width + 4, y + height + 2, 0xe0100010);
        Gui.drawRect(x - 4, y - 4, x + width + 4, y - 3, 0x905000ff);
        Gui.drawRect(x - 4, y + height + 1, x + width + 4, y + height + 2, 0x9028007f);
        Gui.drawRect(x - 4, y - 3, x - 3, y + height + 1, 0x905000ff);
        Gui.drawRect(x + width + 3, y - 3, x + width + 4, y + height + 1, 0x905000ff);
        if (!icon.isEmpty()) {
            GlStateManager.color(1, 1, 1, 1);
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(icon, x + 8, y - 19);
            RenderHelper.disableStandardItemLighting();
        }
        for (String line : lines) {
            mc.fontRenderer.drawStringWithShadow(line, x, y, 0xffffff);
            y += 10;
        }
        GlStateManager.color(1, 1, 1, 1);
    }
}
