package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.EntityMovingWorld;
import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.MovingWorldHandlerClient;

/** Client-side MovingWorld handler for a contraption that is never ridden. */
final class BearingMovingWorldHandlerClient extends MovingWorldHandlerClient {

    private final EntityMovingWorld movingWorld;

    BearingMovingWorldHandlerClient(EntityMovingWorld movingWorld) {
        super(movingWorld);
        this.movingWorld = movingWorld;
    }

    @Override
    public EntityMovingWorld getMovingWorld() {
        return movingWorld;
    }

    @Override
    public void setMovingWorld(EntityMovingWorld movingWorld) {
        // The owning entity is fixed for this handler's lifetime.
    }
}
