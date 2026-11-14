package com.cogwork.cogworkbridge.client;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.client.model.obj.OBJModel;
import net.minecraftforge.common.model.IModelState;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** Loads the burner geometry from the player's upstream resource pack. */
final class UpstreamBlazeBurnerObjModel implements IModel {
    private final IModel model;
    private final Collection<String> groups;
    private final ItemCameraTransforms itemTransforms;

    private UpstreamBlazeBurnerObjModel(IModel model, Collection<String> groups, ItemCameraTransforms transforms) {
        this.model = model;
        this.groups = groups;
        this.itemTransforms = transforms;
    }

    static boolean accepts(ResourceLocation location) {
        if (!"create".equals(location.getNamespace())) return false;
        String path = normalized(location);
        return path.equals("item/blaze_burner") || path.equals("item/empty_blaze_burner")
                || path.equals("block/blaze_burner/block")
                || path.equals("block/blaze_burner/block_with_blaze")
                || path.equals("block/blaze_burner/block_with_fire")
                || path.equals("block/blaze_burner/block_with_soul_fire")
                || path.equals("block/blaze_burner/rods_small")
                || path.equals("block/blaze_burner/rods_large")
                || path.equals("block/blaze_burner/superheated_rods_small")
                || path.equals("block/blaze_burner/superheated_rods_large")
                || path.equals("block/blaze_burner/flame");
    }

    private static String normalized(ResourceLocation location) {
        String path = location.getPath();
        if (path.startsWith("models/")) path = path.substring("models/".length());
        if (path.endsWith(".json")) path = path.substring(0, path.length() - ".json".length());
        return path;
    }

    static IModel load(IResourceManager resources, ResourceLocation location) throws Exception {
        String path = normalized(location);
        boolean item = path.startsWith("item/");
        boolean filled = path.endsWith("block_with_blaze") || path.equals("item/blaze_burner");
        boolean soulFire = path.endsWith("block_with_soul_fire");
        boolean fire = soulFire || path.endsWith("block_with_fire");
        boolean rods = path.endsWith("rods_small") || path.endsWith("rods_large");
        boolean flame = path.endsWith("/flame");
        String obj = rods ? (path.endsWith("rods_small") ? "blaze_rods_small" : "blaze_rods_large")
                : flame ? "blaze_flame" : filled ? "item" : fire ? "campfire" : "blaze_cage";
        String base = "models/block/blaze_burner/";
        OBJModel parsed;
        try (IResource resource = resources.getResource(new ResourceLocation("create", base + obj + ".obj"))) {
            parsed = new OBJModel.Parser(resource, resources).parse();
        }
        String primaryTexture = rods
                ? path.contains("superheated_") ? "create:block/blaze_super" : "create:block/blaze_active"
                : flame ? "create:block/blaze_burner_flame"
                : soulFire ? "create:block/blaze_heater_brazier_soul" : "create:block/blaze_heater_brazier";
        IModel model = parsed.process(ImmutableMap.of("flip-v", "true"))
                .retexture(ImmutableMap.of("#0", primaryTexture,
                        "#1", soulFire ? "farmers_future_delight:blocks/soul_campfire_fire"
                                : fire ? "farmers_future_delight:blocks/campfire_fire" : "create:block/blaze_idle"));
        ItemCameraTransforms transforms = item ? loadItemTransforms(resources, location) : null;
        return new UpstreamBlazeBurnerObjModel(model, parsed.getMatLib().getGroups().keySet(), transforms);
    }

    static ItemCameraTransforms loadItemTransforms(IResourceManager resources,
                                                    ResourceLocation location) throws IOException {
        Set<ResourceLocation> visited = new HashSet<>();
        ModelBlock first = null;
        ModelBlock previous = null;
        ResourceLocation next = modelJsonPath(location);
        while (next != null) {
            if (!visited.add(next)) {
                throw new IOException("Circular item model parent: " + next);
            }
            ModelBlock current;
            try (IResource resource = resources.getResource(next);
                 InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                current = ModelBlock.deserialize(reader);
            }
            if (first == null) {
                first = current;
            } else {
                previous.parent = current;
            }
            previous = current;
            ResourceLocation parent = current.getParentLocation();
            next = parent == null ? null : modelJsonPath(parent);
        }
        return first.getAllTransforms();
    }

    private static ResourceLocation modelJsonPath(ResourceLocation modelLocation) {
        String path = modelLocation.getPath();
        if (!path.startsWith("models/")) {
            path = "models/" + path;
        }
        if (!path.endsWith(".json")) {
            path += ".json";
        }
        return new ResourceLocation(modelLocation.getNamespace(), path);
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
