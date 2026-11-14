package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.LatchBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
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
public final class LatchModels {
    private LatchModels() {}
    private static String path(boolean toggle, int index) {
        if (!toggle) return index % 2 == 0 ? "block/powered_latch" : "block/powered_latch_powered";
        return switch (index) {
            case 1 -> "block/diodes/latch_on";
            case 2 -> "block/powered_toggle_latch_off_powered";
            case 3 -> "block/powered_toggle_latch_on_powered";
            default -> "block/diodes/latch_off";
        };
    }
    private static IModel model(boolean toggle, int index) { return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", path(toggle, index)), "Could not load latch model"); }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (boolean toggle : new boolean[]{false, true}) for (int i = 0; i < 4; i++)
            for (ResourceLocation texture : model(toggle, i).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (LatchBlock block : new LatchBlock[]{AllBlocks.POWERED_LATCH, AllBlocks.POWERED_TOGGLE_LATCH}) for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName());
            IBakedModel base = event.getModelRegistry().getObject(key);
            if (base == null) continue;
            int angle = facing == EnumFacing.SOUTH ? 0 : facing == EnumFacing.WEST ? 90 : facing == EnumFacing.NORTH ? 180 : 270;
            IBakedModel[] models = new IBakedModel[4];
            for (int i = 0; i < 4; i++) models[i] = model(block.isToggle(), i).bake(ModelRotation.getModelRotation(0, angle), DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new BakedModelWrapper<IBakedModel>(base) {
                @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
                    Integer index = state instanceof IExtendedBlockState extended ? extended.getValue(LatchBlock.MODEL) : null;
                    return models[index == null ? 0 : index].getQuads(state, side, rand);
                }
            });
        }
    }
}
