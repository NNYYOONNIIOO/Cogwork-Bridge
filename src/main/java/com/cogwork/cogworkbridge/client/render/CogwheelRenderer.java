package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Renders a rotating cogwheel.
 *
 * <p>The disc and hub geometry live in the block model; this renderer only rotates
 * them around the block's axis.
 */
public class CogwheelRenderer extends TileEntitySpecialRenderer<CogwheelTileEntity> {

    @Override
    public void render(CogwheelTileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getContraptionRenderState() != null
                ? te.getContraptionRenderState() : te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof CogwheelBlock)) {
            return;
        }
        EnumFacing.Axis axis = ((KineticBlock) state.getBlock()).getAxis(state);
        float angle = RotatingModelRenderer.kineticAngle(te, partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(te.getWorld(),
                te.getContraptionRenderPos() != null ? te.getContraptionRenderPos() : te.getPos(),
                state, axis, angle);
        if (te.bracket().present()) FastStateRendering.getInstance().renderFast(te.bracket().model());
        GlStateManager.popMatrix();
    }
}
