package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.content.contraptions.glue.GlueBox;
import com.cogwork.cogworkbridge.content.contraptions.glue.GridPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BearingContraptionGlueTest {

    @Test
    void glueBoundsFollowTheSameQuarterTurnsAsCapturedBlocks() {
        QuarterTurnGrid.GridPos3 anchor = new QuarterTurnGrid.GridPos3(12, 64, -7);
        GlueBox local = GlueBox.of(-2, 0, -3, 2, 1, 1);
        QuarterTurnGrid.GridPos3 lower = new QuarterTurnGrid.GridPos3(-2, 0, -3);
        QuarterTurnGrid.GridPos3 upper = new QuarterTurnGrid.GridPos3(1, 0, 0);

        for (QuarterTurnGrid.Axis axis : QuarterTurnGrid.Axis.values()) {
            for (int turns = 0; turns < 4; turns++) {
                GlueBox result = QuarterTurnGrid.rotateGlueBox(
                        local, anchor.x(), anchor.y(), anchor.z(), axis, turns * 90.0F);
                QuarterTurnGrid.GridPos3 expectedLower = rotate(anchor, lower, axis, turns);
                QuarterTurnGrid.GridPos3 expectedUpper = rotate(anchor, upper, axis, turns);
                assertEquals(Math.min(expectedLower.x(), expectedUpper.x()),
                        result.minX());
                assertEquals(Math.min(expectedLower.y(), expectedUpper.y()),
                        result.minY());
                assertEquals(Math.min(expectedLower.z(), expectedUpper.z()),
                        result.minZ());
                assertEquals(Math.max(expectedLower.x(), expectedUpper.x()) + 1,
                        result.maxX());
                assertEquals(Math.max(expectedLower.y(), expectedUpper.y()) + 1,
                        result.maxY());
                assertEquals(Math.max(expectedLower.z(), expectedUpper.z()) + 1,
                        result.maxZ());
            }
        }
    }

    @Test
    void halfOpenGlueBoundsRoundTripThroughAllQuarterTurns() {
        QuarterTurnGrid.GridPos3 anchor = new QuarterTurnGrid.GridPos3(-4, 70, 9);
        GlueBox originalRelative = GlueBox.of(-1, 0, -4, 3, 2, 1);

        for (QuarterTurnGrid.Axis axis : QuarterTurnGrid.Axis.values()) {
            GlueBox relative = originalRelative;
            for (int turns = 1; turns <= 4; turns++) {
                GlueBox currentWorld = QuarterTurnGrid.rotateGlueBox(
                        relative, anchor.x(), anchor.y(), anchor.z(), axis, 90.0F);
                QuarterTurnGrid.GridPos3 pivot = rotate(anchor,
                        new QuarterTurnGrid.GridPos3(0, 0, 0), axis, 1);
                QuarterTurnGrid.GridPos3 offset = new QuarterTurnGrid.GridPos3(
                        pivot.x() - anchor.x(),
                        pivot.y() - anchor.y(),
                        pivot.z() - anchor.z());
                relative = GlueBox.of(
                        currentWorld.minX() - anchor.x() - offset.x(),
                        currentWorld.minY() - anchor.y() - offset.y(),
                        currentWorld.minZ() - anchor.z() - offset.z(),
                        currentWorld.maxX() - anchor.x() - offset.x(),
                        currentWorld.maxY() - anchor.y() - offset.y(),
                        currentWorld.maxZ() - anchor.z() - offset.z());
            }
            assertEquals(originalRelative.minX(), relative.minX());
            assertEquals(originalRelative.minY(), relative.minY());
            assertEquals(originalRelative.minZ(), relative.minZ());
            assertEquals(originalRelative.maxX(), relative.maxX());
            assertEquals(originalRelative.maxY(), relative.maxY());
            assertEquals(originalRelative.maxZ(), relative.maxZ());
        }
    }

    @Test
    void halfOpenMaximumIsRotatedAsTheLastOccupiedCorner() {
        QuarterTurnGrid.GridPos3 anchor = new QuarterTurnGrid.GridPos3(3, 5, 7);
        GlueBox oneCell = GlueBox.of(0, 0, 0, 1, 1, 1);

        GlueBox turned = QuarterTurnGrid.rotateGlueBox(
                oneCell, anchor.x(), anchor.y(), anchor.z(),
                QuarterTurnGrid.Axis.Y, 90.0F);

        assertEquals(anchor.x(), turned.minX());
        assertEquals(anchor.y(), turned.minY());
        assertEquals(anchor.z(), turned.minZ());
        assertEquals(anchor.x() + 1, turned.maxX());
        assertEquals(anchor.y() + 1, turned.maxY());
        assertEquals(anchor.z() + 1, turned.maxZ());
    }

    @Test
    void rotatedAirGapCellStaysInsideTheRotatedGlueRange() {
        QuarterTurnGrid.GridPos3 anchor = new QuarterTurnGrid.GridPos3(12, 64, -7);
        GlueBox local = GlueBox.span(new GridPos(0, 0, 0),
                new GridPos(2, 0, 0));

        for (QuarterTurnGrid.Axis axis : QuarterTurnGrid.Axis.values()) {
            for (int turns = 0; turns < 4; turns++) {
                float angle = turns * 90.0F;
                GlueBox rotatedBox = QuarterTurnGrid.rotateGlueBox(
                        local, anchor.x(), anchor.y(), anchor.z(), axis, angle);
                Set<GridPos> rotatedCells = QuarterTurnGrid.rotateGlueCells(
                        local.positions(), anchor.x(), anchor.y(), anchor.z(),
                        axis, angle);
                assertEquals(local.positions().size(), rotatedCells.size());
                assertEquals(rotatedBox.maxX() - rotatedBox.minX(),
                        rotatedBox.sizeX());
                assertEquals(rotatedBox.positions(), rotatedCells);
            }
        }
    }

    private static QuarterTurnGrid.GridPos3 rotate(
            QuarterTurnGrid.GridPos3 anchor, QuarterTurnGrid.GridPos3 local,
            QuarterTurnGrid.Axis axis, int turns) {
        QuarterTurnGrid.GridPos3 rotated = QuarterTurnGrid.rotate(local, axis, turns);
        return new QuarterTurnGrid.GridPos3(
                anchor.x() + rotated.x(),
                anchor.y() + rotated.y(),
                anchor.z() + rotated.z());
    }
}
