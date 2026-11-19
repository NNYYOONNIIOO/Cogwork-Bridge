package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.client.model.OcclusionGeometry;
import com.cogwork.cogworkbridge.content.kinetics.encased.EncasedCogwheelBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.List;

/** Selects a baked housing matching the two configured shaft ports. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class EncasedCogModels {
    private static IBakedModel[][][] smallWheels = new IBakedModel[2][3][4];

    private EncasedCogModels() {
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        smallWheels = new IBakedModel[2][3][4];
        Block[] blocks = {AllBlocks.COGWHEEL_ENCASED_ANDESITE, AllBlocks.COGWHEEL_ENCASED_BRASS,
                AllBlocks.LARGE_COGWHEEL_ENCASED_ANDESITE, AllBlocks.LARGE_COGWHEEL_ENCASED_BRASS};
        for (int family = 0; family < blocks.length; family++) {
            for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
                ModelResourceLocation baseKey = new ModelResourceLocation(
                        blocks[family].getRegistryName(), "axis=" + axis.getName());
                IBakedModel base = event.getModelRegistry().getObject(baseKey);
                IBakedModel[] variants = new IBakedModel[4];
                variants[0] = base;
                String facing = axis == EnumFacing.Axis.X ? "east"
                        : axis == EnumFacing.Axis.Y ? "up" : "south";
                boolean complete = base != null;
                for (int mask = 1; mask < variants.length; mask++) {
                    ModelResourceLocation key = new ModelResourceLocation(
                            AllBlocks.encasedCogModel(family, mask).getRegistryName(), "facing=" + facing);
                    variants[mask] = event.getModelRegistry().getObject(key);
                    complete &= variants[mask] != null;
                }
                if (complete) {
                    event.getModelRegistry().putObject(baseKey, new PortModel(base, variants));
                    if (family < 2) {
                        IBakedModel wheel = event.getModelRegistry().getObject(new ModelResourceLocation(
                                AllBlocks.COGWHEEL_SHAFTLESS.getRegistryName(), "facing=" + facing));
                        if (wheel != null) {
                            for (int mask = 0; mask < variants.length; mask++) {
                                smallWheels[family][axis.ordinal()][mask] = OcclusionGeometry.encasedCog(wheel, axis, variants[mask]);
                            }
                        }
                    }
                } else {
                    CogworkBridge.LOGGER.error("Missing encased cog model for {}", baseKey);
                }
            }
        }
    }

    @Nullable
    public static IBakedModel smallWheel(IBlockState state, EnumFacing.Axis axis, int openPorts) {
        int family = state.getBlock() == AllBlocks.COGWHEEL_ENCASED_ANDESITE ? 0 : 1;
        return smallWheels[family][axis.ordinal()][openPorts];
    }

    private static final class PortModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel[] variants;

        private PortModel(IBakedModel original, IBakedModel[] variants) {
            super(original);
            this.variants = variants;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Integer value = state instanceof IExtendedBlockState extended
                    ? extended.getValue(EncasedCogwheelBlock.OPEN_PORTS) : null;
            int mask = value == null ? 0 : value;
            return variants[mask].getQuads(state, side, random);
        }
    }
}
