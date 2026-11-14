package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorBlock;
import com.cogwork.cogworkbridge.content.kinetics.motor.CreativeMotorTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;

/**
 * Renders the creative motor, rotating around its output axis.
 */
public class CreativeMotorRenderer extends TileEntitySpecialRenderer<CreativeMotorTileEntity> {

    @Override
    public void render(CreativeMotorTileEntity te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getContraptionRenderState() != null
                ? te.getContraptionRenderState() : te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof CreativeMotorBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(CreativeMotorBlock.FACING);
        IBlockState rotor = AllBlocks.CREATIVE_MOTOR_ROTOR.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing);
        EnumFacing.Axis axis = facing.getAxis();
        float angle = RotatingModelRenderer.kineticAngle(te, partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(te.getWorld(),
                te.getContraptionRenderPos() != null ? te.getContraptionRenderPos() : te.getPos(),
                rotor, axis, angle);
        GlStateManager.popMatrix();
    }
}
