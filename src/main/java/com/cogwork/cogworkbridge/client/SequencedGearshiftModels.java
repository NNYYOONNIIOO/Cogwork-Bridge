package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.sequencer.SequencedGearshiftBlock;
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
public final class SequencedGearshiftModels {
    private SequencedGearshiftModels() {
    }

    private static IModel load(int stage) {
        ResourceLocation name = new ResourceLocation("create", "block/sequenced_gearshift/seq_" + stage);
        return ModelLoaderRegistry.getModelOrLogError(name, "Could not load sequenced gearshift model " + name);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (int stage = 1; stage <= 5; stage++) {
            for (ResourceLocation texture : load(stage).getTextures()) {
                event.getMap().registerSprite(texture);
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing.Axis axis : new EnumFacing.Axis[]{EnumFacing.Axis.X, EnumFacing.Axis.Z}) {
            for (boolean vertical : new boolean[]{false, true}) {
                ModelResourceLocation key = new ModelResourceLocation(AllBlocks.SEQUENCED_GEARSHIFT.getRegistryName(),
                        "horizontal_axis=" + axis.getName() + ",vertical=" + vertical);
                IBakedModel base = event.getModelRegistry().getObject(key);
                if (base == null) {
                    CogworkBridge.LOGGER.error("Missing sequenced gearshift model {}", key);
                    continue;
                }
                ModelRotation rotation = ModelRotation.getModelRotation(vertical ? 90 : 0,
                        axis == EnumFacing.Axis.X ? 90 : 0);
                IBakedModel[] stages = new IBakedModel[6];
                stages[0] = base;
                for (int stage = 1; stage <= 5; stage++) {
                    stages[stage] = load(stage).bake(rotation, DefaultVertexFormats.BLOCK,
                            texture -> atlas.getAtlasSprite(texture.toString()));
                }
                event.getModelRegistry().putObject(key, new StageModel(base, stages));
            }
        }
    }

    private static final class StageModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel[] stages;

        private StageModel(IBakedModel original, IBakedModel[] stages) {
            super(original);
            this.stages = stages;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Integer stage = state instanceof IExtendedBlockState extended
                    ? extended.getValue(SequencedGearshiftBlock.STAGE) : null;
            int index = stage == null ? 0 : Math.max(0, Math.min(5, stage));
            return stages[index].getQuads(state, side, random);
        }
    }
}
