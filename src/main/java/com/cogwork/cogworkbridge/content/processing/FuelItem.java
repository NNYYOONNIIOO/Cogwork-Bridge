package com.cogwork.cogworkbridge.content.processing;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Explicit upstream burn durations also apply to furnaces and ordinary burner fuel. */
public final class FuelItem extends Item {
    private final int ticks;
    public FuelItem(int ticks) { this.ticks = ticks; }
    @Override public int getItemBurnTime(ItemStack stack) { return ticks; }

    public static final class BlockItem extends ItemBlock {
        private final int ticks;
        public BlockItem(Block block, int ticks) { super(block); this.ticks = ticks; }
        @Override public int getItemBurnTime(ItemStack stack) { return ticks; }
    }
}
