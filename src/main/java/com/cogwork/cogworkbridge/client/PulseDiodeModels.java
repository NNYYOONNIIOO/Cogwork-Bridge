package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.PulseDiodeBlock;
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
public final class PulseDiodeModels {
    private PulseDiodeModels() { }
    private static IModel model(PulseDiodeBlock block, int index) {
        String name = block.getRegistryName().getPath();
        String path = index == 0 ? "block/diodes/" + name : "block/" + name + switch (index) {
            case 1 -> "_powered"; case 2 -> "_powering"; default -> "_powered_powering";
        };
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", path), "Could not load diode model");
    }
    private static PulseDiodeBlock[] blocks() { return new PulseDiodeBlock[]{AllBlocks.PULSE_EXTENDER, AllBlocks.PULSE_REPEATER, AllBlocks.PULSE_TIMER}; }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (PulseDiodeBlock block : blocks()) for (int i = 0; i < 4; i++)
            for (ResourceLocation texture : model(block, i).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (PulseDiodeBlock block : blocks()) for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName());
            IBakedModel base = event.getModelRegistry().getObject(key);
            if (base == null) continue;
            int angle = facing == EnumFacing.SOUTH ? 0 : facing == EnumFacing.WEST ? 90 : facing == EnumFacing.NORTH ? 180 : 270;
            IBakedModel[] models = new IBakedModel[4];
            for (int i = 0; i < 4; i++) models[i] = model(block, i).bake(ModelRotation.getModelRotation(0, angle), DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new BakedModelWrapper<IBakedModel>(base) {
                @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
                    Integer index = state instanceof IExtendedBlockState extended ? extended.getValue(PulseDiodeBlock.MODEL) : null;
                    return models[index == null ? 0 : index].getQuads(state, side, rand);
                }
            });
        }
    }
}
