package com.cogwork.cogworkbridge.client.render;
import com.cogwork.cogworkbridge.content.kinetics.flywheel.FlywheelTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
public final class FlywheelRenderer extends TileEntitySpecialRenderer<FlywheelTileEntity> {
    @Override public void render(FlywheelTileEntity t, double x, double y, double z, float partial, int stage, float alpha) {
        var state = t.getContraptionRenderState() == null ? t.getWorld().getBlockState(t.getPos()) : t.getContraptionRenderState();
        if (!(state.getBlock() instanceof KineticBlock kinetic)) return;
        var axis = kinetic.getAxis(state);
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(t.getWorld(), t.getPos(), AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis), axis, RotatingModelRenderer.kineticAngle(t, partial));
        RotatingModelRenderer.renderRotated(t.getWorld(), t.getPos(), state, axis, t.angle(partial));
        GlStateManager.popMatrix();
    }
}
