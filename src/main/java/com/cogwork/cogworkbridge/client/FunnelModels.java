package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.funnel.FunnelBlock;
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
public final class FunnelModels {
    private static final String[] MODES = {"push_unpowered", "push_powered", "pull_unpowered", "pull_powered"};
    private static final String[] BELT_MODES = {"retracted_unpowered", "retracted_powered", "extended_unpowered", "extended_powered", "pushing_unpowered", "pushing_powered", "pulling_unpowered", "pulling_powered"};
    private FunnelModels() { }
    private static String name(boolean brass, boolean vertical, String mode) {
        return (brass ? "brass" : "andesite") + "_funnel_"
                + (vertical ? brass ? "vertical_" : "vertical_filterless_" : "horizontal_") + mode;
    }
    private static IModel model(String name) {
        ResourceLocation id = new ResourceLocation("create", "block/" + name);
        return ModelLoaderRegistry.getModelOrLogError(id, "Could not load funnel model " + id);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (boolean brass : new boolean[]{false, true}) {
            String material = brass ? "brass" : "andesite";
            IModel inventory = ModelLoaderRegistry.getModelOrLogError(
                    new ResourceLocation("create", "item/" + material + "_funnel"),
                    "Could not load " + material + " funnel item model");
            for (ResourceLocation texture : inventory.getTextures()) event.getMap().registerSprite(texture);
            for (String suffix : new String[]{"", "_frame", "_neutral", "_pull", "_push", "_powered", "_unpowered"})
                event.getMap().registerSprite(new ResourceLocation("create", "block/funnel/" + material + "_funnel" + suffix));
            event.getMap().registerSprite(new ResourceLocation("create", "block/" + material + "_block"));
            for (boolean vertical : new boolean[]{false, true}) for (String mode : MODES)
                for (ResourceLocation texture : model(name(brass, vertical, mode)).getTextures()) event.getMap().registerSprite(texture);
            for (String mode : BELT_MODES)
                for (ResourceLocation texture : model((brass ? "brass" : "andesite") + "_belt_funnel_" + mode).getTextures()) event.getMap().registerSprite(texture);
        }
        event.getMap().registerSprite(new ResourceLocation("create", "block/funnel/funnel_open"));
        event.getMap().registerSprite(new ResourceLocation("create", "block/funnel/funnel_closed"));
        event.getMap().registerSprite(new ResourceLocation("create", "block/funnel/funnel_back"));
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        install(event, AllBlocks.ANDESITE_FUNNEL, false);
        install(event, AllBlocks.BRASS_FUNNEL, true);
    }
    private static void install(ModelBakeEvent event, Block block, boolean brass) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : EnumFacing.VALUES) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) continue;
            ModelRotation rotation = switch (facing) {
                case DOWN -> ModelRotation.X180_Y180;
                case UP, SOUTH -> ModelRotation.X0_Y180;
                case EAST -> ModelRotation.X0_Y90;
                case WEST -> ModelRotation.X0_Y270;
                case NORTH -> ModelRotation.X0_Y0;
            };
            Map<String, IBakedModel> models = new HashMap<>();
            for (String mode : MODES) models.put(mode,
                    model(name(brass, facing.getAxis().isVertical(), mode)).bake(rotation, DefaultVertexFormats.BLOCK,
                            sprite -> atlas.getAtlasSprite(sprite.toString())));
            if (facing.getAxis().isHorizontal()) for (String mode : BELT_MODES) models.put("belt_" + mode,
                    model((brass ? "brass" : "andesite") + "_belt_funnel_" + mode).bake(rotation, DefaultVertexFormats.BLOCK,
                            sprite -> atlas.getAtlasSprite(sprite.toString())));
            event.getModelRegistry().putObject(key, new FunnelModel(original, models));
        }
    }
    private static final class FunnelModel extends BakedModelWrapper<IBakedModel> {
        private final Map<String, IBakedModel> models;
        private FunnelModel(IBakedModel original, Map<String, IBakedModel> models) { super(original); this.models = models; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            if (state instanceof IExtendedBlockState extended) {
                IBakedModel model = models.get(extended.getValue(FunnelBlock.MODEL));
                if (model != null) return model.getQuads(state, side, random);
            }
            return originalModel.getQuads(state, side, random);
        }
    }
}
