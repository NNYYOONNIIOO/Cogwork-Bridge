package com.cogwork.cogworkbridge.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.client.resource.VanillaResourceType;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Caches baked block states in display lists for kinetic TESRs.
 *
 * <p>Uploading every quad from every kinetic tile entity on every frame is very
 * expensive.  The model is immutable between resource reloads, so compile it once
 * and leave the TESR with only a display-list call and its rotation matrix.  The
 * quads still come from the normal block-model pipeline; this class does not own or
 * bundle any texture assets.</p>
 */
public final class FastStateRendering implements ISelectiveResourceReloadListener {

    private static FastStateRendering instance;

    private final Minecraft minecraft;
    private final Tessellator tessellator;
    private final Map<IBlockState, Integer> renderLists = new HashMap<>();
    private final Map<IBakedModel, Integer> modelLists = new IdentityHashMap<>();

    private FastStateRendering(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.tessellator = Tessellator.getInstance();
        if (minecraft.getResourceManager() instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) minecraft.getResourceManager()).registerReloadListener(this);
        }
    }

    /** Must be called from the client pre-init hook, after Minecraft exists. */
    public static synchronized void initialize() {
        if (instance == null) {
            instance = new FastStateRendering(Minecraft.getMinecraft());
        }
    }

    /** Returns the client cache, creating it defensively if a renderer runs early. */
    public static synchronized FastStateRendering getInstance() {
        if (instance == null) {
            initialize();
        }
        return instance;
    }

    /** Renders one baked state using its cached display list. */
    public void renderFast(IBlockState state) {
        synchronized (renderLists) {
            Integer list = renderLists.get(state);
            if (list == null) {
                list = compile(state);
                renderLists.put(state, list);
            }
            minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.callList(list);
        }
    }

    private int compile(IBlockState state) {
        BlockRendererDispatcher dispatcher = minecraft.getBlockRendererDispatcher();
        IBakedModel model = dispatcher.getModelForState(state);
        return compile(model, state);
    }

    /** Transient vertex tint for animated arm bodies; do not cache every rainbow colour. */
    public void renderTinted(IBlockState state, int rgb) {
        if ((rgb & 0xFFFFFF) == 0xFFFFFF) { renderFast(state); return; }
        IBakedModel model = minecraft.getBlockRendererDispatcher().getModelForState(state);
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        for (EnumFacing side : EnumFacing.values()) appendQuads(buffer, model.getQuads(state, side, 0L), rgb);
        appendQuads(buffer, model.getQuads(state, null, 0L), rgb);
        tessellator.draw();
    }

    /** Renders a standalone baked submodel using the same atlas and quad path. */
    public void renderFast(IBakedModel model, IBlockState state) {
        if (model == null) return;
        synchronized (renderLists) {
            Integer list = modelLists.get(model);
            if (list == null) {
                list = compile(model, state);
                modelLists.put(model, list);
            }
            minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.callList(list);
        }
    }

    private int compile(IBakedModel model, IBlockState state) {
        int list = GlStateManager.glGenLists(1);

        GlStateManager.glNewList(list, 4864 /* GL_COMPILE */);
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7 /* GL_QUADS */, DefaultVertexFormats.ITEM);
        for (EnumFacing side : EnumFacing.values()) {
            appendQuads(buffer, model.getQuads(state, side, 0L));
        }
        appendQuads(buffer, model.getQuads(state, null, 0L));
        tessellator.draw();
        GlStateManager.glEndList();
        return list;
    }

    private static void appendQuads(BufferBuilder buffer, Iterable<BakedQuad> quads) {
        appendQuads(buffer, quads, 0xFFFFFF);
    }
    private static void appendQuads(BufferBuilder buffer, Iterable<BakedQuad> quads, int rgb) {
        for (BakedQuad quad : quads) {
            // BakedQuad already contains the atlas UVs and baked lighting data.
            buffer.addVertexData(quad.getVertexData());
            buffer.putColorRGB_F4((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
            Vec3i normal = quad.getFace() == null
                    ? new Vec3i(0, 1, 0) : quad.getFace().getDirectionVec();
            buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
        }
    }

    private void clearLists() {
        synchronized (renderLists) {
            for (Integer list : renderLists.values()) {
                GlStateManager.glDeleteLists(list, 1);
            }
            renderLists.clear();
            for (Integer list : modelLists.values()) {
                GlStateManager.glDeleteLists(list, 1);
            }
            modelLists.clear();
        }
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager manager, Predicate<IResourceType> predicate) {
        if (predicate.test(VanillaResourceType.MODELS) || predicate.test(VanillaResourceType.TEXTURES)) {
            clearLists();
        }
    }
}
