package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock;
import com.cogwork.cogworkbridge.content.redstone.RedstoneLinkTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Displays the two configured frequency items on the actual link slots. */
public final class RedstoneLinkRenderer extends TileEntitySpecialRenderer<RedstoneLinkTileEntity> {
    @Override public void render(RedstoneLinkTileEntity tile, double x, double y, double z,
                                 float partial, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof RedstoneLinkBlock)) return;
        EnumFacing facing = state.getValue(RedstoneLinkBlock.FACING);
        RenderHelper.enableStandardItemLighting();
        for (int slot = 0; slot < 2; slot++) {
            ItemStack item = tile.getFrequency(slot);
            if (item.isEmpty()) continue;
            GlStateManager.pushMatrix();
            Vec3d center = RedstoneLinkBlock.frequencySlotCenter(facing, slot);
            GlStateManager.translate(x + center.x, y + center.y, z + center.z);
            if (facing == EnumFacing.UP) {
                GlStateManager.rotate(-90F, 1, 0, 0);
            } else if (facing == EnumFacing.DOWN) {
                GlStateManager.rotate(90F, 1, 0, 0);
            } else {
                GlStateManager.rotate(-facing.getHorizontalAngle(), 0, 1, 0);
            }
            boolean blockItem = Minecraft.getMinecraft().getRenderItem()
                    .getItemModelWithOverrides(item, tile.getWorld(), null).isGui3d();
            // A flat FIXED item has almost no depth, so put it just outside
            // the frequency pad.  Block items keep their familiar depth.
            GlStateManager.translate(0, 0, blockItem ? .002D : .035D);
            float scale = .5F * (blockItem ? 1F + 1F / 64F : .5F + 1F / 64F);
            GlStateManager.scale(scale, scale, scale);
            Minecraft.getMinecraft().getRenderItem().renderItem(item, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
    }
}
