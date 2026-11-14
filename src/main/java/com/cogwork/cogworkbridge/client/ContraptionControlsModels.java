package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ContraptionControlsModels {
    private static IBakedModel button, enabled, disabled;
    private ContraptionControlsModels() { }
    private static IModel load(String part) {
        ResourceLocation path = new ResourceLocation("create", "block/contraption_controls/" + part);
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load controls part " + path);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String part : new String[]{"button", "indicator_0", "indicator_4"})
            for (ResourceLocation texture : load(part).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        button = load("button").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, r -> atlas.getAtlasSprite(r.toString()));
        enabled = load("indicator_0").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, r -> atlas.getAtlasSprite(r.toString()));
        disabled = load("indicator_4").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, r -> atlas.getAtlasSprite(r.toString()));
    }
    public static IBakedModel button() { return button; }
    public static IBakedModel indicator(boolean off) { return off ? disabled : enabled; }
}
