package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.chute.ChuteBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ChuteModels {
    private static final String[][] PARTS = {
            {"normal", "chute/block"}, {"window", "chute/block_windowed"},
            {"intersection", "chute/block_intersection"}, {"diagonal", "chute/block_diagonal"},
            {"diagonal_intersection", "chute/block_diagonal_intersection"},
            {"smart", "smart_chute/block"}, {"smart_powered", "smart_chute/block_powered"}
    };
    private ChuteModels() { }
    private static IModel model(String name) {
        ResourceLocation location = new ResourceLocation("create", "block/" + name);
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load chute model " + location);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String[] part : PARTS)
            for (ResourceLocation texture : model(part[1]).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        install(event, AllBlocks.CHUTE);
        install(event, AllBlocks.SMART_CHUTE);
    }
    private static void install(ModelBakeEvent event, Block block) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : new EnumFacing[]{EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST}) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) continue;
            Map<String, IBakedModel> models = new HashMap<>();
            for (String[] part : PARTS) {
                ModelRotation rotation = part[0].startsWith("diagonal") ? switch (facing) {
                    case NORTH -> ModelRotation.X0_Y180;
                    case WEST -> ModelRotation.X0_Y90;
                    case EAST -> ModelRotation.X0_Y270;
                    default -> ModelRotation.X0_Y0;
                } : ModelRotation.X0_Y0;
                models.put(part[0], model(part[1]).bake(rotation, DefaultVertexFormats.BLOCK,
                        sprite -> atlas.getAtlasSprite(sprite.toString())));
            }
            event.getModelRegistry().putObject(key, new ChuteModel(original, models));
        }
    }
    private static final class ChuteModel extends BakedModelWrapper<IBakedModel> {
        private final Map<String, IBakedModel> models;
        private ChuteModel(IBakedModel original, Map<String, IBakedModel> models) {
            super(original);
            this.models = models;
        }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            if (state instanceof IExtendedBlockState extended) {
                IBakedModel model = models.get(extended.getValue(ChuteBlock.MODEL));
                if (model != null) return model.getQuads(state, side, random);
            }
            return originalModel.getQuads(state, side, random);
        }
    }
}
