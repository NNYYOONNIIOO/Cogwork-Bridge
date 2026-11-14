package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock;
import com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class PackagePortRenderer extends TileEntitySpecialRenderer<PackagePortTileEntity> {
    private static IBakedModel body, head, headGoggles, tongue, flag;
    private static IModel model(String path) { return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/" + path), "Could not load package port part " + path); }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String path : new String[]{"package_frogport/body", "package_frogport/head", "package_frogport/head_goggles", "package_frogport/tongue", "package_postbox/flag"})
            for (ResourceLocation texture : model(path).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        body = model("package_frogport/body").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        head = model("package_frogport/head").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        headGoggles = model("package_frogport/head_goggles").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        tongue = model("package_frogport/tongue").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        flag = model("package_postbox/flag").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
    }
    @Override public void render(PackagePortTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        var state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof PackagePortBlock block)) return;
        GlStateManager.pushMatrix(); GlStateManager.translate(x + .5, y + .5, z + .5);
        var target = tile.chainPosition();
        float yaw = 180 - state.getValue(PackagePortBlock.FACING).getHorizontalAngle();
        if (block.isFrog() && target != null) yaw = (float)Math.toDegrees(Math.atan2(target.x - tile.getPos().getX() - .5, target.z - tile.getPos().getZ() - .5)) + 180F;
        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.translate(-.5, -.5, -.5);
        // The upstream frog uses paired front/back faces on the same plane.
        // Its cutout render type culls their reverse side; TESRs do not always
        // inherit that state in 1.12, which makes the head flicker.
        boolean wasCulled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.enableCull();
        if (block.isFrog()) {
            FastStateRendering.getInstance().renderFast(body, state);
            GlStateManager.pushMatrix();
            GlStateManager.translate(.5, 10D / 16D, 11D / 16D);
            GlStateManager.rotate(tile.openProgress(partial) * 60, 1, 0, 0);
            GlStateManager.translate(-.5, -10D / 16D, -11D / 16D);
            FastStateRendering.getInstance().renderFast(tile.hasGoggles() ? headGoggles : head, state);
            GlStateManager.popMatrix();
            float open = tile.openProgress(partial);
            if (open > 0) {
                GlStateManager.translate(.5, 10D / 16D, 11D / 16D);
                GlStateManager.scale(1F, 1F, open * 4F / 7F);
                GlStateManager.translate(-.5, -10D / 16D, -11D / 16D);
                FastStateRendering.getInstance().renderFast(tongue, state);
            }
        } else {
            GlStateManager.translate(0, 10D / 16D, 2D / 16D);
            GlStateManager.rotate(tile.flagProgress(partial) * -90, 1, 0, 0);
            GlStateManager.translate(0, -10D / 16D, -2D / 16D);
            FastStateRendering.getInstance().renderFast(flag, state);
        }
        if (!wasCulled) GlStateManager.disableCull();
        GlStateManager.popMatrix();
        if (block.isFrog() && target != null && !tile.animatedPackage().isEmpty()) {
            float progress = tile.animationProgress(partial);
            double blend = tile.isSending() ? Math.min(1, progress * 2) : Math.max(0, 1 - progress);
            double localX = .5 + (target.x - tile.getPos().getX() - .5) * blend;
            double localY = .8 + (target.y - tile.getPos().getY() - .8) * blend;
            double localZ = .5 + (target.z - tile.getPos().getZ() - .5) * blend;
            GlStateManager.pushMatrix(); GlStateManager.translate(x + localX, y + localY, z + localZ);
            GlStateManager.rotate(-yaw, 0, 1, 0); GlStateManager.scale(1.49F, 1.49F, 1.49F);
            RenderHelper.enableStandardItemLighting(); Minecraft.getMinecraft().getRenderItem().renderItem(tile.animatedPackage(), ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
        }
    }
}
