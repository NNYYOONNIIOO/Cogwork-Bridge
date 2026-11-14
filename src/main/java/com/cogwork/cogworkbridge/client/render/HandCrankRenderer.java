package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.crank.HandCrankBlock;
import com.cogwork.cogworkbridge.content.kinetics.crank.HandCrankTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class HandCrankRenderer extends TileEntitySpecialRenderer<HandCrankTileEntity> {
    @Override public void render(HandCrankTileEntity te, double x, double y, double z, float partial, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        IBlockState state = te.getContraptionRenderState() == null ? te.getWorld().getBlockState(te.getPos()) : te.getContraptionRenderState();
        if (!(state.getBlock() instanceof HandCrankBlock)) return;
        EnumFacing facing = state.getValue(HandCrankBlock.FACING);
        BlockPos renderPos = te.getContraptionRenderPos() == null ? te.getPos() : te.getContraptionRenderPos();
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        if (!(te instanceof ValveHandleTileEntity)) {
            RotatingModelRenderer.renderRotated(te.getWorld(), renderPos, AllBlocks.HAND_CRANK_BASE.getDefaultState().withProperty(RenderPartBlock.FACING, facing), facing.getAxis(), RotatingModelRenderer.kineticAngle(te, partial));
        }
        if (te instanceof ValveHandleTileEntity) {
            int color = state.getBlock() instanceof com.cogwork.cogworkbridge.content.kinetics.crank.ValveHandleBlock valve ? valve.getColor() : -1;
            IBlockState part = (color < 0 ? AllBlocks.VALVE_HANDLE_MODEL : AllBlocks.COLOR_VALVE_MODELS[color])
                    .getDefaultState().withProperty(RenderPartBlock.FACING, facing);
            RotatingModelRenderer.renderRotated(te.getWorld(), renderPos, part, facing.getAxis(), te.getHandleAngle(partial));
        } else {
            renderHandle(facing, te.getHandleAngle(partial));
        }
        GlStateManager.popMatrix();
    }

    /** The upstream handle mesh points north, unlike the vertical shaft mesh. */
    private static void renderHandle(EnumFacing facing, float angle) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(.5D, .5D, .5D);
        switch (facing.getAxis()) {
            case X -> GlStateManager.rotate(angle, 1, 0, 0);
            case Y -> GlStateManager.rotate(angle, 0, 1, 0);
            case Z -> GlStateManager.rotate(angle, 0, 0, 1);
        }
        switch (facing) {
            case UP -> GlStateManager.rotate(90, 1, 0, 0);
            case DOWN -> GlStateManager.rotate(-90, 1, 0, 0);
            case SOUTH -> GlStateManager.rotate(180, 0, 1, 0);
            case EAST -> GlStateManager.rotate(-90, 0, 1, 0);
            case WEST -> GlStateManager.rotate(90, 0, 1, 0);
            case NORTH -> { }
        }
        GlStateManager.translate(-.5D, -.5D, -.5D);
        FastStateRendering.getInstance().renderFast(AllBlocks.HAND_CRANK_HANDLE.getDefaultState()
                .withProperty(RenderPartBlock.FACING, EnumFacing.UP));
        GlStateManager.popMatrix();
    }
}
