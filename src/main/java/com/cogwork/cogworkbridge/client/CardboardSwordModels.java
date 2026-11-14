package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.lang3.tuple.Pair;
import javax.vecmath.Matrix4f;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class CardboardSwordModels {
    private static IModel heldModel() {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "item/cardboard_sword/item_in_hand"), "Could not load held cardboard sword");
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : heldModel().getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        ModelResourceLocation key = new ModelResourceLocation(new ResourceLocation("create", "cardboard_sword"), "inventory");
        IBakedModel base = event.getModelRegistry().getObject(key);
        if (base == null) return;
        IBakedModel held = heldModel().bake(TRSRTransformation.identity(), DefaultVertexFormats.ITEM,
                texture -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(texture.toString()));
        event.getModelRegistry().putObject(key, new BakedModelWrapper<IBakedModel>(base) {
            @Override public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
                return type == ItemCameraTransforms.TransformType.GUI ? base.handlePerspective(type) : held.handlePerspective(type);
            }
        });
    }
}
