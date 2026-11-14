package com.cogwork.cogworkbridge.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;

/** Four individually animated segments, using the same pivots as Create's flap models. */
public final class LogisticsFlapRenderer {
    private LogisticsFlapRenderer() { }
    public static void render(IBlockState model, EnumFacing side, float flap, boolean tunnel, double zOffset) {
        if (side.getAxis().isVertical()) return;
        GlStateManager.pushMatrix(); GlStateManager.translate(0.5, 0.5, 0.5);
        GlStateManager.rotate(switch (side) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; }, 0, 1, 0);
        GlStateManager.translate(-0.5 + 0.075 / 16D, -0.5, -0.5 + zOffset);
        double pivotZ = (tunnel ? 1 : 9.5) / 16D;
        for (int segment = 0; segment < 4; segment++) {
            float intensity = segment == 3 ? 1.5F : segment + 1F;
            float angle = (float) Math.sin((1 - Math.abs(flap)) * Math.PI * intensity) * 30F * flap;
            if (flap < 0) angle *= 0.5F;
            GlStateManager.pushMatrix(); GlStateManager.translate(0, 10 / 16D, pivotZ);
            GlStateManager.rotate(angle, 1, 0, 0); GlStateManager.translate(0, -10 / 16D, -pivotZ);
            FastStateRendering.getInstance().renderFast(model); GlStateManager.popMatrix();
            GlStateManager.translate(-3.05 / 16D, 0, 0);
        }
        GlStateManager.popMatrix();
    }
}
