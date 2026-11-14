package com.cogwork.cogworkbridge.registry;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public final class ZincOreBlock extends Block {
    public ZincOreBlock() {
        super(Material.ROCK);
        setHardness(3.0F);
        setResistance(5.0F);
        setHarvestLevel("pickaxe", 1);
    }
}
