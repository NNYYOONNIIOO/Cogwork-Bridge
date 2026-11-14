package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.packager.PackagerBlock;
import com.cogwork.cogworkbridge.content.logistics.packager.PackagerTileEntity;
import com.cogwork.cogworkbridge.content.logistics.packager.RepackagerBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelRotation;
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
public final class PackagerRenderer extends TileEntitySpecialRenderer<PackagerTileEntity> {
    private static IBakedModel hatchOpen, hatchClosed, tray, repackagerTray;
    private static IModel model(String path) { return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/" + path), "Could not load packager part " + path); }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String part : new String[]{"packager/hatch_closed", "packager/hatch_open", "packager/tray", "repackager/tray"})
            for (ResourceLocation texture : model(part).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        hatchClosed = model("packager/hatch_closed").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
        hatchOpen = model("packager/hatch_open").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
        tray = model("packager/tray").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
        repackagerTray = model("repackager/tray").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
    }
    @Override public void render(PackagerTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof PackagerBlock)) return;
        EnumFacing facing = state.getValue(PackagerBlock.FACING).getOpposite();
        int ticks = tile.getAnimation(); boolean inward = tile.isAnimationInward();
        boolean open = ticks > (inward ? 1 : 5) && ticks < 20 - (inward ? 5 : 1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5D + facing.getXOffset() * .49999D, y + .5D + facing.getYOffset() * .49999D, z + .5D + facing.getZOffset() * .49999D);
        if (facing.getAxis().isHorizontal()) GlStateManager.rotate(facing.getHorizontalAngle(), 0, 1, 0);
        else GlStateManager.rotate(facing == EnumFacing.UP ? -90 : 90, 1, 0, 0);
        GlStateManager.translate(-.5D, -.5D, -.5D);
        FastStateRendering.getInstance().renderFast(open ? hatchOpen : hatchClosed, state);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        float offset = tile.getTrayOffset(partial);
        GlStateManager.translate(x + .5D + facing.getXOffset() * offset, y + .5D + facing.getYOffset() * offset, z + .5D + facing.getZOffset() * offset);
        if (facing.getAxis().isHorizontal()) GlStateManager.rotate(facing.getHorizontalAngle(), 0, 1, 0);
        GlStateManager.translate(-.5D, -.5D, -.5D);
        FastStateRendering.getInstance().renderFast(state.getBlock() instanceof RepackagerBlock ? repackagerTray : tray, state);
        var rendered = tile.getRenderedBox();
        if (!rendered.isEmpty()) {
            GlStateManager.translate(.5, .625, .5); GlStateManager.scale(1.49F, 1.49F, 1.49F);
            RenderHelper.enableStandardItemLighting();
            Minecraft.getMinecraft().getRenderItem().renderItem(rendered, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.popMatrix();
    }
}
