package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.display.NixieTubeBlock;
import com.cogwork.cogworkbridge.content.display.NixieTubeTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public final class NixieTubeRenderer extends TileEntitySpecialRenderer<NixieTubeTileEntity> {
    @Override public void render(NixieTubeTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null || tile.text().isEmpty()) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof NixieTubeBlock)) return;
        EnumFacing facing = state.getValue(NixieTubeBlock.FACING);
        NixieTubeBlock.Mount mount = state.getValue(NixieTubeBlock.MOUNT);
        float oldLightX = OpenGlHelper.lastBrightnessX, oldLightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5, y + .5, z + .5);
        GlStateManager.rotate(facing.getHorizontalAngle() - 90F + (mount == NixieTubeBlock.Mount.WALL_REVERSED ? 180F : 0F), 0, 1, 0);
        if (mount == NixieTubeBlock.Mount.WALL) GlStateManager.rotate(-90F, 0, 0, 1);
        if (mount == NixieTubeBlock.Mount.WALL_REVERSED) GlStateManager.rotate(90F, 0, 0, 1);
        GlStateManager.scale(.05F, -.05F, .05F);
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        var font = Minecraft.getMinecraft().fontRenderer;
        int color = tile.color().getColorValue();
        String text = tile.text();
        for (int i = 0; i < Math.min(2, text.length()); i++) {
            String character = text.substring(i, i + 1);
            float charX = (i == 0 ? -5F : 5F) - font.getStringWidth(character) / 2F;
            float charY = mount == NixieTubeBlock.Mount.CEILING ? -5F : -3F;
            font.drawString(character, charX, charY, color, false);
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldLightX, oldLightY);
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
