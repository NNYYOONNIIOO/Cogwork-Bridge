package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelBlock;
import com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.EnumMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class FactoryPanelRenderer extends TileEntitySpecialRenderer<FactoryPanelTileEntity> {
    private static IBakedModel panel;
    private static final Map<EnumFacing, IBakedModel> lines = new EnumMap<>(EnumFacing.class);
    private static final Map<EnumFacing, IBakedModel> arrows = new EnumMap<>(EnumFacing.class);
    private static IModel model() { return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/factory_gauge/panel"), "Could not load factory panel"); }
    private static IModel connectionModel(String type, EnumFacing facing) {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/factory_gauge/connections/" + type + "_" + facing.getName()), "Could not load factory panel connection");
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : model().getTextures()) event.getMap().registerSprite(texture);
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL)
            for (String type : new String[]{"line", "arrow"})
                for (ResourceLocation texture : connectionModel(type, facing).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        panel = model().bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString()));
        lines.clear(); arrows.clear();
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            lines.put(facing, connectionModel("line", facing).bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString())));
            arrows.put(facing, connectionModel("arrow", facing).bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, s -> atlas.getAtlasSprite(s.toString())));
        }
    }
    @Override public void render(FactoryPanelTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null) return;
        var state = tile.getWorld().getBlockState(tile.getPos()); if (!(state.getBlock() instanceof FactoryPanelBlock)) return;
        GlStateManager.pushMatrix(); GlStateManager.translate(x + .5, y + .5, z + .5);
        switch (state.getValue(FactoryPanelBlock.FACING)) {
            case DOWN -> GlStateManager.rotate(180, 1, 0, 0);
            case NORTH -> GlStateManager.rotate(-90, 1, 0, 0);
            case SOUTH -> GlStateManager.rotate(90, 1, 0, 0);
            case EAST -> GlStateManager.rotate(-90, 0, 0, 1);
            case WEST -> GlStateManager.rotate(90, 0, 0, 1);
            default -> { }
        }
        GlStateManager.translate(-.5, -.5, -.5);
        renderConnections(tile, state);
        for (int i = 0; i < 4; i++) {
            if (!tile.active(i)) continue;
            GlStateManager.pushMatrix(); GlStateManager.translate((i & 1) * .5, 0, (i >> 1) * .5);
            FastStateRendering.getInstance().renderFast(panel, state);
            ItemStack item = tile.filter(i);
            if (!item.isEmpty()) {
                GlStateManager.translate(.25, .14, .25); GlStateManager.rotate(90, 1, 0, 0); GlStateManager.scale(.25F, .25F, .25F);
                RenderHelper.enableStandardItemLighting(); Minecraft.getMinecraft().getRenderItem().renderItem(item, ItemCameraTransforms.TransformType.FIXED); RenderHelper.disableStandardItemLighting();
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }

    private static void renderConnections(FactoryPanelTileEntity tile, IBlockState state) {
        EnumFacing facing = state.getValue(FactoryPanelBlock.FACING);
        for (int slot = 0; slot < 4; slot++) {
            if (!tile.active(slot)) continue;
            for (FactoryPanelTileEntity.Connection connection : tile.inputs(slot)) {
                BlockPos difference = connection.pos().subtract(tile.getPos());
                if (difference.distanceSq(BlockPos.ORIGIN) >= 256) continue;
                double x = .25 + (slot & 1) * .5, z = .25 + (slot >> 1) * .5;
                boolean first = true;
                for (EnumFacing step : com.cogwork.cogworkbridge.content.logistics.factory.FactoryPanelPath.route(tile, slot, connection)) {
                    x += step.getXOffset() * .5; z += step.getZOffset() * .5;
                    EnumFacing direction = step.getOpposite();
                    GlStateManager.pushMatrix(); GlStateManager.translate(x, .002, z);
                    FastStateRendering.getInstance().renderFast((first ? arrows : lines).get(direction), state);
                    GlStateManager.popMatrix(); first = false;
                }
            }
        }
    }
}
