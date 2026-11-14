package com.cogwork.cogworkbridge.content.contraptions.bearing;

import java.util.UUID;
import net.minecraft.util.math.BlockPos;

/** Ownership and recovery callbacks for structures driven by a linear actuator. */
public interface LinearContraptionController {
    boolean ownsLinearContraption(UUID id);
    void onContraptionRestored(UUID id);
    default boolean ignoresStructureBlock(BlockPos pos) { return false; }
}
