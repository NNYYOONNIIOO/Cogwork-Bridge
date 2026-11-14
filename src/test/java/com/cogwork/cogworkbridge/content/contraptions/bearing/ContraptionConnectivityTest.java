package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.content.contraptions.glue.GridPos;
import com.cogwork.cogworkbridge.content.contraptions.glue.GlueBox;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContraptionConnectivityTest {

    private static final GridPos A = new GridPos(0, 0, 0);
    private static final GridPos AIR = new GridPos(1, 0, 0);
    private static final GridPos B = new GridPos(2, 0, 0);

    @Test
    void anAirGapBreaksTwoGroupsInsideTheSameGlueBox() {
        Set<GridPos> occupied = Set.of(A, B);

        Set<GridPos> result = ContraptionConnectivity.connectedFrom(
                A, occupied::contains, sameGlue(occupied), ignored -> false, 256);

        assertEquals(Set.of(A), result);
        assertEquals(false, result.contains(AIR));
        assertEquals(false, result.contains(B));
    }

    @Test
    void aFaceConnectedPathStillReachesTheOtherGroup() {
        GridPos middle = new GridPos(1, 0, 0);
        Set<GridPos> occupied = Set.of(A, middle, B);

        Set<GridPos> result = ContraptionConnectivity.connectedFrom(
                A, occupied::contains, sameGlue(occupied), ignored -> false, 256);

        assertEquals(Set.of(A, middle, B), result);
    }

    @Test
    void glueRangeKeepsTheAirSlotWhileActualBondStillStopsAtTheGap() {
        GlueBox glueRange = GlueBox.span(A, B);
        Set<GridPos> captured = Set.of(A, B);

        Set<GridPos> result = ContraptionConnectivity.connectedFrom(
                A, captured::contains,
                (first, second) -> glueRange.contains(first)
                        && glueRange.contains(second),
                ignored -> false, 256);

        assertEquals(Set.of(A), result);
        assertTrue(glueRange.contains(AIR));
    }

    @Test
    void touchingBlocksOutsideTheGlueBondAreNotCaptured() {
        GridPos touching = new GridPos(0, 1, 0);
        Set<GridPos> occupied = Set.of(A, touching);

        Set<GridPos> result = ContraptionConnectivity.connectedFrom(
                A, occupied::contains, (first, second) -> first.equals(A)
                        && second.equals(A), ignored -> false, 256);

        assertEquals(Set.of(A), result);
    }

    @Test
    void terminalBearingIsIncludedWithoutExpandingItsChildStructure() {
        GridPos bearing = new GridPos(1, 0, 0);
        GridPos childBlock = new GridPos(2, 0, 0);
        Set<GridPos> occupied = Set.of(A, bearing, childBlock);

        Set<GridPos> result = ContraptionConnectivity.connectedFrom(
                A, occupied::contains, sameGlue(occupied),
                bearing::equals, 256);

        assertEquals(Set.of(A, bearing), result);
        assertFalse(result.contains(childBlock));
    }

    @Test
    void limitStopsAfterTheBearingRoot() {
        Set<GridPos> occupied = Set.of(A, B);

        Set<GridPos> result = ContraptionConnectivity.connectedFrom(
                A, occupied::contains, sameGlue(occupied), ignored -> false, 1);

        assertEquals(Set.of(A), result);
    }

    private static java.util.function.BiPredicate<GridPos, GridPos> sameGlue(
            Set<GridPos> glue) {
        return (first, second) -> glue.contains(first) && glue.contains(second);
    }
}
