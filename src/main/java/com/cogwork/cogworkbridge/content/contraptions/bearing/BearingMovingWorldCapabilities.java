package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.EntityMovingWorld;
import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.MovingWorldCapabilities;
import net.minecraft.entity.Entity;

/** MovingWorld capabilities for a non-ridable contraption. */
final class BearingMovingWorldCapabilities extends MovingWorldCapabilities {

    BearingMovingWorldCapabilities(EntityMovingWorld movingWorld) {
        super(movingWorld, true);
    }

    @Override
    public boolean mountEntity(Entity entity) {
        return false;
    }
}
