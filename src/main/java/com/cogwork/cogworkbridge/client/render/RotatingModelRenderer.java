package com.cogwork.cogworkbridge.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Draws a block model at the current matrix, optionally rotated around its centre.
 *
 * <p>This mirrors what Create does on modern versions: instead of hand-building
 * quads, it asks the block model system to render a {@code BlockState}. That gives
 * correct UVs, lighting, culling and item models for free.
 */
public final class RotatingModelRenderer {

    private RotatingModelRenderer() {
    }

    /**
     * Renders {@code state} at {@code pos} in {@code world}, rotated by
     * {@code angleDegrees} around {@code axis} about the block centre.
     */
    public static void renderRotated(World world, BlockPos pos, IBlockState state,
                                     EnumFacing.Axis axis, float angleDegrees) {
        renderRotated(world, pos, null, state, axis, angleDegrees);
    }

    /** A dedicated submodel can share the same transform without changing its other users. */
    public static void renderRotated(World world, BlockPos pos, IBakedModel model, IBlockState state,
                                     EnumFacing.Axis axis, float angleDegrees) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5D, 0.5D, 0.5D);
        switch (axis) {
            case X:
                GlStateManager.rotate(angleDegrees, 1.0F, 0.0F, 0.0F);
                break;
            case Z:
                GlStateManager.rotate(angleDegrees, 0.0F, 0.0F, 1.0F);
                break;
            case Y:
            default:
                GlStateManager.rotate(angleDegrees, 0.0F, 1.0F, 0.0F);
                break;
        }
        GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        FastStateRendering.getInstance().renderRotating(model, state, world, pos, axis);
        GlStateManager.popMatrix();
    }

    /** Computes the standard Create kinetic angle at this frame. */
    public static float kineticAngle(World world, float partialTicks, float speed) {
        double time = world == null ? 0.0D : world.getTotalWorldTime() + (double) partialTicks;
        return (float) ((time * speed * 0.3D) % 360.0D);
    }

    /** Uses the stored phase in a structure, or the world animation clock. */
    public static float kineticAngle(com.cogwork.cogworkbridge.kinetic.KineticTileEntity te,
                                     float partialTicks) {
        Float phase = te.getContraptionRenderPhase();
        if (phase != null) {
            return phase;
        }
        return te.getContraptionRenderPos() == null
                ? kineticAngle(te.getWorld(), partialTicks, te.getSpeed())
                : te.getLocalRenderPhase(partialTicks);
    }
}
