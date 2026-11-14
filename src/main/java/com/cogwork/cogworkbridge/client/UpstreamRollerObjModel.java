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

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

/** Adapts the two roller OBJ models from the user's runtime resource pack. */
final class UpstreamRollerObjModel implements IModel {
    private final IModel model;
    private final Collection<String> groups;
    private final ItemCameraTransforms itemTransforms;
    private final boolean wheel;

    private UpstreamRollerObjModel(IModel model, Collection<String> groups,
                                  ItemCameraTransforms itemTransforms, boolean wheel) {
        this.model = model;
        this.groups = groups;
        this.itemTransforms = itemTransforms;
        this.wheel = wheel;
    }

    static boolean isItem(ResourceLocation location) {
        String path = location.getPath();
        return path.equals("item/mechanical_roller")
                || path.equals("models/item/mechanical_roller")
                || path.equals("models/item/mechanical_roller.json");
    }

    static boolean isWheel(ResourceLocation location) {
        String path = location.getPath();
        return path.equals("block/mechanical_roller/wheel")
                || path.equals("models/block/mechanical_roller/wheel")
                || path.equals("models/block/mechanical_roller/wheel.json");
    }

    static IModel load(IResourceManager resources, boolean item) throws Exception {
        String base = "models/block/mechanical_roller/";
        String name = item ? "item" : "wheel";
        OBJModel parsed;
        try (IResource resource = resources.getResource(new ResourceLocation("create", base + name + ".obj"))) {
            parsed = new OBJModel.Parser(resource, resources).parse();
        }
        if (!item) {
            // Forge's OBJ baker rewrites visibility flags, so omit auxiliary bounds before baking.
            parsed.getMatLib().getGroups().keySet().removeIf(group -> group.startsWith("Bounding"));
        }
        Collection<String> groups = parsed.getMatLib().getGroups().keySet();
        IModel model = parsed.process(ImmutableMap.of("flip-v", "true"))
                .retexture(ImmutableMap.of("#wheel", "create:block/roller_wheel",
                        "#metal", "create:block/roller_metal", "#casing", "create:block/roller_casing"));
        ItemCameraTransforms transforms = null;
        if (item) {
            try (IResource resource = resources.getResource(new ResourceLocation("create", base + "item.json"));
                 InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                transforms = ModelBlock.deserialize(reader).getAllTransforms();
            }
        }
        return new UpstreamRollerObjModel(model, groups, transforms, !item);
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        return model.getTextures();
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> textures) {
        OBJModel.OBJState objState = new OBJModel.OBJState(Collections.emptyList(), true, state);
        for (String group : groups) {
            objState.getVisibilityMap().put(group, !wheel || !group.startsWith("Bounding"));
        }
        IBakedModel baked = model.bake(objState, format, textures);
        return itemTransforms == null ? baked
                : new PerspectiveMapWrapper(baked, PerspectiveMapWrapper.getTransforms(itemTransforms));
    }
}
