package com.cogwork.cogworkbridge.content.contraptions.bearing;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedBearingBindingTest {

    @Test
    void recordedMissingChildIsRetriedBeforeSpawnIsAllowed() {
        assertEquals(NestedBearingBinding.State.WAITING_FOR_ENTITY,
                NestedBearingBinding.classify(false, true, false, true));
        assertEquals(NestedBearingBinding.State.NEEDS_SPAWN,
                NestedBearingBinding.classify(false, false, false, true));
        assertEquals(NestedBearingBinding.State.WAITING_FOR_ENTITY,
                NestedBearingBinding.classify(false, false, false, false));
    }

    @Test
    void explicitRestoreUsesTheLoadedChildEntity() {
        assertEquals(NestedBearingBinding.State.READY_TO_RESTORE,
                NestedBearingBinding.classify(true, true, true, true));
        assertEquals(NestedBearingBinding.State.READY_TO_RESTORE,
                NestedBearingBinding.classify(true, true, true, false));
    }

    @Test
    void loadedChildIsBoundWithoutBeingRestoredAsWorldBlocks() {
        assertEquals(NestedBearingBinding.State.READY_TO_BIND,
                NestedBearingBinding.classify(false, true, true, true));
    }

    @Test
    void explicitParentTeardownRestoresExactlyOnce() {
        assertEquals(NestedBearingBinding.State.READY_TO_RESTORE,
                NestedBearingBinding.classify(true, true, false, true));
    }

    @Test
    void entityIdentityMustMatch() {
        UUID expected = UUID.randomUUID();
        assertTrue(NestedBearingBinding.sameEntity(expected, expected));
        assertFalse(NestedBearingBinding.sameEntity(expected, UUID.randomUUID()));
        assertFalse(NestedBearingBinding.sameEntity(null, null));
    }
}
