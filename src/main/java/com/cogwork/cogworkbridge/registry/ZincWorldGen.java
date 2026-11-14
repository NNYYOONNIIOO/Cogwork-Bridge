package com.cogwork.cogworkbridge.registry;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.fml.common.IWorldGenerator;
import java.util.Random;

/** Generates zinc in newly generated overworld chunks. */
public final class ZincWorldGen implements IWorldGenerator {
    private final WorldGenMinable ore = new WorldGenMinable(AllBlocks.ZINC_ORE.getDefaultState(), 8,
            state -> state.getBlock() == Blocks.STONE);
    private final WorldGenMinable deepOre = new WorldGenMinable(AllBlocks.DEEPSLATE_ZINC_ORE.getDefaultState(), 8,
            state -> state.getBlock().getRegistryName() != null && state.getBlock().getRegistryName().toString().equals("farmers_future_delight:deepslate"));
    @Override public void generate(Random random, int x, int z, World world, IChunkGenerator generator, IChunkProvider provider) {
        if (world.provider.getDimension() != 0) return;
        for (int i = 0; i < 8; i++)
            ore.generate(world, random, new BlockPos((x << 4) + random.nextInt(16), random.nextInt(64), (z << 4) + random.nextInt(16)));
        for (int i = 0; i < 4; i++)
            deepOre.generate(world, random, new BlockPos((x << 4) + random.nextInt(16), random.nextInt(32), (z << 4) + random.nextInt(16)));
    }
}
