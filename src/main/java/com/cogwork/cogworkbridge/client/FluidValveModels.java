package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.fluids.FluidValveBlock;
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
public final class FluidValveModels {
    private static final ResourceLocation HORIZONTAL = new ResourceLocation("create", "block/fluid_valve/block_horizontal_open");
    private static final ResourceLocation VERTICAL = new ResourceLocation("create", "block/fluid_valve/block_vertical_open");

    private FluidValveModels() { }

    private static IModel model(ResourceLocation location) {
        return ModelLoaderRegistry.getModelOrLogError(location, "Could not load fluid valve model " + location);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (ResourceLocation location : new ResourceLocation[]{HORIZONTAL, VERTICAL}) {
            for (ResourceLocation texture : model(location).getTextures()) event.getMap().registerSprite(texture);
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : EnumFacing.values()) {
            for (boolean alongFirst : new boolean[]{false, true}) {
                String variant = "axis_along_first=" + alongFirst + ",facing=" + facing.getName();
                ModelResourceLocation key = new ModelResourceLocation(AllBlocks.FLUID_VALVE.getRegistryName(), variant);
                IBakedModel closed = event.getModelRegistry().getObject(key);
                if (closed == null) continue;
                boolean vertical = facing.getAxis() == EnumFacing.Axis.X ? alongFirst
                        : facing.getAxis() == EnumFacing.Axis.Z && !alongFirst;
                int x = facing == EnumFacing.DOWN ? 270 : facing == EnumFacing.UP ? 90 : 0;
                int y = switch (facing) {
                    case EAST -> 270;
                    case NORTH -> 180;
                    case WEST -> 90;
                    case UP, DOWN -> alongFirst ? 0 : 90;
                    default -> 0;
                };
                IBakedModel open = model(vertical ? VERTICAL : HORIZONTAL).bake(
                        ModelRotation.getModelRotation(x, y), DefaultVertexFormats.BLOCK,
                        sprite -> atlas.getAtlasSprite(sprite.toString()));
                event.getModelRegistry().putObject(key, new ValveModel(closed, open));
            }
        }
    }

    private static final class ValveModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel open;

        private ValveModel(IBakedModel closed, IBakedModel open) {
            super(closed);
            this.open = open;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            return state instanceof IExtendedBlockState extended
                    && Boolean.TRUE.equals(extended.getValue(FluidValveBlock.OPEN))
                    ? open.getQuads(state, side, random) : originalModel.getQuads(state, side, random);
        }
    }
}
