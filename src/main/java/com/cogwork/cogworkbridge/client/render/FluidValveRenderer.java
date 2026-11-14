package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.fluids.FluidValveBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidValveTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class FluidValveRenderer extends TileEntitySpecialRenderer<FluidValveTileEntity> {
    @Override
    public void render(FluidValveTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof FluidValveBlock valve)) return;
        EnumFacing facing = state.getValue(FluidValveBlock.FACING);
        EnumFacing.Axis shaftAxis = valve.getAxis(state);
        EnumFacing.Axis pipeAxis = FluidValveBlock.getPipeAxis(state);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(ShaftBlock.AXIS, shaftAxis);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, shaftAxis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));

        GlStateManager.translate(0.5D, 0.5D, 0.5D);
        float horizontal = facing.getAxis().isHorizontal() ? facing.getHorizontalAngle() : 0.0F;
        GlStateManager.rotate(horizontal, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(facing == EnumFacing.UP ? 0.0F : facing == EnumFacing.DOWN ? 180.0F : 90.0F,
                1.0F, 0.0F, 0.0F);
        int offset = pipeAxis == EnumFacing.Axis.Y
                || pipeAxis.isHorizontal() && shaftAxis == EnumFacing.Axis.X ? 90 : 0;
        GlStateManager.rotate(offset - 90.0F * tile.getPointer(partialTicks), 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        IBlockState pointer = AllBlocks.FLUID_VALVE_POINTER.getDefaultState()
                .withProperty(RenderPartBlock.FACING, EnumFacing.UP);
        FastStateRendering.getInstance().renderFast(pointer);
        GlStateManager.popMatrix();
    }
}
