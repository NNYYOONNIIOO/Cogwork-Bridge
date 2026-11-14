package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.decoration.palette.PaletteDoor;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SlidingDoorModels {
    private SlidingDoorModels() { }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        for (ResourceLocation key : new ArrayList<>(event.getModelRegistry().getKeys())) {
            if (!(key instanceof ModelResourceLocation model) || model.getVariant().equals("inventory") || !Reference.MOD_ID.equals(model.getNamespace())) continue;
            Block block = BlockFamily.get(model.getPath());
            if (!(block instanceof PaletteDoor)) continue;
            IBakedModel original = event.getModelRegistry().getObject(model);
            if (original != null) event.getModelRegistry().putObject(model, new DoorModel(original));
        }
    }
    private static class DoorModel extends BakedModelWrapper<IBakedModel> {
        DoorModel(IBakedModel original) { super(original); }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            if (state instanceof IExtendedBlockState extended && Boolean.TRUE.equals(extended.getValue(PaletteDoor.SPECIAL))) return Collections.emptyList();
            return originalModel.getQuads(state, side, random);
        }
    }
}
