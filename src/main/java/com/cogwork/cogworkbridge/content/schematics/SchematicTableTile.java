package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.item.ItemStack;

public final class SchematicTableTile extends SchematicInventoryTile {
    public SchematicTableTile() { super(2); }
    @Override public boolean accepts(int slot, ItemStack stack) { return slot == 0 && stack.getItem() == AllItems.EMPTY_SCHEMATIC; }
}
