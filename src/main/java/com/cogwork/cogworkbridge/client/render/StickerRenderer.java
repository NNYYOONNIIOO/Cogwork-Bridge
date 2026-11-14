package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.chassis.StickerBlock;
import com.cogwork.cogworkbridge.content.contraptions.chassis.StickerTileEntity;
import com.cogwork.cogworkbridge.client.StickerModels;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/** Renders the moving piston head of a sticker. */
public final class StickerRenderer extends TileEntitySpecialRenderer<StickerTileEntity> {
    @Override
    public void render(StickerTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof StickerBlock)) return;
        float progress = tile.getProgress(partialTicks);
        IBakedModel head = StickerModels.headModel();
        if (head == null) return;
        EnumFacing facing = state.getValue(StickerBlock.FACING);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.translate(0.5D, 0.5D, 0.5D);
        rotateTo(facing);
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        GlStateManager.translate(0.0D, progress * progress * 0.25D, 0.0D);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        FastStateRendering.getInstance().renderFast(head, state);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void rotateTo(EnumFacing facing) {
        switch (facing) {
            case DOWN: GlStateManager.rotate(180, 1, 0, 0); break;
            case NORTH: GlStateManager.rotate(-90, 1, 0, 0); break;
            case SOUTH: GlStateManager.rotate(90, 1, 0, 0); break;
            case WEST: GlStateManager.rotate(90, 0, 0, 1); break;
            case EAST: GlStateManager.rotate(-90, 0, 0, 1); break;
            case UP: default: break;
        }
    }
}
