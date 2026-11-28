package com.cogwork.cogworkbridge.client.render;

import com.cogwork.cogworkbridge.client.model.OcclusionGeometry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
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
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.common.property.IExtendedBlockState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
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
    private final Map<IBlockState, Geometry> stateGeometry = new HashMap<>();
    private final Map<IBakedModel, Map<IBlockState, Geometry>> modelGeometry = new IdentityHashMap<>();
    private final Map<IBakedModel, Map<HousingKey, IBakedModel>> housingModels = new IdentityHashMap<>();
    private final Map<IBlockState, Integer> opaqueFaces = new HashMap<>();
    private boolean isolated;

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
        draw(geometry(state), 0);
    }

    /** Standalone GUI blocks need their block-colour handler, independently of kinetic part tints. */
    public void renderGuiBlock(IBlockState state) {
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        IBakedModel model = model(state);
        for (int bucket = 0; bucket < 7; bucket++) {
            for (BakedQuad quad : model.getQuads(state, bucket == 6 ? null : EnumFacing.VALUES[bucket], 0L)) {
                buffer.addVertexData(quad.getVertexData());
                if (quad.hasTintIndex()) {
                    int rgb = minecraft.getBlockColors().colorMultiplier(state, null, null, quad.getTintIndex());
                    float red = (rgb >> 16 & 255) / 255F, green = (rgb >> 8 & 255) / 255F, blue = (rgb & 255) / 255F;
                    for (int vertex = 1; vertex <= 4; vertex++) buffer.putColorMultiplier(red, green, blue, vertex);
                }
                Vec3i normal = quad.getFace() == null ? new Vec3i(0, 1, 0) : quad.getFace().getDirectionVec();
                buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
            }
        }
        tessellator.draw();
    }

    public void renderFast(IBlockState state, World world, BlockPos pos, EnumFacing.Axis axis) {
        Geometry geometry = geometry(state);
        draw(geometry, hiddenSides(geometry, world, pos, axis));
    }

    private Geometry geometry(IBlockState state) {
        return stateGeometry.computeIfAbsent(state,
                key -> new Geometry(model(key), key));
    }

    private IBakedModel model(IBlockState state) {
        IBlockState listed = state instanceof IExtendedBlockState extended ? extended.getClean() : state;
        return minecraft.getBlockRendererDispatcher().getModelForState(listed);
    }

    /** Extended preview states contain world context and must not become global display-list keys. */
    public void renderUncached(IBlockState state) {
        renderUncached(model(state), state, 0xFFFFFF);
    }

    private void renderUncached(IBakedModel model, IBlockState state, int rgb) {
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        for (EnumFacing side : EnumFacing.VALUES) appendQuads(buffer, model.getQuads(state, side, 0L), rgb);
        appendQuads(buffer, model.getQuads(state, null, 0L), rgb);
        tessellator.draw();
    }

    private Geometry geometry(IBakedModel model, IBlockState state) {
        return modelGeometry.computeIfAbsent(model, key -> new HashMap<>())
                .computeIfAbsent(state, key -> new Geometry(model, key));
    }

    /** Transient vertex tint for animated arm bodies; do not cache every rainbow colour. */
    public void renderTinted(IBlockState state, int rgb) {
        if ((rgb & 0xFFFFFF) == 0xFFFFFF) { renderFast(state); return; }
        renderTinted(model(state), state, rgb);
    }

    /** Fixed baked overlay geometry with a transient colour, without a display list per tint. */
    public void renderTinted(IBakedModel model, IBlockState state, int rgb) {
        if (model == null) return;
        Geometry geometry = geometry(model, state);
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);
        for (ModelQuad quad : geometry.quads) appendQuad(buffer, quad.quad, rgb);
        tessellator.draw();
    }

    /** Renders a standalone baked submodel using the same atlas and quad path. */
    public void renderFast(IBakedModel model, IBlockState state) {
        if (model == null) return;
        draw(geometry(model, state), 0);
    }

    public void renderFast(IBakedModel model, IBlockState state, World world, BlockPos pos, EnumFacing.Axis axis) {
        if (model == null) return;
        Geometry geometry = geometry(model, state);
        draw(geometry, hiddenSides(geometry, world, pos, axis));
    }

    /** A static block housing and its moving part are baked separately, but can still share occlusion. */
    public void renderRotating(@javax.annotation.Nullable IBakedModel part, IBlockState state,
                               World world, BlockPos pos, EnumFacing.Axis axis) {
        IBakedModel source = part == null ? model(state) : part;
        IBakedModel visible = source;
        if (!isolated && world != null && pos != null && world.isBlockLoaded(pos, false)) {
            IBlockState housing = world.getBlockState(pos);
            if (housing.getBlock() != state.getBlock() && housing.getRenderType() == EnumBlockRenderType.MODEL) {
                IBlockState actual = housing.getActualState(world, pos);
                IBlockState extended = actual.getBlock().getExtendedState(actual, world, pos);
                // World-dependent meshes can change without changing their listed state.
                if (!(extended instanceof IExtendedBlockState)) {
                    HousingKey key = new HousingKey(state, actual, axis);
                    visible = housingModels.computeIfAbsent(source, model -> new HashMap<>()).computeIfAbsent(key,
                            variant -> OcclusionGeometry.insideHousing(source, state, model(actual), actual, axis));
                }
            }
        }
        Geometry geometry = part == null && visible == source ? geometry(state) : geometry(visible, state);
        draw(geometry, hiddenSides(geometry, world, pos, axis));
    }

    /** Only neighbours known to be opaque, full model blocks can cover an entire end cap. */
    private int hiddenSides(Geometry geometry, World world, BlockPos pos, EnumFacing.Axis axis) {
        if (isolated || world == null || pos == null || geometry.boundaryMask == 0) return 0;
        int mask = 0;
        for (EnumFacing side : EnumFacing.VALUES) {
            if (side.getAxis() != axis || (geometry.boundaryMask & 1 << side.getIndex()) == 0) continue;
            BlockPos neighbourPos = pos.offset(side);
            if (!world.isBlockLoaded(neighbourPos, false)) continue;
            IBlockState neighbour = world.getBlockState(neighbourPos);
            if (neighbour.isOpaqueCube() && neighbour.isFullCube()
                    && neighbour.getRenderType() == EnumBlockRenderType.MODEL
                    && neighbour.getBlock().getRenderLayer() == BlockRenderLayer.SOLID) {
                IBlockState actual = neighbour.getActualState(world, neighbourPos);
                IBlockState extended = actual.getBlock().getExtendedState(actual, world, neighbourPos);
                // World-dependent extended models cannot be cached by their listed state alone.
                if (extended instanceof IExtendedBlockState) continue;
                int faces = opaqueFaces.computeIfAbsent(actual, key -> OcclusionGeometry.opaqueFullFaces(
                        minecraft.getBlockRendererDispatcher().getModelForState(key), key));
                if ((faces & 1 << side.getOpposite().getIndex()) != 0) mask |= 1 << side.getIndex();
            }
        }
        return mask;
    }

    /** GUI models have no physical neighbours, even when their preview tile uses the player's world. */
    public void renderIsolated(Runnable renderer) {
        boolean previous = isolated;
        isolated = true;
        try {
            renderer.run();
        } finally {
            isolated = previous;
        }
    }

    private void draw(Geometry geometry, int hiddenMask) {
        Integer list = geometry.displayLists.get(hiddenMask);
        if (list == null) {
            list = compile(geometry, hiddenMask);
            geometry.displayLists.put(hiddenMask, list);
        }
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.callList(list);
    }

    private int compile(Geometry geometry, int hiddenMask) {
        int list = GlStateManager.glGenLists(1);

        GlStateManager.glNewList(list, 4864 /* GL_COMPILE */);
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7 /* GL_QUADS */, DefaultVertexFormats.ITEM);
        for (ModelQuad quad : geometry.quads) {
            if ((quad.boundaryMask & hiddenMask) == 0) appendQuad(buffer, quad.quad, 0xFFFFFF);
        }
        tessellator.draw();
        GlStateManager.glEndList();
        return list;
    }

    private static void appendQuads(BufferBuilder buffer, Iterable<BakedQuad> quads, int rgb) {
        for (BakedQuad quad : quads) appendQuad(buffer, quad, rgb);
    }

    private static void appendQuad(BufferBuilder buffer, BakedQuad quad, int rgb) {
        // BakedQuad already contains the atlas UVs and baked lighting data.
        buffer.addVertexData(quad.getVertexData());
        buffer.putColorRGB_F4((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
        Vec3i normal = quad.getFace() == null
                ? new Vec3i(0, 1, 0) : quad.getFace().getDirectionVec();
        buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
    }

    private void clearLists() {
        for (Geometry geometry : stateGeometry.values()) geometry.deleteLists();
        stateGeometry.clear();
        for (Map<IBlockState, Geometry> states : modelGeometry.values()) {
            for (Geometry geometry : states.values()) geometry.deleteLists();
        }
        modelGeometry.clear();
        housingModels.clear();
        opaqueFaces.clear();
    }

    private record HousingKey(IBlockState part, IBlockState housing, EnumFacing.Axis axis) { }

    private record ModelQuad(BakedQuad quad, int boundaryMask) { }

    /** Geometry and end-cap classification are immutable until the next resource reload. */
    private static final class Geometry {
        private final List<ModelQuad> quads = new ArrayList<>();
        private final Map<Integer, Integer> displayLists = new HashMap<>();
        private int boundaryMask;

        private Geometry(IBakedModel model, IBlockState state) {
            for (int bucket = 0; bucket < 7; bucket++) {
                for (BakedQuad quad : model.getQuads(state, bucket == 6 ? null : EnumFacing.VALUES[bucket], 0L)) {
                    EnumFacing face = quad.getFace();
                    int mask = face != null && OcclusionGeometry.isBoundaryFace(quad, face) ? 1 << face.getIndex() : 0;
                    quads.add(new ModelQuad(quad, mask));
                    boundaryMask |= mask;
                }
            }
        }

        private void deleteLists() {
            for (Integer list : displayLists.values()) GlStateManager.glDeleteLists(list, 1);
            displayLists.clear();
        }
    }

    @Override
    public void onResourceManagerReload(@Nonnull IResourceManager manager, Predicate<IResourceType> predicate) {
        if (predicate.test(VanillaResourceType.MODELS) || predicate.test(VanillaResourceType.TEXTURES)) {
            clearLists();
        }
    }
}
