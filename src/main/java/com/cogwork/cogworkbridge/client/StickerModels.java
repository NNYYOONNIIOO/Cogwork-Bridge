package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.chassis.StickerBlock;
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
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import javax.annotation.Nullable;
import java.util.List;

/** Bakes the upstream sticker head as a standalone model for its TESR. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class StickerModels {
    private static IBakedModel head;

    private StickerModels() { }

    private static IModel model(String path) {
        ResourceLocation location = new ResourceLocation("create", "block/" + path);
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load sticker model " + location);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : model("sticker/head").getTextures())
            event.getMap().registerSprite(texture);
        for (ResourceLocation texture : model("sticker/block_powered").getTextures())
            event.getMap().registerSprite(texture);
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        head = model("sticker/head").bake(ModelRotation.X0_Y0,
                DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
        for (EnumFacing facing : EnumFacing.VALUES) {
            int x = facing == EnumFacing.UP ? 0 : facing == EnumFacing.DOWN ? 180 : 90;
            int y = facing == EnumFacing.SOUTH ? 180 : facing == EnumFacing.EAST ? 90
                    : facing == EnumFacing.WEST ? 270 : 0;
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.STICKER.getRegistryName(),
                    "facing=" + facing.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) continue;
            IBakedModel powered = model("sticker/block_powered").bake(ModelRotation.getModelRotation(x, y),
                    DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new StickerModel(original, powered));
        }
    }

    public static IBakedModel headModel() {
        return head;
    }

    private static final class StickerModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel powered;
        private StickerModel(IBakedModel original, IBakedModel powered) {
            super(original);
            this.powered = powered;
        }
        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Boolean active = state instanceof IExtendedBlockState
                    ? ((IExtendedBlockState) state).getValue(StickerBlock.POWERED) : null;
            return (Boolean.TRUE.equals(active) ? powered : originalModel).getQuads(state, side, random);
        }
    }
}
