package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelBlock;
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
public final class TunnelModels {
    private static final String[] SHAPES = {"straight", "window", "cross", "t_left", "t_right"};

    private TunnelModels() { }

    private static IModel model(String type, String shape) {
        ResourceLocation id = new ResourceLocation("create", "block/tunnel/" + type + "_tunnel/" + shape);
        return ModelLoaderRegistry.getModelOrLogError(id, "Could not load tunnel model " + id);
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String type : new String[]{"andesite", "brass"}) {
            for (String shape : SHAPES) {
                for (ResourceLocation texture : model(type, shape).getTextures()) {
                    event.getMap().registerSprite(texture);
                }
            }
            // 1.12 may omit child texture overrides when traversing the upstream parent model.
            for (String texture : new String[]{"block/tunnel/" + type + "_tunnel",
                    "block/tunnel/" + type + "_tunnel_top",
                    "block/tunnel/" + type + "_tunnel_top_window",
                    "block/funnel/" + type + "_funnel_neutral",
                    "block/funnel/" + type + "_funnel_frame"}) {
                event.getMap().registerSprite(new ResourceLocation("create", texture));
            }
        }
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        install(event, AllBlocks.ANDESITE_TUNNEL, "andesite");
        install(event, AllBlocks.BRASS_TUNNEL, "brass");
    }

    private static void install(ModelBakeEvent event, Block block, String type) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing.Axis axis : new EnumFacing.Axis[]{EnumFacing.Axis.X, EnumFacing.Axis.Z}) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "axis=" + axis.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) continue;
            Map<String, IBakedModel> models = new HashMap<>();
            for (String shape : SHAPES) {
                models.put(shape, model(type, shape).bake(axis == EnumFacing.Axis.X
                                ? ModelRotation.X0_Y0 : ModelRotation.X0_Y90,
                        DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString())));
            }
            event.getModelRegistry().putObject(key, new TunnelModel(original, models));
        }
    }

    private static final class TunnelModel extends BakedModelWrapper<IBakedModel> {
        private final Map<String, IBakedModel> models;

        private TunnelModel(IBakedModel original, Map<String, IBakedModel> models) {
            super(original);
            this.models = models;
        }

        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            if (state instanceof IExtendedBlockState extended) {
                IBakedModel selected = models.get(extended.getValue(BeltTunnelBlock.MODEL));
                if (selected != null) return selected.getQuads(state, side, random);
            }
            return originalModel.getQuads(state, side, random);
        }
    }
}
