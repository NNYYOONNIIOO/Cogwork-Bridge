package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.redstone.PlacardBlock;
import com.cogwork.cogworkbridge.content.redstone.PlacardTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

public final class PlacardRenderer extends TileEntitySpecialRenderer<PlacardTileEntity> {
    @Override public void render(PlacardTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        ItemStack item = tile.getItem(); if (item.isEmpty()) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos()); if (!(state.getBlock() instanceof PlacardBlock)) return;
        var mount = state.getValue(PlacardBlock.MOUNT); var facing = state.getValue(PlacardBlock.FACING);
        Minecraft mc = Minecraft.getMinecraft(); boolean blockItem = mc.getRenderItem().getItemModelWithOverrides(item, tile.getWorld(), null).isGui3d();
        GlStateManager.pushMatrix(); GlStateManager.translate(x + .5, y + .5, z + .5);
        GlStateManager.rotate(180F + facing.getHorizontalAngle() + (mount == PlacardBlock.Mount.CEILING ? 180F : 0F), 0, 1, 0);
        GlStateManager.rotate(mount == PlacardBlock.Mount.CEILING ? -90F : mount == PlacardBlock.Mount.FLOOR ? 90F : 0F, 1, 0, 0);
        GlStateManager.translate(0, 0, 4.5D / 16D); float scale = blockItem ? .5F : .375F; GlStateManager.scale(scale, scale, scale);
        RenderHelper.enableStandardItemLighting(); mc.getRenderItem().renderItem(item, ItemCameraTransforms.TransformType.FIXED); RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
