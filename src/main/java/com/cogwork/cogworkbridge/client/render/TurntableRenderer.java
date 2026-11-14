package com.cogwork.cogworkbridge.client.render;
import com.cogwork.cogworkbridge.content.kinetics.turntable.TurntableTileEntity;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
public final class TurntableRenderer extends TileEntitySpecialRenderer<TurntableTileEntity> {
    @Override public void render(TurntableTileEntity t, double x, double y, double z, float partial, int stage, float alpha) {
        var state = t.getContraptionRenderState() == null ? t.getWorld().getBlockState(t.getPos()) : t.getContraptionRenderState();
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(t.getWorld(), t.getPos(), state, EnumFacing.Axis.Y, RotatingModelRenderer.kineticAngle(t, partial));
        GlStateManager.popMatrix();
    }
}
