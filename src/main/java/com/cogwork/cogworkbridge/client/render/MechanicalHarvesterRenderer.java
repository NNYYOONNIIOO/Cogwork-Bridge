package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterBlock;
import com.cogwork.cogworkbridge.content.contraptions.actors.harvester.MechanicalHarvesterTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/** Draws the baked blade; the attached frame remains a normal block model. */
public final class MechanicalHarvesterRenderer extends TileEntitySpecialRenderer<MechanicalHarvesterTileEntity> {
    @Override
    public void render(MechanicalHarvesterTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        World world = tile.getWorld();
        if (world == null) {
            return;
        }
        IBlockState state = world.getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MechanicalHarvesterBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(MechanicalHarvesterBlock.FACING);
        float speed = 0.0F;
        World timeWorld = world;
        if (world instanceof FakeWorld fakeWorld
                && fakeWorld.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
            speed = tile.movingAnimationSpeed(contraption, facing);
            timeWorld = fakeWorld.getParentWorld();
        }
        float angle = tile.bladeAngle(timeWorld.getTotalWorldTime() + (double) partialTicks, speed);
        IBlockState blade = AllBlocks.MECHANICAL_HARVESTER_BLADE.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing);
        EnumFacing axis = facing.rotateY();
        double pivotX = 0.5D + facing.getXOffset() / 16.0D;
        double pivotZ = 0.5D + facing.getZOffset() / 16.0D;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + pivotX, y + 6.0D / 16.0D, z + pivotZ);
        GlStateManager.rotate(angle, axis.getXOffset(), 0.0F, axis.getZOffset());
        GlStateManager.translate(-pivotX, -6.0D / 16.0D, -pivotZ);
        FastStateRendering.getInstance().renderFast(blade);
        GlStateManager.popMatrix();
    }
}
