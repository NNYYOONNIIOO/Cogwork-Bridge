package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorBlock;
import com.cogwork.cogworkbridge.content.logistics.ejector.EjectorTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class EjectorRenderer extends TileEntitySpecialRenderer<EjectorTileEntity> {
    @Override public void render(EjectorTileEntity tile, double x, double y, double z, float partialTicks,
                                 int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getContraptionRenderState() != null ? tile.getContraptionRenderState()
                : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof EjectorBlock ejector)) return;
        EnumFacing facing = state.getValue(EjectorBlock.FACING);
        EnumFacing.Axis axis = ejector.getAxis(state);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis);
        IBlockState top = AllBlocks.EJECTOR_TOP.getDefaultState().withProperty(RenderPartBlock.FACING, EnumFacing.NORTH);
        for (EjectorTileEntity.FlightView flight : tile.flightViews(partialTicks)) {
            GlStateManager.pushMatrix(); GlStateManager.translate(x + flight.x(), y + flight.y(), z + flight.z());
            GlStateManager.scale(0.5F, 0.5F, 0.5F); RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(flight.stack(), ItemCameraTransforms.TransformType.GROUND);
            RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, axis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));
        GlStateManager.translate(0.5D, 0, 0.5D);
        GlStateManager.rotate(switch (facing) { case EAST -> -90; case SOUTH -> 180; case WEST -> 90; default -> 0; }, 0, 1, 0);
        GlStateManager.translate(-0.5D, 0, -0.5D);
        GlStateManager.translate(0, 11.25D / 16D, 0.75D / 16D);
        GlStateManager.rotate(-tile.lidProgress(partialTicks) * 70, 1, 0, 0);
        GlStateManager.translate(0, -11.25D / 16D, -0.75D / 16D);
        FastStateRendering.getInstance().renderFast(top);
        ItemStack stack = tile.getStackInSlot(0);
        if (!stack.isEmpty()) {
            GlStateManager.translate(0.5D, 15 / 16D, 0.5D);
            GlStateManager.rotate(90, 1, 0, 0);
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.popMatrix();
    }
}
