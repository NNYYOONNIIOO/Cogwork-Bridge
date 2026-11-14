package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.saw.MechanicalSawBlock;
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
public final class MechanicalSawModels {
    private MechanicalSawModels() { }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        ResourceLocation location = new ResourceLocation("create", "block/mechanical_saw/vertical");
        IModel model = ModelLoaderRegistry.getModelOrLogError(location, "Could not load vertical saw model " + location);
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN}) {
            for (EnumFacing.Axis axis : new EnumFacing.Axis[]{EnumFacing.Axis.X, EnumFacing.Axis.Z}) {
                ModelResourceLocation key = new ModelResourceLocation(AllBlocks.MECHANICAL_SAW.getRegistryName(),
                        "facing=" + facing.getName() + ",shaft_axis=" + axis.getName());
                IBakedModel original = event.getModelRegistry().getObject(key);
                if (original == null) continue;
                int x = facing == EnumFacing.DOWN ? 180 : 0;
                int y = ((axis == EnumFacing.Axis.X ? 270 : 0) + 180) % 360;
                IBakedModel flipped = model.bake(ModelRotation.getModelRotation(x, y), DefaultVertexFormats.BLOCK,
                        sprite -> atlas.getAtlasSprite(sprite.toString()));
                event.getModelRegistry().putObject(key, new FlippedModel(original, flipped));
            }
        }
    }

    private static final class FlippedModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel flipped;

        private FlippedModel(IBakedModel original, IBakedModel flipped) {
            super(original);
            this.flipped = flipped;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            boolean isFlipped = state instanceof IExtendedBlockState extended
                    && Boolean.TRUE.equals(extended.getValue(MechanicalSawBlock.FLIPPED));
            return (isFlipped ? flipped : originalModel).getQuads(state, side, random);
        }
    }
}
