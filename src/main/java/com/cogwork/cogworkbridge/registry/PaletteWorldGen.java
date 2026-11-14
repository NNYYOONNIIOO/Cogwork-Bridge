package com.cogwork.cogworkbridge.registry;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Layered deposits with Create's dimension, rarity and size adapted to 1.12 world height. */
public final class PaletteWorldGen implements IWorldGenerator {
    private static final String[] OVERWORLD = {"crimsite", "asurine", "veridium", "scoria", "limestone", "ochrum"};
    @Override public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
        int dimension = world.provider.getDimension();
        if ((dimension != 0 && dimension != -1) || random.nextInt(18) != 0) return;
        boolean nether = dimension == -1;
        String kind = nether ? (random.nextBoolean() ? "scoria" : "scorchia") : OVERWORLD[random.nextInt(OVERWORLD.length)];
        Block ore = BlockFamily.get(kind);
        if (ore == null) return;
        int x0 = (chunkX << 4) + 8 + random.nextInt(16);
        int z0 = (chunkZ << 4) + 8 + random.nextInt(16);
        int y0 = nether ? 40 + random.nextInt(51) : 8 + random.nextInt(63);
        IBlockState[] layers = new IBlockState[33];
        for (int i = 0; i < layers.length;) {
            IBlockState material = chooseLayer(random, kind, ore, nether);
            int thickness = 2 + random.nextInt(4);
            for (int j = 0; j < thickness && i < layers.length; j++) layers[i++] = material;
        }
        double tiltX = (random.nextDouble() - .5D) * .5D, tiltZ = (random.nextDouble() - .5D) * .5D;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -15; dx <= 15; dx++) for (int dz = -15; dz <= 15; dz++) for (int dy = -15; dy <= 15; dy++) {
            if (dx * dx + dy * dy + dz * dz > 256 || y0 + dy <= 0 || y0 + dy >= 255) continue;
            pos.setPos(x0 + dx, y0 + dy, z0 + dz);
            if (!world.isBlockLoaded(pos)) continue;
            IBlockState existing = world.getBlockState(pos);
            if (!replaceable(existing, nether)) continue;
            int layer = (int) Math.floor(dy + 16 + dx * tiltX + dz * tiltZ + Math.sin((x0 + dx) * .125D) + Math.sin((z0 + dz) * .125D));
            if (layer < 0 || layer >= layers.length || layers[layer] == null) continue;
            world.setBlockState(pos, layers[layer], 2);
        }
    }
    private static IBlockState chooseLayer(Random random, String kind, Block primary, boolean nether) {
        int choice = random.nextInt(5);
        if (choice < 2) return primary.getDefaultState();
        if (nether) {
            if (choice == 2) return null;
            if (kind.equals("scorchia") && choice == 3) return Blocks.SOUL_SAND.getDefaultState();
            Block basalt = external("basalt"); return basalt == null ? Blocks.MAGMA.getDefaultState() : basalt.getDefaultState();
        }
        if (choice == 2) {
            Block tuff = external("tuff"); return tuff == null ? Blocks.STONE.getDefaultState() : tuff.getDefaultState();
        }
        if (choice == 3) {
            Block deepslate = external("deepslate"); return deepslate == null ? Blocks.STONE.getStateFromMeta(5) : deepslate.getDefaultState();
        }
        Block companion = external(kind.equals("ochrum") ? "dripstone_block" : "calcite");
        return companion == null ? Blocks.STONE.getStateFromMeta(3) : companion.getDefaultState();
    }
    private static Block external(String name) { return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("farmers_future_delight", name)); }
    private static boolean replaceable(IBlockState state, boolean nether) {
        if (nether) return state.getBlock() == Blocks.NETHERRACK;
        Block block = state.getBlock();
        if (block == Blocks.STONE) return true;
        ResourceLocation name = block.getRegistryName();
        return name != null && name.getNamespace().equals("farmers_future_delight")
                && (name.getPath().equals("deepslate") || name.getPath().equals("tuff"));
    }
}
