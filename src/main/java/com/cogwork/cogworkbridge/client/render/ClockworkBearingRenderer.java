package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingBlock;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ClockworkBearingTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class ClockworkBearingRenderer extends TileEntitySpecialRenderer<ClockworkBearingTileEntity> {
    @Override public void render(ClockworkBearingTileEntity tile, double x, double y, double z, float partial, int stage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() == null ? tile.getWorld().getBlockState(tile.getPos()) : tile.getContraptionRenderState();
        if (!(state.getBlock() instanceof ClockworkBearingBlock)) return;
        EnumFacing facing = state.getValue(MechanicalBearingBlock.FACING);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        IBlockState top = AllBlocks.MECHANICAL_BEARING_TOP.getDefaultState().withProperty(RenderPartBlock.FACING, facing);
        IBlockState shaft = AllBlocks.MECHANICAL_BEARING_SHAFT.getDefaultState().withProperty(RenderPartBlock.FACING, facing);
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, facing.getAxis(), RotatingModelRenderer.kineticAngle(tile, partial));
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, top, facing.getAxis(), tile.getHourAngle(partial));
        GlStateManager.popMatrix();
    }
}
