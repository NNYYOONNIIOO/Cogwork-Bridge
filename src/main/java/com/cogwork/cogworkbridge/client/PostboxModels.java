package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.port.PackagePortBlock;
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
public final class PostboxModels {
    private PostboxModels() { }
    private static IModel openModel(PackagePortBlock block) {
        String name = block.getRegistryName().getPath();
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", "block/" + name + "_open"), "Could not load open postbox " + name);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (PackagePortBlock block : AllBlocks.POSTBOXES)
            for (ResourceLocation texture : openModel(block).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (PackagePortBlock block : AllBlocks.POSTBOXES) for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName());
            IBakedModel closed = event.getModelRegistry().getObject(key); if (closed == null) continue;
            int rotation = switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
            IBakedModel open = openModel(block).bake(ModelRotation.getModelRotation(0, rotation), DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new BakedModelWrapper<IBakedModel>(closed) {
                @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
                    Boolean value = state instanceof IExtendedBlockState extended ? extended.getValue(PackagePortBlock.OPEN) : null;
                    return (Boolean.TRUE.equals(value) ? open : originalModel).getQuads(state, side, random);
                }
            });
        }
    }
}
