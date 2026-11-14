package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared client/server adaptation of Create 1.21.1 TrackPlacement and BezierConnection. */
final class TrackPlacementGeometry {
    record Plan(Map<BlockPos, TrackShape> tracks, TrackCurve curve, String error) { }
    private static final Vec3d UP = new Vec3d(0, 1, 0);
    private TrackPlacementGeometry() { }

    static Plan plan(BlockPos first, TrackShape a, BlockPos second, TrackShape b, boolean maximise) {
        if (a.junction() || b.junction()) return failure("junction_start");
        Map<BlockPos, TrackShape> tracks = new LinkedHashMap<>();
        tracks.put(first, a); tracks.put(second, b);
        Vec3d axis1 = a.axis(), axis2 = b.axis();
        Vec3d between = new Vec3d(second.subtract(first));
        // A completely straight connection uses actual track blocks, including matching slopes.
        if (axis1.crossProduct(axis2).lengthSquared() < 1E-8 && axis1.crossProduct(between).lengthSquared() < 1E-8) {
            int distance = a.x != 0 ? (second.getX() - first.getX()) / a.x : (second.getZ() - first.getZ()) / a.z;
            for (int i = 1; i < Math.abs(distance); i++) tracks.put(first.add(a.x * i * Integer.signum(distance),
                    a.y * i * Integer.signum(distance), a.z * i * Integer.signum(distance)), a);
            return new Plan(tracks, null, null);
        }
        if (axis1.dotProduct(between) < 0) axis1 = axis1.scale(-1);
        if (axis2.dotProduct(between) > 0) axis2 = axis2.scale(-1);
        Vec3d end1 = start(first, a, axis1), end2 = start(second, b, axis2);
        Vec3d n1 = axis1.normalize(), n2 = axis2.normalize();
        double[] intersection = intersect(end1, end2, n1, n2, 0);
        boolean parallel = intersection == null;
        if (!parallel && (intersection[0] < 0 || intersection[1] < 0)) {
            axis2 = axis2.scale(-1); n2 = axis2.normalize(); end2 = start(second, b, axis2);
            intersection = intersect(end1, end2, n1, n2, 0);
        }
        double ascend = end2.y - end1.y, absAscend = Math.abs(ascend);
        Vec3d normal1 = normal(a), normal2 = normal(b);
        boolean slope = normal1.squareDistanceTo(normal2) > 1E-6;
        boolean sameLine = false;
        int extent1 = 0, extent2 = 0;
        if (parallel) {
            double[] s = intersect(end1, end2, n1, n2.crossProduct(UP), 0);
            if (s == null) return failure("unavailable");
            double t = Math.abs(s[0]), u = Math.abs(s[1]);
            sameLine = u < 1E-5;
            if (!sameLine) {
                if (s[0] < 0) return failure("too_sharp");
                if (absAscend > 1E-5 || Math.abs(n1.y) > 1E-5) return failure("ascending_s_curve");
                double target = u <= 1 ? 3 : u * 2;
                if (t < target) return failure("too_sharp");
                int correction = maximise ? 0 : (int) ((t - target) / axis1.length());
                extent1 = (correction + 1) / 2; extent2 = correction / 2;
            }
        }
        if (slope) {
            if (!sameLine) return failure("slope_turn");
            if (Math.abs(normal1.dotProduct(normal2)) < 1E-5) return failure("opposing_slopes");
            if ((axis1.y < 0 || axis2.y > 0) && ascend > 0
                    || (axis1.y > 0 || axis2.y < 0) && ascend < 0) return failure("too_steep");
            double[] vertical = intersect(end1, end2, n1, n2, Math.abs(axis1.x) < 1E-5 ? 1 : 2);
            if (vertical == null || vertical[0] < 0 || vertical[1] < 0) return failure("too_sharp");
            double dist1 = Math.abs(vertical[0] / axis1.length()), dist2 = Math.abs(vertical[1] / axis2.length());
            double turn = Math.min(dist1, dist2);
            if (turn < 2) return failure("too_sharp");
            extent1 = (int) Math.round(Math.max(0, dist1 - dist2));
            extent2 = (int) Math.round(Math.max(0, dist2 - dist1));
            if (!maximise) { extent1 += (int) (turn - 2); extent2 += (int) (turn - 2); }
        } else if (sameLine && absAscend > 1E-5) {
            if (Math.abs(axis1.y) > 1E-5) return failure("ascending_s_curve");
            double distance = Math.abs(end2.subtract(end1).dotProduct(n1));
            double minimum = Math.max(absAscend < 4 ? absAscend * 4 : absAscend * 3, 6);
            if (distance < minimum) return failure("too_steep");
            int correction = maximise ? 0 : (int) ((distance - minimum) / axis1.length());
            extent1 = (correction + 1) / 2; extent2 = correction / 2;
        }
        if (!parallel) {
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, n1.dotProduct(n2)))));
            if (angle < 60) return failure("turn_90");
            if (intersection[0] < 0 || intersection[1] < 0) return failure("too_sharp");
            double dist1 = intersection[0], dist2 = intersection[1];
            double turn = Math.min(dist1, dist2) - .1;
            boolean ninety = Math.abs(n1.dotProduct(n2)) < 1E-5;
            double minimum = ninety ? 7 : 3.25;
            double fit = minimum + (ninety ? Math.max(0, absAscend - 3) * 2 : Math.max(0, absAscend - 1.5) * 1.5);
            if (turn < minimum) return failure("too_sharp");
            if (turn < fit) return failure("too_steep");
            extent1 = (int) Math.floor((Math.max(0, dist1 - dist2) + (maximise ? 0 : turn - fit)) / axis1.length());
            extent2 = (int) Math.floor((Math.max(0, dist2 - dist1) + (maximise ? 0 : turn - fit)) / axis2.length());
        }
        BlockPos anchor1 = extend(tracks, first, a, axis1, extent1);
        BlockPos anchor2 = extend(tracks, second, b, axis2, extent2);
        end1 = end1.add(axis1.scale(extent1)); end2 = end2.add(axis2.scale(extent2));
        if (anchor1.equals(anchor2) || end1.distanceTo(end2) < 1) return failure("too_sharp");
        double handle = handleLength(end1, end2, n1, n2);
        TrackCurve trial = new TrackCurve(anchor1, anchor2, end1, end2, end1.add(n1.scale(handle)), end2.add(n2.scale(handle)), 1);
        TrackCurve curve = new TrackCurve(anchor1, anchor2, end1, end2, trial.control1, trial.control2, (int) Math.ceil(trial.length()));
        return new Plan(tracks, curve, null);
    }
    private static Plan failure(String error) { return new Plan(new LinkedHashMap<>(), null, error); }
    private static Vec3d start(BlockPos pos, TrackShape shape, Vec3d axis) {
        return new Vec3d(pos.getX() + .5, pos.getY() + (shape.slope() ? .5 : 0), pos.getZ() + .5).add(axis.scale(.5));
    }
    private static Vec3d normal(TrackShape shape) {
        return new Vec3d(-shape.x * shape.y, shape.x * shape.x + shape.z * shape.z, -shape.z * shape.y).normalize();
    }
    private static BlockPos extend(Map<BlockPos, TrackShape> tracks, BlockPos start, TrackShape shape, Vec3d axis, int extent) {
        BlockPos end = start;
        for (int i = 1; i <= extent; i++) {
            end = start.add((int) Math.round(axis.x * i), (int) Math.round(axis.y * i), (int) Math.round(axis.z * i));
            tracks.put(end, shape);
        }
        return end;
    }
    /** Solve two rays in XZ (0), ZY (1), or XY (2). */
    private static double[] intersect(Vec3d a, Vec3d b, Vec3d x, Vec3d y, int plane) {
        double ax = plane == 1 ? a.z : a.x, ay = plane == 0 ? a.z : a.y;
        double bx = plane == 1 ? b.z : b.x, by = plane == 0 ? b.z : b.y;
        double xx = plane == 1 ? x.z : x.x, xy = plane == 0 ? x.z : x.y;
        double yx = plane == 1 ? y.z : y.x, yy = plane == 0 ? y.z : y.y;
        double det = xx * yy - xy * yx;
        if (Math.abs(det) < 1E-6) return null;
        return new double[]{((bx - ax) * yy - (by - ay) * yx) / det,
                ((bx - ax) * xy - (by - ay) * xx) / det};
    }
    private static double handleLength(Vec3d start, Vec3d end, Vec3d a, Vec3d b) {
        double angle = (Math.atan2(-b.z, -b.x) - Math.atan2(a.z, a.x) + Math.PI * 2) % (Math.PI * 2);
        angle = Math.min(angle, Math.PI * 2 - angle);
        if (angle < 1E-5) {
            double[] s = intersect(start, end, a, b.crossProduct(UP), 0);
            if (s != null) {
                double min = Math.min(Math.abs(s[0]), Math.abs(s[1])), max = Math.max(Math.abs(s[0]), Math.abs(s[1]));
                if (min > 1.2 && max / min > 1 && max / min < 3) return max - min;
            }
            return start.distanceTo(end) / 3;
        }
        double[] radius = intersect(start, end, a.crossProduct(UP), b.crossProduct(UP), 0);
        return radius == null ? start.distanceTo(end) / 3 : Math.max(1E-5, Math.abs(radius[1]) * 4 / 3 * Math.tan(angle / 4));
    }
}
