package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.table.TableClothTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;

public final class TableClothRenderer extends TileEntitySpecialRenderer<TableClothTileEntity> {
    @Override public void render(TableClothTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        int count = tile.items().size(); if (count == 0) return;
        GlStateManager.pushMatrix(); GlStateManager.translate(x + .5, y + 3D / 16D, z + .5);
        GlStateManager.rotate(180 - tile.facing().getHorizontalAngle(), 0, 1, 0);
        RenderHelper.enableStandardItemLighting();
        for (int i = 0; i < count; i++) {
            ItemStack item = tile.items().get(i); GlStateManager.pushMatrix();
            if (count > 1) {
                double angle = Math.toRadians(i * 360D / count + 45D);
                GlStateManager.translate(Math.sin(angle) * 5D / 16D, i % 2 == 0 ? -.005 : 0, Math.cos(angle) * 5D / 16D);
            }
            boolean block = Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(item, tile.getWorld(), null).isGui3d();
            GlStateManager.scale(.5F, .5F, .5F);
            if (!block) { GlStateManager.translate(0, -3D / 16D, 0); GlStateManager.rotate(90, 1, 0, 0); }
            Minecraft.getMinecraft().getRenderItem().renderItem(item, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
        ItemStack price=tile.price();
        if (tile.isShop() && !price.isEmpty()) {
            var center=com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock.priceCenter(tile.getWorld(),tile.getPos(),tile);
            GlStateManager.pushMatrix(); GlStateManager.translate(x+center.x,y+center.y,z+center.z);
            GlStateManager.rotate(180-tile.facing().getHorizontalAngle(),0,1,0);
            if (com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock.priceOccluded(tile.getWorld(),tile.getPos(),tile)) GlStateManager.rotate(90,1,0,0);
            GlStateManager.translate(0,0,.01); GlStateManager.scale(.375,.375,.375);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(price,ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.translate(.3,-.4,.04); GlStateManager.scale(.025,-.025,.025);
            Minecraft.getMinecraft().fontRenderer.drawString(Integer.toString(tile.priceCount()),0,0,0xffffff);
            GlStateManager.popMatrix();
        }
    }
}
