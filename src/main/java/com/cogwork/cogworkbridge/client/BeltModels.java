package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class BeltModels {
    private static final String[] PARTS = {"start", "middle", "end"};
    private static final String[] DIRECTIONS = {"north", "south", "east", "west"};
    private static final String[] SLOPES = {"flat", "up", "down", "vertical", "sideways"};
    private static final Map<String, IBakedModel[]> SCROLL_MODELS = new HashMap<>();
    private static final Map<String, IBakedModel> CASING_MODELS = new HashMap<>();
    public static IBakedModel[] models(String key) { return SCROLL_MODELS.get(key); }
    public static IBakedModel casing(String key, String type, boolean pulley) {
        return CASING_MODELS.get(type + ":" + (pulley ? "pulley:" : "") + key);
    }
    private BeltModels() { }
    private static IModel model(String name) {
        ResourceLocation id = new ResourceLocation("create", "block/belt/" + name);
        return ModelLoaderRegistry.getModelOrLogError(id, "Could not load belt model " + id);
    }
    private static IModel casingModel(String name) {
        ResourceLocation id = new ResourceLocation("create", "block/belt_casing/" + name);
        return ModelLoaderRegistry.getModelOrLogError(id, "Could not load belt casing model " + id);
    }
    @SubscribeEvent public static void stitch(TextureStitchEvent.Pre event) {
        event.getMap().registerSprite(new ResourceLocation("create", "block/belt/andesite_belt_casing"));
        for (String part : PARTS) for (String prefix : new String[]{"", "diagonal_"}) {
            for (ResourceLocation texture : model(prefix + part).getTextures()) event.getMap().registerSprite(texture);
            if (prefix.isEmpty()) for (ResourceLocation texture : model(part + "_bottom").getTextures())
                event.getMap().registerSprite(texture);
        }
        for (String name : new String[]{"horizontal_start", "horizontal_middle", "horizontal_end", "horizontal_pulley",
                "diagonal_start", "diagonal_middle", "diagonal_end", "diagonal_pulley",
                "sideways_start", "sideways_middle", "sideways_end", "sideways_pulley"}) {
            for (ResourceLocation texture : casingModel(name).getTextures()) event.getMap().registerSprite(texture);
        }
    }
    @SubscribeEvent public static void bake(ModelBakeEvent event) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        SCROLL_MODELS.clear();
        CASING_MODELS.clear();
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            ModelResourceLocation key = new ModelResourceLocation(AllBlocks.BELT.getRegistryName(), "axis=" + axis.getName());
            IBakedModel original = event.getModelRegistry().getObject(key);
            if (original == null) continue;
            Map<String, IBakedModel[]> models = new HashMap<>();
            for (String part : PARTS) for (String slope : SLOPES) for (String direction : DIRECTIONS) {
                boolean diagonal = slope.equals("up") || slope.equals("down");
                // ModelRotation's X90 applies -90 degrees, so the model's
                // north end maps to the bottom. A descending chain starts at
                // the top and must use the opposite end model.
                boolean swapEnds = slope.equals("down") || slope.equals("vertical")
                        && (direction.equals("north") || direction.equals("west"));
                String displayed = swapEnds ? part.equals("start") ? "end" : part.equals("end") ? "start" : part : part;
                String name = (diagonal ? "diagonal_" : "") + displayed;
                int rotation = switch (direction) { case "east" -> 270; case "north" -> 180; case "west" -> 90; default -> 0; };
                if (slope.equals("up")) rotation = (rotation + 180) % 360;
                ModelRotation transform = slope.equals("sideways") ? ModelRotation.X0_Y0
                        : rotation(rotation, slope.equals("vertical"));
                IBakedModel top = model(name).bake(transform, DefaultVertexFormats.BLOCK,
                        sprite -> atlas.getAtlasSprite(sprite.toString()));
                if (diagonal) top = new SlopedBeltModel(top, part, slope, direction);
                IBakedModel bottom = diagonal ? null : model(displayed + "_bottom").bake(transform,
                        DefaultVertexFormats.BLOCK, sprite -> atlas.getAtlasSprite(sprite.toString()));
                if (slope.equals("sideways")) {
                    top = new SidewaysBeltModel(top, direction);
                    bottom = new SidewaysBeltModel(bottom, direction);
                }
                IBakedModel[] pair = new IBakedModel[]{top, bottom};
                String modelKey = part + "_" + slope + "_" + direction;
                models.put(modelKey, pair); SCROLL_MODELS.put(modelKey, pair);
                String casingPrefix = slope.equals("flat") ? "horizontal_"
                        : slope.equals("up") || slope.equals("down") ? "diagonal_"
                        : slope.equals("sideways") || slope.equals("vertical") ? "sideways_" : null;
                if (casingPrefix != null) {
                    boolean swapCasingEnds = slope.equals("down") || (slope.equals("vertical") || slope.equals("sideways"))
                            && (direction.equals("north") || direction.equals("west"));
                    String casingPart = swapCasingEnds
                            ? part.equals("start") ? "end" : part.equals("end") ? "start" : part : part;
                    ModelRotation casingTransform = slope.equals("vertical")
                            ? rotation((rotation + 90) % 360, true)
                            : slope.equals("sideways") ? sidewaysCasingRotation(direction) : transform;
                    IBakedModel casing = casingModel(casingPrefix + casingPart).bake(casingTransform, DefaultVertexFormats.BLOCK,
                            sprite -> atlas.getAtlasSprite(sprite.toString()));
                    IBakedModel pulley = casingModel(casingPrefix + "pulley").bake(casingTransform, DefaultVertexFormats.BLOCK,
                            sprite -> atlas.getAtlasSprite(sprite.toString()));
                    CASING_MODELS.put("brass:" + modelKey, casing);
                    CASING_MODELS.put("brass:pulley:" + modelKey, pulley);
                    CASING_MODELS.put("andesite:" + modelKey, new TextureRemapModel(casing,
                            atlas.getAtlasSprite("create:block/belt/brass_belt_casing"),
                            atlas.getAtlasSprite("create:block/belt/andesite_belt_casing")));
                    CASING_MODELS.put("andesite:pulley:" + modelKey, new TextureRemapModel(pulley,
                            atlas.getAtlasSprite("create:block/belt/brass_belt_casing"),
                            atlas.getAtlasSprite("create:block/belt/andesite_belt_casing")));
                }
            }
            event.getModelRegistry().putObject(key, new BeltModel(original, models));
        }
    }
    private static ModelRotation sidewaysCasingRotation(String direction) {
        return switch (direction) {
            case "east" -> ModelRotation.X0_Y270; case "west" -> ModelRotation.X180_Y90;
            case "south" -> ModelRotation.X0_Y0; default -> ModelRotation.X180_Y180;
        };
    }
    private static ModelRotation rotation(int y, boolean vertical) {
        if (vertical) return switch (y) {
            case 90 -> ModelRotation.X90_Y90; case 180 -> ModelRotation.X90_Y180;
            case 270 -> ModelRotation.X90_Y270; default -> ModelRotation.X90_Y0;
        };
        return switch (y) {
            case 90 -> ModelRotation.X0_Y90; case 180 -> ModelRotation.X0_Y180;
            case 270 -> ModelRotation.X0_Y270; default -> ModelRotation.X0_Y0;
        };
    }
    private static final class BeltModel extends BakedModelWrapper<IBakedModel> {
        private final Map<String, IBakedModel[]> models;
        private BeltModel(IBakedModel original, Map<String, IBakedModel[]> models) { super(original); this.models = models; }
        // Sloped quads cross block boundaries; 1.12's ambient-occlusion interpolation
        // assumes a unit cube and can produce dark seams at those boundaries.
        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            if (state instanceof IExtendedBlockState extended) {
                IBakedModel[] parts = models.get(extended.getValue(BeltBlock.MODEL));
                if (parts != null) {
                    // Keep a baked surface in the chunk. The animated TESR is an
                    // overlay, so an unavailable or out-of-range tile renderer
                    // cannot make the belt itself disappear.
                    List<BakedQuad> quads = new ArrayList<>(parts[0].getQuads(state, side, random));
                    if (parts[1] != null) quads.addAll(parts[1].getQuads(state, side, random));
                    return quads;
                }
            }
            return originalModel.getQuads(state, side, random);
        }
    }

    /** Roll the horizontal model around its travel axis for shafts on the Y axis. */
    private static final class SidewaysBeltModel extends BakedModelWrapper<IBakedModel> {
        private final List<List<BakedQuad>> faces = new ArrayList<>(7);

        private SidewaysBeltModel(IBakedModel original, String direction) {
            super(original);
            for (int i = 0; i < 7; i++) faces.add(new ArrayList<>());
            for (int i = 0; i < 7; i++) {
                EnumFacing oldSide = i == 6 ? null : EnumFacing.byIndex(i);
                for (BakedQuad quad : original.getQuads(null, oldSide, 0L)) {
                    int[] data = quad.getVertexData().clone();
                    int stride = data.length / 4;
                    for (int v = 0; v < 4; v++) {
                        int at = v * stride;
                        float x = Float.intBitsToFloat(data[at]);
                        float y = Float.intBitsToFloat(data[at + 1]);
                        float z = Float.intBitsToFloat(data[at + 2]);
                        float rolledX = 1F - y, rolledY = x;
                        float newX = direction.equals("east") ? z : direction.equals("west") ? 1F - z
                                : direction.equals("north") ? 1F - rolledX : rolledX;
                        float newZ = direction.equals("east") ? 1F - rolledX : direction.equals("west") ? rolledX
                                : direction.equals("north") ? 1F - z : z;
                        data[at] = Float.floatToRawIntBits(newX);
                        data[at + 1] = Float.floatToRawIntBits(rolledY);
                        data[at + 2] = Float.floatToRawIntBits(newZ);
                    }
                    EnumFacing face = rotateSide(quad.getFace(), direction);
                    BakedQuad transformed = new BakedQuad(data, quad.getTintIndex(), face, quad.getSprite(),
                            quad.shouldApplyDiffuseLighting(), quad.getFormat());
                    faces.get(oldSide == null ? 6 : rotateSide(oldSide, direction).getIndex()).add(transformed);
                }
            }
        }

        private static EnumFacing rotateSide(EnumFacing face, String direction) {
            int x = -face.getYOffset(), y = face.getXOffset(), z = face.getZOffset();
            int rx = direction.equals("east") ? z : direction.equals("west") ? -z
                    : direction.equals("north") ? -x : x;
            int rz = direction.equals("east") ? -x : direction.equals("west") ? x
                    : direction.equals("north") ? -z : z;
            return EnumFacing.getFacingFromVector(rx, y, rz);
        }

        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            return faces.get(side == null ? 6 : side.getIndex());
        }
    }

    /** Split overlapping sloped models at the plane halfway between adjacent belt segments. */
    private static final class SlopedBeltModel extends BakedModelWrapper<IBakedModel> {
        private final List<List<BakedQuad>> faces = new ArrayList<>(7);
        private final int sx, sy, sz;
        private final boolean clipStart, clipEnd;

        private SlopedBeltModel(IBakedModel original, String part, String slope, String direction) {
            super(original);
            sx = direction.equals("east") ? 1 : direction.equals("west") ? -1 : 0;
            sy = slope.equals("up") ? 1 : -1;
            sz = direction.equals("south") ? 1 : direction.equals("north") ? -1 : 0;
            clipStart = !part.equals("start");
            clipEnd = !part.equals("end");
            for (int i = 0; i < 7; i++) {
                EnumFacing side = i == 6 ? null : EnumFacing.byIndex(i);
                List<BakedQuad> clipped = new ArrayList<>();
                for (BakedQuad quad : original.getQuads(null, side, 0L)) clipQuad(quad, clipped);
                faces.add(java.util.Collections.unmodifiableList(clipped));
            }
        }

        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            return faces.get(side == null ? 6 : side.getIndex());
        }

        private double position(int[] vertex) {
            return (sx * (Float.intBitsToFloat(vertex[0]) - 0.5D)
                    + sy * (Float.intBitsToFloat(vertex[1]) - 0.5D)
                    + sz * (Float.intBitsToFloat(vertex[2]) - 0.5D)) * 0.5D;
        }

        private void clipQuad(BakedQuad quad, List<BakedQuad> output) {
            int[] raw = quad.getVertexData();
            int stride = raw.length / 4;
            List<int[]> polygon = new ArrayList<>(4);
            boolean outside = false;
            for (int i = 0; i < 4; i++) {
                int[] vertex = java.util.Arrays.copyOfRange(raw, i * stride, (i + 1) * stride);
                double p = position(vertex);
                outside |= clipStart && p < -0.5D || clipEnd && p > 0.5D;
                polygon.add(vertex);
            }
            if (!outside) { output.add(quad); return; }
            if (clipStart) polygon = clip(polygon, -0.5D, true);
            if (clipEnd) polygon = clip(polygon, 0.5D, false);
            if (polygon.size() < 3) return;
            if (polygon.size() == 4) { addQuad(quad, polygon.get(0), polygon.get(1), polygon.get(2), polygon.get(3), output); return; }
            for (int i = 1; i + 1 < polygon.size(); i++)
                addQuad(quad, polygon.get(0), polygon.get(i), polygon.get(i + 1), polygon.get(i + 1), output);
        }

        private List<int[]> clip(List<int[]> input, double limit, boolean keepAbove) {
            List<int[]> result = new ArrayList<>(input.size() + 1);
            if (input.isEmpty()) return result;
            int[] previous = input.get(input.size() - 1);
            double previousPosition = position(previous);
            boolean previousInside = keepAbove ? previousPosition >= limit : previousPosition <= limit;
            for (int[] current : input) {
                double currentPosition = position(current);
                boolean currentInside = keepAbove ? currentPosition >= limit : currentPosition <= limit;
                if (currentInside != previousInside) {
                    double t = (limit - previousPosition) / (currentPosition - previousPosition);
                    int[] intersection = previous.clone();
                    for (int attribute : new int[]{0, 1, 2, 4, 5}) {
                        float a = Float.intBitsToFloat(previous[attribute]);
                        float b = Float.intBitsToFloat(current[attribute]);
                        intersection[attribute] = Float.floatToRawIntBits((float) (a + (b - a) * t));
                    }
                    result.add(intersection);
                }
                if (currentInside) result.add(current);
                previous = current; previousPosition = currentPosition; previousInside = currentInside;
            }
            return result;
        }

        private static void addQuad(BakedQuad source, int[] a, int[] b, int[] c, int[] d, List<BakedQuad> output) {
            int stride = a.length;
            int[] data = new int[stride * 4];
            System.arraycopy(a, 0, data, 0, stride);
            System.arraycopy(b, 0, data, stride, stride);
            System.arraycopy(c, 0, data, stride * 2, stride);
            System.arraycopy(d, 0, data, stride * 3, stride);
            output.add(new BakedQuad(data, source.getTintIndex(), source.getFace(), source.getSprite(),
                    source.shouldApplyDiffuseLighting(), source.getFormat()));
        }
    }

    private static final class TextureRemapModel extends BakedModelWrapper<IBakedModel> {
        private final TextureAtlasSprite source;
        private final TextureAtlasSprite target;
        private TextureRemapModel(IBakedModel original, TextureAtlasSprite source, TextureAtlasSprite target) {
            super(original); this.source = source; this.target = target;
        }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            List<BakedQuad> quads = originalModel.getQuads(state, side, random);
            List<BakedQuad> result = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                if (quad.getSprite() != source) { result.add(quad); continue; }
                int[] data = quad.getVertexData().clone(); int stride = data.length / 4;
                for (int i = 0; i < 4; i++) {
                    int offset = i * stride + 4;
                    float u = Float.intBitsToFloat(data[offset]);
                    float v = Float.intBitsToFloat(data[offset + 1]);
                    float localU = (u - source.getMinU()) / (source.getMaxU() - source.getMinU());
                    float localV = (v - source.getMinV()) / (source.getMaxV() - source.getMinV());
                    data[offset] = Float.floatToRawIntBits(target.getMinU() + localU * (target.getMaxU() - target.getMinU()));
                    data[offset + 1] = Float.floatToRawIntBits(target.getMinV() + localV * (target.getMaxV() - target.getMinV()));
                }
                result.add(new BakedQuad(data, quad.getTintIndex(), quad.getFace(), target,
                        quad.shouldApplyDiffuseLighting(), quad.getFormat()));
            }
            return result;
        }
    }
}
