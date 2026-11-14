package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.table.TableClothBlock;
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
import java.util.ArrayList;
import java.util.List;

/** Connects tablecloth edges and uses the upstream corner geometry for exposed corners. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class TableClothModels {
    // EnumFacing horizontal indices: south, west, north, east.
    private static final String[] CORNERS = {"south_west", "north_west", "north_east", "south_east"};

    private TableClothModels() { }

    private static IModel corner(String name) {
        ResourceLocation id = new ResourceLocation("create", "block/table_cloth/" + name);
        return ModelLoaderRegistry.getModelOrLogError(id, "Could not load tablecloth corner " + id);
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String name : CORNERS)
            for (ResourceLocation texture : corner(name).getTextures()) event.getMap().registerSprite(texture);
        event.getMap().registerSprite(new ResourceLocation("create", "block/table_cloth/red"));
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        List<List<BakedQuad>> corners = new ArrayList<>();
        for (String name : CORNERS) {
            IBakedModel model = corner(name).bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                    sprite -> atlas.getAtlasSprite(sprite.toString()));
            corners.add(model.getQuads(null, null, 0));
        }
        for (Block cloth : AllBlocks.TABLE_CLOTHS) install(event, cloth, corners);
        install(event, AllBlocks.ANDESITE_TABLE_CLOTH, corners);
        install(event, AllBlocks.BRASS_TABLE_CLOTH, corners);
        install(event, AllBlocks.COPPER_TABLE_CLOTH, corners);
    }

    private static void install(ModelBakeEvent event, Block block, List<List<BakedQuad>> corners) {
        ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "normal");
        IBakedModel original = event.getModelRegistry().getObject(key);
        if (original != null) event.getModelRegistry().putObject(key, new ConnectedModel(original, corners));
    }

    private static final class ConnectedModel extends BakedModelWrapper<IBakedModel> {
        private final List<List<BakedQuad>> corners = new ArrayList<>();

        private ConnectedModel(IBakedModel original, List<List<BakedQuad>> sourceCorners) {
            super(original);
            TextureAtlasSprite target = original.getParticleTexture();
            for (List<BakedQuad> source : sourceCorners) {
                List<BakedQuad> recolored = new ArrayList<>();
                for (BakedQuad quad : source) recolored.add(retexture(quad, target));
                corners.add(recolored);
            }
        }

        @Override public boolean isAmbientOcclusion() { return false; }

        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            List<BakedQuad> base = originalModel.getQuads(state, side, random);
            if (side == null || !side.getAxis().isHorizontal() || !(state instanceof IExtendedBlockState extended)) return base;
            Integer culled = extended.getValue(TableClothBlock.CULLED_SIDES);
            if (culled != null && (culled & (1 << side.rotateY().getHorizontalIndex())) != 0) return base;
            List<BakedQuad> result = new ArrayList<>(base);
            result.addAll(corners.get(side.getHorizontalIndex()));
            return result;
        }
    }

    private static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite target) {
        TextureAtlasSprite source = quad.getSprite();
        float width = source.getMaxU() - source.getMinU(), height = source.getMaxV() - source.getMinV();
        if (width == 0 || height == 0) return quad;
        int[] data = quad.getVertexData().clone();
        int stride = data.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int uv = vertex * stride + 4;
            float u = (Float.intBitsToFloat(data[uv]) - source.getMinU()) / width * 16F;
            float v = (Float.intBitsToFloat(data[uv + 1]) - source.getMinV()) / height * 16F;
            data[uv] = Float.floatToRawIntBits(target.getInterpolatedU(u));
            data[uv + 1] = Float.floatToRawIntBits(target.getInterpolatedV(v));
        }
        return new BakedQuad(data, quad.getTintIndex(), quad.getFace(), target,
                quad.shouldApplyDiffuseLighting(), quad.getFormat());
    }
}
