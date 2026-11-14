package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.fluids.FluidSpoutTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class FluidSpoutRenderer extends TileEntitySpecialRenderer<FluidSpoutTileEntity> {
    private static final String[] PARTS = {"top", "middle", "bottom"};
    private static final IBakedModel[] MODELS = new IBakedModel[PARTS.length];
    private final FluidTankRenderer fluidRenderer = new FluidTankRenderer();

    private static IModel model(String part) {
        ResourceLocation id = new ResourceLocation("create", "block/spout/" + part);
        return ModelLoaderRegistry.getModelOrLogError(id, "Could not load spout part " + id);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (String part : PARTS)
            for (ResourceLocation texture : model(part).getTextures()) event.getMap().registerSprite(texture);
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (int i = 0; i < PARTS.length; i++) {
            MODELS[i] = model(PARTS[i]).bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                    texture -> atlas.getAtlasSprite(texture.toString()));
        }
    }

    public static void renderParts(IBlockState state, float offset) {
        GlStateManager.pushMatrix();
        for (IBakedModel part : MODELS) {
            if (part != null) FastStateRendering.getInstance().renderFast(part, state);
            GlStateManager.translate(0, offset * 3F / 32F, 0);
        }
        GlStateManager.popMatrix();
    }

    @Override
    public void render(FluidSpoutTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (tile.getWorld() == null) return;
        fluidRenderer.render(tile, x, y, z, partialTicks, destroyStage, alpha);
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        float ticks = tile.getProcessingTicks(partialTicks);
        float progress = MathHelper.clamp(1.0F - (ticks - 5.0F) / 10.0F, 0.0F, 1.0F);
        float squeeze = ticks < 0 ? 0 : (2 * progress - 1) * (2 * progress - 1) - 1;
        FluidStack fluid = tile.getFluid();
        float streamRadius = -squeeze / 32.0F;
        if (ticks >= 0 && fluid != null && streamRadius > 0) {
            FluidTankRenderer.renderFluidBox(fluid, x + 0.5D - streamRadius, y - 1.2D,
                    z + 0.5D - streamRadius, streamRadius * 2, 1.2D, streamRadius * 2);
        }
        if (ticks >= 0 && ticks < 2) squeeze = -ticks / 2.0F;
        else if (ticks >= 2 && ticks < 10) squeeze = -1.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        for (IBakedModel part : MODELS) {
            if (part != null) FastStateRendering.getInstance().renderFast(part, state);
            GlStateManager.translate(0, -3.0F * squeeze / 32.0F, 0);
        }
        GlStateManager.popMatrix();
    }
}
