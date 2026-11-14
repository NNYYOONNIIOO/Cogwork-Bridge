package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ChainConveyorRenderer extends TileEntitySpecialRenderer<ChainConveyorTileEntity> {
    private static final ResourceLocation CHAIN = new ResourceLocation("farmers_future_delight", "textures/blocks/iron_chain.png");
    private static IBakedModel wheel, shaft, guard;
    private static IModel model(String name) { return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/chain_conveyor/" + name), "Missing chain conveyor " + name); }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre e) {
        for (String part : new String[]{"wheel", "shaft", "guard"}) for (ResourceLocation texture : model(part).getTextures()) e.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent e) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        wheel = model("wheel").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        shaft = model("shaft").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        guard = model("guard").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
    }
    @Override public void render(ChainConveyorTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        var state = tile.getWorld().getBlockState(tile.getPos());
        GlStateManager.pushMatrix(); GlStateManager.translate(x, y, z);
        FastStateRendering.getInstance().renderFast(wheel, state);
        GlStateManager.pushMatrix(); GlStateManager.translate(.5, .5, .5);
        GlStateManager.rotate(RotatingModelRenderer.kineticAngle(tile, partial), 0, 1, 0);
        GlStateManager.translate(-.5, -.5, -.5); FastStateRendering.getInstance().renderFast(shaft, state); GlStateManager.popMatrix();
        for (BlockPos target : tile.connections()) {
            var geometry = tile.geometry(target); Vec3d delta = geometry.end().subtract(geometry.start());
            float yaw = (float)Math.toDegrees(Math.atan2(delta.x, delta.z));
            GlStateManager.pushMatrix(); GlStateManager.translate(.5, .5, .5); GlStateManager.rotate(yaw, 0, 1, 0);
            GlStateManager.translate(-.5, -.5, -.5); FastStateRendering.getInstance().renderFast(guard, state); GlStateManager.popMatrix();
            Vec3d base = new Vec3d(tile.getPos());
            renderChain(geometry.start().subtract(base), geometry.end().subtract(base), tile.getSpeed(), partial, tile.getWorld().getTotalWorldTime());
        }
        for (var box : tile.parcels()) {
            Vec3d v = tile.packagePosition(box, partial).subtract(new Vec3d(tile.getPos()));
            GlStateManager.pushMatrix(); GlStateManager.translate(v.x, v.y - .55D, v.z);
            float yaw = box.target == null ? box.position : (float)Math.toDegrees(Math.atan2(box.target.getX() - tile.getPos().getX(), box.target.getZ() - tile.getPos().getZ()));
            GlStateManager.rotate(-yaw, 0, 1, 0); GlStateManager.scale(1.49F, 1.49F, 1.49F);
            RenderHelper.enableStandardItemLighting(); Minecraft.getMinecraft().getRenderItem().renderItem(box.item, ItemCameraTransforms.TransformType.FIXED);
            RenderHelper.disableStandardItemLighting(); GlStateManager.popMatrix();
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.popMatrix();
    }
    private static void renderChain(Vec3d from, Vec3d to, float speed, float partial, long time) {
        Vec3d d = to.subtract(from); double length = d.length(); if (length < .01) return;
        Vec3d axis = d.scale(1D / length);
        Vec3d side = new Vec3d(-axis.z, 0, axis.x).normalize().scale(.095D);
        Vec3d up = axis.crossProduct(side).normalize().scale(.095D);
        Vec3d firstPlane = side.add(up).scale(1D / Math.sqrt(2D));
        Vec3d secondPlane = side.subtract(up).scale(1D / Math.sqrt(2D));
        Minecraft.getMinecraft().getTextureManager().bindTexture(CHAIN);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        GlStateManager.color(1F, 1F, 1F, 1F);
        Tessellator t = Tessellator.getInstance(); BufferBuilder b = t.getBuffer(); b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);
        double shift = ((time + partial) * speed / 360D) % 1D;
        quad(b, from, to, firstPlane, 0D, 3D / 16D, shift, shift + length);
        quad(b, from, to, secondPlane, 3D / 16D, 6D / 16D, shift, shift + length);
        t.draw(); if (cull) GlStateManager.enableCull();
    }
    private static void quad(BufferBuilder b, Vec3d a, Vec3d c, Vec3d r, double u0, double u1, double v0, double v1) {
        vertex(b, a.subtract(r), u0, v0); vertex(b, a.add(r), u1, v0); vertex(b, c.add(r), u1, v1); vertex(b, c.subtract(r), u0, v1);
        vertex(b, c.subtract(r), u0, v1); vertex(b, c.add(r), u1, v1); vertex(b, a.add(r), u1, v0); vertex(b, a.subtract(r), u0, v0);
    }
    private static void vertex(BufferBuilder b, Vec3d v, double u, double w) { b.pos(v.x, v.y, v.z).tex(u, w).normal(0, 1, 0).endVertex(); }
}
