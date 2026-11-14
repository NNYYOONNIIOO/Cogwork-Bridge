package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.content.contraptions.glue.GlueBox;
import com.cogwork.cogworkbridge.content.contraptions.glue.GridPos;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Integer-only quarter-turn mapping shared by captured blocks and glue bounds.
 *
 * <p>Coordinates are transformed before adding a world anchor. This keeps a
 * half-open {@link GlueBox} aligned with the blocks mapped by the same turns.</p>
 */
public final class QuarterTurnGrid {

    private QuarterTurnGrid() {
    }

    public static GridPos3 rotate(GridPos3 local, Axis axis, int turns) {
        int x = local.x();
        int y = local.y();
        int z = local.z();
        for (int turn = 0; turn < Math.floorMod(turns, 4); turn++) {
            switch (axis) {
                case X:
                    int nextY = -z;
                    int nextZ = y;
                    y = nextY;
                    z = nextZ;
                    break;
                case Z:
                    int nextX = -y;
                    nextY = x;
                    x = nextX;
                    y = nextY;
                    break;
                case Y:
                default:
                    nextX = z;
                    nextZ = -x;
                    x = nextX;
                    z = nextZ;
                    break;
            }
        }
        return new GridPos3(x, y, z);
    }

    public static GlueBox rotateGlueBox(GlueBox relative, int anchorX,
                                        int anchorY, int anchorZ,
                                        Axis axis, float angle) {
        int turns = Math.floorMod(Math.round(angle / 90.0F), 4);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x : new int[]{relative.minX(), relative.maxX() - 1}) {
            for (int y : new int[]{relative.minY(), relative.maxY() - 1}) {
                for (int z : new int[]{relative.minZ(), relative.maxZ() - 1}) {
                    GridPos3 rotated = rotate(new GridPos3(x, y, z), axis, turns);
                    minX = Math.min(minX, rotated.x());
                    minY = Math.min(minY, rotated.y());
                    minZ = Math.min(minZ, rotated.z());
                    maxX = Math.max(maxX, rotated.x());
                    maxY = Math.max(maxY, rotated.y());
                    maxZ = Math.max(maxZ, rotated.z());
                }
            }
        }
        return GlueBox.of(anchorX + minX, anchorY + minY, anchorZ + minZ,
                anchorX + maxX + 1, anchorY + maxY + 1, anchorZ + maxZ + 1);
    }

    public static Set<GridPos> rotateGlueCells(Set<GridPos> relative,
                                               int anchorX, int anchorY,
                                               int anchorZ, Axis axis,
                                               float angle) {
        int turns = Math.floorMod(Math.round(angle / 90.0F), 4);
        Set<GridPos> rotated = new LinkedHashSet<>();
        for (GridPos pos : relative) {
            GridPos3 mapped = rotate(
                    new GridPos3(pos.x(), pos.y(), pos.z()), axis, turns);
            rotated.add(new GridPos(anchorX + mapped.x(),
                    anchorY + mapped.y(), anchorZ + mapped.z()));
        }
        return Set.copyOf(rotated);
    }

    public enum Axis {
        X,
        Y,
        Z
    }

    public record GridPos3(int x, int y, int z) {
    }
}
