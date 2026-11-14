package com.cogwork.cogworkbridge.content.contraptions.transform;

import net.minecraft.util.EnumFacing;

import java.util.function.Function;

/**
 * Pure rotation transform shared by bearing animation, contraption rendering,
 * selection, and collision. Angles use Minecraft tick speed directly; callers
 * must not add another per-tick multiplier.
 */
public final class ContraptionTransform {

    public enum Axis { X, Y, Z }

    public record Point(double x, double y, double z) {
        public Point add(double dx, double dy, double dz) {
            return new Point(x + dx, y + dy, z + dz);
        }

        public Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        public Point multiply(double scale) {
            return new Point(x * scale, y * scale, z * scale);
        }

        public Point rotate(Axis axis, double degrees) {
            double normalized = normalize(degrees);
            if (normalized == 0.0D) {
                return this;
            }
            if (normalized == 90.0D) {
                return switch (axis) {
                    case X -> new Point(x, -z, y);
                    case Y -> new Point(z, y, -x);
                    case Z -> new Point(-y, x, z);
                };
            }
            if (normalized == 180.0D) {
                return switch (axis) {
                    case X -> new Point(x, -y, -z);
                    case Y -> new Point(-x, y, -z);
                    case Z -> new Point(-x, -y, z);
                };
            }
            if (normalized == 270.0D) {
                return switch (axis) {
                    case X -> new Point(x, z, -y);
                    case Y -> new Point(-z, y, x);
                    case Z -> new Point(y, -x, z);
                };
            }
            double radians = Math.toRadians(normalized);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return switch (axis) {
                case X -> new Point(x, y * cos - z * sin, y * sin + z * cos);
                case Y -> new Point(x * cos + z * sin, y, -x * sin + z * cos);
                case Z -> new Point(x * cos - y * sin, x * sin + y * cos, z);
            };
        }
    }

    public record Box(double minX, double minY, double minZ,
                      double maxX, double maxY, double maxZ) {
        public Box {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("invalid box");
            }
        }

        public Box rotate(Axis axis, double degrees) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (double x : new double[]{this.minX, this.maxX}) {
                for (double y : new double[]{this.minY, this.maxY}) {
                    for (double z : new double[]{this.minZ, this.maxZ}) {
                        Point point = new Point(x, y, z).rotate(axis, degrees);
                        minX = Math.min(minX, point.x());
                        minY = Math.min(minY, point.y());
                        minZ = Math.min(minZ, point.z());
                        maxX = Math.max(maxX, point.x());
                        maxY = Math.max(maxY, point.y());
                        maxZ = Math.max(maxZ, point.z());
                    }
                }
            }
            return new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public Box offset(Point center) {
            return new Box(minX + center.x(), minY + center.y(), minZ + center.z(),
                    maxX + center.x(), maxY + center.y(), maxZ + center.z());
        }

        public Box translate(double dx, double dy, double dz) {
            return new Box(minX + dx, minY + dy, minZ + dz,
                    maxX + dx, maxY + dy, maxZ + dz);
        }

        public double centerX() {
            return (minX + maxX) * 0.5D;
        }

        public double centerY() {
            return (minY + maxY) * 0.5D;
        }

        public double centerZ() {
            return (minZ + maxZ) * 0.5D;
        }
    }

    private ContraptionTransform() {
    }

    /**
     * Transforms a point from a structure's local grid into world space.
     * Nested structures compose parent transforms before their own transform,
     * matching the rendering and collision evaluation order.
     */
    public static Point transformWorld(
            Point localPoint, Point pivot, Axis axis, double degrees,
            Point parentOrigin, double parentX, double parentY, double parentZ) {
        Point parentLocal = new Point(
                localPoint.x() + parentX,
                localPoint.y() + parentY,
                localPoint.z() + parentZ);
        Point childWorld = parentLocal.subtract(pivot)
                .rotate(axis, degrees)
                .add(pivot.x(), pivot.y(), pivot.z());
        return childWorld.add(parentOrigin.x(), parentOrigin.y(), parentOrigin.z());
    }

    /** One tick's angular change, matching Create's 0.3 degrees per RPM. */
    public static double advance(double angle, double speed) {
        if (speed == 0.0D) {
            return normalize(angle);
        }
        return ((angle + speed * 0.3D) % 360.0D + 360.0D) % 360.0D;
    }

    /** Shortest-path interpolation without wrapping at the 0/360 boundary. */
    public static double interpolate(double previous, double current, double partial) {
        double delta = current - previous;
        while (delta < -180.0D) {
            delta += 360.0D;
        }
        while (delta > 180.0D) {
            delta -= 360.0D;
        }
        return normalize(previous + delta * partial);
    }

    /** Signed shortest rotation from one angle to another. */
    public static double shortestDelta(double from, double to) {
        double delta = normalize(to) - normalize(from);
        if (delta > 180.0D) {
            delta -= 360.0D;
        } else if (delta < -180.0D) {
            delta += 360.0D;
        }
        return delta;
    }

    public static double clientTickDelta(double speed, double correction) {
        return speed * 0.3D + correction / 3.0D;
    }

    /** Interpolates the current tick delta without reapplying the delta. */
    public static double interpolateClientTick(double previous, double delta,
                                               double partial) {
        return normalizeNear(previous, previous + delta * partial);
    }

    /** Keeps a near-wrap result close to the previous render angle. */
    public static double normalizeNear(double reference, double angle) {
        double normalized = normalize(angle);
        double delta = shortestDelta(reference, normalized);
        return reference + delta;
    }

    public static double normalize(double angle) {
        double normalized = ((angle % 360.0D) + 360.0D) % 360.0D;
        if (normalized < 1.0E-9D || normalized > 360.0D - 1.0E-9D) {
            return 0.0D;
        }
        if (Math.abs(normalized - 90.0D) < 1.0E-9D) {
            return 90.0D;
        }
        if (Math.abs(normalized - 180.0D) < 1.0E-9D) {
            return 180.0D;
        }
        if (Math.abs(normalized - 270.0D) < 1.0E-9D) {
            return 270.0D;
        }
        return normalized;
    }

    /** Rounds a settle angle to the nearest quarter turn. */
    public static double quarterTurn(double angle) {
        return normalize(Math.rint(normalize(angle) / 90.0D) * 90.0D);
    }

    public static Axis from(EnumFacing.Axis axis) {
        return switch (axis) {
            case X -> Axis.X;
            case Y -> Axis.Y;
            case Z -> Axis.Z;
        };
    }

    /** Rotates a point around the centre of an integer block cell. */
    public static Point rotateAroundBlockCenter(double x, double y, double z,
                                                Point blockCenter, Axis axis,
                                                double degrees) {
        return new Point(x, y, z).subtract(blockCenter)
                .rotate(axis, degrees)
                .add(blockCenter.x(), blockCenter.y(), blockCenter.z());
    }

    public static Box rotateAroundBlockCenter(Box box, Point blockCenter,
                                              Axis axis, double degrees) {
        Box local = new Box(
                box.minX() - blockCenter.x(),
                box.minY() - blockCenter.y(),
                box.minZ() - blockCenter.z(),
                box.maxX() - blockCenter.x(),
                box.maxY() - blockCenter.y(),
                box.maxZ() - blockCenter.z());
        Box rotated = local.rotate(axis, degrees);
        return rotated.offset(blockCenter);
    }

    /** Encloses all transformed corners with one world-space transform. */
    public static Box transformBox(Box box, Function<Point, Point> transform) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{box.minX(), box.maxX()}) {
            for (double y : new double[]{box.minY(), box.maxY()}) {
                for (double z : new double[]{box.minZ(), box.maxZ()}) {
                    Point world = transform.apply(new Point(x, y, z));
                    minX = Math.min(minX, world.x());
                    minY = Math.min(minY, world.y());
                    minZ = Math.min(minZ, world.z());
                    maxX = Math.max(maxX, world.x());
                    maxY = Math.max(maxY, world.y());
                    maxZ = Math.max(maxZ, world.z());
                }
            }
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Converts a world-space point into the unrotated contraption grid. */
    public static Point toLocal(Point worldPoint, Point pivot,
                                Axis axis, double degrees) {
        return worldPoint.subtract(pivot).rotate(axis, -degrees).add(
                pivot.x(), pivot.y(), pivot.z());
    }
}
