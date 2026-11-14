package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.DeskBellBlock;
import com.cogwork.cogworkbridge.content.redstone.DeskBellTileEntity;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class DeskBellRenderer extends TileEntitySpecialRenderer<DeskBellTileEntity> {
    private static IBakedModel bell, plunger;
    private static IModel model(String name) { return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/desk_bell/" + name), "Could not load desk bell " + name); }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String name : new String[]{"bell", "plunger", "block_powered"})
            for (ResourceLocation texture : model(name).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        bell = model("bell").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
        plunger = model("plunger").bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
        for (EnumFacing facing : EnumFacing.VALUES) {
            int x = facing == EnumFacing.UP ? 0 : facing == EnumFacing.DOWN ? 180 : 90;
            int y = facing == EnumFacing.SOUTH ? 180 : facing == EnumFacing.EAST ? 90 : facing == EnumFacing.WEST ? 270 : 0;
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.DESK_BELL.getRegistryName(), "facing=" + facing.getName());
            IBakedModel base = event.getModelRegistry().getObject(key);
            if (base == null) continue;
            IBakedModel pressed = model("block_powered").bake(ModelRotation.getModelRotation(x, y), DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new BakedModelWrapper<IBakedModel>(base) {
                @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
                    return (state instanceof IExtendedBlockState extended && Boolean.TRUE.equals(extended.getValue(DeskBellBlock.PRESSED)) ? pressed : originalModel).getQuads(state, side, rand);
                }
            });
        }
    }
    @Override public void render(DeskBellTileEntity tile, double x, double y, double z, float partial, int destroy, float alpha) {
        if (tile.getWorld() == null || !tile.isPressed() || bell == null || plunger == null) return;
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof DeskBellBlock)) return;
        EnumFacing facing = state.getValue(DeskBellBlock.FACING);
        float p = tile.animation(partial);
        float plunge = (float) (1 - 4 * Math.pow(Math.max(p - .5F, 0F) - .5F, 2));
        float wobble = (float) (Math.pow(p, 1.25F) * 8F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + .5D, y + .5D, z + .5D);
        GlStateManager.rotate(facing.getAxis().isHorizontal() ? facing.getHorizontalAngle() : 0F, 0, 1, 0);
        GlStateManager.rotate(facing == EnumFacing.UP ? 0F : facing == EnumFacing.DOWN ? 180F : 90F, 1, 0, 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(-.5D, -.5D - plunge * .75D / 16D, -.5D);
        FastStateRendering.getInstance().renderFast(plunger, state);
        GlStateManager.popMatrix();
        GlStateManager.translate(0, -1D / 16D, 0);
        GlStateManager.rotate((float) (wobble * Math.sin(p * Math.PI * 4)), 1, 0, 0);
        GlStateManager.rotate((float) (wobble * Math.cos(p * Math.PI * 4)), 0, 0, 1);
        GlStateManager.translate(-.5D, -.5D + 1D / 16D, -.5D);
        FastStateRendering.getInstance().renderFast(bell, state);
        GlStateManager.popMatrix();
    }
}
