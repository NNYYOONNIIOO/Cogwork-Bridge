package com.cogwork.cogworkbridge.kinetic;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** The small interface used by the iterative network resolver. */
public interface NetworkParticipant {

    BlockPos getNetworkPos();

    List<NetworkParticipant> kineticNeighbours();


    void applyNetworkSpeed(float speed, EnumFacing.Axis sourceAxis);

    double signTo(NetworkParticipant next);

    default double speedModifierTo(NetworkParticipant next) {
        return signTo(next);
    }

    /**
     * Computes the speed at one connected neighbour. Most links are fixed ratios;
     * special relays such as the rotation speed controller can impose an absolute
     * output speed.
     */
    default float propagatedSpeedTo(NetworkParticipant next, float inputSpeed) {
        return (float) (inputSpeed * speedModifierTo(next));
    }

    boolean isNetworkSource();

    boolean canConnectKinetics(EnumFacing thisSide, NetworkParticipant source, EnumFacing sourceSide);

    float generatedSpeed();

    EnumFacing.Axis generatedAxis();

    EnumFacing.Axis axis();

    float stressForNetwork();

    float stressCapacity();

    default void breakForOverspeed() {
    }

}
