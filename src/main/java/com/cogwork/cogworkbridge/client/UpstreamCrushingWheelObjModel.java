package com.cogwork.cogworkbridge.client;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.client.model.obj.OBJModel;
import net.minecraftforge.common.model.IModelState;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

/** Bakes the player's Create crushing-wheel OBJ for block and item rendering. */
final class UpstreamCrushingWheelObjModel implements IModel {
    private final IModel model;
    private final Collection<String> groups;
    private final ItemCameraTransforms itemTransforms;

    private UpstreamCrushingWheelObjModel(IModel model, Collection<String> groups,
                                          ItemCameraTransforms itemTransforms) {
        this.model = model;
        this.groups = groups;
        this.itemTransforms = itemTransforms;
    }

    private static String normalized(ResourceLocation location) {
        String path = location.getPath();
        if (path.startsWith("models/")) path = path.substring(7);
        if (path.endsWith(".json")) path = path.substring(0, path.length() - 5);
        return path;
    }

    static boolean accepts(ResourceLocation location) {
        if (!"create".equals(location.getNamespace())) return false;
        String path = normalized(location);
        return path.equals("block/crushing_wheel/block") || path.equals("block/crushing_wheel/item")
                || path.equals("item/crushing_wheel") || path.equals("crushing_wheel");
    }

    static IModel load(IResourceManager resources, ResourceLocation location) throws Exception {
        String base = "models/block/crushing_wheel/";
        OBJModel parsed;
        try (IResource resource = resources.getResource(new ResourceLocation("create", base + "crushing_wheel.obj"))) {
            parsed = new OBJModel.Parser(resource, resources).parse();
        }
        IModel model = parsed.process(ImmutableMap.of("flip-v", "true")).retexture(ImmutableMap.of(
                "#axis", "create:block/axis", "#axis_top", "create:block/axis_top",
                "#plates", "create:block/crushing_wheel_plates",
                "#insert", "create:block/crushing_wheel_insert",
                "#spruce_log_top", "minecraft:blocks/log_spruce_top"));
        ItemCameraTransforms transforms = null;
        String path = normalized(location);
        if (!path.equals("block/crushing_wheel/block")) {
            transforms = UpstreamBlazeBurnerObjModel.loadItemTransforms(resources,
                    new ResourceLocation("create", "item/crushing_wheel"));
        }
        return new UpstreamCrushingWheelObjModel(model, parsed.getMatLib().getGroups().keySet(), transforms);
    }

    @Override public Collection<ResourceLocation> getTextures() { return model.getTextures(); }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> textures) {
        OBJModel.OBJState objState = new OBJModel.OBJState(Collections.emptyList(), true, state);
        for (String group : groups) objState.getVisibilityMap().put(group, true);
        IBakedModel baked = model.bake(objState, format, textures);
        return itemTransforms == null ? baked
                : new PerspectiveMapWrapper(baked, PerspectiveMapWrapper.getTransforms(itemTransforms));
    }
}
