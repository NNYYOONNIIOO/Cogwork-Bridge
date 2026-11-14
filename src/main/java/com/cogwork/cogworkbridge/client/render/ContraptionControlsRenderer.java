package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.client.ContraptionControlsModels;
import com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsBlock;
import com.cogwork.cogworkbridge.content.contraptions.controls.ContraptionControlsTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

public final class ContraptionControlsRenderer extends TileEntitySpecialRenderer<ContraptionControlsTileEntity> {
    @Override public void render(ContraptionControlsTileEntity tile, double x, double y, double z, float partial, int damage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof ContraptionControlsBlock)) return;
        EnumFacing facing = state.getValue(ContraptionControlsBlock.FACING);
        int angle = facing == EnumFacing.EAST ? 90 : facing == EnumFacing.SOUTH ? 180 : facing == EnumFacing.WEST ? 270 : 0;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5D, y + .5D, z + .5D);
        GlStateManager.rotate(angle, 0, 1, 0);
        GlStateManager.translate(-.5D, -.5D, -.5D);
        IBakedModel indicator = ContraptionControlsModels.indicator(tile.isDisabled());
        if (indicator != null) FastStateRendering.getInstance().renderFast(indicator, state);
        IBakedModel button = ContraptionControlsModels.button();
        if (button != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, -tile.button(partial) / 24F, tile.button(partial) / 80F);
            FastStateRendering.getInstance().renderFast(button, state);
            GlStateManager.popMatrix();
        }
        if (!tile.getFilter().isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(.5D, .89D, .35D);
            GlStateManager.rotate(67.5F, 1, 0, 0);
            GlStateManager.scale(.3F, .3F, .3F);
            RenderHelper.enableStandardItemLighting();
            net.minecraft.client.Minecraft.getMinecraft().getRenderItem().renderItem(tile.getFilter(),
                    net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
        if (tile.getWorld() instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity entity
                && entity.isElevator()
                && fake.getParentWorld().getTileEntity(entity.getElevatorController()) instanceof com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyTileEntity pulley) {
            var floor = pulley.getSelectedFloor();
            if (floor != null) {
                GlStateManager.pushMatrix();
                // The button partial faces north, while the upstream floor
                // inscription is transformed from the opposite horizontal basis.
                GlStateManager.translate(.5D, 0D, .5D);
                GlStateManager.rotate(180F, 0F, 1F, 0F);
                GlStateManager.translate(-.5D, 0D, -.5D);
                GlStateManager.translate(.4D, 1.125D - tile.button(partial) / 24F, .5D);
                GlStateManager.rotate(-67.5F, 1, 0, 0);
                drawFloorText(floor.getShortName(), 0F, .15F, -.25F, 12F);
                drawFloorText(floor.getLongName(), -.0635F, .06F, -.25F, 55F);
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.popMatrix();
    }

    private static void drawFloorText(String text, float x, float y, float z, float minimumWidth) {
        if (text == null || text.trim().isEmpty()) return;
        var font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        int actualWidth = font.getStringWidth(text);
        float width = Math.max(actualWidth, minimumWidth);
        float scale = 1F / ((minimumWidth == 12F ? 5F : 3F) * (width - .5F));
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(scale, -scale, scale);
        font.drawString(text, Math.max(0F, width - actualWidth) / 2F, (width - 8F) / 2F, 0xffffb85c, false);
        GlStateManager.popMatrix();
    }
}
