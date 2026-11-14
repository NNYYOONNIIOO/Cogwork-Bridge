package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.processing.press.MechanicalPressBlock;
import com.cogwork.cogworkbridge.content.processing.press.MechanicalPressTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class MechanicalPressRenderer extends TileEntitySpecialRenderer<MechanicalPressTileEntity> {
    @Override
    public void render(MechanicalPressTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MechanicalPressBlock press)) return;
        EnumFacing.Axis axis = press.getAxis(state);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis);
        IBlockState head = AllBlocks.MECHANICAL_PRESS_HEAD.getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, axis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));
        GlStateManager.translate(0.0D, -tile.headOffset(partialTicks), 0.0D);
        if (axis == EnumFacing.Axis.X) {
            GlStateManager.translate(0.5D, 0.0D, 0.5D);
            GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5D, 0.0D, -0.5D);
        }
        FastStateRendering.getInstance().renderFast(head);
        GlStateManager.popMatrix();
    }
}
