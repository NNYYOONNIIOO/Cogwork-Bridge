package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.fluids.MechanicalPumpBlock;
import com.cogwork.cogworkbridge.content.fluids.MechanicalPumpTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class MechanicalPumpRenderer extends TileEntitySpecialRenderer<MechanicalPumpTileEntity> {
    @Override
    public void render(MechanicalPumpTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MechanicalPumpBlock)) return;
        EnumFacing facing = state.getValue(MechanicalPumpBlock.FACING);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        IBlockState cog = AllBlocks.MECHANICAL_PUMP_COG.getDefaultState().withProperty(RenderPartBlock.FACING, facing);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, cog, facing.getAxis(),
                RotatingModelRenderer.kineticAngle(tile, partialTicks));
        GlStateManager.popMatrix();
    }
}
