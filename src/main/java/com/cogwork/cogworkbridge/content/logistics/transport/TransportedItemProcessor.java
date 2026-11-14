package com.cogwork.cogworkbridge.content.logistics.transport;

/** A machine above a belt can hold an item at the centre while processing it. */
public interface TransportedItemProcessor {
    boolean holdTransportedItem(TransportedItemHolder holder);
}
