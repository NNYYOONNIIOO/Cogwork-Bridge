package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.content.schematics.SchematicData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import java.util.*;

/** Read-only render environment. Preview tiles are never loaded into, or ticked by, the real world. */
final class SchematicPreviewWorld extends World {
    private final World parent;
    private final Map<BlockPos, IBlockState> states = new LinkedHashMap<>();
    private final Map<BlockPos, TileEntity> tiles = new LinkedHashMap<>();
    private final Map<Long, Chunk> chunks = new HashMap<>();
    SchematicPreviewWorld(World parent, SchematicData data, Mirror mirror, Rotation rotation) {
        super(parent.getSaveHandler(), parent.getWorldInfo(), parent.provider, parent.profiler, true);
        this.parent = parent;
        this.chunkProvider = createChunkProvider();
        for (SchematicData.Entry entry : data.blocks) {
            try { states.put(SchematicData.transformed(entry.local, mirror, rotation), entry.transformedState(mirror, rotation)); }
            catch (RuntimeException ignored) { }
        }
        // Populate every state before reading NBT: renderers and model data may inspect neighbours.
        for (SchematicData.Entry entry : data.blocks) {
            BlockPos at = SchematicData.transformed(entry.local, mirror, rotation);
            IBlockState state = getBlockState(at);
            if (!state.getBlock().hasTileEntity(state)) continue;
            try {
                TileEntity tile = state.getBlock().createTileEntity(this, state);
                if (tile == null) continue;
                tile.setWorld(this); tile.setPos(at);
                tiles.put(at, tile);
                if (entry.tile != null) {
                    NBTTagCompound tag = entry.tile.copy();
                    tag.setInteger("x", at.getX()); tag.setInteger("y", at.getY()); tag.setInteger("z", at.getZ());
                    tile.readFromNBT(tag); tile.setWorld(this); tile.setPos(at);
                    tile.mirror(mirror); tile.rotate(rotation);
                }
                tile.updateContainingBlockInfo();
            } catch (RuntimeException ignored) { tiles.remove(at); }
        }
    }
    Map<BlockPos, IBlockState> states() { return states; }
    Collection<TileEntity> tiles() { return tiles.values(); }
    boolean hasParent(World world) { return parent == world; }
    @Override public IBlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
    @Override public TileEntity getTileEntity(BlockPos pos) { return tiles.get(pos); }
    @Override public boolean isAirBlock(BlockPos pos) { return getBlockState(pos).getMaterial() == net.minecraft.block.material.Material.AIR; }
    @Override public int getCombinedLight(BlockPos pos, int light) { return 0xf000f0; }
    @Override public int getLight(BlockPos pos) { return 15; }
    @Override public Biome getBiome(BlockPos pos) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        return parent.getBiome(mc.player == null ? BlockPos.ORIGIN : mc.player.getPosition());
    }
    @Override public long getWorldTime() { return parent.getWorldTime(); }
    @Override public long getTotalWorldTime() { return parent.getTotalWorldTime(); }
    @Override public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) { return true; }
    @Override protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return true; }
    @Override public boolean setBlockState(BlockPos pos, IBlockState state, int flags) { return false; }
    @Override public void setTileEntity(BlockPos pos, TileEntity tile) { }
    @Override public void removeTileEntity(BlockPos pos) { }
    @Override public void markChunkDirty(BlockPos pos, TileEntity tile) { }
    @Override public void notifyBlockUpdate(BlockPos pos, IBlockState old, IBlockState state, int flags) { }
    @Override public void scheduleUpdate(BlockPos pos, net.minecraft.block.Block block, int delay) { }
    @Override public void scheduleBlockUpdate(BlockPos pos, net.minecraft.block.Block block, int delay, int priority) { }
    @Override public boolean spawnEntity(Entity entity) { return false; }
    @Override public Chunk getChunk(BlockPos pos) { return getChunk(pos.getX() >> 4, pos.getZ() >> 4); }
    @Override public Chunk getChunk(int x, int z) {
        return chunks.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), key -> new Chunk(this, x, z) {
            @Override public IBlockState getBlockState(BlockPos pos) { return SchematicPreviewWorld.this.getBlockState(pos); }
            @Override public TileEntity getTileEntity(BlockPos pos, EnumCreateEntityType type) { return SchematicPreviewWorld.this.getTileEntity(pos); }
            @Override public boolean isEmpty() { return false; }
        });
    }
    @Override protected IChunkProvider createChunkProvider() {
        return new IChunkProvider() {
            @Override public Chunk getLoadedChunk(int x, int z) { return getChunk(x, z); }
            @Override public Chunk provideChunk(int x, int z) { return getChunk(x, z); }
            @Override public boolean tick() { return false; }
            @Override public String makeString() { return "Schematic preview"; }
            @Override public boolean isChunkGeneratedAt(int x, int z) { return true; }
        };
    }
}
