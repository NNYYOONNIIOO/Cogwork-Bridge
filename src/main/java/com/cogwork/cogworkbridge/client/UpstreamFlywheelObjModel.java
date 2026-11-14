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

/** Bakes the player's Create flywheel OBJ for block and item rendering. */
final class UpstreamFlywheelObjModel implements IModel {
    private final IModel model;
    private final Collection<String> groups;
    private final ItemCameraTransforms itemTransforms;

    private UpstreamFlywheelObjModel(IModel model, Collection<String> groups,
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
        return path.equals("block/flywheel/block") || path.equals("block/flywheel/item")
                || path.equals("item/flywheel") || path.equals("flywheel");
    }

    static IModel load(IResourceManager resources, ResourceLocation location) throws Exception {
        String base = "models/block/flywheel/";
        OBJModel parsed;
        try (IResource resource = resources.getResource(new ResourceLocation("create", base + (normalized(location).equals("block/flywheel/block") ? "flywheel_shaftless.obj" : "flywheel.obj")))) {
            parsed = new OBJModel.Parser(resource, resources).parse();
        }
        IModel model = parsed.process(ImmutableMap.of("flip-v", "true")).retexture(ImmutableMap.of(
                "#0", "create:block/flywheel", "#1", "create:block/axis", "#2", "create:block/axis_top"));
        ItemCameraTransforms transforms = null;
        String path = normalized(location);
        if (!path.equals("block/flywheel/block")) {
            transforms = UpstreamBlazeBurnerObjModel.loadItemTransforms(resources,
                    new ResourceLocation("create", "item/flywheel"));
        }
        return new UpstreamFlywheelObjModel(model, parsed.getMatLib().getGroups().keySet(), transforms);
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
