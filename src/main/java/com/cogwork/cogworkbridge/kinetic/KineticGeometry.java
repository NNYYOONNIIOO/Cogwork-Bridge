package com.cogwork.cogworkbridge.kinetic;

import java.util.Objects;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Pure geometry for 1.21.1-style kinetic connections. */
public final class KineticGeometry {

    private KineticGeometry() {
    }

    public enum Kind {
        SHAFT,
        SMALL_COG,
        LARGE_COG
    }

    public enum Axis {
        X, Y, Z
    }

    public enum Face {
        POSITIVE_X, NEGATIVE_X,
        POSITIVE_Y, NEGATIVE_Y,
        POSITIVE_Z, NEGATIVE_Z
    }

    public enum Direction {
        DOWN, UP, NORTH, SOUTH, WEST, EAST
    }

    public static Face faceFor(Direction direction) {
        return switch (direction) {
            case DOWN -> Face.NEGATIVE_Y;
            case UP -> Face.POSITIVE_Y;
            case NORTH -> Face.NEGATIVE_Z;
            case SOUTH -> Face.POSITIVE_Z;
            case WEST -> Face.NEGATIVE_X;
            case EAST -> Face.POSITIVE_X;
        };
    }

    public record Position(int x, int y, int z) {
        public Position add(Position other) {
            return new Position(x + other.x, y + other.y, z + other.z);
        }

        public Position subtract(Position other) {
            return new Position(x - other.x, y - other.y, z - other.z);
        }
    }

    public record Block(Axis axis, Kind kind, Set<Face> shaftFaces) {
        public Block {
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(shaftFaces, "shaftFaces");
            shaftFaces = Collections.unmodifiableSet(EnumSet.copyOf(shaftFaces));
        }

        public static Block shaft(Axis axis) {
            return bidirectional(axis, Kind.SHAFT);
        }

        public static Block smallCog(Axis axis) {
            return bidirectional(axis, Kind.SMALL_COG);
        }

        public static Block largeCog(Axis axis) {
            return bidirectional(axis, Kind.LARGE_COG);
        }

        public static Block bidirectional(Axis axis, Kind kind) {
            return new Block(axis, kind, EnumSet.of(
                    positive(axis), negative(axis)));
        }

        public static Block directional(Axis axis, Kind kind, Face... faces) {
            return new Block(axis, kind, EnumSet.copyOf(java.util.Arrays.asList(faces)));
        }

        public boolean isCog() {
            return kind != Kind.SHAFT;
        }

        public boolean hasShaft(Face face) {
            return shaftFaces.contains(face);
        }
    }

    public static double rotationModifier(Block from, Position fromPos, Block to, Position toPos) {
        Position diff = toPos.subtract(fromPos);
        Axis direction = oneStepDirection(diff);
        int manhattan = Math.abs(diff.x) + Math.abs(diff.y) + Math.abs(diff.z);

        // Upstream first checks the shaft sockets on both endpoints. Cogs expose
        // sockets along their own axis, so a shaft or motor can drive a cog here.
        if (direction != null
                && from.hasShaft(face(direction))
                && to.hasShaft(opposite(face(direction)))) {
            return 1.0D;
        }

        // Small <-> Small: touching in the radial plane. Two small gears
        // never mesh diagonally.
        if (from.kind == Kind.SMALL_COG && to.kind == Kind.SMALL_COG) {
            return manhattan == 1
                    && direction != null
                    && direction != from.axis
                    && from.axis == to.axis ? -1.0D : 0.0D;
        }

        // Large <-> Large: one step along both wheel axes, with different
        // axes, is the only geometry accepted by the 1.21.1 propagator.
        if (from.kind == Kind.LARGE_COG && to.kind == Kind.LARGE_COG) {
            if (!isLargeToLarge(from.axis, to.axis, diff)) {
                return 0.0D;
            }
            boolean sourcePositive = valueOnAxis(from.axis, diff) > 0;
            boolean targetPositive = valueOnAxis(to.axis, diff) > 0;
            return sourcePositive ^ targetPositive ? -1.0D : 1.0D;
        }

        // Large <-> Small: diagonal placement, perpendicular to the shared
        // wheel axis. The direction of the call determines the gear ratio.
        if (from.kind == Kind.LARGE_COG && to.kind == Kind.SMALL_COG) {
            return isLargeToSmall(from.axis, to.axis, diff) ? -2.0D : 0.0D;
        }
        if (from.kind == Kind.SMALL_COG && to.kind == Kind.LARGE_COG) {
            return isLargeToSmall(to.axis, from.axis, diff) ? -0.5D : 0.0D;
        }

        return 0.0D;
    }

    public static java.util.List<Position> candidateOffsets(Block block) {
        java.util.LinkedHashSet<Position> offsets = new java.util.LinkedHashSet<>();
        for (Axis axis : Axis.values()) {
            offsets.add(direction(axis, -1));
            offsets.add(direction(axis, 1));
        }
        if (block.isCog()) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Position offset = new Position(x, y, z);
                        if (valueOnAxis(block.axis, offset) == 0
                                && squared(offset) == 2) {
                            offsets.add(offset);
                        }
                    }
                }
            }
            if (block.kind == Kind.LARGE_COG) {
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            Position offset = new Position(x, y, z);
                            if (squared(offset) == 2) {
                                offsets.add(offset);
                            }
                        }
                    }
                }
            }
        }
        return new java.util.ArrayList<>(offsets);
    }

    public static boolean isLargeToLarge(Axis fromAxis, Axis toAxis, Position diff) {
        if (fromAxis == toAxis) {
            return false;
        }
        for (Axis axis : Axis.values()) {
            int value = valueOnAxis(axis, diff);
            if (axis == fromAxis || axis == toAxis) {
                if (value == 0) {
                    return false;
                }
            } else if (value != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isLargeToSmall(Axis largeAxis, Axis smallAxis, Position diff) {
        if (largeAxis != smallAxis || valueOnAxis(largeAxis, diff) != 0) {
            return false;
        }
        for (Axis axis : Axis.values()) {
            if (axis != largeAxis && Math.abs(valueOnAxis(axis, diff)) != 1) {
                return false;
            }
        }
        return true;
    }

    private static int squared(Position position) {
        return position.x * position.x + position.y * position.y + position.z * position.z;
    }

    private static Axis oneStepDirection(Position diff) {
        int nonZero = 0;
        Axis result = null;
        for (Axis axis : Axis.values()) {
            int value = valueOnAxis(axis, diff);
            if (value != 0) {
                nonZero++;
                result = axis;
                if (Math.abs(value) != 1) {
                    return null;
                }
            }
        }
        return nonZero == 1 && result != null
                ? Axis.valueOf(result.name()) : null;
    }

    private static Position direction(Axis axis, int amount) {
        return switch (axis) {
            case X -> new Position(amount, 0, 0);
            case Y -> new Position(0, amount, 0);
            case Z -> new Position(0, 0, amount);
        };
    }

    private static Face face(Axis axis) {
        return switch (axis) {
            case X -> Face.POSITIVE_X;
            case Y -> Face.POSITIVE_Y;
            case Z -> Face.POSITIVE_Z;
        };
    }

    private static Face positive(Axis axis) {
        return face(axis);
    }

    private static Face negative(Axis axis) {
        return opposite(face(axis));
    }

    private static Face opposite(Face face) {
        return Face.valueOf(
                (face.name().startsWith("POSITIVE_") ? "NEGATIVE_" : "POSITIVE_")
                        + face.name().substring(face.name().indexOf('_') + 1));
    }

    private static int valueOnAxis(Axis axis, Position position) {
        return switch (axis) {
            case X -> position.x;
            case Y -> position.y;
            case Z -> position.z;
        };
    }
}
