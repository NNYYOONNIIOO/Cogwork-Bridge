package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerBlock;
import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class MechanicalMixerRenderer extends TileEntitySpecialRenderer<MechanicalMixerTileEntity> {
    @Override
    public void render(MechanicalMixerTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MechanicalMixerBlock)) return;

        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        IBlockState cog = AllBlocks.COGWHEEL_SHAFTLESS.getDefaultState()
                .withProperty(RenderPartBlock.FACING, EnumFacing.UP);
        IBlockState pole = AllBlocks.MECHANICAL_MIXER_POLE.getDefaultState()
                .withProperty(RenderPartBlock.FACING, EnumFacing.UP);
        IBlockState head = AllBlocks.MECHANICAL_MIXER_HEAD.getDefaultState()
                .withProperty(RenderPartBlock.FACING, EnumFacing.UP);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, cog, EnumFacing.Axis.Y,
                tile.rotationAngle(partialTicks, 1.0F));

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, -tile.headOffset(partialTicks), 0.0D);
        FastStateRendering.getInstance().renderFast(pole);
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5D, 0.5D, 0.5D);
        GlStateManager.rotate(tile.headAngle(partialTicks), 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        GlStateManager.translate(0.0D, -tile.headOffset(partialTicks), 0.0D);
        FastStateRendering.getInstance().renderFast(head);
        GlStateManager.popMatrix();
        GlStateManager.popMatrix();
    }
}
