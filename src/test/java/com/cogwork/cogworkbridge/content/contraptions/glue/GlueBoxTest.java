package com.cogwork.cogworkbridge.content.contraptions.glue;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlueBoxTest {

    @Test
    void minimizedGlueBoxKeepsItsOuterRangeWhenCapturedBlocksAreDisconnected() {
        GlueBox glue = GlueBox.span(
                new GridPos(0, 0, 0), new GridPos(2, 0, 0));
        Set<GridPos> occupied = Set.of(
                new GridPos(0, 0, 0), new GridPos(2, 0, 0));

        GlueBox minimized = glue.minimizeTo(occupied);
        assertEquals(0, minimized.minX());
        assertEquals(3, minimized.maxX());
    }

    @Test
    void airCellsCannotBridgeDetachedBlocks() {
        GlueBox box = GlueBox.span(
                new GridPos(0, 0, 0), new GridPos(2, 0, 0));
        Set<GridPos> connected = Set.of(
                new GridPos(0, 0, 0), new GridPos(2, 0, 0));

        Set<GridPos> selection = box.connectedGroup(
                new GridPos(0, 0, 0), new GridPos(2, 0, 0), connected::contains);

        assertTrue(selection.isEmpty());
    }

    @Test
    void minimizingAPersistedAirGapKeepsTheAirSlotForLaterPlacement() {
        GlueBox glue = GlueBox.span(
                new GridPos(0, 0, 0), new GridPos(2, 0, 0));
        Set<GridPos> occupied = Set.of(
                new GridPos(0, 0, 0), new GridPos(2, 0, 0));

        GlueBox minimized = glue.minimizeTo(occupied);
        assertTrue(minimized.contains(new GridPos(1, 0, 0)));
    }

    @Test
    void spanCoversBothEndpointsAndEverythingBetween() {
        GlueBox box = GlueBox.span(new GridPos(2, -1, 4), new GridPos(-3, 2, 4));

        assertEquals(-3, box.minX());
        assertEquals(-1, box.minY());
        assertEquals(4, box.minZ());
        assertEquals(3, box.maxX());
        assertEquals(3, box.maxY());
        assertEquals(5, box.maxZ());
        assertTrue(box.contains(new GridPos(2, 2, 4)));
        assertTrue(box.contains(new GridPos(-3, -1, 4)));
        assertFalse(box.contains(new GridPos(-4, -1, 4)));
    }

    @Test
    void connectedGroupUsesOnlyFaceAdjacentSolidCells() {
        GlueBox box = GlueBox.span(new GridPos(0, 0, 0), new GridPos(2, 0, 0));
        Set<GridPos> solid = new HashSet<>();
        solid.add(new GridPos(0, 0, 0));
        solid.add(new GridPos(1, 0, 0));
        solid.add(new GridPos(2, 0, 0));

        Set<GridPos> group = box.connectedGroup(new GridPos(0, 0, 0),
                new GridPos(2, 0, 0), solid::contains);
        assertEquals(3, group.size());

        solid.remove(new GridPos(1, 0, 0));
        assertTrue(box.connectedGroup(new GridPos(0, 0, 0),
                new GridPos(2, 0, 0), solid::contains).isEmpty());
    }

    @Test
    void diagonalNeighboursAreNotPartOfTheSelectionGroup() {
        GlueBox box = GlueBox.span(new GridPos(0, 0, 0), new GridPos(1, 1, 1));
        Set<GridPos> solid = Set.of(new GridPos(0, 0, 0), new GridPos(1, 1, 1));

        assertTrue(box.connectedGroup(new GridPos(0, 0, 0),
                new GridPos(1, 1, 1), solid::contains).isEmpty());
    }

    @Test
    void selectionDistanceLimitMatchesUpstream() {
        assertTrue(GlueBox.withinSelectionDistance(
                new GridPos(0, 0, 0), new GridPos(24, 0, 0)));
        assertTrue(GlueBox.withinSelectionDistance(
                new GridPos(0, 0, 0), new GridPos(25, 0, 0)));
        assertFalse(GlueBox.withinSelectionDistance(
                new GridPos(0, 0, 0), new GridPos(26, 0, 0)));
    }

    @Test
    void selectionDistanceUsesSquaredEuclideanDistance() {
        assertTrue(GlueBox.withinSelectionDistance(
                new GridPos(0, 0, 0), new GridPos(15, 20, 0)));
        assertFalse(GlueBox.withinSelectionDistance(
                new GridPos(0, 0, 0), new GridPos(16, 20, 0)));
    }

    @Test
    void durabilityBoundaryMatchesTheItemContract() {
        assertTrue(com.cogwork.cogworkbridge.content.contraptions.glue.GlueSelectionRules
                .canAfford(false, 98, 99));
        assertFalse(com.cogwork.cogworkbridge.content.contraptions.glue.GlueSelectionRules
                .canAfford(false, 99, 99));
        assertTrue(com.cogwork.cogworkbridge.content.contraptions.glue.GlueSelectionRules
                .canAfford(true, 99, 99));
    }

    @Test
    void croppingAndIntersectionKeepHalfOpenBlockBounds() {
        GlueBox first = GlueBox.span(new GridPos(0, 0, 0), new GridPos(3, 2, 2));
        GlueBox second = GlueBox.span(new GridPos(2, 1, 0), new GridPos(5, 4, 2));

        assertTrue(first.intersects(second));
        GlueBox crop = first.crop(second);
        assertEquals(2, crop.minX());
        assertEquals(1, crop.minY());
        assertEquals(4, crop.maxX());
        assertEquals(3, crop.maxY());
        assertTrue(crop.positions().contains(new GridPos(3, 2, 1)));
        assertFalse(crop.positions().contains(new GridPos(2, 0, 1)));
    }

    @Test
    void croppingToStructureBoundsDropsFarAwayGlueExtents() {
        GlueBox glue = GlueBox.of(-10, -2, -10, 20, 8, 20);
        GlueBox structure = GlueBox.of(2, 4, 3, 5, 7, 6);
        GlueBox crop = glue.crop(structure);
        assertEquals(2, crop.minX());
        assertEquals(4, crop.minY());
        assertEquals(3, crop.minZ());
        assertEquals(5, crop.maxX());
        assertEquals(7, crop.maxY());
        assertEquals(6, crop.maxZ());
    }

    @Test
    void glueIsMinimizedToCapturedBlockCentres() {
        GlueBox glue = GlueBox.of(0, 0, 0, 8, 3, 2);
        Set<GridPos> blocks = new LinkedHashSet<>();
        blocks.add(new GridPos(1, 1, 0));
        blocks.add(new GridPos(2, 1, 0));
        blocks.add(new GridPos(3, 1, 0));

        GlueBox minimized = glue.minimizeTo(blocks);

        assertEquals(1, minimized.minX());
        assertEquals(1, minimized.minY());
        assertEquals(0, minimized.minZ());
        assertEquals(4, minimized.maxX());
        assertEquals(2, minimized.maxY());
        assertEquals(1, minimized.maxZ());
    }

    @Test
    void singleBlockGlueIsNotRestored() {
        Set<GridPos> blocks = new LinkedHashSet<>();
        blocks.add(new GridPos(1, 1, 0));

        assertNull(GlueBox.of(0, 0, 0, 8, 3, 2)
                .minimizeTo(blocks));
    }

}
