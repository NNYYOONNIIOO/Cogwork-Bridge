package com.cogwork.cogworkbridge.client.model;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.animation.IClip;

/** Keeps Forge's loading and retexturing contracts while pruning immutable baked geometry. */
public final class OcclusionModel implements IModel {
    private final IModel source;

    public OcclusionModel(IModel source) { this.source = source; }
    @Override public Collection<ResourceLocation> getDependencies() { return source.getDependencies(); }
    @Override public Collection<ResourceLocation> getTextures() { return source.getTextures(); }
    @Override public IModelState getDefaultState() { return source.getDefaultState(); }
    @Override public Optional<? extends IClip> getClip(String name) { return source.getClip(name); }
    @Override public Optional<ModelBlock> asVanillaModel() { return source.asVanillaModel(); }
    @Override public IModel process(ImmutableMap<String, String> data) { return new OcclusionModel(source.process(data)); }
    @Override public IModel retexture(ImmutableMap<String, String> textures) { return new OcclusionModel(source.retexture(textures)); }
    @Override public IModel smoothLighting(boolean value) { return new OcclusionModel(source.smoothLighting(value)); }
    @Override public IModel gui3d(boolean value) { return new OcclusionModel(source.gui3d(value)); }
    @Override public IModel uvlock(boolean value) { return new OcclusionModel(source.uvlock(value)); }

    @Override public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> textures) {
        return OcclusionGeometry.optimize(source.bake(state, format, textures));
    }
}
