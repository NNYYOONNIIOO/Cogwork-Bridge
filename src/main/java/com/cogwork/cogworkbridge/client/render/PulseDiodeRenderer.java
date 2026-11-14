package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock;
import com.cogwork.cogworkbridge.content.redstone.PulseDiodeTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class PulseDiodeRenderer extends TileEntitySpecialRenderer<PulseDiodeTileEntity> {
    private static IBakedModel indicator;

    private static IModel model() {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/diodes/indicator"),
                "Could not load pulse diode indicator");
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : model().getTextures()) event.getMap().registerSprite(texture);
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        indicator = model().bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                sprite -> atlas.getAtlasSprite(sprite.toString()));
    }

    @Override public void render(PulseDiodeTileEntity tile, double x, double y, double z,
                                 float partial, int destroy, float alpha) {
        if (indicator == null || tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof PulseDiodeBlock)) return;
        float progress = tile.getProgress();
        float red = (44F + (205F - 44F) * progress) / 255F;
        float green = 3F * (1F - progress) / 255F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5D, y, z + .5D);
        EnumFacing facing = state.getValue(PulseDiodeBlock.FACING);
        GlStateManager.rotate(facing == EnumFacing.SOUTH ? 0F : facing == EnumFacing.WEST ? 90F
                : facing == EnumFacing.NORTH ? 180F : 270F, 0F, 1F, 0F);
        GlStateManager.translate(-.5D, 0D, -.5D);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        for (EnumFacing side : EnumFacing.values()) append(buffer, indicator.getQuads(state, side, 0L), red, green);
        append(buffer, indicator.getQuads(state, null, 0L), red, green);
        Tessellator.getInstance().draw();
        GlStateManager.popMatrix();
    }

    private static void append(BufferBuilder buffer, Iterable<BakedQuad> quads, float red, float green) {
        for (BakedQuad quad : quads) {
            buffer.addVertexData(quad.getVertexData());
            buffer.putColorRGB_F4(red, green, 0F);
            var normal = quad.getFace() == null ? EnumFacing.UP.getDirectionVec() : quad.getFace().getDirectionVec();
            buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
        }
    }
}
