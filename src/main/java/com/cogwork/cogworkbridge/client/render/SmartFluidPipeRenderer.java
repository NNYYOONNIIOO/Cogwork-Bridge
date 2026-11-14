package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeBlock;
import com.cogwork.cogworkbridge.content.fluids.SmartFluidPipeTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

public final class SmartFluidPipeRenderer extends TileEntitySpecialRenderer<SmartFluidPipeTileEntity> {
    @Override
    public void render(SmartFluidPipeTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        ItemStack item = tile.getFilterItem();
        if (item.isEmpty()) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof SmartFluidPipeBlock)) return;
        Vec3d center = SmartFluidPipeBlock.filterCenter(state);
        Minecraft minecraft = Minecraft.getMinecraft();
        IBakedModel model = minecraft.getRenderItem().getItemModelWithOverrides(item, null, null);
        float itemScale = (model.isGui3d() ? 1.0F : 0.5F) + 1.0F / 64.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + center.x, y + center.y, z + center.z);
        GlStateManager.rotate(SmartFluidPipeBlock.filterYaw(state), 0, 1, 0);
        GlStateManager.rotate(SmartFluidPipeBlock.filterPitch(state), 1, 0, 0);
        GlStateManager.scale(0.51F, 0.51F, 0.51F);
        GlStateManager.scale(itemScale, itemScale, itemScale);
        if (!model.isGui3d()) GlStateManager.translate(0.0F, 0.0F, -0.15F);
        RenderHelper.enableStandardItemLighting();
        minecraft.getRenderItem().renderItem(item, ItemCameraTransforms.TransformType.FIXED);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
}
