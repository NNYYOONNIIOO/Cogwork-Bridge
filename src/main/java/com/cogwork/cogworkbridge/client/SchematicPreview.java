package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.schematics.SchematicData;
import com.cogwork.cogworkbridge.content.schematics.SchematicFluids;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import java.util.*;

/** Cache a translucent display list so large projections do not rebuild meshes every frame. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class SchematicPreview {
    private static SchematicPreviewWorld preview;
    private static final Set<TileEntity> failedRenderers = Collections.newSetFromMap(new IdentityHashMap<>());
    private static SchematicData current;
    private static Rotation currentRotation;
    private static Mirror currentMirror;
    private static int structureList;
    private SchematicPreview() { }
    public static void render(SchematicData data, Mirror mirror, Rotation rotation) {
        if (current != data || currentMirror != mirror || currentRotation != rotation || structureList == 0
                || preview == null || !preview.hasParent(Minecraft.getMinecraft().world))
            rebuild(data, mirror, rotation);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        if (structureList != 0) GlStateManager.callList(structureList);
        renderTiles();
    }
    private static void rebuild(SchematicData data, Mirror mirror, Rotation rotation) {
        if (structureList != 0) GlStateManager.glDeleteLists(structureList, 1);
        structureList = 0; current = data; currentMirror = mirror; currentRotation = rotation;
        preview = new SchematicPreviewWorld(Minecraft.getMinecraft().world, data, mirror, rotation);
        failedRenderers.clear();
        Map<BlockPos, IBlockState> transformed = preview.states();
        Map<BlockPos, IBlockState> contained = new HashMap<>();
        for (SchematicData.Entry entry : data.blocks) {
            if (entry.state.getMaterial() == net.minecraft.block.material.Material.AIR) continue;
            try {
                BlockPos local = SchematicData.transformed(entry.local, mirror, rotation);
                if (entry.containedFluid != null) contained.put(local, entry.containedFluid);
            }
            catch (RuntimeException ignored) { }
        }
        int list = GlStateManager.glGenLists(1);
        if (list == 0) return;
        boolean complete = false;
        GlStateManager.glNewList(list, 4864);
        try {
            for (SchematicData.Entry entry : data.blocks) {
                if (entry.state.getMaterial() == net.minecraft.block.material.Material.AIR) continue;
                try {
                    if (SchematicFluids.fluidOf(entry.state) != null) continue;
                    BlockPos p = SchematicData.transformed(entry.local, mirror, rotation);
                    IBlockState state = preview.getBlockState(p);
                    if (state.getRenderType() != EnumBlockRenderType.MODEL) continue;
                    try { state = state.getActualState(preview, p); } catch (RuntimeException ignored) { }
                    IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(state);
                    try { state = state.getBlock().getExtendedState(state, preview, p); } catch (RuntimeException ignored) { }
                    List<BakedQuad> quads = new ArrayList<>();
                    for (EnumFacing side : EnumFacing.VALUES) collectQuads(quads, model, state, side);
                    collectQuads(quads, model, state, null);
                    GlStateManager.pushMatrix();
                    try {
                        GlStateManager.translate(p.getX(), p.getY(), p.getZ());
                        drawModel(quads);
                    } finally { GlStateManager.popMatrix(); }
                } catch (RuntimeException ignored) { }
            }
            try {
                Map<BlockPos, IBlockState> fluidView = new HashMap<>(transformed);
                fluidView.putAll(contained);
                renderFluids(fluidView);
            } catch (RuntimeException ignored) { }
            complete = true;
        } finally {
            GlStateManager.glEndList();
            if (complete) structureList = list;
            else GlStateManager.glDeleteLists(list, 1);
        }
    }

    private static void renderTiles() {
        var dispatcher = net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance;
        net.minecraft.world.World oldWorld = dispatcher.world;
        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        dispatcher.setWorld(preview);
        try {
            for (TileEntity tile : preview.tiles()) {
                if (failedRenderers.contains(tile)) continue;
                var renderer = dispatcher.getRenderer(tile);
                if (renderer == null) continue;
                BlockPos at = tile.getPos();
                int depth = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MODELVIEW_STACK_DEPTH);
                GlStateManager.pushMatrix();
                try {
                    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
                    Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                    GlStateManager.enableTexture2D(); GlStateManager.enableAlpha(); GlStateManager.enableBlend();
                    GlStateManager.disableLighting(); GlStateManager.color(.73F, .82F, 1F, .45F);
                    if (tile.hasFastRenderer()) {
                        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                        buffer.begin(7, DefaultVertexFormats.BLOCK);
                        try {
                            renderer.renderTileEntityFast(tile, at.getX(), at.getY(), at.getZ(),
                                    Minecraft.getMinecraft().getRenderPartialTicks(), -1, .45F, buffer);
                            Tessellator.getInstance().draw();
                        } finally { finishBuffer(buffer); buffer.setTranslation(0, 0, 0); }
                    } else renderer.render(tile, at.getX(), at.getY(), at.getZ(),
                            Minecraft.getMinecraft().getRenderPartialTicks(), -1, .45F);
                } catch (RuntimeException ignored) {
                    finishBuffer(Tessellator.getInstance().getBuffer());
                    failedRenderers.add(tile);
                }
                finally {
                    // Restore through GlStateManager so its cache agrees with GL, including after a failed TESR.
                    while (org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MODELVIEW_STACK_DEPTH) > depth)
                        GlStateManager.popMatrix();
                    Tessellator.getInstance().getBuffer().setTranslation(0, 0, 0);
                    OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                    GlStateManager.enableTexture2D(); GlStateManager.disableLighting(); GlStateManager.enableBlend();
                    GlStateManager.enableCull(); GlStateManager.enableDepth(); GlStateManager.enableAlpha();
                    GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER, .01F);
                    Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                    GlStateManager.depthMask(false); GlStateManager.color(1F, 1F, 1F, 1F);
                    GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                }
            }
        } finally {
            dispatcher.setWorld(oldWorld);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }
    }

    /** Liquids use their atlas sprites and neighbouring blueprint states, not missing baked block models. */
    private static void renderFluids(Map<BlockPos, IBlockState> states) {
        ProjectionAccess access = new ProjectionAccess(states);
        Tessellator tessellator = Tessellator.getInstance(); BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.disableCull();
        boolean drawing = false;
        try {
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        drawing = true;
        for (Map.Entry<BlockPos, IBlockState> entry : states.entrySet()) {
          try {
            Fluid fluid = SchematicFluids.fluidOf(entry.getValue());
            if (fluid == null || fluid.getStill() == null) continue;
            BlockPos pos = entry.getKey();
            TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
            TextureAtlasSprite top = atlas.getAtlasSprite(fluid.getStill().toString());
            TextureAtlasSprite side = atlas.getAtlasSprite((fluid.getFlowing() == null ? fluid.getStill() : fluid.getFlowing()).toString());
            int color = fluid == FluidRegistry.WATER ? access.getBiome(pos).getWaterColorMultiplier() : fluid.getColor();
            float height = fluidHeight(access, pos, fluid);
            double x = pos.getX(), y = pos.getY(), z = pos.getZ(), h = y + height;
            if (SchematicFluids.fluidOf(access.getBlockState(pos.up())) != fluid && !access.getBlockState(pos.up()).isOpaqueCube())
                quad(buffer, top, color, x,y+height,z, x,y+height,z+1, x+1,y+height,z+1, x+1,y+height,z);
            if (SchematicFluids.fluidOf(access.getBlockState(pos.down())) != fluid && !access.getBlockState(pos.down()).isOpaqueCube())
                quad(buffer, top, color, x,y,z+1, x,y,z, x+1,y,z, x+1,y,z+1);
            for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                BlockPos other = pos.offset(direction); IBlockState neighbour = access.getBlockState(other);
                if (neighbour.isOpaqueCube()) continue;
                float bottom = SchematicFluids.fluidOf(neighbour) == fluid ? fluidHeight(access, other, fluid) : 0;
                if (bottom >= height - .0001F) continue;
                double low = y + bottom;
                switch (direction) {
                    case NORTH -> quad(buffer, side, color, x+1,low,z, x,low,z, x,h,z, x+1,h,z);
                    case SOUTH -> quad(buffer, side, color, x,low,z+1, x+1,low,z+1, x+1,h,z+1, x,h,z+1);
                    case WEST -> quad(buffer, side, color, x,low,z, x,low,z+1, x,h,z+1, x,h,z);
                    case EAST -> quad(buffer, side, color, x+1,low,z+1, x+1,low,z, x+1,h,z, x+1,h,z+1);
                    default -> { }
                }
            }
          } catch (RuntimeException ignored) { }
        }
        tessellator.draw();
        drawing = false;
        } finally {
            if (drawing) finishBuffer(buffer);
            GlStateManager.enableCull();
        }
    }
    private static float fluidHeight(ProjectionAccess access, BlockPos pos, Fluid fluid) {
        if (SchematicFluids.fluidOf(access.getBlockState(pos.up())) == fluid) return 1;
        IBlockState state = access.getBlockState(pos);
        if (state.getBlock() instanceof BlockLiquid)
            return Math.max(.05F, 1F - BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL)));
        if (state.getBlock() instanceof BlockFluidBase base)
            return Math.max(.05F, Math.min(1F, Math.abs(base.getFilledPercentage(access, pos))));
        return .875F;
    }
    private static void quad(BufferBuilder buffer, TextureAtlasSprite sprite, int color,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        buffer.pos(x1,y1,z1).tex(sprite.getMinU(),sprite.getMinV()).color(r,g,b,115).endVertex();
        buffer.pos(x2,y2,z2).tex(sprite.getMinU(),sprite.getMaxV()).color(r,g,b,115).endVertex();
        buffer.pos(x3,y3,z3).tex(sprite.getMaxU(),sprite.getMaxV()).color(r,g,b,115).endVertex();
        buffer.pos(x4,y4,z4).tex(sprite.getMaxU(),sprite.getMinV()).color(r,g,b,115).endVertex();
    }
    private static final class ProjectionAccess implements IBlockAccess {
        private final Map<BlockPos, IBlockState> states;
        ProjectionAccess(Map<BlockPos, IBlockState> states) { this.states = states; }
        @Override public TileEntity getTileEntity(BlockPos pos) { return preview == null ? null : preview.getTileEntity(pos); }
        @Override public int getCombinedLight(BlockPos pos, int light) { return 0xf000f0; }
        @Override public IBlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getMaterial() == net.minecraft.block.material.Material.AIR; }
        @Override public Biome getBiome(BlockPos pos) {
            Minecraft mc = Minecraft.getMinecraft(); return mc.world.getBiome(mc.player.getPosition());
        }
        @Override public int getStrongPower(BlockPos pos, EnumFacing side) { return 0; }
        @Override public WorldType getWorldType() { return WorldType.DEFAULT; }
        @Override public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean fallback) { return getBlockState(pos).isOpaqueCube(); }
    }
    private static void drawModel(List<BakedQuad> quads) {
        Tessellator tessellator = Tessellator.getInstance(); BufferBuilder buffer = tessellator.getBuffer();
        boolean drawing = false;
        try {
            buffer.begin(7, DefaultVertexFormats.ITEM);
            drawing = true;
            append(buffer, quads);
            tessellator.draw();
            drawing = false;
        } finally {
            if (drawing) finishBuffer(buffer);
        }
    }
    private static void collectQuads(List<BakedQuad> result, IBakedModel model, IBlockState state, EnumFacing side) {
        try {
            List<BakedQuad> quads = model.getQuads(state, side, 0L);
            if (quads == null) return;
            int expected = DefaultVertexFormats.ITEM.getIntegerSize() * 4;
            for (BakedQuad quad : quads)
                if (quad != null && quad.getVertexData() != null && quad.getVertexData().length == expected) result.add(quad);
        } catch (RuntimeException ignored) { }
    }
    private static void finishBuffer(BufferBuilder buffer) {
        try { buffer.finishDrawing(); } catch (IllegalStateException ignored) { }
    }
    private static void append(BufferBuilder buffer, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            buffer.addVertexData(quad.getVertexData());
            for (int vertex = 1; vertex <= 4; vertex++)
                buffer.putColorRGBA(buffer.getColorIndex(vertex), 185, 210, 255, 115);
            net.minecraft.util.math.Vec3i normal = quad.getFace() == null ? new net.minecraft.util.math.Vec3i(0, 1, 0) : quad.getFace().getDirectionVec();
            buffer.putNormal(normal.getX(), normal.getY(), normal.getZ());
        }
    }
    public static void clear() {
        if (structureList != 0) GlStateManager.glDeleteLists(structureList, 1);
        preview = null; failedRenderers.clear(); structureList = 0; current = null;
    }
    @SubscribeEvent public static void modelsReloaded(ModelBakeEvent event) { clear(); }
}
