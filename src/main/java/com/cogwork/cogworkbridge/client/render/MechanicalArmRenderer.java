package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmBlock;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = com.cogwork.cogworkbridge.Reference.MOD_ID, value = Side.CLIENT)
public final class MechanicalArmRenderer extends TileEntitySpecialRenderer<MechanicalArmTileEntity> {
    private static IBakedModel gogglesClaw;
    private static IModel gogglesModel() {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/mechanical_arm/claw_base_goggles"),
                "Could not load mechanical arm goggles model");
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : gogglesModel().getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        gogglesClaw = gogglesModel().bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                texture -> atlas.getAtlasSprite(texture.toString()));
    }
    @Override public void render(MechanicalArmTileEntity arm, double x, double y, double z, float partial, int stage, float alpha) {
        if (arm.getWorld() == null) return;
        IBlockState state = arm.getContraptionRenderState() != null ? arm.getContraptionRenderState() : arm.getWorld().getBlockState(arm.getPos());
        if (!(state.getBlock() instanceof MechanicalArmBlock)) return;
        boolean ceiling = state.getValue(MechanicalArmBlock.CEILING);
        float[] from = angles(arm, arm.previousTargetOffset(), arm.previousTargetSide(), ceiling);
        float[] to = angles(arm, arm.targetOffset(), arm.targetSide(), ceiling);
        if (arm.hasTarget() && arm.startsAtRest()) {
            from[1] = 135F;
            from[2] = 45F;
            from[3] = 0F;
        }
        float progress = arm.animationProgress(partial);
        float base = arm.hasTarget() ? lerpAngle(from[0], to[0], progress) : from[0];
        if (arm.getTileData().getBoolean("TutorialArm")) {
            float[] rest = {0, 135, 45, 0};
            if (progress < .5F) to = rest;
            else from = rest;
            progress = Math.min(1, progress < .5F ? progress * 2 : (progress - .5F) * 2);
        }
        float lower = from[1] + (to[1] - from[1]) * progress;
        float upper = from[2] + (to[2] - from[2]) * progress;
        float head = lerpAngle(from[3], to[3], progress);
        boolean dancing = arm.isDancing() && arm.getSpeed() != 0;
        if (dancing) {
            float time = arm.getWorld().getTotalWorldTime() + partial + Math.floorMod(arm.getPos().hashCode(), 64);
            base = time * 10 % 360;
            lower = 135 - 45 + ((float) Math.sin(time / 4) + 1) * .5F * 60;
            upper = 90 - 45 + ((float) Math.sin(time / 8) + 1) * .25F * 140;
            head = -(lower - 135);
        }
        ItemStack held = arm.getHeldItem();
        boolean blockItem = !held.isEmpty() && held.getItem() instanceof net.minecraft.item.ItemBlock
                && Minecraft.getMinecraft().getRenderItem().getItemModelWithOverrides(held, arm.getWorld(), null).isGui3d();
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        RotatingModelRenderer.renderRotated(arm.getWorld(), arm.getPos(), AllBlocks.ARM_COG.getDefaultState(),
                EnumFacing.Axis.Y, RotatingModelRenderer.kineticAngle(arm, partial));
        GlStateManager.translate(0.5, 0.5, 0.5);
        int color = dancing ? java.awt.Color.HSBtoRGB((arm.getWorld().getTotalWorldTime() + partial) / 100F % 1, 1, 1) : 0xFFFFFF;
        if (ceiling) GlStateManager.rotate(180, 1, 0, 0);
        GlStateManager.translate(0, 4 / 16D, 0); GlStateManager.rotate(base, 0, 1, 0);
        FastStateRendering.getInstance().renderFast(AllBlocks.ARM_BASE.getDefaultState());
        GlStateManager.translate(0, 2 / 16D, 0); GlStateManager.rotate(lower, 1, 0, 0);
        FastStateRendering.getInstance().renderTinted(AllBlocks.ARM_LOWER.getDefaultState(), color);
        GlStateManager.translate(0, 0, -14 / 16D); GlStateManager.rotate(upper - 180, 1, 0, 0);
        FastStateRendering.getInstance().renderTinted(AllBlocks.ARM_UPPER.getDefaultState(), color);
        GlStateManager.translate(0, 0, -15 / 16D); GlStateManager.rotate(head - 45, 1, 0, 0);
        if (arm.hasGoggles() && gogglesClaw != null) {
            GlStateManager.pushMatrix();
            if (ceiling) GlStateManager.rotate(180, 0, 0, 1);
            FastStateRendering.getInstance().renderFast(gogglesClaw, state);
            GlStateManager.popMatrix();
        } else FastStateRendering.getInstance().renderFast(AllBlocks.ARM_CLAW.getDefaultState());
        for (int sign : new int[]{-1, 1}) {
            double gap = held.isEmpty() ? 1 / 16D : blockItem ? 3 / 16D : 5 / 64D;
            GlStateManager.pushMatrix(); GlStateManager.translate(0, -sign * gap, -6 / 16D);
            FastStateRendering.getInstance().renderFast((sign > 0 ? AllBlocks.ARM_GRIP_LOWER : AllBlocks.ARM_GRIP_UPPER).getDefaultState());
            GlStateManager.popMatrix();
        }
        if (!held.isEmpty()) {
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.rotate(90, 1, 0, 0);
            GlStateManager.translate(0, blockItem ? -9 / 16D : -10 / 16D, 0);
            float scale = blockItem ? .5F : .625F;
            GlStateManager.scale(scale, scale, scale);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(held, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
    }
    private static float[] angles(MechanicalArmTileEntity arm, BlockPos offset, EnumFacing side, boolean ceiling) {
        if (offset.equals(BlockPos.ORIGIN)) return new float[]{0, 135, 45, 0};
        Vec3d clawTarget = new Vec3d(offset.getX() + 0.5D, offset.getY() + 0.5D, offset.getZ() + 0.5D);
        if (arm.getTileData().getBoolean("TutorialArm")) {
            String path = arm.getWorld().getBlockState(arm.getPos().add(offset)).getBlock().getRegistryName().getPath();
            if (path.equals("depot")) clawTarget = clawTarget.add(0, 6 / 16D, 0);
            else if (path.contains("funnel")) clawTarget = clawTarget.add(side.getXOffset() * .15,
                    side.getYOffset() * .15, side.getZOffset() * .15);
        }
        Vec3d target = clawTarget.add(-side.getXOffset() * 0.5D, -side.getYOffset() * 0.5D,
                -side.getZOffset() * 0.5D);
        Vec3d origin = new Vec3d(0.5D, 0.5D + (ceiling ? -6 / 16D : 6 / 16D), 0.5D);
        Vec3d diff = target.subtract(origin);
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float base = (float) Math.toDegrees(Math.atan2(diff.x, diff.z)) + 180F;
        if (ceiling) {
            diff = new Vec3d(diff.x, -diff.y, diff.z);
            base = 180F - base;
        }
        double alphaOffset = Math.toDegrees(Math.atan2(diff.y, horizontal));
        double lowerLength = 14 / 16D, upperLength = 15 / 16D;
        double distance = Math.max(1 / 8D, Math.min(lowerLength + upperLength,
                Math.sqrt(diff.y * diff.y + horizontal * horizontal)));
        double alphaRatio = (-upperLength * upperLength + lowerLength * lowerLength + distance * distance)
                / (2 * lowerLength * distance);
        double betaRatio = (-distance * distance + lowerLength * lowerLength + upperLength * upperLength)
                / (2 * upperLength * lowerLength);
        double alpha = Math.toDegrees(Math.acos(clamp(alphaRatio))) + alphaOffset;
        double beta = Math.toDegrees(Math.acos(clamp(betaRatio)));

        Vec3d headPosition = rotateX(new Vec3d(0, upperLength, 0), beta + 180D);
        headPosition = rotateX(headPosition.add(0, lowerLength, 0), alpha - 90D);
        headPosition = rotateY(headPosition, base);
        if (ceiling) headPosition = rotateX(headPosition, 180D);
        headPosition = headPosition.add(origin);
        Vec3d headDiff = clawTarget.subtract(headPosition);
        if (ceiling) headDiff = new Vec3d(headDiff.x, -headDiff.y, headDiff.z);
        double headHorizontal = Math.sqrt(headDiff.x * headDiff.x + headDiff.z * headDiff.z);
        float head = (float) -(alpha + beta + 135D - Math.toDegrees(Math.atan2(headDiff.y, headHorizontal)));
        return new float[]{base, (float) alpha, (float) beta, head};
    }

    private static float lerpAngle(float from, float to, float progress) {
        return from + net.minecraft.util.math.MathHelper.wrapDegrees(to - from) * progress;
    }

    private static double clamp(double value) {
        return Math.max(-1D, Math.min(1D, value));
    }

    private static Vec3d rotateX(Vec3d vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians), cos = Math.cos(radians);
        return new Vec3d(vector.x, vector.y * cos - vector.z * sin, vector.y * sin + vector.z * cos);
    }

    private static Vec3d rotateY(Vec3d vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians), cos = Math.cos(radians);
        return new Vec3d(vector.x * cos + vector.z * sin, vector.y, -vector.x * sin + vector.z * cos);
    }

}
