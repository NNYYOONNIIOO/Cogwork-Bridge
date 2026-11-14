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

/** Load the unbundled 1.21 OBJ meshes through Forge 1.12's OBJ baker. */
final class UpstreamPowerObjModel implements IModel {
    private final IModel model;
    private final Collection<String> groups;
    private final ItemCameraTransforms transforms;
    private UpstreamPowerObjModel(IModel model, Collection<String> groups, ItemCameraTransforms transforms) {
        this.model = model; this.groups = groups; this.transforms = transforms;
    }
    private static String path(ResourceLocation location) {
        String path = location.getPath();
        if (path.startsWith("models/")) path = path.substring(7);
        return path.endsWith(".json") ? path.substring(0, path.length() - 5) : path;
    }
    static boolean accepts(ResourceLocation location) {
        if (!"create".equals(location.getNamespace())) return false;
        String path = path(location);
        return path.equals("block/valve_handle") || path.startsWith("block/valve_handle/material_") || path.startsWith("item/") && path.endsWith("_valve_handle")
                || path.equals("item/chain_conveyor") || path.equals("block/chain_conveyor/item")
                || path.equals("block/chain_conveyor/wheel") || path.equals("block/chain_conveyor/shaft")
                || path.equals("block/chain_conveyor/guard")
                || path.equals("block/large_water_wheel/block") || path.equals("block/large_water_wheel/item")
                || path.startsWith("block/large_water_wheel/material_") || path.equals("item/large_water_wheel")
                || path.equals("block/water_wheel/wheel") || path.equals("block/water_wheel/item")
                || path.startsWith("block/water_wheel/material_") || path.equals("item/water_wheel");
    }
    static IModel load(IResourceManager resources, ResourceLocation location) throws Exception {
        String path = path(location);
        boolean chain = path.contains("chain_conveyor");
        boolean valve = path.contains("valve_handle");
        boolean smallWheel = path.contains("water_wheel") && !path.contains("large_water_wheel");
        boolean itemModel = path.startsWith("item/") || path.endsWith("/item");
        String chainPart = path.endsWith("/wheel") ? "conveyor_wheel" : path.endsWith("/shaft") ? "conveyor_shaft"
                : path.endsWith("/guard") ? "conveyor_ports" : "item";
        String base = chain ? "models/block/chain_conveyor/" + chainPart + ".obj"
                : valve ? "models/block/valve_handle/valve_handle.obj"
                : smallWheel ? itemModel ? "models/block/water_wheel/water_wheel_item.obj" : "models/block/water_wheel/water_wheel.obj"
                : "models/block/large_water_wheel/waterwheel_large.obj";
        OBJModel parsed;
        try (IResource resource = resources.getResource(new ResourceLocation("create", base))) {
            parsed = new OBJModel.Parser(resource, resources).parse();
        }
        IModel model = parsed.process(ImmutableMap.of("flip-v", "true"));
        if (chain) {
            model = model.retexture(ImmutableMap.of("#axis", "create:block/axis", "#axis_top", "create:block/axis_top",
                    "#conveyor_casing", "create:block/conveyor_casing", "#conveyor_port", "create:block/conveyor_port",
                    "#bullwheel", "create:block/bullwheel"));
        } else if (valve) {
            String color = path.startsWith("item/") ? path.substring(5, path.length() - "_valve_handle".length())
                    : path.contains("material_") ? path.substring(path.indexOf("material_") + 9) : "copper";
            model = model.retexture(ImmutableMap.of("#3", "create:block/valve_handle/valve_handle_" + color));
        }
        else {
            String wood = path.contains("material_") ? path.substring(path.indexOf("material_") + 9) : "spruce";
            String log = wood.equals("dark_oak") ? "big_oak" : wood;
            boolean nether = wood.equals("crimson") || wood.equals("warped");
            String logTexture = nether ? "farmers_future_delight:block/" + wood + "_stem" : "minecraft:blocks/log_" + log;
            String planksTexture = nether ? "farmers_future_delight:block/" + wood + "_planks" : "minecraft:blocks/planks_" + wood;
            model = model.retexture(ImmutableMap.of("#gearbox", "create:block/gearbox", "#axis", "create:block/axis", "#axis_top", "create:block/axis_top",
                    "#metal", "create:block/waterwheel_metal", "#log", logTexture,
                    "#log_top", logTexture + "_top", "#planks", planksTexture));
        }
        ResourceLocation transformModel = chain && itemModel ? new ResourceLocation("create", "block/chain_conveyor/item") : smallWheel && itemModel
                ? new ResourceLocation("create", "block/water_wheel/item") : new ResourceLocation("create", path);
        ItemCameraTransforms transforms = itemModel
                ? UpstreamBlazeBurnerObjModel.loadItemTransforms(resources, transformModel) : null;
        return new UpstreamPowerObjModel(model, parsed.getMatLib().getGroups().keySet(), transforms);
    }
    @Override public Collection<ResourceLocation> getTextures() { return model.getTextures(); }
    @Override public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> textures) {
        OBJModel.OBJState objState = new OBJModel.OBJState(Collections.emptyList(), true, state);
        for (String group : groups) objState.getVisibilityMap().put(group, true);
        IBakedModel baked = model.bake(objState, format, textures);
        return transforms == null ? baked : new PerspectiveMapWrapper(baked, PerspectiveMapWrapper.getTransforms(transforms));
    }
}
