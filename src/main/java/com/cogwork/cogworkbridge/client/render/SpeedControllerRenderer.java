package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerBlock;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Renders the controller's rotating shaft and the optional upper bracket. */
public final class SpeedControllerRenderer extends TileEntitySpecialRenderer<SpeedControllerTileEntity> {
    @Override
    public void render(SpeedControllerTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof SpeedControllerBlock controller)) {
            return;
        }
        EnumFacing.Axis axis = controller.getAxis(state);
        BlockPos renderPos = tile.getContraptionRenderPos() != null
                ? tile.getContraptionRenderPos() : tile.getPos();
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, axis);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, axis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));

        BlockPos above = renderPos.up();
        IBlockState cogState = tile.getWorld().getBlockState(above);
        if (cogState.getBlock() instanceof CogwheelBlock cog && cog.isLarge()
                && cog.getAxis(cogState) != EnumFacing.Axis.Y
                && cog.getAxis(cogState) != axis) {
            IBlockState bracket = AllBlocks.SPEED_CONTROLLER_BRACKET.getDefaultState()
                    .withProperty(RenderPartBlock.FACING, EnumFacing.UP);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0D, 1.0D, 0.0D);
            GlStateManager.translate(0.5D, 0.5D, 0.5D);
            GlStateManager.rotate(axis == EnumFacing.Axis.X ? 180.0F : 90.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5D, -0.5D, -0.5D);
            FastStateRendering.getInstance().renderFast(bracket);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }
}
