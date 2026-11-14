package com.cogwork.cogworkbridge.client.particle;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class FluidParticle extends Particle {
    public FluidParticle(World world, FluidStack fluid, double x, double y, double z, double vx, double vy, double vz) {
        super(world, x, y, z, vx, vy, vz);
        motionX = vx; motionY = vy; motionZ = vz;
        particleGravity = .35F; particleScale = .5F; particleMaxAge = 20 + rand.nextInt(10);
        setParticleTexture(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(fluid.getFluid().getStill(fluid).toString()));
        int color = fluid.getFluid().getColor(fluid);
        setRBGColorF((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
        int alpha = color >>> 24;
        if (alpha != 0) setAlphaF(alpha / 255F);
    }
    @Override public int getFXLayer() { return 1; }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            ResourceLocation still = fluid.getStill();
            if (still != null) event.getMap().registerSprite(still);
        }
    }
}
