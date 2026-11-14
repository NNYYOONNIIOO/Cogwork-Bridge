package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.hatch.ItemHatchBlock;
import com.cogwork.cogworkbridge.content.logistics.hatch.ItemHatchTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

public final class ItemHatchRenderer extends TileEntitySpecialRenderer<ItemHatchTileEntity> {
    @Override public void render(ItemHatchTileEntity tile, double x, double y, double z, float partialTicks,
                                 int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        ItemStack filter = tile.getFilter();
        if (filter.isEmpty() || !(state.getBlock() instanceof ItemHatchBlock)) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y, z + 0.5D);
        GlStateManager.rotate(state.getValue(ItemHatchBlock.FACING).getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(0, 5.15D / 16, 1.5D / 16);
        GlStateManager.rotate(-45, 1, 0, 0);
        GlStateManager.scale(0.3F, 0.3F, 0.3F);
        RenderHelper.enableStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItem(filter, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
