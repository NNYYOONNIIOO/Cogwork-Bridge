package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.decoration.palette.CopycatBlock;
import com.cogwork.cogworkbridge.content.decoration.palette.CopycatTile;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class CopycatModels {
    private CopycatModels() { }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        for (ResourceLocation key : new ArrayList<>(event.getModelRegistry().getKeys())) {
            if (!(key instanceof ModelResourceLocation model) || model.getVariant().equals("inventory") || !Reference.MOD_ID.equals(model.getNamespace())) continue;
            if (!model.getPath().equals("copycat_panel") && !model.getPath().equals("copycat_step")) continue;
            IBakedModel original = event.getModelRegistry().getObject(model);
            if (original != null) event.getModelRegistry().putObject(model, new CopycatModel(original));
        }
    }
    @SubscribeEvent public static void colors(ColorHandlerEvent.Block event) {
        event.getBlockColors().registerBlockColorHandler((state, access, pos, tint) -> {
            if (access == null || pos == null) return 0xffffff;
            TileEntity tile = access.getTileEntity(pos);
            if (!(tile instanceof CopycatTile copycat) || copycat.material().getBlock() == Blocks.AIR) return 0xffffff;
            return event.getBlockColors().colorMultiplier(copycat.material(), access, pos, tint);
        }, BlockFamily.get("copycat_panel"), BlockFamily.get("copycat_step"));
    }

    private static class CopycatModel extends BakedModelWrapper<IBakedModel> {
        CopycatModel(IBakedModel model) { super(model); }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
            IBlockState material = state instanceof IExtendedBlockState extended ? extended.getValue(CopycatBlock.MATERIAL) : null;
            if (material == null || material.getBlock() == Blocks.AIR) {
                return layer == null || layer == BlockRenderLayer.CUTOUT ? originalModel.getQuads(state, side, random) : Collections.emptyList();
            }
            if (layer != null && !material.getBlock().canRenderInLayer(material, layer)) return Collections.emptyList();
            IBlockState modelState = material instanceof IExtendedBlockState extended ? extended.getClean() : material;
            IBakedModel source = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(modelState);
            if (state.getBlock() instanceof CopycatBlock.Panel && material.getBlock() instanceof net.minecraft.block.BlockTrapDoor)
                return source.getQuads(material, side, random);
            if (state.getBlock() instanceof CopycatBlock.Panel && CopycatBlock.isBars(material)) {
                IBlockState bars = BlockFamily.get("render_copycat_bars").getDefaultState()
                        .withProperty(com.cogwork.cogworkbridge.registry.RenderPartBlock.FACING, state.getValue(CopycatBlock.Panel.FACING));
                IBakedModel template = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(bars);
                return recolor(template.getQuads(bars, side, random), source.getParticleTexture());
            }
            if (side != null) return Collections.emptyList();
            AxisAlignedBB bounds = ((CopycatBlock) state.getBlock()).bounds(state);
            Integer hidden = state instanceof IExtendedBlockState extended ? extended.getValue(CopycatBlock.OCCLUSION) : null;
            int occlusion = hidden == null ? 0 : hidden;
            List<BakedQuad> output = new ArrayList<>();
            for (EnumFacing face : EnumFacing.VALUES)
                if ((occlusion & 1 << face.getIndex()) == 0) addClipped(output, source.getQuads(material, face, random), bounds);
            for (BakedQuad quad : source.getQuads(material, null, random))
                if (quad.getFace() == null || (occlusion & 1 << quad.getFace().getIndex()) == 0)
                    addClipped(output, Collections.singletonList(quad), bounds);
            return output;
        }
    }

    private static List<BakedQuad> recolor(List<BakedQuad> quads, TextureAtlasSprite target) {
        if (target == null || quads.isEmpty()) return quads;
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            TextureAtlasSprite source = quad.getSprite();
            float width = source.getMaxU() - source.getMinU(), height = source.getMaxV() - source.getMinV();
            if (width == 0 || height == 0) { result.add(quad); continue; }
            int[] data = quad.getVertexData().clone(); int stride = data.length / 4;
            for (int i = 0; i < 4; i++) {
                int uv = i * stride + 4;
                float u = (Float.intBitsToFloat(data[uv]) - source.getMinU()) / width * 16F;
                float v = (Float.intBitsToFloat(data[uv + 1]) - source.getMinV()) / height * 16F;
                data[uv] = Float.floatToRawIntBits(target.getInterpolatedU(u));
                data[uv + 1] = Float.floatToRawIntBits(target.getInterpolatedV(v));
            }
            result.add(new BakedQuad(data, quad.getTintIndex(), quad.getFace(), target, quad.shouldApplyDiffuseLighting(), quad.getFormat()));
        }
        return result;
    }

    /** Crop axis-aligned source quads without scaling their texture coordinates. */
    private static void addClipped(List<BakedQuad> out, List<BakedQuad> quads, AxisAlignedBB box) {
        double[] low = {box.minX, box.minY, box.minZ}, high = {box.maxX, box.maxY, box.maxZ};
        for (BakedQuad quad : quads) {
            EnumFacing face = quad.getFace();
            if (face == null) continue;
            int normal = face.getAxis() == EnumFacing.Axis.X ? 0 : face.getAxis() == EnumFacing.Axis.Y ? 1 : 2;
            int a = (normal + 1) % 3, b = (normal + 2) % 3;
            int[] data = quad.getVertexData().clone(); int stride = data.length / 4;
            float[][] p = new float[4][3]; float[] u = new float[4], v = new float[4];
            float minA = Float.POSITIVE_INFINITY, maxA = Float.NEGATIVE_INFINITY, minB = Float.POSITIVE_INFINITY, maxB = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                for (int axis = 0; axis < 3; axis++) p[i][axis] = Float.intBitsToFloat(data[i * stride + axis]);
                u[i] = Float.intBitsToFloat(data[i * stride + 4]); v[i] = Float.intBitsToFloat(data[i * stride + 5]);
                minA = Math.min(minA, p[i][a]); maxA = Math.max(maxA, p[i][a]); minB = Math.min(minB, p[i][b]); maxB = Math.max(maxB, p[i][b]);
            }
            if (maxA <= low[a] || minA >= high[a] || maxB <= low[b] || minB >= high[b]) continue;
            int ia = -1, ib = -1;
            for (int i = 1; i < 4; i++) {
                if (Math.abs(p[i][a] - p[0][a]) > .0001F && Math.abs(p[i][b] - p[0][b]) < .0001F) ia = i;
                if (Math.abs(p[i][b] - p[0][b]) > .0001F && Math.abs(p[i][a] - p[0][a]) < .0001F) ib = i;
            }
            if (ia < 0 || ib < 0) continue;
            for (int i = 0; i < 4; i++) {
                float ca = (float) Math.max(low[a], Math.min(high[a], p[i][a]));
                float cb = (float) Math.max(low[b], Math.min(high[b], p[i][b]));
                float fa = (ca - p[0][a]) / (p[ia][a] - p[0][a]);
                float fb = (cb - p[0][b]) / (p[ib][b] - p[0][b]);
                data[i * stride + a] = Float.floatToRawIntBits(ca);
                data[i * stride + b] = Float.floatToRawIntBits(cb);
                data[i * stride + normal] = Float.floatToRawIntBits((float) (face.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? high[normal] : low[normal]));
                data[i * stride + 4] = Float.floatToRawIntBits(u[0] + fa * (u[ia] - u[0]) + fb * (u[ib] - u[0]));
                data[i * stride + 5] = Float.floatToRawIntBits(v[0] + fa * (v[ia] - v[0]) + fb * (v[ib] - v[0]));
            }
            out.add(new BakedQuad(data, quad.getTintIndex(), face, quad.getSprite(), quad.shouldApplyDiffuseLighting(), quad.getFormat()));
        }
    }
}
