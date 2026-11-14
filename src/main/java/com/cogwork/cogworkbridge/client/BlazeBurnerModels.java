package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class BlazeBurnerModels {
    private static final ResourceLocation FIRE = new ResourceLocation("create", "block/blaze_burner/block_with_fire");
    public static final ResourceLocation FLAME_TEXTURE = new ResourceLocation("create", "block/blaze_burner_flame");
    public static final ResourceLocation FLAME_SCROLL = new ResourceLocation("create", "block/blaze_burner_flame_scroll");
    public static final ResourceLocation SUPER_FLAME_SCROLL =
            new ResourceLocation("create", "block/blaze_burner_flame_superheated_scroll");
    private static final Map<Part, IBakedModel> PARTS = new EnumMap<>(Part.class);

    public enum Part {
        INERT("blaze/inert"), IDLE("blaze/idle"), ACTIVE("blaze/active"),
        SUPER("blaze/super"), SUPER_ACTIVE("blaze/super_active"),
        RODS_SMALL("rods_small"), RODS_LARGE("rods_large"),
        SUPER_RODS_SMALL("superheated_rods_small"), SUPER_RODS_LARGE("superheated_rods_large"),
        FLAME("flame"), GOGGLES("goggles"), GOGGLES_SMALL("goggles_small"),
        CONDUCTOR_HAT("create", "entity/train_hat");

        private final ResourceLocation model;

        Part(String path) {
            model = new ResourceLocation("create", "block/blaze_burner/" + path);
        }
        Part(String namespace, String path) { model = new ResourceLocation(namespace, path); }
    }

    private BlazeBurnerModels() { }

    private static IModel fireModel() {
        return ModelLoaderRegistry.getModelOrLogError(FIRE, "Could not load empty burner fire model " + FIRE);
    }

    private static IModel partModel(Part part) {
        return ModelLoaderRegistry.getModelOrLogError(part.model,
                "Could not load burner animation model " + part.model);
    }

    @Nullable
    public static IBakedModel part(Part part) {
        return PARTS.get(part);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        event.getMap().registerSprite(FLAME_TEXTURE);
        event.getMap().registerSprite(FLAME_SCROLL);
        event.getMap().registerSprite(SUPER_FLAME_SCROLL);
        for (ResourceLocation texture : fireModel().getTextures()) event.getMap().registerSprite(texture);
        for (Part part : Part.values()) {
            for (ResourceLocation texture : partModel(part).getTextures()) {
                event.getMap().registerSprite(texture);
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        PARTS.clear();
        for (Part part : Part.values()) {
            PARTS.put(part, partModel(part).bake(TRSRTransformation.identity(), DefaultVertexFormats.ITEM,
                    texture -> atlas.getAtlasSprite(texture.toString())));
        }
        IBakedModel lit = fireModel().bake(TRSRTransformation.identity(), DefaultVertexFormats.BLOCK,
                texture -> atlas.getAtlasSprite(texture.toString()));
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.EMPTY_BLAZE_BURNER.getRegistryName(),
                    "facing=" + facing.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original != null) event.getModelRegistry().putObject(key, new EmptyBurnerModel(original, lit));
        }
    }

    private static final class EmptyBurnerModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel lit;

        private EmptyBurnerModel(IBakedModel original, IBakedModel lit) {
            super(original);
            this.lit = lit;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            boolean burning = state instanceof IExtendedBlockState extended
                    && Boolean.TRUE.equals(extended.getValue(BlazeBurnerBlock.LIT));
            return (burning ? lit : originalModel).getQuads(state, side, random);
        }
    }
}
