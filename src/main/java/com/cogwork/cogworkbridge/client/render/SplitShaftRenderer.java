package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.kinetics.transmission.SplitShaftBlock;
import com.cogwork.cogworkbridge.content.kinetics.transmission.SplitShaftTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.RenderPartBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Renders the two shaft halves independently when a clutch is disengaged. */
public final class SplitShaftRenderer extends TileEntitySpecialRenderer<SplitShaftTileEntity> {

    @Override
    public void render(SplitShaftTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        World world = tile.getWorld();
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : world.getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof SplitShaftBlock block)) {
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
            float angle = RotatingModelRenderer.kineticAngle(tile, partialTicks);
            if (block.isClutch() && tile.isPowered()) {
                angle = adjacentAngle(world, tile.getPos(), face, partialTicks);
            } else {
                angle *= (float) block.shaftPortModifier(world, tile.getPos(), state, face);
            }
            IBlockState shaft = AllBlocks.CREATIVE_MOTOR_ROTOR.getDefaultState()
                    .withProperty(RenderPartBlock.FACING, face);
            RotatingModelRenderer.renderRotated(world, renderPos, shaft, face.getAxis(), angle);
        }
        GlStateManager.popMatrix();
    }

    private static float adjacentAngle(World world, BlockPos pos, EnumFacing face, float partialTicks) {
        BlockPos otherPos = pos.offset(face);
        IBlockState otherState = world.getBlockState(otherPos);
        TileEntity otherTile = world.getTileEntity(otherPos);
        if (!(otherState.getBlock() instanceof KineticBlock other)
                || !(otherTile instanceof KineticTileEntity kinetic)
                || !other.canConnectShaftTowards(world, otherPos, otherState, face.getOpposite())) {
            return 0.0F;
        }
        return RotatingModelRenderer.kineticAngle(kinetic, partialTicks)
                * (float) other.shaftPortModifier(world, otherPos, otherState, face.getOpposite());
    }
}
