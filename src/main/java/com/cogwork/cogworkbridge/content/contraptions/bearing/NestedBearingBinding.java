package com.cogwork.cogworkbridge.content.contraptions.bearing;

import java.util.UUID;

/**
 * Pure retention rules for a nested bearing child. A transient entity lookup
 * miss must never turn into world restoration.
 */
final class NestedBearingBinding {

    enum State {
        NEEDS_SPAWN,
        WAITING_FOR_ENTITY,
        READY_TO_BIND,
        READY_TO_RESTORE
    }

    private NestedBearingBinding() {
    }

    static State classify(boolean explicitRestore,
                          boolean expectedEntityIdPresent,
                          boolean childEntityAlive,
                          boolean snapshotAvailable) {
        if (explicitRestore) {
            return childEntityAlive
                    ? State.READY_TO_RESTORE
                    : snapshotAvailable ? State.READY_TO_RESTORE
                    : State.WAITING_FOR_ENTITY;
        }
        if (childEntityAlive) {
            return State.READY_TO_BIND;
        }
        if (snapshotAvailable && expectedEntityIdPresent) {
            return State.WAITING_FOR_ENTITY;
        }
        return snapshotAvailable
                ? State.NEEDS_SPAWN : State.WAITING_FOR_ENTITY;
    }

    static boolean sameEntity(UUID expected, UUID candidate) {
        return expected != null && expected.equals(candidate);
    }
}
