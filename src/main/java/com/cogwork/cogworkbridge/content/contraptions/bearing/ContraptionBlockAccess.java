package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import com.cogwork.cogworkbridge.content.contraptions.transform.ContraptionTransform;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunk;
import net.minecraft.util.math.Vec3d;
import com.cogwork.cogworkbridge.content.fluids.FluidTankBlock;
import com.cogwork.cogworkbridge.content.fluids.FluidTankTileEntity;

import java.util.HashMap;
import java.util.Map;

/** Block access for baked-model rendering and per-block collision lookup. */
public final class ContraptionBlockAccess implements IBlockAccess {

    private final Map<BlockPos, IBlockState> states = new HashMap<>();
    private final BearingContraption contraption;
    private final boolean createTiles;
    private final Biome biome;
    private final net.minecraft.world.World world;
    private final ContraptionTransform.Point lightPivot;
    private final ContraptionTransform.Axis lightAxis;
    private final float lightAngle;
    private final BearingContraptionEntity transformEntity;
    private final BlockPos anchor;
    private final Map<BlockPos, TileEntity> tileEntities = new HashMap<>();
    private final Map<BlockPos, Integer> lightSources = new HashMap<>();
    private final Map<BlockPos, Integer> propagatedBlockLights = new HashMap<>();
    private final Map<BlockPos, Integer> propagatedSkyLights = new HashMap<>();
    private final Map<BlockPos, Integer> skyFloor = new HashMap<>();
    private static final java.util.Set<net.minecraft.block.Block> FAILED_EMISSIONS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private MobileChunk liveChunk;
    private boolean lightFieldBuilt;
    private boolean buildingLightField;

    public ContraptionBlockAccess(BearingContraption contraption, net.minecraft.world.World world,
                                  BlockPos lightPos) {
        this(contraption, world,
                new ContraptionTransform.Point(
                        lightPos.getX() + 0.5D,
                        lightPos.getY() + 0.5D,
                        lightPos.getZ() + 0.5D),
                ContraptionTransform.Axis.Y, 0.0F);
    }

    public ContraptionBlockAccess(BearingContraption contraption, net.minecraft.world.World world,
                                  ContraptionTransform.Point lightPivot,
                                  ContraptionTransform.Axis lightAxis,
                                  float lightAngle) {
        this(contraption, world, lightPivot, lightAxis, lightAngle,
                null, true);
    }

    public ContraptionBlockAccess(BearingContraption contraption,
                                  net.minecraft.world.World world,
                                  BearingContraptionEntity transformEntity) {
        this(contraption, world, transformEntity.getRotationPivot(),
                transformEntity.getRotationAxis(), transformEntity.getAngle(),
                transformEntity, true);
    }

    private ContraptionBlockAccess(BearingContraption contraption,
                                   net.minecraft.world.World world,
                                   ContraptionTransform.Point lightPivot,
                                   ContraptionTransform.Axis lightAxis,
                                   float lightAngle, boolean createTiles) {
        this(contraption, world, lightPivot, lightAxis, lightAngle,
                null, createTiles);
    }

    private ContraptionBlockAccess(BearingContraption contraption,
                                   net.minecraft.world.World world,
                                   ContraptionTransform.Point lightPivot,
                                   ContraptionTransform.Axis lightAxis,
                                   float lightAngle,
                                   BearingContraptionEntity transformEntity,
                                   boolean createTiles) {
        this.contraption = contraption;
        this.createTiles = createTiles;
        this.world = world;
        this.lightPivot = lightPivot;
        this.lightAxis = lightAxis;
        this.lightAngle = lightAngle;
        this.transformEntity = transformEntity;
        this.anchor = contraption.anchor();
        this.biome = world.getBiome(new BlockPos(lightPivot.x(),
                lightPivot.y(), lightPivot.z()));
        for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
            BlockPos local = snapshot.pos().subtract(contraption.anchor());
            states.put(local, snapshot.state());
        }
    }

    /**
     * Builds the state-only view used by the moving chunk's baked lighting.
     * It must not create or deserialize tiles during vertex generation.
     */
    public static ContraptionBlockAccess lightOnly(
            BearingContraption contraption, net.minecraft.world.World world,
            ContraptionTransform.Point lightPivot,
            ContraptionTransform.Axis lightAxis, float lightAngle) {
        return new ContraptionBlockAccess(
                contraption, world, lightPivot, lightAxis, lightAngle, false);
    }

    public static ContraptionBlockAccess lightOnly(
            BearingContraption contraption, net.minecraft.world.World world,
            BearingContraptionEntity entity) {
        return new ContraptionBlockAccess(
                contraption, world, entity.getRotationPivot(),
                entity.getRotationAxis(), entity.getAngle(), entity, false);
    }

    /**
     * Uses the moving chunk as the authority after assembly. Snapshot tiles
     * remain the fallback for model generation and structural collisions,
     * where a live chunk must not be required.
     */
    public ContraptionBlockAccess withLiveChunk(MobileChunk chunk) {
        liveChunk = chunk;
        return this;
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        if (liveChunk != null) {
            return liveChunk.getTileEntity(pos);
        }
        TileEntity existing = tileEntities.get(pos);
        if (existing != null || !createTiles) {
            return existing;
        }
        IBlockState state = states.get(pos);
        NBTTagCompound data = contraption.tileDataAt(anchor.add(pos));
        if (state == null || data == null) {
            return null;
        }
        BlockPos worldPos = anchor.add(pos);
        if (state.getBlock() instanceof FluidTankBlock) {
            FluidTankTileEntity.relocateTag(data, worldPos);
        } else {
            data.setInteger("x", worldPos.getX());
            data.setInteger("y", worldPos.getY());
            data.setInteger("z", worldPos.getZ());
        }
        TileEntity tile = state.getBlock().createTileEntity(world, state);
        if (tile == null) {
            if (state.getBlock().hasTileEntity(state)
                    && com.cogwork.cogworkbridge.util.LogOnce.first("contraption-create-tile", state.getBlock())) {
                com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                        "Could not create contraption tile entity for {} at {}",
                        state.getBlock().getRegistryName(), pos);
            }
            return null;
        }
        tile.setWorld(world);
        tile.setPos(worldPos);
        tile.readFromNBT(data);
        if (tile instanceof com.cogwork.cogworkbridge.kinetic.KineticTileEntity kinetic) {
            kinetic.setContraptionRenderContext(state, pos);
        }
        tileEntities.put(pos.toImmutable(), tile);
        return tile;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        ensureLightField();
        int external = realWorldLightAt(pos);
        int sky = Math.max(localSkyLightAt(pos),
                Math.max(0, Math.min(15, external >> 20 & 15)));
        sky = Math.max(0, Math.min(15, sky));
        int block = Math.max(Math.max(0, Math.min(15, lightValue)),
                Math.max(localBlockLightAt(pos),
                        Math.max(0, Math.min(15, external >> 4 & 15))));
        return (sky << 20) | (block << 4);
    }

    /** Samples the real world light at the rendered centre of a local block. */
    public int getWorldLightAt(BlockPos localPos) {
        return realWorldLightAt(localPos);
    }

    private int realWorldLightAt(BlockPos localPos) {
        BlockPos sample = new BlockPos(getWorldCenter(localPos));
        int sky = 0;
        int block = 0;
        if (world.isBlockLoaded(sample)) {
            sky = world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY,
                    sample);
            block = world.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK,
                    sample);
        }
        sky = Math.max(0, Math.min(15, sky));
        block = Math.max(0, Math.min(15, block));
        return (sky << 20) | (block << 4);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        if (liveChunk != null) {
            return liveChunk.getBlockState(pos);
        }
        return states.getOrDefault(pos, Blocks.AIR.getDefaultState());
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        if (liveChunk != null) {
            IBlockState state = liveChunk.getBlockState(pos);
            return state.getBlock().isAir(state, this, pos);
        }
        IBlockState state = states.get(pos);
        return state == null || state.getBlock().isAir(state, this, pos);
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return biome;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return ContraptionWorldContext.strongPower(this, pos, direction);
    }

    public int getWeakPower(BlockPos pos, EnumFacing direction) {
        return ContraptionWorldContext.weakPower(this, pos, direction);
    }

    private int localEmissionAt(IBlockState state, BlockPos pos) {
        try {
            if (state.getBlock().isAir(state, this, pos)) {
                return 0;
            }
            return Math.max(0, Math.min(15, state.getLightValue(this, pos)));
        } catch (RuntimeException exception) {
            if (FAILED_EMISSIONS.add(state.getBlock())) {
                com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                        "Could not read carried light emission for {}", state, exception);
            }
            return 0;
        }
    }

    public int propagatedLightAt(BlockPos pos) {
        ensureLightField();
        return localBlockLightAt(pos);
    }

    /**
     * Returns the local block light after attenuation.
     */
    private int localBlockLightAt(BlockPos pos) {
        Integer propagated = propagatedBlockLights.get(pos);
        return Math.max(0, Math.min(15,
                propagated != null ? propagated : 0));
    }

    /** Returns the local sky light after attenuation through captured blocks. */
    private int localSkyLightAt(BlockPos pos) {
        return Math.max(0, Math.min(15,
                propagatedSkyLights.getOrDefault(pos, defaultLocalSkyLight(pos))));
    }

    private void ensureLightField() {
        if (lightFieldBuilt || buildingLightField) {
            return;
        }
        buildingLightField = true;
        try {
            for (Map.Entry<BlockPos, IBlockState> entry : states.entrySet()) {
                int emission = localEmissionAt(entry.getValue(), entry.getKey());
                if (emission > 0) {
                    lightSources.put(entry.getKey(), emission);
                }
            }
            buildLocalBlockLightField();
            buildLocalSkyLightField();
            lightFieldBuilt = true;
        } finally {
            buildingLightField = false;
        }
    }

    private void buildLocalBlockLightField() {
        java.util.ArrayDeque<LightNode> queue = new java.util.ArrayDeque<>();
        for (Map.Entry<BlockPos, Integer> source : lightSources.entrySet()) {
            enqueueLight(propagatedBlockLights, queue,
                    source.getKey(), source.getValue());
        }
        while (!queue.isEmpty()) {
            LightNode node = queue.removeFirst();
            if (propagatedBlockLights.getOrDefault(node.pos(), 0) != node.level()) {
                continue;
            }
            if (node.level() <= 1) {
                continue;
            }
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos next = node.pos().offset(face);
                IBlockState nextState = getBlockState(next);
                int opacity = Math.max(1,
                        nextState.getLightOpacity(this, next));
                int level = node.level() - opacity;
                enqueueLight(propagatedBlockLights, queue, next, level);
            }
        }
    }

    private void buildLocalSkyLightField() {
        if (states.isEmpty()) {
            return;
        }
        java.util.ArrayDeque<LightNode> queue = new java.util.ArrayDeque<>();
        for (BlockPos pos : states.keySet()) {
            BlockPos column = new BlockPos(pos.getX(), 0, pos.getZ());
            int sky = skyLightAt(column, 0);
            skyFloor.put(column, sky);
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : states.keySet()) {
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                BlockPos column = new BlockPos(x, 0, z);
                int fallback = skyFloor.getOrDefault(column, 0);
                seedBoundarySky(new BlockPos(x, maxY + 1, z), fallback, queue);
                for (int y = minY - 1; y <= maxY + 1; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!states.containsKey(pos) && isBoundary(pos)) {
                        seedBoundarySky(pos, fallback, queue);
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            LightNode node = queue.removeFirst();
            if (propagatedSkyLights.getOrDefault(node.pos(), 0) != node.level()) {
                continue;
            }
            if (node.level() <= 1) {
                continue;
            }
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos next = node.pos().offset(face);
                IBlockState nextState = getBlockState(next);
                int opacity = Math.max(1,
                        nextState.getLightOpacity(this, next));
                int level = node.level() - opacity;
                enqueueLight(propagatedSkyLights, queue, next, level);
            }
        }
    }

    private void seedBoundarySky(BlockPos pos, int fallback,
                                  java.util.ArrayDeque<LightNode> queue) {
        if (states.containsKey(pos)) {
            return;
        }
        int sky = skyLightAt(pos, fallback);
        if (sky > 0) {
            enqueueLight(propagatedSkyLights, queue, pos, sky);
        }
    }

    private static void enqueueLight(Map<BlockPos, Integer> levels,
                                     java.util.ArrayDeque<LightNode> queue,
                                     BlockPos pos, int level) {
        if (level <= levels.getOrDefault(pos, 0)) {
            return;
        }
        BlockPos immutable = pos.toImmutable();
        levels.put(immutable, level);
        queue.addLast(new LightNode(immutable, level));
    }

    private boolean isBoundary(BlockPos pos) {
        for (EnumFacing face : EnumFacing.values()) {
            if (!states.containsKey(pos.offset(face))) {
                return true;
            }
        }
        return false;
    }

    private int skyLightAt(BlockPos localPos, int fallback) {
        Vec3d center = getWorldCenter(localPos);
        BlockPos sample = new BlockPos(center);
        if (!world.isBlockLoaded(sample)) {
            return fallback;
        }
        return Math.max(0, Math.min(15,
                world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, sample)));
    }

    private int defaultLocalSkyLight(BlockPos pos) {
        BlockPos column = new BlockPos(pos.getX(), 0, pos.getZ());
        return skyFloor.getOrDefault(column, 0);
    }

    public Vec3d getWorldCenter(BlockPos localPos) {
        return worldPointForLocal(new ContraptionTransform.Point(
                localPos.getX() + 0.5D, localPos.getY() + 0.5D,
                localPos.getZ() + 0.5D));
    }

    public Vec3d worldPointForLocal(ContraptionTransform.Point localPoint) {
        double x = localPoint.x() + anchor.getX();
        double y = localPoint.y() + anchor.getY();
        double z = localPoint.z() + anchor.getZ();
        if (transformEntity != null) {
            return transformEntity.localToWorld(new Vec3d(x, y, z));
        }
        if (lightAngle == 0.0F) {
            return new Vec3d(x, y, z);
        }
        ContraptionTransform.Point transformed =
                ContraptionTransform.rotateAroundBlockCenter(
                        x, y, z, lightPivot, lightAxis, lightAngle);
        return new Vec3d(transformed.x(), transformed.y(),
                transformed.z());
    }

    public AxisAlignedBB worldBoxForLocal(AxisAlignedBB localBox) {
        Vec3d[] corners = new Vec3d[]{
                worldPointForLocal(new ContraptionTransform.Point(
                        localBox.minX, localBox.minY, localBox.minZ)),
                worldPointForLocal(new ContraptionTransform.Point(
                        localBox.maxX, localBox.minY, localBox.minZ)),
                worldPointForLocal(new ContraptionTransform.Point(
                        localBox.minX, localBox.maxY, localBox.minZ)),
                worldPointForLocal(new ContraptionTransform.Point(
                        localBox.maxX, localBox.maxY, localBox.minZ)),
                worldPointForLocal(new ContraptionTransform.Point(
                        localBox.minX, localBox.minY, localBox.maxZ)),
                worldPointForLocal(new ContraptionTransform.Point(
                        localBox.maxX, localBox.minY, localBox.maxZ)),
                worldPointForLocal(new ContraptionTransform.Point(
                        localBox.minX, localBox.maxY, localBox.maxZ)),
                worldPointForLocal(new ContraptionTransform.Point(
                        localBox.maxX, localBox.maxY, localBox.maxZ))
        };
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3d corner : corners) {
            minX = Math.min(minX, corner.x);
            minY = Math.min(minY, corner.y);
            minZ = Math.min(minZ, corner.z);
            maxX = Math.max(maxX, corner.x);
            maxY = Math.max(maxY, corner.y);
            maxZ = Math.max(maxZ, corner.z);
        }
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public WorldType getWorldType() {
        return WorldType.DEFAULT;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }

    private record LightNode(BlockPos pos, int level) {
    }
}
