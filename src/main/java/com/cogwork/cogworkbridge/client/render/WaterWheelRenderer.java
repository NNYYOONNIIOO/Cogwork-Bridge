package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.waterwheel.WaterWheelTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

public final class WaterWheelRenderer extends TileEntitySpecialRenderer<WaterWheelTileEntity> {
    @Override public void render(WaterWheelTileEntity te, double x, double y, double z, float partial, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        IBlockState state = te.getContraptionRenderState() == null ? te.getWorld().getBlockState(te.getPos()) : te.getContraptionRenderState();
        if (!(state.getBlock() instanceof WaterWheelBlock)) return;
        BlockPos renderPos = te.getContraptionRenderPos() == null ? te.getPos() : te.getContraptionRenderPos();
        EnumFacing.Axis axis = state.getValue(KineticBlock.AXIS);
        EnumFacing facing = axis == EnumFacing.Axis.X ? EnumFacing.EAST : axis == EnumFacing.Axis.Z ? EnumFacing.SOUTH : EnumFacing.UP;
        IBlockState model = AllBlocks.SMALL_WATER_WHEEL_MODELS[te.getWood()].getDefaultState().withProperty(RenderPartBlock.FACING, facing);
        boolean alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.translate(x, y, z);
            RotatingModelRenderer.renderRotated(te.getWorld(), renderPos, model, axis, RotatingModelRenderer.kineticAngle(te, partial));
        } finally {
            GlStateManager.alphaFunc(alphaFunction, alphaReference);
            if (!alphaEnabled) GlStateManager.disableAlpha();
            GlStateManager.popMatrix();
        }
    }
}
