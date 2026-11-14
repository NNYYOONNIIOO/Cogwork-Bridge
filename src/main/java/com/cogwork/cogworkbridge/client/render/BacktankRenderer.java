package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.equipment.BacktankBlock;
import com.cogwork.cogworkbridge.content.equipment.BacktankTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public final class BacktankRenderer extends TileEntitySpecialRenderer<BacktankTileEntity> {
    @Override
    public void render(BacktankTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getContraptionRenderState() == null
                ? tile.getWorld().getBlockState(tile.getPos()) : tile.getContraptionRenderState();
        if (!(state.getBlock() instanceof BacktankBlock)) return;
        boolean netherite = state.getBlock() == AllBlocks.NETHERITE_BACKTANK;
        float angle = RotatingModelRenderer.kineticAngle(tile, partial);
        IBlockState shaft = (netherite ? AllBlocks.NETHERITE_BACKTANK_SHAFT : AllBlocks.COPPER_BACKTANK_SHAFT)
                .getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP);
        IBlockState cogs = (netherite ? AllBlocks.NETHERITE_BACKTANK_COGS : AllBlocks.COPPER_BACKTANK_COGS)
                .getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), tile.getPos(), shaft, EnumFacing.Axis.Y, angle);
        GlStateManager.translate(.5D, .5D, .5D);
        int orientation = switch (state.getValue(BacktankBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        GlStateManager.rotate(orientation, 0, 1, 0);
        GlStateManager.translate(-.5D, -.5D, -.5D);
        GlStateManager.translate(0, 6.5D / 16D, 11D / 16D);
        GlStateManager.rotate(angle / 4F, 1, 0, 0);
        GlStateManager.translate(0, -6.5D / 16D, -11D / 16D);
        FastStateRendering.getInstance().renderFast(cogs);
        GlStateManager.popMatrix();
    }
}
