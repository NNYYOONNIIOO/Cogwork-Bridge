package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.schematics.SchematicannonTile;
import com.cogwork.cogworkbridge.content.schematics.SchematicFluids;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.math.BlockPos;

public final class SchematicannonRenderer extends TileEntitySpecialRenderer<SchematicannonTile> {
    @Override public void render(SchematicannonTile tile, double x, double y, double z, float partial, int stage, float alpha) {
        if (tile.getWorld() == null) return;
        BlockPos origin = tile.getPos(), target = tile.hasTarget() ? tile.target() : origin.add(0, 4, 4);
        double dx = target.getX() - origin.getX(), dy = target.getY() - origin.getY(), dz = target.getZ() - origin.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz)) + 90F;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) Math.toDegrees(Math.atan2(distance, dy * 3D + distance * 2D)) + 10F;
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        GlStateManager.pushMatrix(); GlStateManager.translate(.5, 0, .5); GlStateManager.rotate(yaw, 0, 1, 0); GlStateManager.translate(-.5, 0, -.5);
        FastStateRendering.getInstance().renderFast(AllBlocks.SCHEMATICANNON_CONNECTOR.getDefaultState()); GlStateManager.popMatrix();
        GlStateManager.pushMatrix(); GlStateManager.translate(.5, 15D / 16D, .5); GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.rotate(pitch, 0, 0, 1); GlStateManager.translate(-.5, -15D / 16D, -.5);
        FastStateRendering.getInstance().renderFast(AllBlocks.SCHEMATICANNON_PIPE.getDefaultState()); GlStateManager.popMatrix();
        for (SchematicannonTile.Flight flight : tile.flights()) {
            double fx = flight.target().getX() - origin.getX(), fy = flight.target().getY() - origin.getY();
            double fz = flight.target().getZ() - origin.getZ(), duration = flight.duration();
            double t = Math.max(0, Math.min(1, (duration - flight.ticks() + partial) / duration));
            double rise = Math.sqrt(fx * fx + fy * fy + fz * fz) * .6D + fy;
            GlStateManager.pushMatrix(); GlStateManager.translate(.5D + fx * t, 1.5D + 2 * (1 - t) * t * rise + t * t * fy, .5D + fz * t);
            GlStateManager.rotate((float) (360 * t), 0, 1, 0); GlStateManager.rotate((float) (360 * t), 1, 0, 0);
            GlStateManager.scale(.3, .3, .3);
            if (flight.state() == null) {
                net.minecraft.item.ItemStack item = new net.minecraft.item.ItemStack(flight.armorStand()
                        ? net.minecraft.init.Items.ARMOR_STAND : net.minecraft.init.Items.ITEM_FRAME);
                net.minecraft.client.Minecraft.getMinecraft().getRenderItem().renderItem(item,
                        net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.GROUND);
            } else if (SchematicFluids.fluidOf(flight.state()) != null) {
                net.minecraft.item.ItemStack item = flight.material();
                if (item.isEmpty()) item = SchematicFluids.displayContainer(SchematicFluids.fluidOf(flight.state()));
                if (!item.isEmpty()) net.minecraft.client.Minecraft.getMinecraft().getRenderItem().renderItem(item,
                        net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.GROUND);
            } else if (flight.state().getMaterial() != net.minecraft.block.material.Material.AIR) {
                GlStateManager.translate(-.5, -.5, -.5);
                FastStateRendering.getInstance().renderFast(flight.state());
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
    @Override public boolean isGlobalRenderer(SchematicannonTile tile) { return !tile.flights().isEmpty(); }
}
