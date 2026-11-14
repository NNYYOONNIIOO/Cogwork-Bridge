package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.AnalogLeverBlock;
import com.cogwork.cogworkbridge.content.redstone.AnalogLeverTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class AnalogLeverRenderer extends TileEntitySpecialRenderer<AnalogLeverTileEntity> {
    private static IBakedModel handle, indicator;
    private static IModel model(String name) { return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/analog_lever/" + name), "Could not load analog lever " + name); }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String name : new String[]{"handle", "indicator"}) for (ResourceLocation texture : model(name).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        handle = model("handle").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
        indicator = model("indicator").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
    }
    @Override public void render(AnalogLeverTileEntity tile, double x, double y, double z, float partial, int stage, float alpha) {
        if (tile.getWorld() == null || handle == null || indicator == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof AnalogLeverBlock)) return;
        EnumFacing facing = state.getValue(AnalogLeverBlock.FACING);
        AnalogLeverBlock.Mount mount = state.getValue(AnalogLeverBlock.MOUNT);
        float value = Math.max(0F, Math.min(15F, tile.animation(partial)));
        float yaw = facing == EnumFacing.NORTH ? 0F : facing == EnumFacing.EAST ? 90F : facing == EnumFacing.SOUTH ? 180F : 270F;
        if (mount == AnalogLeverBlock.Mount.CEILING) yaw = (yaw + 180F) % 360F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5D, y + .5D, z + .5D);
        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.rotate(mount == AnalogLeverBlock.Mount.FLOOR ? 0 : mount == AnalogLeverBlock.Mount.WALL ? 90 : 180, 1, 0, 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, -7D / 16D, 0);
        GlStateManager.rotate(value * 6F, 1, 0, 0);
        GlStateManager.translate(-.5D, -1D / 16D, -.5D);
        FastStateRendering.getInstance().renderFast(handle, state);
        GlStateManager.popMatrix();
        GlStateManager.translate(-.5D, -.5D, -.5D);
        float t = value / 15F;
        renderIndicator(state, (44F + 161F * t) / 255F, (3F - 3F * t) / 255F);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private static void renderIndicator(IBlockState state, float red, float green) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        for (EnumFacing side : EnumFacing.values()) appendIndicator(buffer, indicator.getQuads(state, side, 0L), red, green);
        appendIndicator(buffer, indicator.getQuads(state, null, 0L), red, green);
        tessellator.draw();
    }

    private static void appendIndicator(BufferBuilder buffer, Iterable<BakedQuad> quads, float red, float green) {
        for (BakedQuad quad : quads) {
            buffer.addVertexData(quad.getVertexData());
            buffer.putColorRGB_F4(red, green, 0F);
            Vec3i normal = quad.getFace() == null ? new Vec3i(0, 1, 0) : quad.getFace().getDirectionVec();
            buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
        }
    }
}
