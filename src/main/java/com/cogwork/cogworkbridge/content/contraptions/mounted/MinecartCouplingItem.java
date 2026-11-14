package com.cogwork.cogworkbridge.content.contraptions.mounted;

import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.item.Item;

/** Couples two selected minecarts; interaction is handled for both hands by MinecartCouplingEvents. */
public final class MinecartCouplingItem extends Item {
    public MinecartCouplingItem() { setCreativeTab(ModCreativeTab.INSTANCE); }
}
