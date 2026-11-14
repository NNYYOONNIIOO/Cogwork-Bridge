package com.cogwork.cogworkbridge.content.contraptions.glue;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable super-glue bounds and pure selection rules. */
public final class GlueBox {

    public static final int MAX_SELECTION_DISTANCE = 25;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private GlueBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static GlueBox span(GridPos first, GridPos second) {
        return new GlueBox(
                Math.min(first.x(), second.x()),
                Math.min(first.y(), second.y()),
                Math.min(first.z(), second.z()),
                Math.max(first.x(), second.x()) + 1,
                Math.max(first.y(), second.y()) + 1,
                Math.max(first.z(), second.z()) + 1);
    }

    public static GlueBox of(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new GlueBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }

    public boolean contains(GridPos pos) {
        return pos.x() >= minX && pos.x() < maxX
                && pos.y() >= minY && pos.y() < maxY
                && pos.z() >= minZ && pos.z() < maxZ;
    }

    public boolean intersects(GlueBox other) {
        return minX < other.maxX && maxX > other.minX
                && minY < other.maxY && maxY > other.minY
                && minZ < other.maxZ && maxZ > other.minZ;
    }

    public GlueBox crop(GlueBox other) {
        return new GlueBox(
                Math.max(minX, other.minX),
                Math.max(minY, other.minY),
                Math.max(minZ, other.minZ),
                Math.min(maxX, other.maxX),
                Math.min(maxY, other.maxY),
                Math.min(maxZ, other.maxZ));
    }

    public GlueBox expand(int amount) {
        return new GlueBox(minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount);
    }

    public GlueBox translated(int dx, int dy, int dz) {
        return new GlueBox(minX + dx, minY + dy, minZ + dz,
                maxX + dx, maxY + dy, maxZ + dz);
    }

    public GlueBox union(GlueBox other) {
        return new GlueBox(
                Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    /**
     * Upstream stores glue as the smallest box containing the captured rigid
     * blocks. A one-cell glue volume has no moving bond and is discarded.
     */
    public GlueBox minimizeTo(Set<GridPos> capturedBlocks) {
        Set<GridPos> inside = new LinkedHashSet<>();
        for (GridPos pos : capturedBlocks) {
            if (contains(pos)) {
                inside.add(pos);
            }
        }

        if (inside.size() < 2) {
            return null;
        }
        int newMinX = Integer.MAX_VALUE;
        int newMinY = Integer.MAX_VALUE;
        int newMinZ = Integer.MAX_VALUE;
        int newMaxX = Integer.MIN_VALUE;
        int newMaxY = Integer.MIN_VALUE;
        int newMaxZ = Integer.MIN_VALUE;
        for (GridPos pos : inside) {
            newMinX = Math.min(newMinX, pos.x());
            newMinY = Math.min(newMinY, pos.y());
            newMinZ = Math.min(newMinZ, pos.z());
            newMaxX = Math.max(newMaxX, pos.x() + 1);
            newMaxY = Math.max(newMaxY, pos.y() + 1);
            newMaxZ = Math.max(newMaxZ, pos.z() + 1);
        }
        return new GlueBox(newMinX, newMinY, newMinZ,
                newMaxX, newMaxY, newMaxZ);
    }

    /** Applies an arbitrary axis rotation to all eight box corners. */
    public Box rotate(com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform.Axis axis,
                      double degrees) {
        com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform.Box rotated =
                new com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform.Box(
                        minX, minY, minZ, maxX, maxY, maxZ).rotate(axis, degrees);
        return new Box(rotated.minX(), rotated.minY(), rotated.minZ(),
                rotated.maxX(), rotated.maxY(), rotated.maxZ());
    }

    public record Box(double minX, double minY, double minZ,
                      double maxX, double maxY, double maxZ) {
    }

    public int sizeX() { return maxX - minX; }
    public Set<GridPos> positions() {
        Set<GridPos> positions = new LinkedHashSet<>();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    positions.add(new GridPos(x, y, z));
                }
            }
        }
        return Collections.unmodifiableSet(positions);
    }

    /** Face-connected group reachable within the selection box. */
    public Set<GridPos> connectedGroup(GridPos first, GridPos second,
                                       Occupancy occupancy) {
        if (!contains(first) || !contains(second) || !occupancy.solid(first)) {
            return Collections.emptySet();
        }

        Deque<GridPos> frontier = new ArrayDeque<>();
        Set<GridPos> visited = new HashSet<>();
        frontier.add(first);
        visited.add(first);

        while (!frontier.isEmpty()) {
            GridPos current = frontier.removeFirst();
            for (Direction direction : Direction.values()) {
                GridPos next = current.offset(direction);
                if (!contains(next) || !occupancy.solid(next) || !visited.add(next)) {
                    continue;
                }
                frontier.addLast(next);
            }
        }
        return visited.contains(second) ? visited : Collections.emptySet();
    }

    public static boolean withinSelectionDistance(GridPos first, GridPos second) {
        int dx = first.x() - second.x();
        int dy = first.y() - second.y();
        int dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz <= MAX_SELECTION_DISTANCE * MAX_SELECTION_DISTANCE;
    }

    public interface Occupancy {
        boolean solid(GridPos pos);
    }
}
