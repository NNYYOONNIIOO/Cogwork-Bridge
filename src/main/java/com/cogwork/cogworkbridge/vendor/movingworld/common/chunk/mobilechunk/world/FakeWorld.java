/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/chunk/mobilechunk/world/FakeWorld.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.FakeChunk;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunk;
import com.google.common.base.Predicate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.biome.Biome;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * A wrapper for MobileChunks, used to give blocks accurate information about it's neighbors.
 */
public class FakeWorld extends World {

    MobileChunk mobileChunk;
    private final java.util.Map<Long, Chunk> chunks = new java.util.HashMap<>();

    private FakeWorld(boolean remote, World parentWorld) {
        super(parentWorld.getSaveHandler(), parentWorld.getWorldInfo(), parentWorld.provider, parentWorld.profiler, remote);
        this.chunkProvider = createChunkProvider();
    }

    public static FakeWorld getFakeWorld(MobileChunk chunk) {
        FakeWorld retVal = new FakeWorld(chunk.world.isRemote, chunk.world);
        retVal.mobileChunk = chunk;
        return retVal;
    }

    public MobileChunk getMobileChunk() {
        return mobileChunk;
    }

    public World getParentWorld() {
        return mobileChunk.world;
    }

    private com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld
    contraptionWorld() {
        if (mobileChunk.getEntityMovingWorld() instanceof
                com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity bearing) {
            return bearing.getContraptionLocalWorld();
        }
        return null;
    }

    public BlockPos toWorldPos(BlockPos localPos) {
        if (mobileChunk.getEntityMovingWorld() == null) {
            return localPos;
        }
        net.minecraft.entity.Entity entity = mobileChunk.getEntityMovingWorld();
        if (entity instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity bearing) {
            net.minecraft.util.math.Vec3d center =
                    bearing.localBlockCenterToWorld(localPos);
            return new BlockPos(center);
        }
        return new BlockPos(entity);
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return mobileChunk.getTileEntity(pos);
    }

    @Override
    public List<Entity> getEntitiesWithinAABBExcludingEntity(
            @Nullable Entity excluded, AxisAlignedBB area) {
        var local = contraptionWorld();
        return local == null ? Collections.emptyList()
                : local.getEntitiesWithinAABBExcludingEntity(excluded, area);
    }

    @Override
    public List<Entity> getEntitiesInAABBexcluding(
            @Nullable Entity excluded, AxisAlignedBB area,
            @Nullable Predicate<? super Entity> predicate) {
        var local = contraptionWorld();
        return local == null ? Collections.emptyList()
                : local.getEntitiesInAABBexcluding(excluded, area, predicate);
    }

    @Override
    public <T extends Entity> List<T> getEntitiesWithinAABB(
            Class<? extends T> entityType, AxisAlignedBB area) {
        var local = contraptionWorld();
        return local == null ? Collections.emptyList()
                : local.getEntitiesWithinAABB(entityType, area);
    }

    @Override
    public <T extends Entity> List<T> getEntitiesWithinAABB(
            Class<? extends T> entityType, AxisAlignedBB area,
            @Nullable Predicate<? super T> filter) {
        var local = contraptionWorld();
        return local == null ? Collections.emptyList()
                : local.getEntitiesWithinAABB(entityType, area, filter);
    }

    @Override
    public IChunkProvider createChunkProvider() {
        return new MobileChunkProvider(this);
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        return chunks.computeIfAbsent(key, ignored -> new FakeChunk(mobileChunk, chunkX, chunkZ));
    }

    @Override
    public Chunk getChunk(BlockPos pos) {
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * Third-party networks call World#getChunkProvider directly instead of
     * going through World#getChunk. Keep that API backed by the same virtual
     * chunk map so a moving-world block cannot hand them a null provider.
     */
    private static final class MobileChunkProvider implements IChunkProvider {
        private final FakeWorld world;

        private MobileChunkProvider(FakeWorld world) {
            this.world = world;
        }

        @Override
        public Chunk getLoadedChunk(int x, int z) {
            return world.getChunk(x, z);
        }

        @Override
        public Chunk provideChunk(int x, int z) {
            return world.getChunk(x, z);
        }

        @Override
        public boolean tick() {
            return false;
        }

        @Override
        public String makeString() {
            return "MovingWorld virtual chunk provider";
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return true;
        }
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return mobileChunk.getBlockState(pos);
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState state) {
        return setBlockState(pos, state, 3);
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState state, int flags) {
        var local = contraptionWorld();
        return local != null ? local.setBlockState(pos, state, flags)
                : mobileChunk.setBlockState(pos, state);
    }

    @Override
    public void scheduleBlockUpdate(BlockPos pos, net.minecraft.block.Block block,
                                    int delay, int priority) {
        var local = contraptionWorld();
        if (local != null) {
            local.scheduleBlockUpdate(pos, block, delay, priority);
        }
    }

    @Override
    public void scheduleUpdate(BlockPos pos, net.minecraft.block.Block block, int delay) {
        scheduleBlockUpdate(pos, block, delay, 0);
    }

    @Override
    public void updateBlockTick(BlockPos pos, net.minecraft.block.Block block,
                                int delay, int priority) {
        scheduleBlockUpdate(pos, block, delay, priority);
    }

    @Override
    public boolean isUpdateScheduled(BlockPos pos, net.minecraft.block.Block block) {
        var local = contraptionWorld();
        return local != null && local.isUpdateScheduled(pos, block);
    }

    @Override
    public boolean isBlockTickPending(BlockPos pos, net.minecraft.block.Block block) {
        var local = contraptionWorld();
        return local != null && local.isBlockTickPending(pos, block);
    }

    @Override
    public void notifyNeighborsOfStateChange(BlockPos pos,
                                             net.minecraft.block.Block block,
                                             boolean updateObservers) {
        var local = contraptionWorld();
        if (local != null) {
            local.notifyNeighborsOfStateChange(pos, block, updateObservers);
        }
    }

    @Override
    public void notifyBlockUpdate(BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        var local = contraptionWorld();
        if (local != null) local.notifyBlockUpdate(pos, oldState, newState, flags);
    }

    @Override
    public boolean spawnEntity(Entity entity) {
        var local = contraptionWorld();
        return local != null && local.spawnEntity(entity);
    }

    @Override
    public void playEvent(int type, BlockPos pos, int data) {
        var local = contraptionWorld();
        if (local != null) local.playEvent(type, pos, data);
    }

    @Override
    public void playEvent(@Nullable EntityPlayer player, int type, BlockPos pos, int data) {
        var local = contraptionWorld();
        if (local != null) local.playEvent(player, type, pos, data);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        var local = contraptionWorld();
        return local == null ? 0 : local.getStrongPower(pos, direction);
    }

    @Override
    public int getStrongPower(BlockPos pos) {
        var local = contraptionWorld();
        return local == null ? 0 : local.getStrongPower(pos);
    }

    @Override
    public int getRedstonePower(BlockPos pos, EnumFacing direction) {
        var local = contraptionWorld();
        return local == null ? 0 : local.getRedstonePower(pos, direction);
    }

    @Override
    public int getRedstonePowerFromNeighbors(BlockPos pos) {
        var local = contraptionWorld();
        return local == null ? 0 : local.getRedstonePowerFromNeighbors(pos);
    }

    @Override
    public boolean isBlockPowered(BlockPos pos) {
        var local = contraptionWorld();
        return local != null && local.isBlockPowered(pos);
    }

    public boolean isBlockIndirectlyGettingPowered(BlockPos pos) {
        var local = contraptionWorld();
        return local != null && local.isBlockIndirectlyGettingPowered(pos);
    }

    @Override
    public boolean isSidePowered(BlockPos pos, EnumFacing side) {
        var local = contraptionWorld();
        return local != null && local.isSidePowered(pos, side);
    }

    @Override
    public void playSound(@Nullable EntityPlayer player, double x, double y,
                          double z, SoundEvent sound, SoundCategory category,
                          float volume, float pitch) {
        var local = contraptionWorld();
        if (local != null) {
            local.playSound(player, x, y, z, sound, category, volume, pitch);
        }
    }

    @Override
    public void spawnParticle(EnumParticleTypes particle, double x, double y,
                              double z, double vx, double vy, double vz,
                              int... parameters) {
        var local = contraptionWorld();
        if (local != null) {
            local.spawnParticle(particle, x, y, z, vx, vy, vz, parameters);
        }
    }

    @Override
    public void setTileEntity(BlockPos pos, @Nullable TileEntity tileEntityIn) {
        mobileChunk.setTileEntity(pos, tileEntityIn);
    }

    @Override
    public void markChunkDirty(BlockPos pos, TileEntity unusedTileEntity) {
        mobileChunk.setChunkModified();
        if (mobileChunk.side().isServer()) {
            mobileChunk.markTileDirty(pos);
        }
    }

    private boolean isValidPosition(BlockPos pos) {
        return pos.getX() >= mobileChunk.minX() && pos.getZ() >= mobileChunk.minZ() && pos.getX() < mobileChunk.maxX() && pos.getZ() < mobileChunk.maxZ() && pos.getY() >= 0 && pos.getY() < mobileChunk.maxY();
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) {
        return contraptionWorld() != null || isValidPosition(pos);
    }

    @Override
    public boolean isAreaLoaded(BlockPos from, BlockPos to) {
        return contraptionWorld() != null || super.isAreaLoaded(from, to);
    }

    @Override
    public boolean isAreaLoaded(BlockPos center, int range) {
        return contraptionWorld() != null || super.isAreaLoaded(center, range);
    }

    @Override
    public boolean isAreaLoaded(BlockPos center, int range, boolean allowEmpty) {
        return contraptionWorld() != null || super.isAreaLoaded(center, range, allowEmpty);
    }

    @Override
    public boolean isAreaLoaded(BlockPos from, BlockPos to, boolean allowEmpty) {
        return contraptionWorld() != null || super.isAreaLoaded(from, to, allowEmpty);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        var local = contraptionWorld();
        return local == null ? getParentWorld().getBiome(toWorldPos(pos)) : local.getBiome(pos);
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        var local = contraptionWorld();
        return local == null ? getParentWorld().canSeeSky(toWorldPos(pos)) : local.canSeeSky(pos);
    }

    @Override
    public boolean isRainingAt(BlockPos pos) {
        var local = contraptionWorld();
        return local == null ? getParentWorld().isRainingAt(toWorldPos(pos)) : local.isRainingAt(pos);
    }

    @Override
    public boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return true;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        if (getBlockState(pos) == null || isAirBlock(pos)) return _default;

        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    @Override
    public boolean isBlockModifiable(EntityPlayer player, BlockPos pos) {
        return getParentWorld().isBlockModifiable(player, toWorldPos(pos));
    }

    @Override
    public boolean canMineBlockBody(EntityPlayer player, BlockPos pos) {
        return getParentWorld().canMineBlockBody(player, toWorldPos(pos));
    }

    @Override
    public float getLightBrightness(BlockPos pos) {
        int packed = getCombinedLight(pos, 0);
        int sky = Math.max(0, Math.min(15,
                packed >> 20 & 15));
        int block = Math.max(0, Math.min(15,
                packed >> 4 & 15));
        sky = Math.max(0, sky - getSkylightSubtracted());
        int combined = Math.max(sky, block);
        return provider.getLightBrightnessTable()[combined];
    }

    @Override
    public int getLightFor(net.minecraft.world.EnumSkyBlock type,
                           BlockPos pos) {
        var local = contraptionWorld();
        if (local != null) return local.getLightFor(type, pos);
        return super.getLightFor(type, pos);
    }

    @Override
    public long getTotalWorldTime() {
        return mobileChunk.world.getTotalWorldTime();
    }

    @Override
    public long getWorldTime() {
        return mobileChunk.world.getWorldTime();
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return mobileChunk.getCombinedLight(pos, lightValue);
    }

    public Chunk getChunkFromChunkCoords(int chunkX, int chunkZ) {
        return getChunk(chunkX, chunkZ);
    }
}
