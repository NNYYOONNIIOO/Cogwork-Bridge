package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.elevator.RedstoneContactBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
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
public final class RedstoneContactModels {
    private RedstoneContactModels() { }
    private static IModel model(Block block, String variant) {
        ResourceLocation path = new ResourceLocation("create", "block/" + block.getRegistryName().getPath() + "/block_" + variant);
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load powered contact " + path);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (Block block : new Block[]{AllBlocks.REDSTONE_CONTACT, AllBlocks.ELEVATOR_CONTACT})
            for (ResourceLocation texture : model(block, "powered").getTextures()) event.getMap().registerSprite(texture);
        for (ResourceLocation texture : model(AllBlocks.ELEVATOR_CONTACT, "dim").getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (Block block : new Block[]{AllBlocks.REDSTONE_CONTACT, AllBlocks.ELEVATOR_CONTACT}) for (EnumFacing facing : EnumFacing.VALUES) {
            int x = facing == EnumFacing.UP ? 0 : facing == EnumFacing.DOWN ? 180 : 90;
            int y = facing == EnumFacing.SOUTH ? 180 : facing == EnumFacing.EAST ? 90 : facing == EnumFacing.WEST ? 270 : 0;
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName());
            IBakedModel base = event.getModelRegistry().getObject(key);
            if (base == null) continue;
            IBakedModel powered = model(block, "powered").bake(ModelRotation.getModelRotation(x, y), DefaultVertexFormats.BLOCK,
                    sprite -> atlas.getAtlasSprite(sprite.toString()));
            IBakedModel dim = block == AllBlocks.ELEVATOR_CONTACT
                    ? model(block, "dim").bake(ModelRotation.getModelRotation(x, y), DefaultVertexFormats.BLOCK,
                    sprite -> atlas.getAtlasSprite(sprite.toString())) : base;
            event.getModelRegistry().putObject(key, new Model(base, powered, dim));
        }
    }
    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel powered, dim;
        Model(IBakedModel base, IBakedModel powered, IBakedModel dim) { super(base); this.powered = powered; this.dim = dim; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Boolean active = state instanceof IExtendedBlockState extended ? extended.getValue(RedstoneContactBlock.POWERING) : null;
            Boolean calling = state instanceof IExtendedBlockState extended ? extended.getValue(RedstoneContactBlock.CALLING) : null;
            return (Boolean.TRUE.equals(active) ? powered : Boolean.TRUE.equals(calling) ? dim : originalModel).getQuads(state, side, random);
        }
    }
}
