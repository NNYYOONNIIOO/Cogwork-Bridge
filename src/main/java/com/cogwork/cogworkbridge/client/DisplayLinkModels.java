package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.display.DisplayLinkBlock;
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

/** Selects the powered model using synced tile data, keeping metadata for facing. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class DisplayLinkModels {
    private DisplayLinkModels() { }
    private static IModel poweredModel() {
        ResourceLocation location = new ResourceLocation("create", "block/display_link/block_powered");
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load display link powered model " + location);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation texture : poweredModel().getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : EnumFacing.VALUES) {
            int x = facing == EnumFacing.UP ? 0 : facing == EnumFacing.DOWN ? 180 : 90;
            int y = facing == EnumFacing.SOUTH ? 180 : facing == EnumFacing.EAST ? 90
                    : facing == EnumFacing.WEST ? 270 : 0;
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.DISPLAY_LINK.getRegistryName(),
                    "facing=" + facing.getName());
            IBakedModel base = event.getModelRegistry().getObject(key);
            if (base == null) continue;
            IBakedModel powered = poweredModel().bake(ModelRotation.getModelRotation(x, y),
                    DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            event.getModelRegistry().putObject(key, new Model(base, powered));
        }
    }
    private static final class Model extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel powered;
        Model(IBakedModel original, IBakedModel powered) { super(original); this.powered = powered; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Boolean active = state instanceof IExtendedBlockState extended ? extended.getValue(DisplayLinkBlock.POWERED) : null;
            return (Boolean.TRUE.equals(active) ? powered : originalModel).getQuads(state, side, random);
        }
    }
}
