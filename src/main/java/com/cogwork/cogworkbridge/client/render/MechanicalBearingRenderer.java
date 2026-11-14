package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingBlock;
import com.cogwork.cogworkbridge.content.contraptions.bearing.MechanicalBearingTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;

/**
 * Renders the moving shaft and top cap of the mechanical bearing.
 *
 * <p>The housing is part of the normal block model. Only the independently
 * rotating parts are submitted here, so static geometry stays on the chunk
 * model path and the TESR remains cheap.</p>
 */
public class MechanicalBearingRenderer extends TileEntitySpecialRenderer<MechanicalBearingTileEntity> {

    @Override
    public void render(MechanicalBearingTileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getContraptionRenderState() != null
                ? te.getContraptionRenderState() : te.getWorld().getBlockState(te.getPos());
        if (!(state.getBlock() instanceof MechanicalBearingBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(MechanicalBearingBlock.FACING);
        IBlockState assembly = AllBlocks.MECHANICAL_BEARING_ASSEMBLY.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing);
        IBlockState top = (te instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity
                ? AllBlocks.WINDMILL_BEARING_TOP : AllBlocks.MECHANICAL_BEARING_TOP).getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing);
        IBlockState shaft = AllBlocks.MECHANICAL_BEARING_SHAFT.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing);
        Float contraptionPhase = te.getContraptionRenderPhase();
        float angle = 0.0F;
        if (contraptionPhase != null) {
            angle = contraptionPhase;
        } else if (te.isAssembled()) {
            net.minecraft.world.World entityWorld = te.getWorld();
            if (entityWorld instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fakeWorld) {
                entityWorld = fakeWorld.getParentWorld();
            }
            Entity entity = entityWorld.getEntityByID(te.getContraptionEntityId());
            angle = entity instanceof BearingContraptionEntity contraption
                    && contraption.hasContraptionData()
                    ? contraption.getRenderAngleFromBearing(partialTicks)
                    : te.getRenderAngle(partialTicks);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        EnumFacing.Axis axis = facing.getAxis();
        net.minecraft.util.math.BlockPos renderPos = te.getContraptionRenderPos() != null
                ? te.getContraptionRenderPos() : te.getPos();
        if (!(te instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.WindmillBearingTileEntity))
            RotatingModelRenderer.renderRotated(te.getWorld(), renderPos, assembly, axis, angle);
        RotatingModelRenderer.renderRotated(te.getWorld(), renderPos, shaft, axis,
                RotatingModelRenderer.kineticAngle(te, partialTicks));
        RotatingModelRenderer.renderRotated(te.getWorld(), renderPos, top, axis, angle);
        GlStateManager.popMatrix();
    }
}
