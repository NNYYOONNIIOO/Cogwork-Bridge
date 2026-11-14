package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeBlock;
import com.cogwork.cogworkbridge.content.kinetics.gauge.GaugeTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class GaugeRenderer extends TileEntitySpecialRenderer<GaugeTileEntity> {
    @Override public void render(GaugeTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getContraptionRenderState() == null ? tile.getWorld().getBlockState(tile.getPos())
                : tile.getContraptionRenderState();
        if (!(state.getBlock() instanceof GaugeBlock block)) return;
        EnumFacing.Axis axis = block.getAxis(state);
        EnumFacing facing = state.getValue(GaugeBlock.FACING);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos,
                AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis), axis,
                RotatingModelRenderer.kineticAngle(tile, partial));
        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            if (face == facing.getOpposite() || face.getAxis() == axis || axis == EnumFacing.Axis.Y && face != facing) continue;
            GlStateManager.pushMatrix();
            GlStateManager.translate(.5D, .5D, .5D);
            GlStateManager.rotate(switch (face) { case NORTH -> 90F; case SOUTH -> -90F; case WEST -> 180F; default -> 0F; }, 0, 1, 0);
            GlStateManager.translate(-.5D, -.5D, -.5D);
            FastStateRendering.getInstance().renderFast((block.isStressGauge() ? AllBlocks.GAUGE_HEAD_STRESS : AllBlocks.GAUGE_HEAD_SPEED).getDefaultState());
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 5.75D / 16D, 5.75D / 16D);
            GlStateManager.rotate(-90F * tile.getDial(partial), 1, 0, 0);
            GlStateManager.translate(0, -5.75D / 16D, -5.75D / 16D);
            FastStateRendering.getInstance().renderFast(AllBlocks.GAUGE_DIAL.getDefaultState());
            GlStateManager.popMatrix();
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
}
