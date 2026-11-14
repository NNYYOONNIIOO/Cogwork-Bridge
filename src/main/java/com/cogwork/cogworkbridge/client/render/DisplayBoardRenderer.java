package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.display.DisplayBoardBlock;
import com.cogwork.cogworkbridge.content.display.DisplayBoardTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public final class DisplayBoardRenderer extends TileEntitySpecialRenderer<DisplayBoardTileEntity> {
    @Override public void render(DisplayBoardTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getContraptionRenderState() == null ? tile.getWorld().getBlockState(tile.getPos()) : tile.getContraptionRenderState();
        if (!(state.getBlock() instanceof DisplayBoardBlock)) return;
        EnumFacing facing = state.getValue(DisplayBoardBlock.FACING);
        EnumFacing left = facing.rotateY();
        net.minecraft.util.math.BlockPos start = tile.getPos();
        for (int i = 0; i < 31 && tile.getWorld().isBlockLoaded(start.offset(left))
                && tile.getWorld().getBlockState(start.offset(left)) == state; i++) start = start.offset(left);
        int column = Math.abs(tile.getPos().getX() - start.getX()) + Math.abs(tile.getPos().getZ() - start.getZ());
        int width = 0;
        while (width < 32 && tile.getWorld().isBlockLoaded(start.offset(left.getOpposite(), width))
                && tile.getWorld().getBlockState(start.offset(left.getOpposite(), width)) == state) width++;
        width = Math.max(1, width);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), tile.getPos(),
                AllBlocks.COGWHEEL_SHAFTLESS.getDefaultState().withProperty(RenderPartBlock.FACING, facing),
                facing.getAxis(), RotatingModelRenderer.kineticAngle(tile, partial));
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5, y + .5, z + .5);
        // The upstream model's visible face is north (z=3/16); 1.12's
        // horizontal angle is zero for south, so correct that 180-degree offset.
        GlStateManager.rotate(180F - facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(0, 0, -.315);
        GlStateManager.scale(-1F / 32F, -1F / 32F, 1F / 32F);
        GlStateManager.disableLighting();
        var font = Minecraft.getMinecraft().fontRenderer;
        int first = DisplayBoardTileEntity.firstSlot(width, column);
        for (int i = 0; i < 2; i++) {
            String line = tile.animatedLine(i);
            for (int c = 0; c < line.length(); c++) {
                String glyph = line.substring(c, c + 1);
                float px = -16F + DisplayBoardTileEntity.slotPadding(width) + (first + c) * 7F
                        - column * 32F + (7F - font.getStringWidth(glyph)) / 2F;
                font.drawString(glyph, px, i == 0 ? -11F : 5F, tile.lineColor(i), false);
            }
        }
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }
}
