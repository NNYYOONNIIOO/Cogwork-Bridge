package com.cogwork.cogworkbridge.content.contraptions.bearing;

import java.util.UUID;

/** Owner of an independently rotating structure such as a clock hand. */
public interface RotaryContraptionController {
    boolean ownsRotaryContraption(UUID id);
    default void onRotaryContraptionRestored(UUID id) { }
}
