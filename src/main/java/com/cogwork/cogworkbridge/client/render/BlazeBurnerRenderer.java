package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.client.BlazeBurnerModels;
import com.cogwork.cogworkbridge.client.BlazeBurnerModels.Part;
import com.cogwork.cogworkbridge.content.processing.basin.BasinBlock;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerBlock;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity.HeatLevel;
import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.Map;
import java.util.WeakHashMap;

/** Renders the animated parts independently of the stationary burner cage. */
public final class BlazeBurnerRenderer extends TileEntitySpecialRenderer<BlazeBurnerTileEntity> {
    private final Map<BlazeBurnerTileEntity, Animation> animations = new WeakHashMap<>();

    @Override
    public void render(BlazeBurnerTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        World world = tile.getWorld();
        if (world == null || !tile.isOccupied()) return;
        IBlockState state = world.getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof BlazeBurnerBlock)) return;

        HeatLevel heat = tile.getHeatLevel();
        boolean heated = heat.ordinal() >= HeatLevel.FADING.ordinal();
        Block above = world.getBlockState(tile.getPos().up()).getBlock();
        boolean boilerAbove = world.getTileEntity(tile.getPos().up()) instanceof FluidTankTileEntity tank
                && tank.isBoiler();
        boolean working = heated && (above instanceof BasinBlock || boilerAbove);
        Animation animation = animations.computeIfAbsent(tile, ignored -> new Animation());
        animation.advance(world, tile, state, working);
        float work = animation.previousWork + (animation.work - animation.previousWork) * partialTicks;
        float yaw = animation.previousYaw + MathHelper.wrapDegrees(animation.yaw - animation.previousYaw) * partialTicks;
        boolean blockAbove = work * 0.175F > 0.125F;

        double time = world.getTotalWorldTime() + partialTicks
                + Math.floorMod(tile.getPos().hashCode(), 13) * 16.0D;
        double divisor = heated ? 64.0D : 16.0D;
        double wave = time / 16.0D;
        double headOffset = Math.sin(wave) / divisor - work * 0.175D * 0.75D;
        Part head = heat == HeatLevel.SEETHING ? (blockAbove ? Part.SUPER_ACTIVE : Part.SUPER)
                : heat == HeatLevel.KINDLED && blockAbove ? Part.ACTIVE
                : heated ? Part.IDLE : Part.INERT;

        float oldLightX = OpenGlHelper.lastBrightnessX;
        float oldLightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.0D, headOffset, 0.0D);
            GlStateManager.translate(0.5D, 0.0D, 0.5D);
            GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5D, 0.0D, -0.5D);
            draw(head, state);
            if (tile.isTutorialConductor()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(.5D, .75D, .5D);
                GlStateManager.scale(1.5D, 1.5D, 1.5D);
                GlStateManager.translate(-.5D, 0, -.5D);
                draw(Part.CONDUCTOR_HAT, state);
                GlStateManager.popMatrix();
            }
            if (tile.hasGoggles()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(0.0D, 0.5D, 0.0D);
                draw(head == Part.INERT ? Part.GOGGLES_SMALL : Part.GOGGLES, state);
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();

            if (heated) {
                boolean superheated = heat == HeatLevel.SEETHING;
                drawOffset(superheated ? Part.SUPER_RODS_SMALL : Part.RODS_SMALL, state,
                        Math.sin(wave + Math.PI) / divisor + work * 0.175D + 0.125D);
                drawOffset(superheated ? Part.SUPER_RODS_LARGE : Part.RODS_LARGE, state,
                        Math.sin(wave + Math.PI / 2.0D) / divisor + work * 0.175D - 3.0D / 16.0D);
                if (blockAbove) drawFlame(state, heat, world.getTotalWorldTime() + partialTicks, yaw);
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldLightX, oldLightY);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private static void drawOffset(Part part, IBlockState state, double offset) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, offset, 0.0D);
        draw(part, state);
        GlStateManager.popMatrix();
    }

    private static void draw(Part part, IBlockState state) {
        FastStateRendering.getInstance().renderFast(BlazeBurnerModels.part(part), state);
    }

    private static void drawFlame(IBlockState state, HeatLevel heat, double time, float yaw) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite original = atlas.getAtlasSprite(BlazeBurnerModels.FLAME_TEXTURE.toString());
        TextureAtlasSprite target = atlas.getAtlasSprite((heat == HeatLevel.SEETHING
                ? BlazeBurnerModels.SUPER_FLAME_SCROLL : BlazeBurnerModels.FLAME_SCROLL).toString());
        boolean scrolling = original != atlas.getMissingSprite() && target != atlas.getMissingSprite();
        int previousTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        if (scrolling) {
            double speed = 1.0D / 32.0D + heat.ordinal() / 64.0D;
            double u = speed * time / 2.0D;
            double v = speed * time;
            double uScroll = (u - Math.floor(u)) * (target.getMaxU() - target.getMinU()) / 2.0D;
            double vScroll = (v - Math.floor(v)) * (target.getMaxV() - target.getMinV()) / 2.0D;
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.translate(target.getMinU() - original.getMinU() + uScroll,
                    target.getMinV() - original.getMinV() + vScroll, 0.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.5D, 0.0D, 0.5D);
            GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5D, 0.0D, -0.5D);
            draw(Part.FLAME, state);
        } finally {
            GlStateManager.popMatrix();
            if (scrolling) {
                GlStateManager.matrixMode(GL11.GL_TEXTURE);
                GlStateManager.popMatrix();
                GlStateManager.matrixMode(previousMode);
            }
            GlStateManager.setActiveTexture(previousTexture);
        }
    }

    private static final class Animation {
        private long lastTick = Long.MIN_VALUE;
        private float previousYaw;
        private float yaw;
        private float previousWork;
        private float work;

        private void advance(World world, BlazeBurnerTileEntity tile, IBlockState state, boolean working) {
            long tick = world.getTotalWorldTime();
            if (tick == lastTick) return;
            float facing = (state.getValue(BlazeBurnerBlock.FACING).getHorizontalAngle() + 180.0F) % 360.0F;
            if (lastTick == Long.MIN_VALUE) yaw = facing;
            lastTick = tick;
            previousYaw = yaw;
            previousWork = work;
            work += ((working ? 1.0F : 0.0F) - work) * 0.25F;
            EntityPlayer player = world == Minecraft.getMinecraft().world ? Minecraft.getMinecraft().player : null;
            float target = working ? facing : 0.0F;
            if (!working && player != null && !player.isInvisible()) {
                double dx = player.posX - tile.getPos().getX() - 0.5D;
                double dz = player.posZ - tile.getPos().getZ() - 0.5D;
                target = (float) Math.toDegrees(-Math.atan2(dz, dx)) - 90.0F;
            }
            yaw += MathHelper.wrapDegrees(target - yaw) * (working ? 0.125F : 0.25F);
        }
    }
}
