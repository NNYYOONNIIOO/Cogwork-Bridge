package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageBlock;
import com.cogwork.cogworkbridge.content.contraptions.gantry.GantryCarriageTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
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
public final class GantryCarriageRenderer extends TileEntitySpecialRenderer<GantryCarriageTileEntity> {
    private static IBakedModel wheels;
    private static IModel model() {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/gantry_carriage/wheels"), "Could not load gantry wheels");
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : model().getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        wheels = model().bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
    }
    @Override public void render(GantryCarriageTileEntity tile, double x, double y, double z, float partial, int stage, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getContraptionRenderState() == null ? tile.getWorld().getBlockState(tile.getPos()) : tile.getContraptionRenderState();
        if (!(state.getBlock() instanceof GantryCarriageBlock carriage)) return;
        EnumFacing facing = state.getValue(GantryCarriageBlock.FACING);
        EnumFacing.Axis rotationAxis = carriage.getAxis(state);
        EnumFacing.Axis railAxis = java.util.Arrays.stream(EnumFacing.Axis.values())
                .filter(axis -> axis != rotationAxis && axis != facing.getAxis()).findFirst().orElse(EnumFacing.Axis.X);
        float angle = RotatingModelRenderer.kineticAngle(tile, partial);
        if (railAxis == EnumFacing.Axis.X && facing == EnumFacing.UP
                || railAxis == EnumFacing.Axis.Y && (facing == EnumFacing.NORTH || facing == EnumFacing.EAST)) angle = -angle;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        IBlockState shaft = AllBlocks.SHAFT.getDefaultState().withProperty(ShaftBlock.AXIS, rotationAxis);
        RotatingModelRenderer.renderRotated(tile.getWorld(), tile.getPos(), shaft, rotationAxis,
                RotatingModelRenderer.kineticAngle(tile, partial));
        GlStateManager.translate(.5D, .5D, .5D);
        GlStateManager.rotate(facing.getAxis().isHorizontal() ? facing.getHorizontalAngle() : 0F, 0, 1, 0);
        GlStateManager.rotate(facing == EnumFacing.UP ? 0F : facing == EnumFacing.DOWN ? 180F : 90F, 1, 0, 0);
        GlStateManager.rotate(state.getValue(GantryCarriageBlock.AXIS_ALONG_FIRST) ^ facing.getAxis() == EnumFacing.Axis.X ? 0F : 90F, 0, 1, 0);
        GlStateManager.translate(0, -9D / 16D, 0);
        GlStateManager.rotate(-angle, 1, 0, 0);
        GlStateManager.translate(0, 9D / 16D, 0);
        GlStateManager.translate(-.5D, -.5D, -.5D);
        FastStateRendering.getInstance().renderFast(wheels, state);
        GlStateManager.popMatrix();
    }
}
