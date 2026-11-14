package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.processing.basin.BasinBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class BasinModels {
    private static final ResourceLocation DIRECTIONAL = new ResourceLocation("create", "block/basin/block_directional");

    private BasinModels() { }

    private static IModel directional() {
        return ModelLoaderRegistry.getModelOrLogError(DIRECTIONAL, "Could not load basin outlet model " + DIRECTIONAL);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : directional().getTextures()) event.getMap().registerSprite(texture);
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        ModelResourceLocation key = new ModelResourceLocation(AllBlocks.BASIN.getRegistryName(), "normal");
        IBakedModel original = event.getModelRegistry().getObject(key);
        if (original == null) return;
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        IModel source = directional();
        Map<EnumFacing, IBakedModel> models = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            int rotation = switch (side) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> throw new IllegalStateException("Invalid basin outlet " + side);
            };
            models.put(side, source.bake(ModelRotation.getModelRotation(0, rotation), DefaultVertexFormats.BLOCK,
                    sprite -> atlas.getAtlasSprite(sprite.toString())));
        }
        event.getModelRegistry().putObject(key, new BasinModel(original, models));
    }

    private static final class BasinModel extends BakedModelWrapper<IBakedModel> {
        private final Map<EnumFacing, IBakedModel> models;

        private BasinModel(IBakedModel original, Map<EnumFacing, IBakedModel> models) {
            super(original);
            this.models = models;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            EnumFacing output = state instanceof IExtendedBlockState extended
                    ? extended.getValue(BasinBlock.OUTPUT) : null;
            return models.getOrDefault(output, originalModel).getQuads(state, side, random);
        }
    }
}
