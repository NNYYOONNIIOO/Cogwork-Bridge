package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftBlock;
import com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

public final class GantryShaftRenderer extends TileEntitySpecialRenderer<GantryShaftTileEntity> {
    @Override public void render(GantryShaftTileEntity tile, double x, double y, double z, float partial, int stage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getContraptionRenderState() == null
                ? tile.getWorld().getBlockState(tile.getPos()) : tile.getContraptionRenderState();
        if (!(state.getBlock() instanceof GantryShaftBlock shaft)) return;
        state = shaft.getExtendedState(state, tile.getWorld(), tile.getPos());
        Integer visual = state instanceof net.minecraftforge.common.property.IExtendedBlockState extended
                ? extended.getValue(GantryShaftBlock.VISUAL) : null;
        net.minecraft.client.renderer.block.model.IBakedModel model =
                com.cogwork.cogworkbridge.client.GantryShaftModels.variant(state.getValue(GantryShaftBlock.FACING), visual == null ? 0 : visual);
        if (model == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(.5D, .5D, .5D);
        float angle = RotatingModelRenderer.kineticAngle(tile, partial);
        switch (shaft.getAxis(state)) {
            case X -> GlStateManager.rotate(angle, 1, 0, 0);
            case Y -> GlStateManager.rotate(angle, 0, 1, 0);
            case Z -> GlStateManager.rotate(angle, 0, 0, 1);
        }
        GlStateManager.translate(-.5D, -.5D, -.5D);
        FastStateRendering.getInstance().renderFast(model, state);
        GlStateManager.popMatrix();
    }
}
