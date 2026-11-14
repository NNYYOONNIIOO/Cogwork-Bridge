package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyBlock;
import com.cogwork.cogworkbridge.content.contraptions.elevator.ElevatorPulleyTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
public final class ElevatorPulleyRenderer extends TileEntitySpecialRenderer<ElevatorPulleyTileEntity> {
    private static final String[] PARTS = {"rope_coil", "rope", "rope_half", "pulley_magnet"};
    private static final Map<String, IBakedModel> MODELS = new HashMap<>();
    private static final ResourceLocation COIL_SCROLL = new ResourceLocation("create", "textures/block/elevator_pulley_coil_scroll.png");
    private static final ResourceLocation BELT_SCROLL = new ResourceLocation("create", "textures/block/elevator_pulley_belt_scroll.png");
    private static IModel model(String part) {
        ResourceLocation location = new ResourceLocation("create", "block/elevator_pulley/" + part);
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load elevator pulley model " + location);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String part : PARTS) for (ResourceLocation texture : model(part).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        MODELS.clear();
        for (String part : PARTS) MODELS.put(part, model(part).bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                sprite -> atlas.getAtlasSprite(sprite.toString())));
    }
    @Override public void render(ElevatorPulleyTileEntity tile, double x, double y, double z,
                                 float partial, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof ElevatorPulleyBlock pulley)) return;
        EnumFacing.Axis axis = pulley.getAxis(state);
        float cable = Math.max(0F, tile.getCableOffset(partial));
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(ShaftBlock.AXIS, axis);
        RotatingModelRenderer.renderRotated(tile.getWorld(), tile.getPos(), shaft, axis,
                RotatingModelRenderer.kineticAngle(tile, partial));
        GlStateManager.pushMatrix();
        GlStateManager.translate(.5, .5, .5);
        GlStateManager.rotate(180F + state.getValue(ElevatorPulleyBlock.FACING).getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-.5, -.5, -.5);
        if (cable == 0F) FastStateRendering.getInstance().renderFast(MODELS.get("rope_coil"), state);
        else {
            float value = cable / 2F + 3F / 16F;
            renderScrolling(MODELS.get("rope_coil"), state, COIL_SCROLL,
                    (float) (-value - Math.floor(-value * 2F) / 2F));
        }
        float beltScroll = (float) ((-(cable + .5F) - Math.floor(-(cable + .5F))) / 2F);
        float fraction = cable % 1F;
        if (cable > 0F && (fraction < .25F || fraction > .75F))
            renderAt(tile, state, "rope_half", fraction > .75F ? fraction - 1F : fraction, beltScroll);
        for (int i = 0; i < cable - .25F; i++) renderAt(tile, state, "rope", cable - i, beltScroll);
        renderAt(tile, state, "pulley_magnet", cable);
        GlStateManager.popMatrix();
        GlStateManager.popMatrix();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
    }
    private static void renderAt(ElevatorPulleyTileEntity tile, IBlockState state, String part, float offset) {
        renderAt(tile, state, part, offset, Float.NaN);
    }
    private static void renderAt(ElevatorPulleyTileEntity tile, IBlockState state, String part, float offset, float scroll) {
        BlockPos lightPos = tile.getPos().down(Math.max(0, (int) offset));
        int light = tile.getWorld().getCombinedLight(lightPos, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16 & 65535);
        GlStateManager.pushMatrix(); GlStateManager.translate(0, -offset, 0);
        if (Float.isNaN(scroll)) FastStateRendering.getInstance().renderFast(MODELS.get(part), state);
        else renderScrolling(MODELS.get(part), state, BELT_SCROLL, scroll);
        GlStateManager.popMatrix();
    }
    private static void renderScrolling(IBakedModel model, IBlockState state, ResourceLocation texture, float scroll) {
        if (model == null) return;
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        for (EnumFacing side : EnumFacing.VALUES) append(buffer, model.getQuads(state, side, 0L), scroll);
        append(buffer, model.getQuads(state, null, 0L), scroll);
        tessellator.draw();
    }
    private static void append(BufferBuilder buffer, Iterable<BakedQuad> quads, float scroll) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            float width = sprite.getMaxU() - sprite.getMinU(), height = sprite.getMaxV() - sprite.getMinV();
            if (width <= 0 || height <= 0) continue;
            int[] data = quad.getVertexData().clone();
            int stride = data.length / 4;
            for (int i = 0; i < 4; i++) {
                int uv = i * stride + 4;
                float u = (Float.intBitsToFloat(data[uv]) - sprite.getMinU()) / width;
                float v = (Float.intBitsToFloat(data[uv + 1]) - sprite.getMinV()) / height;
                data[uv] = Float.floatToRawIntBits(u); data[uv + 1] = Float.floatToRawIntBits(v * .5F + scroll);
            }
            buffer.addVertexData(data); buffer.putColorRGB_F4(1F, 1F, 1F);
            Vec3i normal = quad.getFace() == null ? new Vec3i(0, 1, 0) : quad.getFace().getDirectionVec();
            buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
        }
    }
}
