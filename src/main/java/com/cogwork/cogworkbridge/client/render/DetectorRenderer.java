package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.redstone.DetectorBlock;
import com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Render the same stored filter on each exposed detector panel. */
public final class DetectorRenderer extends TileEntitySpecialRenderer<DetectorTileEntity> {
    @Override public void render(DetectorTileEntity tile, double x, double y, double z,
                                 float partial, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        ItemStack filter = tile.getFilter();
        if (filter.isEmpty()) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof DetectorBlock)) return;
        Minecraft mc = Minecraft.getMinecraft();
        boolean block = mc.getRenderItem().getItemModelWithOverrides(filter, tile.getWorld(), null).isGui3d();
        RenderHelper.enableStandardItemLighting();
        for (EnumFacing side : EnumFacing.values()) {
            if (!DetectorBlock.isFilterSide(state, side)) continue;
            Vec3d center = DetectorBlock.filterCenter(side);
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + center.x, y + center.y, z + center.z);
            if (side == EnumFacing.UP) GlStateManager.rotate(-90F, 1, 0, 0);
            else if (side == EnumFacing.DOWN) GlStateManager.rotate(90F, 1, 0, 0);
            else GlStateManager.rotate(-side.getHorizontalAngle(), 0, 1, 0);
            GlStateManager.translate(0, 0, block ? .002D : .035D);
            float scale = .5F * (block ? 1F + 1F / 64F : .5F + 1F / 64F);
            GlStateManager.scale(scale, scale, scale);
            mc.getRenderItem().renderItem(filter, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
    }
}
