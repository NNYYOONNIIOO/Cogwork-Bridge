package com.cogwork.cogworkbridge.client.particle;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Uses the same sixteen animated sprites and upright yaw-facing plane as upstream. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class WifiParticle extends Particle {
    public WifiParticle(World world, double x, double y, double z) {
        super(world, x, y + 1, z);
        motionX = motionY = motionZ = 0; canCollide = false; particleMaxAge = 16;
        frame(0);
    }
    private void frame(int age) {
        setParticleTexture(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("create:particle/wifi_" + Math.min(15, age)));
    }
    @Override public int getFXLayer() { return 1; }
    @Override public void onUpdate() { if (++particleAge >= particleMaxAge) setExpired(); else frame(particleAge); }
    @Override public void renderParticle(BufferBuilder buffer, Entity viewer, float partial, float rx, float rz, float ryz, float rxy, float rxz) {
        double x = posX - interpPosX, y = posY - interpPosY, z = posZ - interpPosZ;
        double yaw = Math.toRadians(viewer.prevRotationYaw + (viewer.rotationYaw - viewer.prevRotationYaw) * partial);
        double dx = Math.cos(yaw) * .5, dz = Math.sin(yaw) * .5;
        float u0 = particleTexture.getMinU(), u1 = particleTexture.getMaxU(), v0 = particleTexture.getMinV(), v1 = particleTexture.getMaxV();
        buffer.pos(x-dx,y-.5,z-dz).tex(u1,v1).color(1F,1F,1F,1F).lightmap(240,240).endVertex();
        buffer.pos(x-dx,y+.5,z-dz).tex(u1,v0).color(1F,1F,1F,1F).lightmap(240,240).endVertex();
        buffer.pos(x+dx,y+.5,z+dz).tex(u0,v0).color(1F,1F,1F,1F).lightmap(240,240).endVertex();
        buffer.pos(x+dx,y-.5,z+dz).tex(u0,v1).color(1F,1F,1F,1F).lightmap(240,240).endVertex();
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (int i = 0; i < 16; i++) event.getMap().registerSprite(new ResourceLocation("create", "particle/wifi_" + i));
    }
}
