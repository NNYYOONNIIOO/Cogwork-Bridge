package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.fluids.HosePulleyBlock;
import com.cogwork.cogworkbridge.content.fluids.HosePulleyTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class HosePulleyRenderer extends TileEntitySpecialRenderer<HosePulleyTileEntity> {
    private static final String[] PARTS = {"hose_coil", "rope", "rope_half", "pulley_magnet", "rope_half_magnet"};
    private static final ResourceLocation SCROLL_TEXTURE = new ResourceLocation(
            "create", "textures/block/hose_pulley_coil_scroll.png");
    private static final Map<String, IBakedModel> MODELS = new HashMap<>();

    private static IModel model(String part) {
        ResourceLocation location = new ResourceLocation("create", "block/hose_pulley/" + part);
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load hose pulley model " + location);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (String part : PARTS) {
            for (ResourceLocation texture : model(part).getTextures()) event.getMap().registerSprite(texture);
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        MODELS.clear();
        for (String part : PARTS) {
            MODELS.put(part, model(part).bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                    sprite -> atlas.getAtlasSprite(sprite.toString())));
        }
    }

    @Override
    public void render(HosePulleyTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        IBlockState state = tile.getContraptionRenderState() != null
                ? tile.getContraptionRenderState() : tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof HosePulleyBlock pulley)) return;
        EnumFacing.Axis shaftAxis = pulley.getAxis(state);
        BlockPos renderPos = tile.getContraptionRenderPos() == null ? tile.getPos() : tile.getContraptionRenderPos();
        float offset = Math.max(0.0F, tile.getCableOffset(partialTicks));
        float oldLightX = OpenGlHelper.lastBrightnessX;
        float oldLightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(ShaftBlock.AXIS, shaftAxis);
        RotatingModelRenderer.renderRotated(tile.getWorld(), renderPos, shaft, shaftAxis,
                RotatingModelRenderer.kineticAngle(tile, partialTicks));

        GlStateManager.pushMatrix();
        // The upstream coil partial is authored facing north. Its planes must
        // face along Z when the shaft runs along X, as in the upstream item
        // model; the Z-axis shaft uses the unrotated partial.
        if (shaftAxis == EnumFacing.Axis.X) {
            GlStateManager.translate(0.5D, 0.5D, 0.5D);
            GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(-0.5D, -0.5D, -0.5D);
        }
        renderCoil(state, offset);
        GlStateManager.popMatrix();

        renderAt(tile, state, offset > 0.25F ? "pulley_magnet" : "rope_half_magnet", offset);
        float fraction = offset % 1.0F;
        if (offset > 0.75F && (fraction < 0.25F || fraction > 0.75F)) {
            renderAt(tile, state, "rope_half", fraction > 0.75F ? fraction - 1.0F : fraction);
        }
        for (int i = 0; i < offset - 1.25F; i++) renderAt(tile, state, "rope", offset - i - 1.0F);
        GlStateManager.popMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, oldLightX, oldLightY);
    }

    private static void renderCoil(IBlockState state, float offset) {
        IBakedModel model = MODELS.get("hose_coil");
        if (model == null) return;

        if (offset == 0.0F) {
            FastStateRendering.getInstance().renderFast(model, state);
            return;
        }

        // The upstream scrolling sheet is 16x32. Forge 1.12 rejects a non-square
        // atlas sprite without animation metadata, so draw this one sheet directly.
        float value = offset / 2.0F + 3.0F / 16.0F;
        float scroll = (float) (-value - Math.floor(-value * 2.0F) / 2.0F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(SCROLL_TEXTURE);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        for (EnumFacing side : EnumFacing.VALUES) {
            appendCoil(buffer, model.getQuads(state, side, 0L), scroll);
        }
        appendCoil(buffer, model.getQuads(state, null, 0L), scroll);
        tessellator.draw();
    }

    private static void appendCoil(BufferBuilder buffer, Iterable<BakedQuad> quads, float scroll) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            float width = sprite.getMaxU() - sprite.getMinU();
            float height = sprite.getMaxV() - sprite.getMinV();
            if (width <= 0 || height <= 0) continue;
            int[] data = quad.getVertexData().clone();
            int stride = data.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                int uv = vertex * stride + 4;
                float u = (Float.intBitsToFloat(data[uv]) - sprite.getMinU()) / width;
                float v = (Float.intBitsToFloat(data[uv + 1]) - sprite.getMinV()) / height;
                data[uv] = Float.floatToRawIntBits(u);
                data[uv + 1] = Float.floatToRawIntBits(v * 0.5F + scroll);
            }
            buffer.addVertexData(data);
            buffer.putColorRGB_F4(1.0F, 1.0F, 1.0F);
            Vec3i normal = quad.getFace() == null ? new Vec3i(0, 1, 0) : quad.getFace().getDirectionVec();
            buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
        }
    }

    private static void renderAt(HosePulleyTileEntity tile, IBlockState state, String part, float offset) {
        BlockPos lightPos = tile.getPos().down(Math.max(0, (int) offset));
        int light = tile.getWorld().getCombinedLight(lightPos, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16 & 65535);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0D, -offset, 0.0D);
        FastStateRendering.getInstance().renderFast(MODELS.get(part), state);
        GlStateManager.popMatrix();
    }
}
