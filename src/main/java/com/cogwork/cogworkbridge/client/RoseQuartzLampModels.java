package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.RoseQuartzLampBlock;
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
public final class RoseQuartzLampModels {
    private RoseQuartzLampModels() {}
    private static IModel litModel() {
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/rose_quartz_lamp_powered"), "Could not load rose quartz lamp");
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : litModel().getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        ModelResourceLocation key = new ModelResourceLocation(AllBlocks.ROSE_QUARTZ_LAMP.getRegistryName(), "normal");
        IBakedModel base = event.getModelRegistry().getObject(key);
        if (base == null) return;
        IBakedModel lit = litModel().bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                sprite -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(sprite.toString()));
        event.getModelRegistry().putObject(key, new BakedModelWrapper<IBakedModel>(base) {
            @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
                return (state instanceof IExtendedBlockState extended && Boolean.TRUE.equals(extended.getValue(RoseQuartzLampBlock.LIT))
                        ? lit : originalModel).getQuads(state, side, rand);
            }
        });
    }
}
