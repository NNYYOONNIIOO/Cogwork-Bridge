package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.gearbox.GearboxBlock;
import com.cogwork.cogworkbridge.content.kinetics.gearbox.GearboxTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** The static housing stays in the block model; each port uses a baked half-shaft. */
public final class GearboxRenderer extends TileEntitySpecialRenderer<GearboxTileEntity> {

    @Override
    public void render(GearboxTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof GearboxBlock gearbox)) {
            return;
        }
        BlockPos renderPos = tile.getContraptionRenderPos() != null
                ? tile.getContraptionRenderPos() : tile.getPos();
        float angle = RotatingModelRenderer.kineticAngle(tile, partialTicks);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        for (EnumFacing face : EnumFacing.VALUES) {
            if (face.getAxis() == gearbox.getAxis(state)) {
                continue;
            }
            IBlockState shaft = AllBlocks.CREATIVE_MOTOR_ROTOR.getDefaultState()
                    .withProperty(RenderPartBlock.FACING, face);
            RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, face.getAxis(),
                    (float) (angle * gearbox.shaftPortModifier(state, face)));
        }
        GlStateManager.popMatrix();
    }
}
