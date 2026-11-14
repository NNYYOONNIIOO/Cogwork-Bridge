package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.fluids.FluidTankBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class FluidTankModels {
    private static final String[] LEVELS = {"single", "bottom", "middle", "top"};
    private static final String[] WINDOWS = {"", "_window", "_window_nw", "_window_ne", "_window_sw", "_window_se"};

    private FluidTankModels() { }

    private static ResourceLocation location(boolean creative, String name) {
        return new ResourceLocation("create", creative ? "block/creative_" + name : "block/fluid_tank/block_" + name);
    }

    private static IModel load(ResourceLocation location) {
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load fluid tank model " + location);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (String name : new String[]{"fluid_tank", "fluid_tank_top", "fluid_tank_inner",
                "creative_fluid_tank", "creative_casing"}) {
            event.getMap().registerSprite(new ResourceLocation("create", "block/" + name + "_connected"));
        }
        for (boolean creative : new boolean[]{false, true}) {
            for (String level : LEVELS) for (String window : WINDOWS) {
                for (ResourceLocation texture : load(location(creative, level + window)).getTextures()) {
                    event.getMap().registerSprite(texture);
                }
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        install(event, AllBlocks.FLUID_TANK, false);
        install(event, AllBlocks.CREATIVE_FLUID_TANK, true);
    }

    private static void install(ModelBakeEvent event, Block block, boolean creative) {
        ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "normal");
        IBakedModel original = event.getModelRegistry().getObject(key);
        if (original == null) return;
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        Map<String, IBakedModel> models = new HashMap<>();
        for (String level : LEVELS) for (String window : WINDOWS) {
            String name = level + window;
            models.put(name, load(location(creative, name)).bake(ModelRotation.X0_Y0,
                    DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString())));
        }
        event.getModelRegistry().putObject(key, new TankModel(original, models, creative));
    }

    private static final class TankModel extends BakedModelWrapper<IBakedModel> {
        private final Map<String, IBakedModel> models;
        private final boolean creative;

        private TankModel(IBakedModel original, Map<String, IBakedModel> models, boolean creative) {
            super(original);
            this.models = models;
            this.creative = creative;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            if (state instanceof IExtendedBlockState extended) {
                IBakedModel model = models.get(extended.getValue(FluidTankBlock.MODEL));
                if (model != null) {
                    // 1.21 culls the faces between members of the same tank, even
                    // when those faces look inward rather than toward the neighbor.
                    if (side != null) return Collections.emptyList();
                    Integer mask = extended.getValue(FluidTankBlock.CULLED_SIDES);
                    int culled = mask == null ? 0 : mask;
                    List<BakedQuad> quads = new ArrayList<>();
                    for (EnumFacing direction : EnumFacing.values()) {
                        if ((culled & (1 << direction.getIndex())) == 0) {
                            quads.addAll(model.getQuads(state, direction, random));
                        }
                    }
                    quads.addAll(model.getQuads(state, null, random));
                    Integer context = extended.getValue(FluidTankBlock.CONNECTED);
                    return context == null ? quads : connectedTextures(quads, context);
                }
            }
            return originalModel.getQuads(state, side, random);
        }

        private List<BakedQuad> connectedTextures(List<BakedQuad> quads, int context) {
            TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
            List<BakedQuad> result = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                EnumFacing face = quad.getFace();
                TextureAtlasSprite source = quad.getSprite();
                String name = creative ? (face.getAxis().isVertical() ? "creative_casing" : "creative_fluid_tank")
                        : face.getAxis().isHorizontal() ? "fluid_tank"
                        : source == atlas.getAtlasSprite("create:block/fluid_tank_inner") ? "fluid_tank_inner" : "fluid_tank_top";
                if (source != atlas.getAtlasSprite("create:block/" + name)) { result.add(quad); continue; }
                TextureAtlasSprite target = atlas.getAtlasSprite("create:block/" + name + "_connected");
                if (target == atlas.getMissingSprite()) { result.add(quad); continue; }
                int bits = context >> (face.getIndex() * 4) & 15;
                boolean up = (bits & 1) != 0, down = (bits & 2) != 0;
                boolean left = (bits & 4) != 0, right = (bits & 8) != 0;
                int col = left && right ? 2 : left ? 3 : right ? 1 : 0;
                int row = up && down ? 1 : up ? 2 : down ? 0 : 3;
                int[] data = quad.getVertexData().clone();
                int stride = data.length / 4;
                for (int vertex = 0; vertex < 4; vertex++) {
                    int uv = vertex * stride + 4;
                    float u = Float.intBitsToFloat(data[uv]);
                    float v = Float.intBitsToFloat(data[uv + 1]);
                    float localU = (u - source.getMinU()) / (source.getMaxU() - source.getMinU()) * 16F;
                    float localV = (v - source.getMinV()) / (source.getMaxV() - source.getMinV()) * 16F;
                    data[uv] = Float.floatToRawIntBits(target.getInterpolatedU((localU + col * 16F) / 4F));
                    data[uv + 1] = Float.floatToRawIntBits(target.getInterpolatedV((localV + row * 16F) / 4F));
                }
                result.add(new BakedQuad(data, quad.getTintIndex(), face, target, quad.shouldApplyDiffuseLighting(), quad.getFormat()));
            }
            return result;
        }
    }
}
