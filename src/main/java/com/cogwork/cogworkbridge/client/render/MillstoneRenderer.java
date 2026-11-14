package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.processing.millstone.MillstoneBlock;
import com.cogwork.cogworkbridge.content.processing.millstone.MillstoneTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class MillstoneRenderer extends TileEntitySpecialRenderer<MillstoneTileEntity> {
    @Override
    public void render(MillstoneTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MillstoneBlock)) return;
        BlockPos renderPos = tile.getContraptionRenderPos() != null ? tile.getContraptionRenderPos() : tile.getPos();
        IBlockState inner = AllBlocks.MILLSTONE_INNER.getDefaultState()
                .withProperty(RenderPartBlock.FACING, EnumFacing.UP);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, inner,
                EnumFacing.Axis.Y, tile.animationAngle(partialTicks));
        GlStateManager.popMatrix();
    }
}
