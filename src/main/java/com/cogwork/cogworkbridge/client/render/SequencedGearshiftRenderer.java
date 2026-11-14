package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftBlock;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SequencedGearshiftRenderer extends TileEntitySpecialRenderer<SequencedGearshiftTileEntity> {
    @Override
    public void render(SequencedGearshiftTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        World world = tile.getWorld();
        if (world == null) {
            return;
        }
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : world.getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof SequencedGearshiftBlock block)) {
            return;
        }
        BlockPos renderPos = tile.getContraptionRenderPos() != null
                ? tile.getContraptionRenderPos() : tile.getPos();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        for (EnumFacing face : EnumFacing.VALUES) {
            if (face.getAxis() != block.getAxis(state)) {
                continue;
            }
            float angle = face == tile.getInputFace() ? RotatingModelRenderer.kineticAngle(tile, partialTicks)
                    : tile.getOutputRenderAngle(partialTicks);
            IBlockState shaft = AllBlocks.CREATIVE_MOTOR_ROTOR.getDefaultState()
                    .withProperty(RenderPartBlock.FACING, face);
            RotatingModelRenderer.renderRotated(world, renderPos, shaft, face.getAxis(), angle);
        }
        GlStateManager.popMatrix();
    }
}
