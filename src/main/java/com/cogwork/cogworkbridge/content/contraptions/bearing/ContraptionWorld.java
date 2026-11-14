package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.Chunk;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunk;
import com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform;

import javax.annotation.Nullable;
import com.google.common.base.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Local world used by contraption block and tile-entity renderers.
 *
 * <p>The wrapper mirrors the relevant world-query behavior of a moving chunk:
 * snapshot states and tile entities are addressed in the structure's local grid.</p>
 */
public final class ContraptionWorld extends World {

    private ContraptionBlockAccess access;
    private final World parent;
    private final MobileChunk movingChunk;
    private final Map<BlockPos, ScheduledTick> scheduledTicks =
            new java.util.HashMap<>();
    private final Map<Long, Chunk> chunks = new java.util.HashMap<>();
    private long contraptionTick;
    private int poseKey;
    private int contentKey;

    public ContraptionWorld(World parent, ContraptionBlockAccess access,
                            MobileChunk movingChunk) {
        super(parent.getSaveHandler(), parent.getWorldInfo(), parent.provider,
                parent.profiler, parent.isRemote);
        this.parent = parent;
        this.movingChunk = movingChunk;
        this.access = access.withLiveChunk(movingChunk);
        this.chunkProvider = createChunkProvider();
    }

    public BlockPos worldPosition(BlockPos local) { return new BlockPos(access.getWorldCenter(local)); }

    @Override
    protected IChunkProvider createChunkProvider() {
        return new ContraptionChunkProvider(this);
    }

    @Override
    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        return chunks.computeIfAbsent(key, ignored -> new ContraptionChunk(this, chunkX, chunkZ));
    }

    @Override
    public Chunk getChunk(BlockPos pos) {
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Provides a stable virtual chunk API for third-party tile entities. */
    private static final class ContraptionChunkProvider implements IChunkProvider {
        private final ContraptionWorld world;

        private ContraptionChunkProvider(ContraptionWorld world) {
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
            return "Contraption virtual chunk provider";
        }

        @Override
        public boolean isChunkGeneratedAt(int x, int z) {
            return true;
        }
    }

    @Override
    public void markChunkDirty(BlockPos pos, TileEntity tileEntity) {
        movingChunk.setChunkModified();
        if (!isRemote) {
            movingChunk.markTileDirty(pos);
        }
    }

    @Override
    public void notifyBlockUpdate(BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        movingChunk.setChunkModified();
        if (!isRemote) movingChunk.markTileDirty(pos);
    }

    @Override
    public boolean spawnEntity(Entity entity) {
        if (entity == null || entity.world != this && entity.world != movingChunk.getFakeWorld()) return false;
        World previousWorld = entity.world;
        double x = entity.posX, y = entity.posY, z = entity.posZ;
        double vx = entity.motionX, vy = entity.motionY, vz = entity.motionZ;
        Vec3d point = access.worldPointForLocal(new ContraptionTransform.Point(x, y, z));
        Vec3d origin = access.worldPointForLocal(new ContraptionTransform.Point(0, 0, 0));
        Vec3d velocity = access.worldPointForLocal(new ContraptionTransform.Point(vx, vy, vz)).subtract(origin);
        entity.setWorld(parent);
        entity.setPosition(point.x, point.y, point.z);
        entity.motionX = velocity.x;
        entity.motionY = velocity.y;
        entity.motionZ = velocity.z;
        if (parent.spawnEntity(entity)) return true;
        entity.setWorld(previousWorld);
        entity.setPosition(x, y, z);
        entity.motionX = vx;
        entity.motionY = vy;
        entity.motionZ = vz;
        return false;
    }

    @Override
    protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
        return true;
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos) {
        return true;
    }

    @Override
    public boolean isBlockLoaded(BlockPos pos, boolean allowEmpty) {
        return true;
    }

    @Override
    public boolean isAreaLoaded(BlockPos from, BlockPos to) {
        return true;
    }

    @Override
    public boolean isAreaLoaded(BlockPos center, int range) {
        return true;
    }

    @Override
    public boolean isAreaLoaded(BlockPos center, int range,
                                boolean allowEmpty) {
        return true;
    }

    @Override
    public boolean isAreaLoaded(BlockPos from, BlockPos to,
                                boolean allowEmpty) {
        return true;
    }

    @Override
    public long getTotalWorldTime() {
        return parent.getTotalWorldTime();
    }

    @Override
    public long getWorldTime() {
        return parent.getWorldTime();
    }

    @Override
    public int getSkylightSubtracted() {
        return parent.getSkylightSubtracted();
    }

    @Override
    public BiomeProvider getBiomeProvider() {
        return parent.getBiomeProvider();
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return access.getBlockState(pos);
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return access.getTileEntity(pos);
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState state, int flags) {
        pos = pos.toImmutable();
        IBlockState oldState = getBlockState(pos);
        if (oldState == state || oldState.equals(state)) {
            return false;
        }
        scheduledTicks.remove(pos);
        boolean blockChanged = oldState.getBlock() != state.getBlock();
        TileEntity oldTile = getTileEntity(pos);
        boolean refreshTile = oldTile != null && oldTile.shouldRefresh(this, pos, oldState, state);
        if (blockChanged && !oldState.getBlock().isAir(oldState, this, pos)) {
            try {
                oldState.getBlock().breakBlock(this, pos, oldState);
            } catch (RuntimeException exception) {
                logFailure("break block", pos, oldState, exception);
            }
        }
        if (oldTile != null && (blockChanged || refreshTile)) {
            movingChunk.removeChunkBlockTileEntity(pos);
        }
        if (!movingChunk.setBlockState(pos, state)) {
            return false;
        }
        TileEntity newTile = getTileEntity(pos);
        // MobileChunk.getTileEntity can create the new tile lazily.
        boolean createdTile = newTile != null && newTile != oldTile;
        if (newTile == null && state.getBlock().hasTileEntity(state)) {
            newTile = state.getBlock().createTileEntity(this, state);
            if (newTile != null) {
                movingChunk.setTileEntity(pos, newTile);
                createdTile = true;
            }
        }
        if (newTile != null) {
            if (createdTile) newTile.onLoad();
            newTile.updateContainingBlockInfo();
        }
        if (blockChanged) {
            try {
                state.getBlock().onBlockAdded(this, pos, state);
            } catch (RuntimeException exception) {
                logFailure("block-added hook", pos, state, exception);
            }
        }
        if ((flags & 2) != 0) {
            notifyBlockUpdate(pos, oldState, state, flags);
        }
        if ((flags & 1) != 0) {
            notifyNeighbours(pos, oldState.getBlock());
        }
        movingChunk.setChunkModified();
        return true;
    }

    @Override
    public boolean setBlockToAir(BlockPos pos) {
        return setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
    }

    @Nullable public BearingContraptionEntity getBearingContraption() {
        return movingChunk.getEntityMovingWorld() instanceof BearingContraptionEntity entity ? entity : null;
    }

    /** Drop an item at a moving block's transformed position in the real world. */
    public void spawnCapturedDrop(BlockPos pos, ItemStack stack) {
        if (!isRemote && !stack.isEmpty()
                && movingChunk.getEntityMovingWorld() instanceof BearingContraptionEntity entity) {
            entity.spawnDrop(pos, java.util.Collections.singletonList(stack));
        }
    }

    @Override
    public void setTileEntity(BlockPos pos, TileEntity tileEntity) {
        movingChunk.setTileEntity(pos.toImmutable(), tileEntity);
        movingChunk.setChunkModified();
    }

    @Override
    public void removeTileEntity(BlockPos pos) {
        movingChunk.removeChunkBlockTileEntity(pos.toImmutable());
        movingChunk.setChunkModified();
    }

    @Override
    public boolean isUpdateScheduled(BlockPos pos, net.minecraft.block.Block block) {
        return scheduledTickFor(pos, block) != null;
    }

    @Override
    public boolean isBlockTickPending(BlockPos pos, net.minecraft.block.Block block) {
        return scheduledTickFor(pos, block) != null;
    }

    @Override
    public void scheduleBlockUpdate(BlockPos pos, net.minecraft.block.Block block,
                                    int delay, int priority) {
        BlockPos local = pos.toImmutable();
        IBlockState state = getBlockState(local);
        if (state.getBlock() != block) {
            return;
        }
        scheduledTicks.put(local,
                new ScheduledTick(block, contraptionTick + Math.max(1, delay)));
    }

    @Override
    public void scheduleUpdate(BlockPos pos, net.minecraft.block.Block block,
                               int delay) {
        scheduleBlockUpdate(pos, block, delay, 0);
    }

    @Override
    public void updateBlockTick(BlockPos pos, net.minecraft.block.Block block,
                                int delay, int priority) {
        scheduleBlockUpdate(pos, block, delay, priority);
    }

    public void tickScheduledUpdates() {
        contraptionTick++;
        List<BlockPos> due = new ArrayList<>();
        for (Map.Entry<BlockPos, ScheduledTick> entry :
                scheduledTicks.entrySet()) {
            if (entry.getValue().dueTick() <= contraptionTick) {
                due.add(entry.getKey());
            }
        }
        for (BlockPos pos : due) {
            ScheduledTick scheduled = scheduledTicks.remove(pos);
            IBlockState state = getBlockState(pos);
            // Explicitly scheduled block ticks are independent of random-tick eligibility.
            if (scheduled == null || state.getBlock() != scheduled.block()
                    || movingChunk.isAirBlock(pos)) {
                continue;
            }
            try {
                state.getBlock().updateTick(this, pos, state, parent.rand);
            } catch (RuntimeException exception) {
                logFailure("scheduled block tick", pos, state, exception);
            }
        }
    }

    @Override
    public void notifyNeighborsOfStateChange(BlockPos pos,
                                             net.minecraft.block.Block source,
                                             boolean updateObservers) {
        notifyNeighbours(pos, source);
        if (updateObservers) {
            updateObservingBlocksAt(pos, source);
        }
    }

    private void notifyNeighbours(BlockPos pos, net.minecraft.block.Block source) {
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos neighbour = pos.offset(face);
            IBlockState neighbourState = getBlockState(neighbour);
            try {
                neighbourState.getBlock().onNeighborChange(
                        this, neighbour, pos);
                neighbourState.neighborChanged(
                        this, neighbour, source, pos);
            } catch (RuntimeException exception) {
                logFailure("neighbour update", neighbour, neighbourState,
                        exception);
            }
        }
    }

    private void logFailure(String action, BlockPos pos, IBlockState state,
                            RuntimeException exception) {
        if (com.cogwork.cogworkbridge.util.LogOnce.first("contraption-world-" + action, state.getBlock(), exception)) {
            com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                    "Carried block {} failed its {} at {}",
                    state, action, pos, exception);
        }
    }

    public MobileChunk movingChunk() {
        return movingChunk;
    }

    public int poseKey() {
        return poseKey;
    }

    public int contentKey() {
        return contentKey;
    }

    public void refreshAccess(ContraptionBlockAccess access, int poseKey,
                              int contentKey) {
        this.access = access.withLiveChunk(movingChunk);
        this.poseKey = poseKey;
        this.contentKey = contentKey;
    }

    public void clearScheduledUpdates() {
        scheduledTicks.clear();
    }

    private ScheduledTick scheduledTickFor(BlockPos pos,
                                           net.minecraft.block.Block block) {
        ScheduledTick scheduled = scheduledTicks.get(pos);
        return scheduled != null && scheduled.block() == block
                ? scheduled : null;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return access.getCombinedLight(pos, lightValue);
    }

    @Override
    public float getLightBrightness(BlockPos pos) {
        int packed = access.getCombinedLight(pos, 0);
        int sky = Math.max(0, Math.min(15, packed >> 20 & 15));
        int block = Math.max(0, Math.min(15, packed >> 4 & 15));
        sky = Math.max(0, sky - getSkylightSubtracted());
        return provider.getLightBrightnessTable()[Math.max(sky, block)];
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return access.isAirBlock(pos);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return parent.getBiome(new BlockPos(worldPointForLocal(pos)));
    }

    @Override
    public WorldType getWorldType() {
        return parent.getWorldType();
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        IBlockState state = getBlockState(pos);
        return state.getStrongPower(this, pos, direction);
    }

    public int getWeakPower(BlockPos pos, EnumFacing direction) {
        IBlockState state = getBlockState(pos);
        return state.getWeakPower(this, pos, direction);
    }

    @Override
    public int getRedstonePower(BlockPos pos, EnumFacing direction) {
        IBlockState state = getBlockState(pos);
        return state.getBlock().shouldCheckWeakPower(
                state, this, pos, direction)
                ? getStrongPower(pos)
                : getWeakPower(pos, direction);
    }

    @Override
    public int getRedstonePowerFromNeighbors(BlockPos pos) {
        int power = 0;
        for (EnumFacing face : EnumFacing.values()) {
            power = Math.max(power, getRedstonePower(pos.offset(face), face));
            if (power >= 15) {
                return power;
            }
        }
        return power;
    }

    @Override
    public int getStrongPower(BlockPos pos) {
        int power = 0;
        for (EnumFacing face : EnumFacing.values()) {
            power = Math.max(power, getStrongPower(pos.offset(face), face));
            if (power >= 15) return power;
        }
        return power;
    }

    public boolean isBlockIndirectlyGettingPowered(BlockPos pos) {
        return getRedstonePowerFromNeighbors(pos) > 0;
    }

    @Override
    public boolean isBlockPowered(BlockPos pos) {
        return getRedstonePowerFromNeighbors(pos) > 0;
    }

    @Override
    public boolean isSidePowered(BlockPos pos, EnumFacing side) {
        return getRedstonePower(pos.offset(side), side) > 0;
    }

    @Override
    public int getLightFor(net.minecraft.world.EnumSkyBlock type, BlockPos pos) {
        int packed = access.getCombinedLight(pos, 0);
        return type == net.minecraft.world.EnumSkyBlock.SKY
                ? packed >> 20 & 15 : packed >> 4 & 15;
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return ContraptionWorldContext.canSeeSky(
                parent, worldPointForLocal(pos));
    }

    @Override
    public boolean isRainingAt(BlockPos pos) {
        return ContraptionWorldContext.isRainingAt(
                parent, worldPointForLocal(pos));
    }

    private Vec3d worldPointForLocal(BlockPos pos) {
        return access.getWorldCenter(pos);
    }

    private AxisAlignedBB worldBoxForLocal(AxisAlignedBB localBox) {
        return access.worldBoxForLocal(localBox);
    }

    @Override
    public java.util.List<Entity> getEntitiesWithinAABBExcludingEntity(
            @Nullable Entity excluded, AxisAlignedBB area) {
        return parent.getEntitiesWithinAABBExcludingEntity(
                excluded, worldBoxForLocal(area));
    }

    @Override
    public java.util.List<Entity> getEntitiesInAABBexcluding(
            @Nullable Entity excluded, AxisAlignedBB area,
            @Nullable Predicate<? super Entity> predicate) {
        return parent.getEntitiesInAABBexcluding(
                excluded, worldBoxForLocal(area), predicate);
    }

    @Override
    public boolean isMaterialInBB(AxisAlignedBB area,
                                  net.minecraft.block.material.Material material) {
        int minX = net.minecraft.util.math.MathHelper.floor(area.minX);
        int maxX = net.minecraft.util.math.MathHelper.ceil(area.maxX);
        int minY = net.minecraft.util.math.MathHelper.floor(area.minY);
        int maxY = net.minecraft.util.math.MathHelper.ceil(area.maxY);
        int minZ = net.minecraft.util.math.MathHelper.floor(area.minZ);
        int maxZ = net.minecraft.util.math.MathHelper.ceil(area.maxZ);
        for (BlockPos pos : BlockPos.getAllInBox(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX - 1, maxY - 1, maxZ - 1))) {
            IBlockState state = getBlockState(pos);
            Boolean override = state.getBlock().isAABBInsideMaterial(
                    this, pos, area, material);
            if (override != null) {
                if (override) {
                    return true;
                }
                continue;
            }
            if (state.getMaterial() == material) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleMaterialAcceleration(
            AxisAlignedBB area, net.minecraft.block.material.Material material,
            Entity entity) {
        if (entity == null) {
            return false;
        }
        int minX = net.minecraft.util.math.MathHelper.floor(area.minX);
        int maxX = net.minecraft.util.math.MathHelper.ceil(area.maxX);
        int minY = net.minecraft.util.math.MathHelper.floor(area.minY);
        int maxY = net.minecraft.util.math.MathHelper.ceil(area.maxY);
        int minZ = net.minecraft.util.math.MathHelper.floor(area.minZ);
        int maxZ = net.minecraft.util.math.MathHelper.ceil(area.maxZ);
        boolean inside = false;
        Vec3d acceleration = Vec3d.ZERO;
        for (BlockPos pos : BlockPos.getAllInBox(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX - 1, maxY - 1, maxZ - 1))) {
            IBlockState state = getBlockState(pos);
            net.minecraft.block.Block block = state.getBlock();
            Boolean materialOverride = block.isEntityInsideMaterial(
                    this, pos, state, entity, maxY, material, false);
            if (materialOverride != null) {
                if (!materialOverride) {
                    continue;
                }
                inside = true;
                acceleration = block.modifyAcceleration(
                        this, pos, entity, acceleration);
                continue;
            }
            if (state.getMaterial() != material) {
                continue;
            }
            if (block instanceof net.minecraft.block.BlockLiquid) {
                double surface = (double) (pos.getY() + 1)
                        - net.minecraft.block.BlockLiquid
                        .getLiquidHeightPercent(state.getValue(
                                net.minecraft.block.BlockLiquid.LEVEL));
                if (maxY < surface) {
                    continue;
                }
            }
            inside = true;
            acceleration = block.modifyAcceleration(
                    this, pos, entity, acceleration);
        }
        if (acceleration.lengthSquared() > 0.0D
                && entity.isPushedByWater()) {
            Vec3d origin = access.worldPointForLocal(
                    new ContraptionTransform.Point(0.0D, 0.0D, 0.0D));
            Vec3d end = access.worldPointForLocal(new ContraptionTransform.Point(
                    acceleration.x, acceleration.y, acceleration.z));
            acceleration = end.subtract(origin).normalize();
            entity.motionX += acceleration.x * 0.014D;
            entity.motionY += acceleration.y * 0.014D;
            entity.motionZ += acceleration.z * 0.014D;
        }
        return inside;
    }

    @Override
    public void playEvent(int eventId, BlockPos pos, int data) {
        BlockPos center = new BlockPos(access.getWorldCenter(pos));
        parent.playEvent(eventId, center, data);
    }

    @Override
    public void playEvent(@Nullable EntityPlayer player, int eventId,
                          BlockPos pos, int data) {
        BlockPos center = new BlockPos(access.getWorldCenter(pos));
        parent.playEvent(player, eventId, center, data);
    }

    @Override
    public void playSound(@Nullable EntityPlayer player, double x,
                          double y, double z, SoundEvent sound,
                          SoundCategory category, float volume, float pitch) {
        Vec3d center = access.worldPointForLocal(
                new ContraptionTransform.Point(x, y, z));
        parent.playSound(player, center.x, center.y, center.z, sound,
                category, volume, pitch);
    }

    @Override
    public void playSound(double x, double y, double z, SoundEvent sound,
                          SoundCategory category, float volume, float pitch,
                          boolean distanceDelay) {
        Vec3d center = access.worldPointForLocal(
                new ContraptionTransform.Point(x, y, z));
        parent.playSound(center.x, center.y, center.z, sound, category,
                volume, pitch, distanceDelay);
    }

    @Override
    public void spawnParticle(EnumParticleTypes particle, double x,
                              double y, double z, double velocityX,
                              double velocityY, double velocityZ,
                              int... parameters) {
        Vec3d center = access.worldPointForLocal(
                new ContraptionTransform.Point(x, y, z));
        parent.spawnParticle(particle, center.x, center.y, center.z,
                velocityX, velocityY, velocityZ, parameters);
    }

    @Override
    public void spawnParticle(EnumParticleTypes particle, boolean longDistance,
                              double x, double y, double z, double velocityX,
                              double velocityY, double velocityZ,
                              int... parameters) {
        Vec3d center = access.worldPointForLocal(
                new ContraptionTransform.Point(x, y, z));
        parent.spawnParticle(particle, longDistance, center.x, center.y,
                center.z, velocityX, velocityY, velocityZ, parameters);
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
        return access.isSideSolid(pos, side, defaultValue);
    }

    @Override
    public boolean isBlockModifiable(EntityPlayer player, BlockPos pos) {
        return parent.isBlockModifiable(
                player, new BlockPos(access.getWorldCenter(pos)));
    }

    @Override
    public boolean canMineBlockBody(EntityPlayer player, BlockPos pos) {
        return parent.canMineBlockBody(
                player, new BlockPos(access.getWorldCenter(pos)));
    }

    private static final class ContraptionChunk extends Chunk {
        private final int originX;
        private final int originZ;

        private ContraptionChunk(ContraptionWorld world, int chunkX, int chunkZ) {
            super(world, chunkX, chunkZ);
            originX = chunkX << 4;
            originZ = chunkZ << 4;
        }

        @Override
        public IBlockState getBlockState(int x, int y, int z) {
            return getWorld().getBlockState(new BlockPos(originX + (x & 15), y, originZ + (z & 15)));
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return getWorld().getBlockState(pos);
        }

        @Override
        public void markDirty() {
            getWorld().markChunkDirty(BlockPos.ORIGIN, null);
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos,
                                        EnumCreateEntityType createEntityType) {
            return getWorld().getTileEntity(pos);
        }
    }

    private record ScheduledTick(net.minecraft.block.Block block,
                                 long dueTick) {
    }
}
