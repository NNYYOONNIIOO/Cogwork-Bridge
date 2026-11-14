package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.kinetics.transmission.SplitShaftBlock;
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

/** Selects the powered housing model without adding a saved block-state property. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SplitShaftModels {

    private SplitShaftModels() {
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        wrap(event, AllBlocks.CLUTCH, AllBlocks.CLUTCH_POWERED_MODEL);
        wrap(event, AllBlocks.GEARSHIFT, AllBlocks.GEARSHIFT_POWERED_MODEL);
    }

    private static void wrap(ModelBakeEvent event, Block block, Block poweredModel) {
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            String axisName = axis.getName();
            String facing = axis == EnumFacing.Axis.X ? "east"
                    : axis == EnumFacing.Axis.Y ? "up" : "south";
            ModelResourceLocation baseKey = new ModelResourceLocation(block.getRegistryName(), "axis=" + axisName);
            ModelResourceLocation poweredKey = new ModelResourceLocation(poweredModel.getRegistryName(), "facing=" + facing);
            IBakedModel base = event.getModelRegistry().getObject(baseKey);
            IBakedModel powered = event.getModelRegistry().getObject(poweredKey);
            if (base == null || powered == null) {
                CogworkBridge.LOGGER.error("Missing split-shaft model {} or {}", baseKey, poweredKey);
                continue;
            }
            event.getModelRegistry().putObject(baseKey, new PoweredModel(base, powered));
        }
    }

    private static final class PoweredModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel poweredModel;

        private PoweredModel(IBakedModel original, IBakedModel poweredModel) {
            super(original);
            this.poweredModel = poweredModel;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            boolean powered = state instanceof IExtendedBlockState extended
                    && Boolean.TRUE.equals(extended.getValue(SplitShaftBlock.POWERED));
            return (powered ? poweredModel : originalModel).getQuads(state, side, random);
        }
    }
}
