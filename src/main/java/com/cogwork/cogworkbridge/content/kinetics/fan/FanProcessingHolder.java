package com.cogwork.cogworkbridge.content.kinetics.fan;

/** A transport surface that can expose its contents to a fan's processing air. */
public interface FanProcessingHolder {
    void applyFanProcessing(EncasedFanTileEntity.Medium medium);
}
