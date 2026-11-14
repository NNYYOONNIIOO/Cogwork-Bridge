package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.packager.PackagerBlock;
import com.cogwork.cogworkbridge.content.logistics.packager.RepackagerBlock;
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
public final class PackagerModels {
    private PackagerModels() { }
    private static IModel model(String block, boolean vertical, String suffix) {
        String path = "block/" + block + "/block" + (vertical ? "_vertical" : "") + suffix;
        return ModelLoaderRegistry.getModelOrLogError(new ResourceLocation("create", path), "Could not load packager state " + path);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (boolean vertical : new boolean[]{false, true}) for (String block : new String[]{"packager", "repackager"}) {
            for (ResourceLocation texture : model(block, vertical, "_powered").getTextures()) event.getMap().registerSprite(texture);
            if (block.equals("packager")) for (ResourceLocation texture : model(block, vertical, "_linked").getTextures()) event.getMap().registerSprite(texture);
        }
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        var atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (PackagerBlock block : new PackagerBlock[]{AllBlocks.PACKAGER, AllBlocks.REPACKAGER}) for (EnumFacing facing : EnumFacing.VALUES) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName());
            IBakedModel base = event.getModelRegistry().getObject(key); if (base == null) continue;
            boolean vertical = facing.getAxis() == EnumFacing.Axis.Y;
            int y = facing == EnumFacing.WEST ? 90 : facing == EnumFacing.NORTH ? 180 : facing == EnumFacing.EAST ? 270 : 0;
            String name = block instanceof RepackagerBlock ? "repackager" : "packager";
            IBakedModel powered = model(name, vertical, "_powered").bake(ModelRotation.getModelRotation(0, y), DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            IBakedModel linked = block instanceof RepackagerBlock ? powered : model(name, vertical, "_linked").bake(ModelRotation.getModelRotation(0, y), DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new BakedModelWrapper<IBakedModel>(base) {
                @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
                    Integer value = state instanceof IExtendedBlockState extended ? extended.getValue(PackagerBlock.STATUS) : null;
                    return (value == null || value == 0 ? originalModel : value == 1 ? powered : linked).getQuads(state, side, random);
                }
            });
        }
    }
}
