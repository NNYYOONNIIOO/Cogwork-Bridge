package com.cogwork.cogworkbridge.content.equipment;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.oredict.OreDictionary;
import java.util.LinkedHashMap;
import java.util.Map;

/** Grow in an empty simulated volume, then merge the tree into the real world. */
public final class TreeFertilizerItem extends Item {
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float x, float y, float z) {
        IBlockState state = world.getBlockState(pos); Block sapling = state.getBlock();
        if (!(sapling instanceof IGrowable) || !isSapling(state)) return EnumActionResult.PASS;
        if (!player.canPlayerEdit(pos, side, player.getHeldItem(hand))) return EnumActionResult.FAIL;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        DreamWorld dream = new DreamWorld(world, pos);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos at = pos.add(dx, 0, dz); IBlockState nearby = world.getBlockState(at);
            if (nearby.getBlock() == sapling) {
                if (nearby.getPropertyKeys().contains(BlockSapling.STAGE)) nearby = nearby.withProperty(BlockSapling.STAGE, 1);
                dream.seed(at, nearby);
            }
        }
        IBlockState grown = dream.getBlockState(pos);
        ((IGrowable) sapling).grow(dream, world.rand, pos, grown);
        dream.apply(player, sapling);
        world.playEvent(2005, pos, 0);
        if (!player.capabilities.isCreativeMode) player.getHeldItem(hand).shrink(1);
        return EnumActionResult.SUCCESS;
    }
    private static boolean isSapling(IBlockState state) {
        if (state.getBlock() instanceof BlockSapling) return true;
        Item item = Item.getItemFromBlock(state.getBlock());
        if (item == null) return false;
        ItemStack stack = new ItemStack(item, 1, state.getBlock().damageDropped(state));
        for (int id : OreDictionary.getOreIDs(stack)) if ("treeSapling".equals(OreDictionary.getOreName(id))) return true;
        return false;
    }
    private static final class DreamWorld extends World {
        private final World real; private final BlockPos origin; private final IBlockState soil;
        private final Map<BlockPos, IBlockState> changes = new LinkedHashMap<>();
        DreamWorld(World real, BlockPos origin) {
            super(real.getSaveHandler(), real.getWorldInfo(), real.provider, real.profiler, false);
            this.real = real; this.origin = origin;
            IBlockState below = real.getBlockState(origin.down());
            this.soil = below.getBlock() == Blocks.GRASS ? Blocks.DIRT.getDefaultState() : below;
            this.chunkProvider = real.getChunkProvider();
        }
        void seed(BlockPos pos, IBlockState state) { changes.put(pos.toImmutable(), state); }
        @Override protected IChunkProvider createChunkProvider() { return real == null ? null : real.getChunkProvider(); }
        @Override protected boolean isChunkLoaded(int x, int z, boolean empty) { return true; }
        @Override public boolean isBlockLoaded(BlockPos pos) { return true; }
        @Override public boolean isBlockLoaded(BlockPos pos, boolean empty) { return true; }
        @Override public boolean isAreaLoaded(BlockPos from, BlockPos to) { return true; }
        @Override public boolean isAreaLoaded(BlockPos from, BlockPos to, boolean empty) { return true; }
        @Override public IBlockState getBlockState(BlockPos pos) {
            IBlockState state = changes.get(pos);
            if (state != null) return state;
            return pos.getY() < origin.getY() ? soil : Blocks.AIR.getDefaultState();
        }
        @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getBlock().isAir(getBlockState(pos), this, pos); }
        @Override public boolean setBlockState(BlockPos pos, IBlockState state, int flags) {
            if (pos.getY() < 0 || pos.getY() >= 256) return false;
            changes.put(pos.toImmutable(), state); return true;
        }
        @Override public boolean destroyBlock(BlockPos pos, boolean drop) { return setBlockState(pos, Blocks.AIR.getDefaultState(), 3); }
        @Override public net.minecraft.world.biome.Biome getBiome(BlockPos pos) { return real.getBiome(pos); }
        @Override public int getLightFromNeighbors(BlockPos pos) { return 15; }
        void apply(EntityPlayer player, Block sapling) {
            for (Map.Entry<BlockPos, IBlockState> entry : changes.entrySet()) {
                BlockPos pos = entry.getKey(); IBlockState next = entry.getValue(), old = real.getBlockState(pos);
                if (!real.isBlockModifiable(player, pos) || old.getBlockHardness(real, pos) < 0 || next.equals(old)) continue;
                if (next.getBlock() == Blocks.AIR && old.getBlock() != sapling) continue;
                if (next.getBlock().isLeaves(next, this, pos) && old.getCollisionBoundingBox(real, pos) != null) continue;
                boolean drop = old.getBlock() != sapling && old.getBlock() != next.getBlock()
                        && !old.getBlock().isReplaceable(real, pos) && !old.getBlock().isLeaves(old, real, pos);
                real.destroyBlock(pos, drop);
                real.setBlockState(pos, next, 3);
            }
        }
    }
}
