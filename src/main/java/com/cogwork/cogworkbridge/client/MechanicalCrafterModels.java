package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.EnumMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class MechanicalCrafterModels {
    public enum Part {
        ARROW("arrow"), BELT_FRAME("belt"), BELT("belt_animated"), LID("lid");

        private final ResourceLocation location;

        Part(String path) {
            location = new ResourceLocation("create", "block/mechanical_crafter/" + path);
        }
    }

    private static final Map<Part, IBakedModel> MODELS = new EnumMap<>(Part.class);

    private MechanicalCrafterModels() { }

    private static IModel load(Part part) {
        return ModelLoaderRegistry.getModelOrLogError(part.location,
                "Could not load mechanical crafter model " + part.location);
    }

    @SubscribeEvent
    public static void stitch(TextureStitchEvent.Pre event) {
        for (Part part : Part.values()) {
            for (ResourceLocation texture : load(part).getTextures()) {
                event.getMap().registerSprite(texture);
            }
        }
    }

    @SubscribeEvent
    public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        MODELS.clear();
        for (Part part : Part.values()) {
            MODELS.put(part, load(part).bake(ModelRotation.X0_Y0, DefaultVertexFormats.ITEM,
                    sprite -> atlas.getAtlasSprite(sprite.toString())));
        }
    }

    public static IBakedModel get(Part part) {
        return MODELS.get(part);
    }

    public static int pointingRotation(MechanicalCrafterDirection pointing) {
        return switch (pointing) {
            case LEFT -> 90;
            case UP -> 0;
            case RIGHT -> 270;
            case DOWN -> 180;
        };
    }

    public static int facingRotation(EnumFacing facing) {
        EnumFacing horizontal = facing.getAxis() == EnumFacing.Axis.Y ? EnumFacing.NORTH : facing;
        return Math.floorMod(90 - (int) horizontal.getHorizontalAngle(), 360);
    }
}
