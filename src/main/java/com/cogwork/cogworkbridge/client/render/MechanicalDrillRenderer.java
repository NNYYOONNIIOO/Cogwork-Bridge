package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillBlock;
import com.cogwork.cogworkbridge.content.kinetics.drill.MechanicalDrillTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MechanicalDrillRenderer extends TileEntitySpecialRenderer<MechanicalDrillTileEntity> {
    @Override
    public void render(MechanicalDrillTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MechanicalDrillBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(MechanicalDrillBlock.FACING);
        World renderWorld = tile.getWorld();
        float angle = RotatingModelRenderer.kineticAngle(tile, partialTicks);
        if (renderWorld instanceof FakeWorld fakeWorld
                && fakeWorld.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
            float animationSpeed = contraption.isStalled() && contraption.getSpeed() != 0.0F
                    ? 700.0F : tile.movingAnimationSpeed(contraption, facing,
                    contraption.getAngle(), contraption.getSpeed());
            angle = (fakeWorld.getParentWorld().getTotalWorldTime() + partialTicks) * animationSpeed / 20.0F;
        }
        IBlockState head = AllBlocks.MECHANICAL_DRILL_HEAD.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing);
        BlockPos renderPos = tile.getContraptionRenderPos() != null
                ? tile.getContraptionRenderPos() : tile.getPos();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(renderWorld, renderPos, head, facing.getAxis(), angle);
        GlStateManager.popMatrix();
    }
}
