package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedShaftBlock;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedShaftTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Renders the baked shaft inside the static casing. */
public final class EncasedShaftRenderer extends TileEntitySpecialRenderer<EncasedShaftTileEntity> {

    @Override
    public void render(EncasedShaftTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof EncasedShaftBlock block)) {
            return;
        }
        EnumFacing.Axis axis = block.getAxis(state);
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis);
        BlockPos renderPos = tile.getContraptionRenderPos() != null
                ? tile.getContraptionRenderPos() : tile.getPos();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, axis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));
        GlStateManager.popMatrix();
    }
}
