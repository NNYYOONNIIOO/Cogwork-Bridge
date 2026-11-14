package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import java.util.Random;

public final class DepotRenderer extends TileEntitySpecialRenderer<DepotTileEntity> {
    @Override
    public void render(DepotTileEntity depot, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (depot.getTransportedItem().isEmpty() && depot.comparatorLevel() == 0) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D, y + 15.0D / 16.0D, z + 0.5D);
        RenderHelper.enableStandardItemLighting();
        renderStack(depot.getTransportedItem(), 0, depot);
        for (int slot = 1; slot < depot.getSlots(); slot++) {
            ItemStack output = depot.getStackInSlot(slot);
            if (output.isEmpty()) continue;
            GlStateManager.pushMatrix();
            double angle = (slot - 1) * Math.PI / 4.0D;
            GlStateManager.translate(Math.cos(angle) * 0.35D, 0, Math.sin(angle) * 0.35D);
            renderStack(output, slot, depot);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private static void renderStack(ItemStack stack, int seed, DepotTileEntity depot) {
        if (stack.isEmpty()) return;
        GlStateManager.pushMatrix();
        Minecraft mc = Minecraft.getMinecraft();
        boolean blockItem = mc.getRenderItem().getItemModelWithOverrides(stack, depot.getWorld(), null).isGui3d();
        Random random = new Random(seed);
        GlStateManager.rotate(random.nextFloat() * 360F, 0, 1, 0);
        int copies = Math.min(4, 1 + (31 - Integer.numberOfLeadingZeros(stack.getCount())) / 2);
        for (int i = 0; i < copies; i++) {
            GlStateManager.pushMatrix();
            if (blockItem) GlStateManager.translate(random.nextFloat() * .0625D * i, 0,
                    random.nextFloat() * .0625D * i);
            GlStateManager.scale(.5F, .5F, .5F);
            if (!blockItem) {
                GlStateManager.translate(0, -3D / 16D, 0);
                GlStateManager.rotate(90F, 1, 0, 0);
            }
            mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
            if (!blockItem) GlStateManager.rotate(10F, 0, 1, 0);
            GlStateManager.translate(0, blockItem ? 1D / 64D : 1D / 16D, 0);
        }
        GlStateManager.popMatrix();
    }
}
