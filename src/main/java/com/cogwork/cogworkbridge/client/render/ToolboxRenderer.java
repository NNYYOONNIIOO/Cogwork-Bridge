package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.equipment.ToolboxBlock;
import com.cogwork.cogworkbridge.content.equipment.ToolboxTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public final class ToolboxRenderer extends TileEntitySpecialRenderer<ToolboxTileEntity> {
    @Override public void render(ToolboxTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof ToolboxBlock block)) return;
        float open = tile.openProgress(partial);
        float drawer = tile.drawerProgress(partial);
        float angle = switch (state.getValue(ToolboxBlock.FACING)) { case EAST -> -90F; case SOUTH -> 180F; case WEST -> 90F; default -> 0F; };
        GlStateManager.pushMatrix(); GlStateManager.translate(x + .5D, y + .5D, z + .5D); GlStateManager.rotate(angle, 0, 1, 0); GlStateManager.translate(-.5D, -.5D, -.5D);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 6D / 16D, 12D / 16D); GlStateManager.rotate(135F * open, 1, 0, 0); GlStateManager.translate(0, -6D / 16D, -12D / 16D);
        FastStateRendering.getInstance().renderFast(AllBlocks.TOOLBOX_LIDS[block.color()].getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP));
        GlStateManager.popMatrix();
        for (int i = 0; i < 2; i++) {
            // The closed drawer front otherwise shares the casing's exact depth.
            GlStateManager.pushMatrix(); GlStateManager.translate(0, i / 8D, -drawer * .175D * (2 - i) - 1D / 1024D);
            FastStateRendering.getInstance().renderFast(AllBlocks.TOOLBOX_DRAWER.getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.UP));
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
}
