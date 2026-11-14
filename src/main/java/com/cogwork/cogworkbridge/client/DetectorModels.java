package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.DetectorBlock;
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
public final class DetectorModels {
    private DetectorModels() { }
    private static IModel model(boolean threshold, DetectorBlock.Target target, int level) {
        String path = threshold ? "block/threshold_switch/block_" + target.getName() + "_" + level : "block/content_observer/block" + (level == 0 ? "" : "_powered");
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", path), "Could not load detector " + path);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (DetectorBlock.Target target : DetectorBlock.Target.values()) {
            for (int i = 0; i <= 5; i++) for (ResourceLocation texture : model(true, target, i).getTextures()) event.getMap().registerSprite(texture);
            for (int i = 0; i < 2; i++) for (ResourceLocation texture : model(false, target, i).getTextures()) event.getMap().registerSprite(texture);
        }
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (DetectorBlock block : new DetectorBlock[]{AllBlocks.CONTENT_OBSERVER, AllBlocks.STOCKPILE_SWITCH})
            for (DetectorBlock.Target target : DetectorBlock.Target.values()) for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
                ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName() + ",target=" + target.getName());
                IBakedModel base = event.getModelRegistry().getObject(key); if (base == null) continue;
                int y = facing == EnumFacing.NORTH ? 0 : facing == EnumFacing.EAST ? 90 : facing == EnumFacing.SOUTH ? 180 : 270;
                int x = block.isThreshold() ? 0 : target == DetectorBlock.Target.CEILING ? 270 : target == DetectorBlock.Target.FLOOR ? 90 : 0;
                int count = block.isThreshold() ? 6 : 2; IBakedModel[] models = new IBakedModel[count];
                for (int i = 0; i < count; i++) models[i] = model(block.isThreshold(), target, i).bake(ModelRotation.getModelRotation(x, y), DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
                event.getModelRegistry().putObject(key, new BakedModelWrapper<IBakedModel>(base) {
                    @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
                        Integer value = state instanceof IExtendedBlockState extended ? extended.getValue(DetectorBlock.LEVEL) : null;
                        int index = value == null ? 0 : Math.min(count - 1, value); return models[index].getQuads(state, side, rand);
                    }
                });
            }
    }
}
