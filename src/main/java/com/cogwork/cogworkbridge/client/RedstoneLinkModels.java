package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.redstone.RedstoneLinkBlock;
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

/** Selects the upstream transmitter/receiver and powered model without using metadata. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class RedstoneLinkModels {
    private RedstoneLinkModels() { }
    private static IModel model(boolean receiver, boolean wall, boolean powered) {
        String name = (receiver ? "receiver" : "transmitter") + (wall ? "_vertical" : "")
                + (powered ? "_powered" : "");
        ResourceLocation path = new ResourceLocation("create", "block/redstone_link/" + name);
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load wireless link model " + path);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (boolean receiver : new boolean[]{false, true}) for (boolean wall : new boolean[]{false, true})
            for (boolean powered : new boolean[]{false, true})
                for (ResourceLocation texture : model(receiver, wall, powered).getTextures())
                    event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : EnumFacing.VALUES) {
            boolean wall = facing.getAxis().isHorizontal();
            int x = facing == EnumFacing.UP ? 0 : facing == EnumFacing.DOWN ? 180 : 270;
            int y = wall ? (int) facing.getHorizontalAngle() : 180;
            ModelRotation rotation = ModelRotation.getModelRotation(x, y);
            IBakedModel[] variants = new IBakedModel[4];
            for (int i = 0; i < variants.length; i++)
                variants[i] = model((i & 2) != 0, wall, (i & 1) != 0).bake(rotation,
                        DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.REDSTONE_LINK.getRegistryName(),
                    "facing=" + facing.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original != null) event.getModelRegistry().putObject(key, new Model(original, variants));
        }
    }
    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel[] variants;
        private Model(IBakedModel original, IBakedModel[] variants) { super(original); this.variants = variants; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Integer model = state instanceof IExtendedBlockState extended
                    ? extended.getValue(RedstoneLinkBlock.MODEL) : null;
            return (model == null ? originalModel : variants[model]).getQuads(state, side, random);
        }
    }
}
