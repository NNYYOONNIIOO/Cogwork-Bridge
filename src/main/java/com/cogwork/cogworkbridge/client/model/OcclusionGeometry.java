package com.cogwork.cogworkbridge.client.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.apache.commons.lang3.tuple.Pair;
import javax.vecmath.Matrix4f;

/** Cuts enclosed surfaces once, preserving the exposed geometry and its original UVs. */
public final class OcclusionGeometry {
    private static final double EPSILON = 0.000001D;
    private static final Map<TextureAtlasSprite, OpaquePixels> OPACITY = new IdentityHashMap<>();

    private OcclusionGeometry() { }

    public static synchronized void clear() {
        OPACITY.clear();
    }

    public static IBakedModel optimize(IBakedModel model) {
        if (model.isBuiltInRenderer()) return model;
        List<Box> boxes = solidBoxes(model, null);
        return clip(model, null, boxes, boxes, true);
    }

    /** Clips a rotating part against the volume that stays enclosed at every rotation angle. */
    public static IBakedModel insideHousing(IBakedModel part, IBlockState partState,
                                            IBakedModel housing, IBlockState housingState, EnumFacing.Axis axis) {
        int a = axisIndex(axis), u = (a + 1) % 3, v = (a + 2) % 3;
        List<Box> enclosed = new ArrayList<>();
        for (Box box : solidBoxes(housing, housingState)) {
            double radius = Math.min(Math.min(.5D - box.low[u], box.high[u] - .5D),
                    Math.min(.5D - box.low[v], box.high[v] - .5D));
            if (radius <= EPSILON) continue;
            // The corners of this square stay inside the inscribed cylinder while rotating.
            double half = radius / Math.sqrt(2D);
            double[] low = box.low.clone(), high = box.high.clone();
            low[u] = low[v] = .5D - half;
            high[u] = high[v] = .5D + half;
            enclosed.add(new Box(low, high));
        }
        return enclosed.isEmpty() ? part : clip(part, partState, enclosed, solidBoxes(part, partState), false);
    }

    private static List<Box> solidBoxes(IBakedModel model, @Nullable IBlockState state) {
        if (model instanceof GeometryModel geometry) return geometry.solids;
        if (model.isBuiltInRenderer()) return List.of();
        Set<Rectangle> opaque = new HashSet<>();
        for (int bucket = 0; bucket < 7; bucket++) {
            for (BakedQuad quad : model.getQuads(state, bucket == 6 ? null : EnumFacing.VALUES[bucket], 0L)) {
                Face face = Face.read(quad);
                Rectangle rectangle = face == null ? null : face.rectangle();
                if (rectangle != null && opaque(quad)) opaque.add(rectangle);
            }
        }
        return closedBoxes(opaque);
    }

    private static IBakedModel clip(IBakedModel model, @Nullable IBlockState state, List<Box> boxes,
                                    List<Box> solids, boolean classifyBoundary) {
        List<List<BakedQuad>> buckets = new ArrayList<>(7);
        for (int bucket = 0; bucket < 7; bucket++) {
            List<BakedQuad> quads = model.getQuads(state, bucket == 6 ? null : EnumFacing.VALUES[bucket], 0L);
            buckets.add(quads);
        }
        List<List<BakedQuad>> visible = new ArrayList<>(7);
        for (int bucket = 0; bucket < 7; bucket++) visible.add(new ArrayList<>());
        boolean changed = false;
        for (int bucket = 0; bucket < 7; bucket++) for (BakedQuad quad : buckets.get(bucket)) {
            Face face = Face.read(quad);
            List<BakedQuad> pieces = face == null ? List.of(quad) : face.outside(boxes);
            changed |= pieces.size() != 1 || pieces.getFirst() != quad;
            int destination = bucket;
            // Legacy upstream JSONs often leave boundary surfaces in the unculled bucket.
            // Preserve explicit cull directions; infer only an outward unit-block boundary.
            if (classifyBoundary && bucket == 6) {
                EnumFacing side = quad.getFace();
                if (side != null && face != null && onBoundary(face, side)) {
                    destination = side.getIndex();
                    changed = true;
                }
            }
            visible.get(destination).addAll(pieces);
        }
        if (!changed) return model;
        for (int bucket = 0; bucket < 7; bucket++) visible.set(bucket, Collections.unmodifiableList(visible.get(bucket)));
        return new GeometryModel(model, visible, solids);
    }

    /** The entire rotating face must stay inside the neighbour's unit-square face. */
    public static boolean isBoundaryFace(BakedQuad quad, EnumFacing side) {
        Face face = Face.read(quad);
        if (face == null) return false;
        return onBoundary(face, side) && face.radius(axis(side)) <= .5D + EPSILON;
    }

    private static boolean onBoundary(Face face, EnumFacing side) {
        int axis = axis(side);
        double boundary = side.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1D : 0D;
        if (face.normal[axis] * side.getAxisDirection().getOffset() < 1D - EPSILON) return false;
        for (int a = 0; a < 3; a++) {
            if (a == axis) {
                if (Math.abs(face.low[a] - boundary) > EPSILON || Math.abs(face.high[a] - boundary) > EPSILON) return false;
            } else if (face.low[a] < -EPSILON || face.high[a] > 1D + EPSILON) return false;
        }
        return true;
    }

    /** Full opaque faces of a neighbour, independent of its collision box. */
    public static int opaqueFullFaces(IBakedModel model, IBlockState state) {
        if (model.isBuiltInRenderer()) return 0;
        int mask = 0;
        for (int bucket = 0; bucket < 7; bucket++) {
            for (BakedQuad quad : model.getQuads(state, bucket == 6 ? null : EnumFacing.VALUES[bucket], 0L)) {
                Face face = Face.read(quad);
                Rectangle rectangle = face == null ? null : face.rectangle();
                if (rectangle == null || !opaque(quad)) continue;
                long unit = coordinate(1D);
                if (rectangle.plane != (rectangle.positive ? unit : 0L)
                        || rectangle.u0 != 0L || rectangle.v0 != 0L || rectangle.u1 != unit || rectangle.v1 != unit) continue;
                EnumFacing side = EnumFacing.getFacingFromAxis(rectangle.positive
                        ? EnumFacing.AxisDirection.POSITIVE : EnumFacing.AxisDirection.NEGATIVE,
                        rectangle.axis == 0 ? EnumFacing.Axis.X : rectangle.axis == 1 ? EnumFacing.Axis.Y : EnumFacing.Axis.Z);
                mask |= 1 << side.getIndex();
            }
        }
        return mask;
    }

    /** A dedicated encased-cog model; the other users retain their complete wheel. */
    public static IBakedModel encasedCog(IBakedModel original, EnumFacing.Axis axis, IBakedModel housing) {
        List<Box> boxes = solidBoxes(housing, null);
        if (boxes.isEmpty()) return original;
        List<List<BakedQuad>> buckets = new ArrayList<>(7);
        for (int bucket = 0; bucket < 7; bucket++) {
            List<BakedQuad> kept = new ArrayList<>();
            for (BakedQuad quad : original.getQuads(null, bucket == 6 ? null : EnumFacing.VALUES[bucket], 0L)) {
                Face face = Face.read(quad);
                boolean covered = face != null;
                for (int a = 0; a < 3 && covered; a++) {
                    if (a == axisIndex(axis)) {
                        covered = Math.abs(face.normal[a]) > 1D - EPSILON
                                && (Math.abs(face.low[a] - .375D) < EPSILON && Math.abs(face.high[a] - .375D) < EPSILON
                                || Math.abs(face.low[a] - .625D) < EPSILON && Math.abs(face.high[a] - .625D) < EPSILON);
                    } else {
                        covered = face.low[a] >= .25D - EPSILON && face.high[a] <= .75D + EPSILON;
                    }
                }
                // A resource pack may expose the hub or make its casing translucent.
                // Require the actual housing to cover it throughout a full rotation.
                if (covered) {
                    covered = false;
                    for (Box box : boxes) {
                        if (box.hidesRotating(face, axisIndex(axis))) {
                            covered = true;
                            break;
                        }
                    }
                }
                if (!covered) kept.add(quad);
            }
            buckets.add(Collections.unmodifiableList(kept));
        }
        return new GeometryModel(original, buckets, solidBoxes(original, null));
    }

    private static int axis(EnumFacing face) { return axisIndex(face.getAxis()); }
    private static int axisIndex(EnumFacing.Axis axis) { return axis == EnumFacing.Axis.X ? 0 : axis == EnumFacing.Axis.Y ? 1 : 2; }
    private static long coordinate(double value) { return Math.round(value / EPSILON); }

    private static boolean opaque(BakedQuad quad) {
        VertexFormat format = quad.getFormat();
        for (int i = 0; i < format.getElementCount(); i++) {
            VertexFormatElement element = format.getElement(i);
            if (element.getUsage() != VertexFormatElement.EnumUsage.COLOR) continue;
            if (element.getType() != VertexFormatElement.EnumType.UBYTE || element.getElementCount() != 4
                    || format.getOffset(i) % 4 != 0) return false;
            int offset = format.getOffset(i) / 4, stride = format.getIntegerSize();
            int[] data = quad.getVertexData();
            for (int vertex = 0; vertex < 4; vertex++) if ((data[vertex * stride + offset] >>> 24) != 255) return false;
        }
        return pixels(quad.getSprite()).covers(quad);
    }

    private static synchronized OpaquePixels pixels(TextureAtlasSprite sprite) {
        return OPACITY.computeIfAbsent(sprite, OpaquePixels::new);
    }

    /** Summed transparency across every animation frame, queried only within a face's UVs. */
    private static final class OpaquePixels {
        private final int width, height;
        private final int[] transparent;

        private OpaquePixels(TextureAtlasSprite texture) {
            width = texture.getIconWidth();
            height = texture.getIconHeight();
            boolean[] holes = new boolean[width * height];
            boolean valid = texture.getFrameCount() > 0 && !texture.getIconName().equals("missingno")
                    && !texture.getIconName().equals("minecraft:missingno");
            for (int frame = 0; valid && frame < texture.getFrameCount(); frame++) {
                int[][] data = texture.getFrameTextureData(frame);
                valid = data != null && data.length > 0 && data[0] != null && data[0].length == holes.length;
                if (valid) for (int pixel = 0; pixel < holes.length; pixel++) holes[pixel] |= (data[0][pixel] >>> 24) != 255;
            }
            transparent = new int[(width + 1) * (height + 1)];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int at = (y + 1) * (width + 1) + x + 1;
                transparent[at] = (!valid || holes[y * width + x] ? 1 : 0)
                        + transparent[at - 1] + transparent[at - width - 1] - transparent[at - width - 2];
            }
        }

        private boolean covers(BakedQuad quad) {
            if (width == 0 || height == 0) return false;
            VertexFormat format = quad.getFormat();
            int uv = -1;
            for (int i = 0; i < format.getElementCount(); i++) {
                VertexFormatElement element = format.getElement(i);
                if (element.getUsage() == VertexFormatElement.EnumUsage.UV && element.getIndex() == 0) uv = i;
            }
            if (uv < 0) return false;
            float[] value = new float[4];
            double u0 = Double.POSITIVE_INFINITY, v0 = u0, u1 = Double.NEGATIVE_INFINITY, v1 = u1;
            TextureAtlasSprite sprite = quad.getSprite();
            double du = sprite.getMaxU() - sprite.getMinU(), dv = sprite.getMaxV() - sprite.getMinV();
            if (du <= 0 || dv <= 0) return false;
            for (int vertex = 0; vertex < 4; vertex++) {
                LightUtil.unpack(quad.getVertexData(), value, format, vertex, uv);
                double u = (value[0] - sprite.getMinU()) / du * width;
                double v = (value[1] - sprite.getMinV()) / dv * height;
                if (!Double.isFinite(u) || !Double.isFinite(v) || u < -EPSILON || v < -EPSILON
                        || u > width + EPSILON || v > height + EPSILON) return false;
                u0 = Math.min(u0, u); u1 = Math.max(u1, u);
                v0 = Math.min(v0, v); v1 = Math.max(v1, v);
            }
            int x0 = Math.min(width - 1, Math.max(0, (int) Math.floor(u0 + EPSILON)));
            int y0 = Math.min(height - 1, Math.max(0, (int) Math.floor(v0 + EPSILON)));
            int x1 = Math.min(width, Math.max(x0 + 1, (int) Math.ceil(u1 - EPSILON)));
            int y1 = Math.min(height, Math.max(y0 + 1, (int) Math.ceil(v1 - EPSILON)));
            int stride = width + 1;
            return transparent[y1 * stride + x1] - transparent[y1 * stride + x0]
                    - transparent[y0 * stride + x1] + transparent[y0 * stride + x0] == 0;
        }
    }

    private static List<Box> closedBoxes(Set<Rectangle> faces) {
        List<Box> boxes = new ArrayList<>();
        for (Rectangle west : faces) {
            if (west.axis != 0 || west.positive) continue;
            for (Rectangle east : faces) {
                if (east.axis != 0 || !east.positive || east.plane <= west.plane
                        || west.u0 != east.u0 || west.u1 != east.u1 || west.v0 != east.v0 || west.v1 != east.v1) continue;
                // Rectangle coordinates for X are (Y,Z), for Y are (Z,X), for Z are (X,Y).
                if (!faces.contains(new Rectangle(1, false, west.u0, west.v0, west.v1, west.plane, east.plane))
                        || !faces.contains(new Rectangle(1, true, west.u1, west.v0, west.v1, west.plane, east.plane))
                        || !faces.contains(new Rectangle(2, false, west.v0, west.plane, east.plane, west.u0, west.u1))
                        || !faces.contains(new Rectangle(2, true, west.v1, west.plane, east.plane, west.u0, west.u1))) continue;
                boxes.add(new Box(new double[]{west.plane * EPSILON, west.u0 * EPSILON, west.v0 * EPSILON},
                        new double[]{east.plane * EPSILON, west.u1 * EPSILON, west.v1 * EPSILON}));
            }
        }
        return boxes;
    }

    private record Rectangle(int axis, boolean positive, long plane, long u0, long u1, long v0, long v1) { }

    private record Patch(double u0, double u1, double v0, double v1) {
        private void subtract(double cutU0, double cutU1, double cutV0, double cutV1, List<Patch> output) {
            double left = Math.max(u0, cutU0), right = Math.min(u1, cutU1);
            double bottom = Math.max(v0, cutV0), top = Math.min(v1, cutV1);
            if (right - left < EPSILON || top - bottom < EPSILON) { output.add(this); return; }
            if (left - u0 > EPSILON) output.add(new Patch(u0, left, v0, v1));
            if (u1 - right > EPSILON) output.add(new Patch(right, u1, v0, v1));
            if (bottom - v0 > EPSILON) output.add(new Patch(left, right, v0, bottom));
            if (v1 - top > EPSILON) output.add(new Patch(left, right, top, v1));
        }
    }

    private record Box(double[] low, double[] high) {
        private boolean hidesRotating(Face face, int axis) {
            double front = face.center[axis] + face.normal[axis] * EPSILON * 2;
            if (front <= low[axis] || front >= high[axis]) return false;
            int u = (axis + 1) % 3, v = (axis + 2) % 3;
            double radius = face.radius(axis);
            return low[u] <= .5D - radius && high[u] >= .5D + radius
                    && low[v] <= .5D - radius && high[v] >= .5D + radius;
        }

        private boolean hides(Face face) {
            for (int axis = 0; axis < 3; axis++) {
                if (face.low[axis] < low[axis] - EPSILON / 2 || face.high[axis] > high[axis] + EPSILON / 2) return false;
                // Probe toward the front of the face. Its own outward boundary must remain visible.
                double front = face.center[axis] + face.normal[axis] * EPSILON * 2;
                if (front <= low[axis] || front >= high[axis]) return false;
            }
            return true;
        }
    }

    private static final class Face {
        private final BakedQuad quad;
        private final double[][] vertices = new double[4][3];
        private final double[] low = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        private final double[] high = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        private final double[] center = new double[3], normal = new double[3];

        private Face(BakedQuad quad) { this.quad = quad; }

        private double radius(int axis) {
            int u = (axis + 1) % 3, v = (axis + 2) % 3;
            double radiusSquared = 0D;
            for (double[] vertex : vertices) {
                double x = vertex[u] - .5D, y = vertex[v] - .5D;
                radiusSquared = Math.max(radiusSquared, x * x + y * y);
            }
            return Math.sqrt(radiusSquared);
        }

        private List<BakedQuad> outside(List<Box> boxes) {
            Rectangle rectangle = rectangle();
            if (rectangle == null) {
                for (Box box : boxes) if (box.hides(this)) return List.of();
                return List.of(quad);
            }
            int a = rectangle.axis, u = (a + 1) % 3, v = (a + 2) % 3;
            Patch whole = new Patch(low[u], high[u], low[v], high[v]);
            List<Patch> patches = List.of(whole);
            for (Box box : boxes) {
                // A face's own outward surface must survive; a touching inward surface can go.
                double probe = center[a] + normal[a] * EPSILON * 2D;
                if (probe <= box.low[a] || probe >= box.high[a]) continue;
                List<Patch> next = new ArrayList<>();
                for (Patch patch : patches) patch.subtract(box.low[u], box.high[u], box.low[v], box.high[v], next);
                patches = next;
                if (patches.isEmpty()) return List.of();
            }
            if (patches.size() == 1 && patches.getFirst() == whole) return List.of(quad);
            List<BakedQuad> result = new ArrayList<>(patches.size());
            for (Patch patch : patches) result.add(slice(patch, u, v));
            return result;
        }

        /** Interpolate the original attributes, including rotated UVs, at each new corner. */
        private BakedQuad slice(Patch patch, int u, int v) {
            VertexFormat format = quad.getFormat();
            int[] data = new int[quad.getVertexData().length];
            float[][][] attributes = new float[4][format.getElementCount()][4];
            for (int vertex = 0; vertex < 4; vertex++) for (int element = 0; element < format.getElementCount(); element++) {
                LightUtil.unpack(quad.getVertexData(), attributes[vertex][element], format, vertex, element);
            }
            for (int vertex = 0; vertex < 4; vertex++) {
                double pu = Math.abs(vertices[vertex][u] - low[u]) < EPSILON ? patch.u0 : patch.u1;
                double pv = Math.abs(vertices[vertex][v] - low[v]) < EPSILON ? patch.v0 : patch.v1;
                double fu = (pu - low[u]) / (high[u] - low[u]), fv = (pv - low[v]) / (high[v] - low[v]);
                double[] weights = new double[4];
                for (int corner = 0; corner < 4; corner++) {
                    weights[corner] = (Math.abs(vertices[corner][u] - low[u]) < EPSILON ? 1D - fu : fu)
                            * (Math.abs(vertices[corner][v] - low[v]) < EPSILON ? 1D - fv : fv);
                }
                for (int element = 0; element < format.getElementCount(); element++) {
                    float[] value = new float[4];
                    for (int component = 0; component < 4; component++) {
                        double sum = 0D;
                        for (int corner = 0; corner < 4; corner++) sum += attributes[corner][element][component] * weights[corner];
                        value[component] = (float) sum;
                    }
                    LightUtil.pack(value, data, format, vertex, element);
                }
            }
            return new BakedQuad(data, quad.getTintIndex(), quad.getFace(), quad.getSprite(),
                    quad.shouldApplyDiffuseLighting(), format);
        }

        @Nullable private static Face read(BakedQuad quad) {
            VertexFormat format = quad.getFormat();
            int offset = -1;
            for (int i = 0; i < format.getElementCount(); i++) {
                VertexFormatElement element = format.getElement(i);
                if (element.getUsage() == VertexFormatElement.EnumUsage.POSITION
                        && element.getType() == VertexFormatElement.EnumType.FLOAT && element.getElementCount() >= 3) {
                    if (format.getOffset(i) % 4 != 0) return null;
                    offset = format.getOffset(i) / 4;
                    break;
                }
            }
            int[] data = quad.getVertexData();
            int stride = format.getIntegerSize();
            if (offset < 0 || data.length != stride * 4 || offset + 2 >= stride) return null;
            Face face = new Face(quad);
            for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                double value = Float.intBitsToFloat(data[vertex * stride + offset + axis]);
                if (!Double.isFinite(value)) return null;
                face.vertices[vertex][axis] = value;
                face.low[axis] = Math.min(face.low[axis], value);
                face.high[axis] = Math.max(face.high[axis], value);
                face.center[axis] += value / 4;
            }
            double[] a = face.vertices[0], b = face.vertices[1], c = face.vertices[2];
            for (int axis = 0; axis < 3; axis++) {
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                face.normal[axis] = (b[u] - a[u]) * (c[v] - a[v]) - (b[v] - a[v]) * (c[u] - a[u]);
            }
            double length = Math.sqrt(face.normal[0] * face.normal[0] + face.normal[1] * face.normal[1] + face.normal[2] * face.normal[2]);
            if (length < EPSILON * EPSILON) return null;
            for (int axis = 0; axis < 3; axis++) face.normal[axis] /= length;
            return face;
        }

        @Nullable private Rectangle rectangle() {
            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(normal[axis]) < 1D - EPSILON || high[axis] - low[axis] > EPSILON) continue;
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                if (high[u] - low[u] < EPSILON || high[v] - low[v] < EPSILON) return null;
                int corners = 0;
                for (double[] vertex : vertices) {
                    int x = Math.abs(vertex[u] - low[u]) < EPSILON ? 0 : Math.abs(vertex[u] - high[u]) < EPSILON ? 1 : -1;
                    int y = Math.abs(vertex[v] - low[v]) < EPSILON ? 0 : Math.abs(vertex[v] - high[v]) < EPSILON ? 1 : -1;
                    if (x < 0 || y < 0) return null;
                    corners |= 1 << (x + 2 * y);
                }
                if (corners != 15) return null;
                return new Rectangle(axis, normal[axis] > 0, coordinate(center[axis]), coordinate(low[u]), coordinate(high[u]), coordinate(low[v]), coordinate(high[v]));
            }
            return null;
        }
    }

    private static final class GeometryModel extends BakedModelWrapper<IBakedModel> {
        private final List<List<BakedQuad>> faces;
        private final List<Box> solids;
        private GeometryModel(IBakedModel original, List<List<BakedQuad>> faces, List<Box> solids) {
            super(original);
            this.faces = faces;
            this.solids = List.copyOf(solids);
        }
        @Override public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long random) {
            return faces.get(side == null ? 6 : side.getIndex());
        }

        @Override public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> perspective = originalModel.handlePerspective(type);
            return Pair.of(perspective.getLeft() == originalModel ? this : perspective.getLeft(), perspective.getRight());
        }
    }
}
