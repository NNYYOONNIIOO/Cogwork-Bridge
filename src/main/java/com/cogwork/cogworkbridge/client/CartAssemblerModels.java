package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.mounted.CartAssemblerBlock;
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
public final class CartAssemblerModels {
    private static final String[] RAILS = {"regular", "powered_rail", "detector_rail", "activator_rail", "controller_rail"};
    private CartAssemblerModels() { }
    private static IModel model(int visual) {
        String name = "block_" + RAILS[visual % RAILS.length] + (visual >= RAILS.length ? "_powered" : "");
        ResourceLocation path = new ResourceLocation("create", "block/cart_assembler/" + name);
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load cart assembler model " + path);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (int i = 0; i < 10; i++)
            for (ResourceLocation texture : model(i).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (boolean x : new boolean[]{false, true}) {
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.CART_ASSEMBLER.getRegistryName(),
                    "shape=" + (x ? "east_west" : "north_south"));
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) continue;
            IBakedModel[] variants = new IBakedModel[10];
            for (int i = 0; i < variants.length; i++)
                variants[i] = model(i).bake(ModelRotation.getModelRotation(0, x ? 270 : 0),
                        DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new AssemblerModel(original, variants));
        }
    }
    private static final class AssemblerModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel[] variants;
        private AssemblerModel(IBakedModel original, IBakedModel[] variants) { super(original); this.variants = variants; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Integer visual = state instanceof IExtendedBlockState extended ? extended.getValue(CartAssemblerBlock.VISUAL) : null;
            return variants[visual == null ? 1 : visual].getQuads(state, side, random);
        }
    }
}
