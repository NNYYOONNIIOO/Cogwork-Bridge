package com.cogwork.cogworkbridge.content.redstone;

/** A value exposed directly to a stockpile switch, in its native units. */
public interface ThresholdObservable {
    int thresholdMinimum();
    int thresholdMaximum();
    int thresholdValue();
}
