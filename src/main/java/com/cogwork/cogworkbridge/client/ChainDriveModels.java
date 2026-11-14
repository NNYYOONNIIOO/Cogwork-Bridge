package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.chain.ChainDriveBlock;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bakes the user's upstream chain housings in each orientation. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ChainDriveModels {
    private static final String[] SUFFIXES = {
            "single", "end_horizontal", "middle_horizontal", "end_vertical", "middle_vertical"
    };

    private ChainDriveModels() {
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (boolean adjustable : new boolean[]{false, true}) {
            for (boolean powered : new boolean[]{false, true}) {
                if (powered && !adjustable) {
                    continue;
                }
                for (String suffix : SUFFIXES) {
                    IModel model = load(adjustable, powered, suffix);
                    for (ResourceLocation texture : model.getTextures()) {
                        event.getMap().registerSprite(texture);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        wrap(event, AllBlocks.ENCASED_CHAIN_DRIVE, false);
        wrap(event, AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT, true);
    }

    private static void wrap(ModelBakeEvent event, Block block, boolean adjustable) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing.Axis shaftAxis : EnumFacing.Axis.values()) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(),
                    "axis=" + shaftAxis.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) {
                CogworkBridge.LOGGER.error("Missing chain-drive model {}", key);
                continue;
            }
            Map<Integer, IBakedModel> models = new HashMap<>();
            for (int powered = 0; powered <= (adjustable ? 1 : 0); powered++) {
                models.put(powered, bakeModel(adjustable, powered != 0, "single",
                        rotationFor(EnumFacing.SOUTH, shaftAxis, null, null), atlas));
                for (EnumFacing.Axis chainAxis : EnumFacing.Axis.values()) {
                    if (chainAxis == shaftAxis) {
                        continue;
                    }
                    for (int mask = 1; mask <= 3; mask++) {
                        boolean vertical = shaftAxis == EnumFacing.Axis.Y;
                        String suffix = (mask == 3 ? "middle_" : "end_")
                                + (vertical ? "vertical" : "horizontal");
                        EnumFacing sourceShaft = vertical ? EnumFacing.UP : EnumFacing.EAST;
                        EnumFacing sourceLink = vertical ? EnumFacing.SOUTH : EnumFacing.NORTH;
                        EnumFacing targetLink = EnumFacing.getFacingFromAxis(
                                mask == 1 ? EnumFacing.AxisDirection.NEGATIVE : EnumFacing.AxisDirection.POSITIVE,
                                chainAxis);
                        int modelKey = (chainAxis.ordinal() + 1) * 8 + mask * 2 + powered;
                        models.put(modelKey, bakeModel(adjustable, powered != 0, suffix,
                                rotationFor(sourceShaft, shaftAxis, sourceLink, targetLink), atlas));
                    }
                }
            }
            event.getModelRegistry().putObject(key, new ChainModel(original, models));
        }
    }

    private static IModel load(boolean adjustable, boolean powered, String suffix) {
        String path = adjustable ? "block/adjustable_chain_gearshift_" + suffix + (powered ? "_powered" : "")
                : "block/encased_chain_drive/" + suffix;
        ResourceLocation name = new ResourceLocation("create", path);
        return ModelLoaderRegistry.getModelOrLogError(name, "Could not load chain-drive model " + name);
    }

    private static IBakedModel bakeModel(boolean adjustable, boolean powered, String suffix,
                                         ModelRotation rotation, TextureMap atlas) {
        return load(adjustable, powered, suffix).bake(rotation, DefaultVertexFormats.BLOCK,
                texture -> atlas.getAtlasSprite(texture.toString()));
    }

    private static ModelRotation rotationFor(EnumFacing sourceShaft, EnumFacing.Axis targetShaft,
                                             @Nullable EnumFacing sourceLink, @Nullable EnumFacing targetLink) {
        for (ModelRotation rotation : ModelRotation.values()) {
            if (rotation.rotateFace(sourceShaft).getAxis() == targetShaft
                    && (sourceLink == null || rotation.rotateFace(sourceLink) == targetLink)) {
                return rotation;
            }
        }
        throw new IllegalStateException("No rotation for chain drive " + targetShaft + " / " + targetLink);
    }

    private static final class ChainModel extends BakedModelWrapper<IBakedModel> {
        private final Map<Integer, IBakedModel> models;

        private ChainModel(IBakedModel original, Map<Integer, IBakedModel> models) {
            super(original);
            this.models = models;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Integer model = state instanceof IExtendedBlockState extended
                    ? extended.getValue(ChainDriveBlock.MODEL) : null;
            return models.getOrDefault(model, originalModel).getQuads(state, side, random);
        }
    }
}
