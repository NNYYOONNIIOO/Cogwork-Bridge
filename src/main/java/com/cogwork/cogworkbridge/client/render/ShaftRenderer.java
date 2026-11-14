package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Renders a rotating shaft.
 *
 * <p>The shape comes from the block model; this renderer only supplies the rotation
 * around the block's axis, exactly like Create's kinetic renderers.
 */
public class ShaftRenderer extends TileEntitySpecialRenderer<ShaftTileEntity> {

    @Override
    public void render(ShaftTileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getContraptionRenderState() != null
                ? te.getContraptionRenderState() : te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof ShaftBlock)) {
            return;
        }
        EnumFacing.Axis axis = ((KineticBlock) state.getBlock()).getAxis(state);
        float angle = RotatingModelRenderer.kineticAngle(te, partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.decoration.palette.GirderShaftBlock)
            state = com.cogwork.cogworkbridge.registry.AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis);
        RotatingModelRenderer.renderRotated(te.getWorld(),
                te.getContraptionRenderPos() != null ? te.getContraptionRenderPos() : te.getPos(),
                state, axis, angle);
        if (te.bracket().present()) FastStateRendering.getInstance().renderFast(te.bracket().model());
        GlStateManager.popMatrix();
    }
}
