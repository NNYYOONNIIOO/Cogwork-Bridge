package com.cogwork.cogworkbridge.content.contraptions.bearing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContraptionInteractionMathTest {

    @Test
    void reachIsMeasuredToTheSelectedBlock() {
        assertTrue(ContraptionInteractionMath.withinInteractionReach(
                8.0D, 0.0D, 0.0D));
        assertFalse(ContraptionInteractionMath.withinInteractionReach(
                8.01D, 0.0D, 0.0D));
    }

    @Test
    void distantAnchorCannotRejectAReachableEdgeBlock() {
        assertFalse(ContraptionInteractionMath.withinInteractionReach(
                24.0D, 0.0D, 0.0D));
        assertTrue(ContraptionInteractionMath.withinInteractionReach(
                7.5D, 0.0D, 0.0D));
    }

    @Test
    void reachUsesClosestPointOnTargetBox() {
        assertTrue(ContraptionInteractionMath.intersectsInteractionReach(
                0.0D, 0.0D, 0.0D,
                7.0D, -0.5D, -0.5D, 8.0D, 0.5D, 0.5D));
        assertFalse(ContraptionInteractionMath.intersectsInteractionReach(
                0.0D, 0.0D, 0.0D,
                8.01D, -0.5D, -0.5D, 9.0D, 0.5D, 0.5D));
    }

    @Test
    void removedSnapshotDoesNotReplayWorldWritingExplosionHook() {
        assertFalse(ContraptionInteractionMath.shouldCallBlockExplosionHook());
    }
}
