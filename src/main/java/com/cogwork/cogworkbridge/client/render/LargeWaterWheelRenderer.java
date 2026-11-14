package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.waterwheel.LargeWaterWheelTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.math.BlockPos;

public final class LargeWaterWheelRenderer extends TileEntitySpecialRenderer<LargeWaterWheelTileEntity> {
    @Override public void render(LargeWaterWheelTileEntity te, double x, double y, double z, float partial, int destroy, float alpha) {
        if (te.getWorld() == null) return;
        IBlockState state = te.getContraptionRenderState() == null ? te.getWorld().getBlockState(te.getPos()) : te.getContraptionRenderState();
        if (!(state.getBlock() instanceof LargeWaterWheelBlock)) return;
        BlockPos renderPos = te.getContraptionRenderPos() == null ? te.getPos() : te.getContraptionRenderPos();
        net.minecraft.util.EnumFacing.Axis axis = state.getValue(KineticBlock.AXIS);
        net.minecraft.util.EnumFacing facing = axis == net.minecraft.util.EnumFacing.Axis.X ? net.minecraft.util.EnumFacing.EAST
                : axis == net.minecraft.util.EnumFacing.Axis.Z ? net.minecraft.util.EnumFacing.SOUTH : net.minecraft.util.EnumFacing.UP;
        IBlockState model = AllBlocks.WATER_WHEEL_MODELS[te.getWood()].getDefaultState().withProperty(RenderPartBlock.FACING, facing);
        GlStateManager.pushMatrix(); GlStateManager.translate(x,y,z);
        RotatingModelRenderer.renderRotated(te.getWorld(), renderPos, model, axis, RotatingModelRenderer.kineticAngle(te, partial));
        GlStateManager.popMatrix();
    }
}
