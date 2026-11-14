package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.piston.MechanicalPistonBlock;
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
import java.util.List;

/** Selects the upstream extended and moving housings without storing state in metadata. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class PistonModels {
    private PistonModels() { }
    private static IModel model(String state, boolean rotated) {
        ResourceLocation path = new ResourceLocation("create", "block/mechanical_piston/" + state + "/block" + (rotated ? "_rotated" : ""));
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load piston model " + path);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String state : new String[]{"extended", "moving"}) for (boolean rotated : new boolean[]{false, true})
            for (ResourceLocation texture : model(state, rotated).getTextures()) event.getMap().registerSprite(texture);
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (Block block : new Block[]{AllBlocks.MECHANICAL_PISTON, AllBlocks.STICKY_MECHANICAL_PISTON}) {
            for (EnumFacing facing : EnumFacing.values()) for (boolean first : new boolean[]{false, true}) {
                String variant = "axis_along_first=" + first + ",facing=" + facing.getName();
                ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), variant);
                IBakedModel retracted = event.getModelRegistry().getObject(key);
                if (retracted == null) continue;
                boolean rotated = first ^ (facing.getAxis() == EnumFacing.Axis.X);
                int x = facing == EnumFacing.DOWN ? 180 : facing == EnumFacing.UP ? 0 : 90;
                int y = switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0; };
                ModelRotation rotation = ModelRotation.getModelRotation(x, y);
                IBakedModel extended = model("extended", rotated).bake(rotation, DefaultVertexFormats.BLOCK,
                        sprite -> atlas.getAtlasSprite(sprite.toString()));
                IBakedModel moving = model("moving", rotated).bake(rotation, DefaultVertexFormats.BLOCK,
                        sprite -> atlas.getAtlasSprite(sprite.toString()));
                event.getModelRegistry().putObject(key, new PistonModel(retracted, extended, moving));
            }
        }
    }
    private static final class PistonModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel extended, moving;
        private PistonModel(IBakedModel retracted, IBakedModel extended, IBakedModel moving) {
            super(retracted); this.extended = extended; this.moving = moving;
        }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            Integer visual = state instanceof IExtendedBlockState s ? s.getValue(MechanicalPistonBlock.VISUAL_STATE) : null;
            return (visual == null || visual == 0 ? originalModel : visual == 1 ? extended : moving).getQuads(state, side, random);
        }
    }
}
