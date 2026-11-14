package com.cogwork.cogworkbridge.registry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.Random;

public final class DeepZincOreBlock extends Block {
    public DeepZincOreBlock() { super(Material.ROCK); setHardness(4.5F); setResistance(6F); setHarvestLevel("pickaxe", 1); }
    @Override public Item getItemDropped(IBlockState state, Random random, int fortune) {
        for (ItemStack raw : OreDictionary.getOres("rawZinc", false)) if (!raw.isEmpty()) return raw.getItem();
        return AllItems.ZINC_INGOT;
    }
    @Override public int quantityDroppedWithBonus(int fortune, Random random) {
        return fortune <= 0 ? 1 : Math.max(1, random.nextInt(fortune + 2));
    }
}
