package com.cogwork.cogworkbridge.content.kinetics.shaft;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;

/** Shaft conduit: the cheapest element in a network. */
public class ShaftTileEntity extends KineticTileEntity {
    @Override
    public float getStressImpact() {
        return 0.5F;
    }
}
