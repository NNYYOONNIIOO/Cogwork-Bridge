package com.cogwork.cogworkbridge.client;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;

import java.lang.reflect.Field;

/**
 * Selects models from the runtime Create asset pack and lets Forge bake them.
 *
 * <p>The loader is deliberately a thin adapter.  The upstream jar remains an
 * asset-only resource pack, while Forge's own vanilla loader still handles
 * parent resolution, texture collection, culling, lighting and baking.  The
 * resource pack adapts the small JSON-format difference before this delegate
 * is called.</p>
 */
public final class UpstreamModelLoader implements ICustomModelLoader {

    public static final UpstreamModelLoader INSTANCE = new UpstreamModelLoader();
    private static final ICustomModelLoader VANILLA_LOADER = findVanillaLoader();
    private IResourceManager resources;

    private UpstreamModelLoader() {
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        resources = resourceManager;
        com.cogwork.cogworkbridge.client.model.OcclusionGeometry.clear();
    }

    @Override
    public boolean accepts(ResourceLocation location) {
        if (!"create".equals(location.getNamespace())) {
            return false;
        }
        String path = location.getPath();
        return UpstreamTrackObjModel.accepts(location)
                || UpstreamFlywheelObjModel.accepts(location)
                || UpstreamBlazeBurnerObjModel.accepts(location)
                || UpstreamPowerObjModel.accepts(location)
                || UpstreamCrushingWheelObjModel.accepts(location)
                || UpstreamRollerObjModel.isItem(location) || UpstreamRollerObjModel.isWheel(location)
                || (path.startsWith("models/") && path.endsWith(".json"))
                || path.startsWith("block/")
                || path.startsWith("entity/")
                || path.startsWith("item/");
    }

    @Override
    public IModel loadModel(ResourceLocation location) throws Exception {
        return new com.cogwork.cogworkbridge.client.model.OcclusionModel(loadUpstreamModel(location));
    }

    private IModel loadUpstreamModel(ResourceLocation location) throws Exception {
        if (UpstreamTrackObjModel.accepts(location)) return UpstreamTrackObjModel.load(resources, location);
        if (UpstreamFlywheelObjModel.accepts(location)) return UpstreamFlywheelObjModel.load(resources, location);
        if (UpstreamPowerObjModel.accepts(location)) {
            return UpstreamPowerObjModel.load(resources, location);
        }
        if (UpstreamBlazeBurnerObjModel.accepts(location)) {
            return UpstreamBlazeBurnerObjModel.load(resources, location);
        }
        if (UpstreamCrushingWheelObjModel.accepts(location)) {
            return UpstreamCrushingWheelObjModel.load(resources, location);
        }
        if (UpstreamRollerObjModel.isItem(location)) {
            return UpstreamRollerObjModel.load(resources, true);
        }
        if (UpstreamRollerObjModel.isWheel(location)) {
            return UpstreamRollerObjModel.load(resources, false);
        }
        return VANILLA_LOADER.loadModel(location);
    }

    private static ICustomModelLoader findVanillaLoader() {
        try {
            Class<?> loaderClass = Class.forName(ModelLoader.class.getName() + "$VanillaLoader");
            Field instance = loaderClass.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            return (ICustomModelLoader) instance.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access Forge's vanilla model loader", exception);
        }
    }

    @Override
    public String toString() {
        return "CogworkBridgeUpstreamModelLoader";
    }
}
