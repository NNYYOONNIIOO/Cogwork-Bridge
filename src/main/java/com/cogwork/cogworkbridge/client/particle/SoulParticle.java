package com.cogwork.cogworkbridge.client.particle;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SoulParticle extends Particle {
    private final int loops;
    public SoulParticle(World world, double x, double y, double z) {
        super(world, x, y, z); motionX = motionY = motionZ = 0;
        canCollide = false; particleScale = 1F; loops = 1 + rand.nextInt(2);
        particleMaxAge = (17 + 16 * loops + 20) * 2;
        setFrame("start", 1);
    }
    @Override public int getFXLayer() { return 1; }
    @Override public void onUpdate() {
        prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
        if (++particleAge >= particleMaxAge) { setExpired(); return; }
        int frame = particleAge / 2;
        if (frame < 17) setFrame("start", frame + 1);
        else if (frame < 17 + loops * 16) setFrame("anim", (frame - 17) % 16 + 1);
        else setFrame("end", Math.min(20, frame - 17 - loops * 16 + 1));
    }
    private void setFrame(String phase, int frame) {
        String path = "create:particle/soul_sprite_" + phase + "_" + frame;
        setParticleTexture(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(path));
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String phase : new String[]{"start", "anim", "end"}) {
            int max = phase.equals("start") ? 17 : phase.equals("anim") ? 16 : 20;
            for (int frame = 1; frame <= max; frame++) event.getMap().registerSprite(new ResourceLocation("create", "particle/soul_sprite_" + phase + "_" + frame));
        }
    }
}
