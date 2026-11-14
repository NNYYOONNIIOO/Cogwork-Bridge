package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class EncasedCogwheelRenderer extends TileEntitySpecialRenderer<EncasedCogwheelTileEntity> {

    @Override
    public void render(EncasedCogwheelTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof EncasedCogwheelBlock cog)) {
            return;
        }
        BlockPos renderPos = tile.getContraptionRenderPos() != null
                ? tile.getContraptionRenderPos() : tile.getPos();
        float angle = RotatingModelRenderer.kineticAngle(tile, partialTicks);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        EnumFacing.Axis axis = cog.getAxis(state);
        EnumFacing orientation = axis == EnumFacing.Axis.X ? EnumFacing.EAST
                : axis == EnumFacing.Axis.Z ? EnumFacing.SOUTH : EnumFacing.UP;
        IBlockState wheel = (cog.isLarge() ? AllBlocks.LARGE_COGWHEEL_SHAFTLESS : AllBlocks.COGWHEEL_SHAFTLESS)
                .getDefaultState().withProperty(RenderPartBlock.FACING, orientation);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, wheel, axis, angle);
        for (EnumFacing face : EnumFacing.VALUES) {
            if (face.getAxis() != axis || !tile.isOpen(face)) {
                continue;
            }
            IBlockState shaft = AllBlocks.CREATIVE_MOTOR_ROTOR.getDefaultState()
                    .withProperty(RenderPartBlock.FACING, face);
            RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, face.getAxis(), angle);
        }
        GlStateManager.popMatrix();
    }
}
