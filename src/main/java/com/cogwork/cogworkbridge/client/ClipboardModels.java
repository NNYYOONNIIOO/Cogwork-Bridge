package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
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
public final class ClipboardModels {
    private ClipboardModels() { }

    private static IModel writtenModel() {
        ResourceLocation path = new ResourceLocation("create", "block/clipboard/block_written");
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load clipboard model " + path);
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : writtenModel().getTextures()) event.getMap().registerSprite(texture);
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : EnumFacing.VALUES) {
            int x = facing == EnumFacing.UP ? 0 : facing == EnumFacing.DOWN ? 180 : 90;
            int y = facing == EnumFacing.EAST ? 90 : facing == EnumFacing.SOUTH ? 180
                    : facing == EnumFacing.WEST ? 270 : 0;
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.CLIPBOARD.getRegistryName(),
                    "facing=" + facing.getName());
            IBakedModel empty = event.getModelRegistry().getObject(key);
            if (empty == null) continue;
            IBakedModel written = writtenModel().bake(ModelRotation.getModelRotation(x, y),
                    DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new Model(empty, written));
        }
    }

    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel written;
        private Model(IBakedModel empty, IBakedModel written) { super(empty); this.written = written; }

        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Boolean hasContent = state instanceof IExtendedBlockState
                    ? ((IExtendedBlockState) state).getValue(ClipboardBlock.WRITTEN) : null;
            return (Boolean.TRUE.equals(hasContent) ? written : originalModel).getQuads(state, side, random);
        }
    }
}
