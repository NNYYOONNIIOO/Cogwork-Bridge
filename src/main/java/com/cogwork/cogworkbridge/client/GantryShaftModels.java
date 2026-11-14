package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.gantry.GantryShaftBlock;
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

/** Bakes Create's start/middle/end rack models and their powered textures. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class GantryShaftModels {
    private static final String[] PARTS = {"single", "start", "end", "middle"};
    private static final IBakedModel[][] VARIANTS = new IBakedModel[6][8];
    private GantryShaftModels() { }

    private static IModel model(int part, boolean powered, boolean flipped) {
        String partName = PARTS[part];
        String name = !powered && !flipped ? "gantry_shaft/block_" + partName
                : "gantry_shaft_" + partName + (powered ? "_powered" : "") + (flipped ? "_flipped" : "");
        ResourceLocation path = new ResourceLocation("create", "block/" + name);
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load gantry model " + path);
    }

    @Nullable
    public static IBakedModel variant(EnumFacing facing, int visual) {
        return VARIANTS[facing.getIndex()][Math.max(0, Math.min(7, visual))];
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (int part = 0; part < PARTS.length; part++)
            for (boolean powered : new boolean[]{false, true})
                for (boolean flipped : new boolean[]{false, true})
                    for (ResourceLocation texture : model(part, powered, flipped).getTextures())
                        event.getMap().registerSprite(texture);
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing facing : EnumFacing.VALUES) {
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.GANTRY_SHAFT.getRegistryName(),
                    "facing=" + facing.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            int x = facing == EnumFacing.DOWN ? 180 : facing == EnumFacing.UP ? 0 : 90;
            int y = switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
            boolean flipped = facing.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE;
            IBakedModel[] variants = new IBakedModel[8];
            for (int i = 0; i < variants.length; i++)
                variants[i] = model(i % 4, i >= 4, flipped).bake(ModelRotation.getModelRotation(x, y),
                        DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
            VARIANTS[facing.getIndex()] = variants;
            event.getModelRegistry().putObject(key, new RackModel(original == null ? variants[0] : original, variants));
        }
    }

    private static final class RackModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel[] variants;
        private RackModel(IBakedModel original, IBakedModel[] variants) { super(original); this.variants = variants; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Integer visual = state instanceof IExtendedBlockState extended ? extended.getValue(GantryShaftBlock.VISUAL) : null;
            return variants[visual == null ? 0 : visual].getQuads(state, side, random);
        }
    }
}
