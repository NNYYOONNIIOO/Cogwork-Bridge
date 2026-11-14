package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.vault.ItemVaultBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Applies the upstream rectangle connected-texture sheet to each vault face. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ItemVaultModels {
    private static final String[] FACE = {"bottom", "top", "front", "side"};
    private ItemVaultModels() { }
    private static ResourceLocation sprite(String face, boolean large) {
        return new ResourceLocation("create", "block/vault/vault_" + face + (large ? "_large" : "_medium"));
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String face : FACE) for (boolean large : new boolean[]{false, true}) event.getMap().registerSprite(sprite(face, large));
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        for (EnumFacing.Axis axis : new EnumFacing.Axis[]{EnumFacing.Axis.X, EnumFacing.Axis.Z}) {
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.ITEM_VAULT.getRegistryName(), "axis=" + axis.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original != null) event.getModelRegistry().putObject(key, new Model(original, axis));
        }
    }
    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private final EnumFacing.Axis axis;
        Model(IBakedModel original, EnumFacing.Axis axis) { super(original); this.axis = axis; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            List<BakedQuad> original = originalModel.getQuads(state, side, random);
            if (!(state instanceof IExtendedBlockState extended)) return original;
            Integer context = extended.getValue(ItemVaultBlock.CONNECTED); if (context == null) return original;
            TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
            List<BakedQuad> result = new ArrayList<>(original.size());
            for (BakedQuad quad : original) {
                EnumFacing face = quad.getFace(); int bits = context >> (face.getIndex() * 4) & 15;
                boolean up = (bits & 1) != 0, down = (bits & 2) != 0, left = (bits & 4) != 0, right = (bits & 8) != 0;
                int col = left && right ? 2 : left ? 3 : right ? 1 : 0;
                int row = up && down ? 1 : up ? 2 : down ? 0 : 3;
                String kind = face == EnumFacing.UP ? "top" : face == EnumFacing.DOWN ? "bottom"
                        : face.getAxis() == axis ? "front" : "side";
                TextureAtlasSprite source = quad.getSprite(), target = atlas.getAtlasSprite(sprite(kind, (context & (1 << 24)) != 0).toString());
                int[] vertices = quad.getVertexData().clone(); int stride = vertices.length / 4;
                for (int i = 0; i < 4; i++) {
                    int offset = i * stride; float u = Float.intBitsToFloat(vertices[offset + 4]);
                    float v = Float.intBitsToFloat(vertices[offset + 5]);
                    float localU = (u - source.getMinU()) / (source.getMaxU() - source.getMinU()) * 16F;
                    float localV = (v - source.getMinV()) / (source.getMaxV() - source.getMinV()) * 16F;
                    vertices[offset + 4] = Float.floatToRawIntBits(target.getInterpolatedU((localU + col * 16F) / 4F));
                    vertices[offset + 5] = Float.floatToRawIntBits(target.getInterpolatedV((localV + row * 16F) / 4F));
                }
                result.add(new BakedQuad(vertices, quad.getTintIndex(), face, target, quad.shouldApplyDiffuseLighting(), quad.getFormat()));
            }
            return result;
        }
    }
}
