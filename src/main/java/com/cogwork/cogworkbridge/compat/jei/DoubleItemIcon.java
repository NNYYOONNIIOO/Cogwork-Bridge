package com.cogwork.cogworkbridge.compat.jei;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** The upstream category icon layout: an item with a half-size badge at the lower right. */
final class DoubleItemIcon implements IDrawable {
    private final IDrawable primary;
    private final IDrawable secondary;

    DoubleItemIcon(IGuiHelper helper, ItemStack primary, ItemStack secondary) {
        this.primary = helper.createDrawableIngredient(primary);
        this.secondary = helper.createDrawableIngredient(secondary);
    }

    @Override public int getWidth() { return 18; }
    @Override public int getHeight() { return 18; }

    @Override
    public void draw(Minecraft minecraft, int xOffset, int yOffset) {
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableDepth();
            primary.draw(minecraft, xOffset + 1, yOffset + 1);
            GlStateManager.translate(xOffset + 10, yOffset + 10, 100);
            GlStateManager.scale(.5F, .5F, .5F);
            secondary.draw(minecraft, 0, 0);
        } finally {
            GlStateManager.popMatrix();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }
    }
}
