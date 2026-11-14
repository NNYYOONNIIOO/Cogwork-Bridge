package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class CrushingWheelRenderer extends TileEntitySpecialRenderer<CrushingWheelTileEntity> {
    @Override
    public void render(CrushingWheelTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState source = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(source.getBlock() instanceof CrushingWheelBlock)) return;
        EnumFacing.Axis axis = source.getValue(KineticBlock.AXIS);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        IBlockState wheel = AllBlocks.CRUSHING_WHEEL.getDefaultState().withProperty(KineticBlock.AXIS, axis);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, wheel, axis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));
        GlStateManager.popMatrix();
    }
}
