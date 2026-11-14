package com.cogwork.cogworkbridge.compat.ponder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.model.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Registered only when Ponder is installed, before texture stitching. */
final class PonderModels {
    private static IBakedModel hat;
    private static IModel source() { return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "entity/train_hat"), "Create conductor hat"); }
    @SubscribeEvent public void stitch(TextureStitchEvent.Pre event) { for (ResourceLocation texture : source().getTextures()) event.getMap().registerSprite(texture); }
    @SubscribeEvent public void bake(ModelBakeEvent event) {
        hat = source().bake(ModelRotation.X0_Y0, DefaultVertexFormats.ITEM, texture -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));
    }
    static void hat() {
        if (hat == null) return;
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix(); GlStateManager.translate(-.5, 0, -.5);
        Minecraft.getMinecraft().getRenderItem().renderItem(new ItemStack(Items.STICK), hat);
        GlStateManager.popMatrix();
    }
}
