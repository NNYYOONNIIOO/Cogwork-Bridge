package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerBlock;
import com.cogwork.cogworkbridge.content.contraptions.actors.roller.MechanicalRollerTileEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public final class MechanicalRollerRenderer extends TileEntitySpecialRenderer<MechanicalRollerTileEntity> {
    @Override
    public void render(MechanicalRollerTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        World world = tile.getWorld();
        if (world == null) {
            return;
        }
        IBlockState state = world.getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof MechanicalRollerBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(MechanicalRollerBlock.FACING);
        float speed = 0.0F;
        World timeWorld = world;
        if (world instanceof FakeWorld fakeWorld
                && fakeWorld.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
            speed = tile.movingAnimationSpeed(contraption, facing);
            timeWorld = fakeWorld.getParentWorld();
        }
        float angle = tile.wheelAngle(timeWorld.getTotalWorldTime() + (double) partialTicks, speed);
        IBlockState frame = AllBlocks.MECHANICAL_ROLLER_FRAME.getDefaultState()
                .withProperty(RenderPartBlock.FACING, facing);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        FastStateRendering.getInstance().renderFast(frame);
        GlStateManager.popMatrix();

        IBlockState wheel = AllBlocks.MECHANICAL_ROLLER_WHEEL.getDefaultState()
                .withProperty(RenderPartBlock.FACING, EnumFacing.NORTH);
        EnumFacing axis = facing.rotateY();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5D + facing.getXOffset() * 17.0D / 16.0D,
                y - 0.25D, z + 0.5D + facing.getZOffset() * 17.0D / 16.0D);
        GlStateManager.rotate(angle, axis.getXOffset(), 0.0F, axis.getZOffset());
        if (facing.getAxis() == EnumFacing.Axis.Z) {
            GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
        }
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        FastStateRendering.getInstance().renderFast(wheel);
        GlStateManager.popMatrix();
    }
}
