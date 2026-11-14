package com.cogwork.cogworkbridge.content.logistics.transport;

import net.minecraft.item.ItemStack;

/** An item presented to a processing machine above a transport surface. */
public interface TransportedItemHolder {
    ItemStack getTransportedItem();

    /** Consume one presented item and store or eject its processed result. */
    void replaceOneWith(ItemStack result);

    /** Consume one input and preserve all outputs, including an empty roll. */
    void replaceOneWith(java.util.List<ItemStack> results);
}
