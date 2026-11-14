package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock;
import com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class MechanicalPistonRenderer extends TileEntitySpecialRenderer<MechanicalPistonTileEntity> {
    @Override public void render(MechanicalPistonTileEntity tile, double x, double y, double z, float partial, int stage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() == null ? tile.getWorld().getBlockState(tile.getPos()) : tile.getContraptionRenderState();
        if (!(state.getBlock() instanceof MechanicalPistonBlock piston)) return;
        EnumFacing.Axis axis = piston.getAxis(state);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(ShaftBlock.AXIS, axis);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, axis, RotatingModelRenderer.kineticAngle(tile, partial));
        GlStateManager.popMatrix();
    }
}
