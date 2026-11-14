package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.chassis.ChassisBlock;
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
import java.util.ArrayList;
import java.util.List;

/** Selects the upstream chassis models from the tile's sticky faces. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ChassisModels {
    private static final String[] LINEAR_SUFFIX = {"", "_top", "_bottom", "_top_bottom"};
    private ChassisModels() { }

    private static IModel model(String name) {
        ResourceLocation path = new ResourceLocation("create", "block/" + name);
        return ModelLoaderRegistry.getModelOrLogError(path, "Could not load chassis model " + path);
    }

    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        for (String family : new String[]{"linear_chassis", "secondary_linear_chassis"})
            for (String suffix : LINEAR_SUFFIX)
                for (ResourceLocation texture : model(family + suffix).getTextures()) event.getMap().registerSprite(texture);
        for (String part : new String[]{"radial_chassis/base", "radial_chassis_side_x", "radial_chassis_side_x_sticky",
                "radial_chassis_side_y", "radial_chassis_side_y_sticky", "radial_chassis_side_z", "radial_chassis_side_z_sticky"})
            for (ResourceLocation texture : model(part).getTextures()) event.getMap().registerSprite(texture);
    }

    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        install(event, AllBlocks.LINEAR_CHASSIS, "linear_chassis");
        install(event, AllBlocks.SECONDARY_LINEAR_CHASSIS, "secondary_linear_chassis");
        install(event, AllBlocks.RADIAL_CHASSIS, null);
    }

    private static IBakedModel bake(String name, int x, int y, TextureMap atlas) {
        return model(name).bake(ModelRotation.getModelRotation(x, y), DefaultVertexFormats.BLOCK,
                sprite -> atlas.getAtlasSprite(sprite.toString()));
    }

    private static void install(ModelBakeEvent event, Block block, @Nullable String family) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            ModelResourceLocation key = new ModelResourceLocation(block.getRegistryName(), "axis=" + axis.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) continue;
            if (family != null) {
                int x = axis == EnumFacing.Axis.Y ? 0 : 90;
                int y = axis == EnumFacing.Axis.X ? 90 : axis == EnumFacing.Axis.Z ? 180 : 0;
                IBakedModel[] variants = new IBakedModel[4];
                for (int i = 0; i < variants.length; i++) variants[i] = bake(family + LINEAR_SUFFIX[i], x, y, atlas);
                event.getModelRegistry().putObject(key, new LinearModel(original, variants, axis));
            } else {
                IBakedModel base = bake("radial_chassis/base", axis == EnumFacing.Axis.Y ? 0 : 90,
                        axis == EnumFacing.Axis.X ? 90 : 0, atlas);
                IBakedModel[][] sides = new IBakedModel[6][2];
                for (EnumFacing side : EnumFacing.VALUES) {
                    if (side.getAxis() == axis) continue;
                    String name;
                    int x = 0, y = 0;
                    if (axis == EnumFacing.Axis.Y) {
                        name = "radial_chassis_side_y";
                        y = side == EnumFacing.NORTH ? 180 : side == EnumFacing.WEST ? 90 : side == EnumFacing.EAST ? 270 : 0;
                    } else if (axis == EnumFacing.Axis.X) {
                        name = "radial_chassis_side_x";
                        x = side == EnumFacing.DOWN ? 180 : side == EnumFacing.NORTH ? 90 : side == EnumFacing.SOUTH ? 270 : 0;
                    } else if (side.getAxis() == EnumFacing.Axis.Y) {
                        name = "radial_chassis_side_x";
                        x = side == EnumFacing.DOWN ? 180 : 0;
                        y = 90;
                    } else {
                        name = "radial_chassis_side_z";
                        y = side == EnumFacing.WEST ? 180 : 0;
                    }
                    sides[side.getIndex()][0] = bake(name, x, y, atlas);
                    sides[side.getIndex()][1] = bake(name + "_sticky", x, y, atlas);
                }
                event.getModelRegistry().putObject(key, new RadialModel(original, base, sides));
            }
        }
    }

    private static int sticky(@Nullable IBlockState state) {
        Integer value = state instanceof IExtendedBlockState extended ? extended.getValue(ChassisBlock.STICKY) : null;
        return value == null ? 0 : value;
    }

    private static final class LinearModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel[] variants;
        private final EnumFacing.Axis axis;
        private LinearModel(IBakedModel original, IBakedModel[] variants, EnumFacing.Axis axis) {
            super(original); this.variants = variants; this.axis = axis;
        }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            int mask = sticky(state);
            EnumFacing positive = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis);
            int variant = ((mask & 1 << positive.getIndex()) != 0 ? 1 : 0)
                    | ((mask & 1 << positive.getOpposite().getIndex()) != 0 ? 2 : 0);
            return variants[variant].getQuads(state, side, random);
        }
    }

    private static final class RadialModel extends BakedModelWrapper<IBakedModel> {
        private final IBakedModel base;
        private final IBakedModel[][] sides;
        private RadialModel(IBakedModel original, IBakedModel base, IBakedModel[][] sides) {
            super(original); this.base = base; this.sides = sides;
        }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            List<BakedQuad> result = new ArrayList<>(base.getQuads(state, side, random));
            int mask = sticky(state);
            for (EnumFacing face : EnumFacing.VALUES) {
                IBakedModel[] variants = sides[face.getIndex()];
                if (variants[0] != null) result.addAll(variants[(mask & 1 << face.getIndex()) != 0 ? 1 : 0]
                        .getQuads(state, side, random));
            }
            return result;
        }
    }
}
