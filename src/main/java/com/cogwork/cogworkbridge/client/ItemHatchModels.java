package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.hatch.ItemHatchBlock;
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
public final class ItemHatchModels {
    private ItemHatchModels() { }
    private static IModel model(boolean open) {
        ResourceLocation id = new ResourceLocation("create", "block/item_hatch/block_" + (open ? "open" : "closed"));
        return ModelLoaderRegistry.getModelOrLogError(id, "Could not load item hatch model " + id);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (boolean open : new boolean[]{false, true}) for (ResourceLocation texture : model(open).getTextures())
            event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.ITEM_HATCH.getRegistryName(), "facing=" + facing.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) continue;
            ModelRotation rotation = switch (facing) {
                case SOUTH -> ModelRotation.X0_Y180; case EAST -> ModelRotation.X0_Y90;
                case WEST -> ModelRotation.X0_Y270; default -> ModelRotation.X0_Y0;
            };
            IBakedModel opened = model(true).bake(rotation, DefaultVertexFormats.BLOCK,
                    texture -> atlas.getAtlasSprite(texture.toString()));
            event.getModelRegistry().putObject(key, new HatchModel(original, opened));
        }
    }
    private static final class HatchModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel open;
        private HatchModel(IBakedModel original, IBakedModel open) { super(original); this.open = open; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            return state instanceof IExtendedBlockState extended && Boolean.TRUE.equals(extended.getValue(ItemHatchBlock.OPEN))
                    ? open.getQuads(state, side, random) : originalModel.getQuads(state, side, random);
        }
    }
}
