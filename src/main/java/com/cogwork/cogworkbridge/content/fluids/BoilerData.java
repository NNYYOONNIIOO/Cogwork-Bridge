package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.content.kinetics.steam.SteamEngineBlock;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Boiler supply is a rolling input rate, rather than the stored fluid level. */
final class BoilerData {
    private final int[] samples = new int[10];
    private int sampleIndex, gathered, engines, heat, whistles;
    private boolean passive;
    private float waterRate;

    boolean active() { return engines > 0 || whistles > 0; }
    boolean heated() { return active() && (heat > 0 || passive); }
    int heat() { return heat; }
    boolean passiveHeat() { return heat == 0 && passive; }
    float waterRate() { return waterRate; }
    void addWater(int amount) { gathered = (int) Math.min(Integer.MAX_VALUE, (long) gathered + Math.max(0, amount)); }
    void tick(FluidTankTileEntity tank) {
        World world = tank.getWorld();
        long time = world.getTotalWorldTime();
        if (time % 20 == 0) {
            evaluate(tank);
            if (active() && Math.min(heat, Math.min(tank.getWidth() * tank.getWidth() * tank.getHeight() / 4, (int) Math.ceil(waterRate) / 10)) >= 18)
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, tank.getPos(), "steam_engine_maxed");
        }
        if (!active() || time % 5 != 0) return;
        samples[sampleIndex] = gathered; gathered = 0; sampleIndex = (sampleIndex + 1) % samples.length;
        int max = 0; for (int sample : samples) max = Math.max(max, sample);
        float nextWater = tank instanceof CreativeFluidTankTileEntity ? 200F : max / 5F;
        if (Float.compare(waterRate, nextWater) != 0) { waterRate = nextWater; tank.syncStructure(); }
    }
    private void evaluate(FluidTankTileEntity tank) {
        World world = tank.getWorld(); BlockPos base = tank.getPos();
        java.util.Set<Integer> pitches = new java.util.HashSet<>();
        int nextEngines = 0, nextHeat = 0, nextWhistles = 0; boolean nextPassive = false;
        for (int x = 0; x < tank.getWidth(); x++) for (int z = 0; z < tank.getWidth(); z++) {
            BlockPos heater = base.add(x, -1, z);
            if (world.isBlockLoaded(heater)) {
                IBlockState state = world.getBlockState(heater);
                TileEntity tile = world.getTileEntity(heater);
                if (tile instanceof BlazeBurnerTileEntity burner) {
                    int level = burner.getProcessingHeat();
                    nextHeat += level;
                    if (level == 0 && burner.isOccupied()) nextPassive = true;
                } else if (state.getBlock() == Blocks.FIRE || state.getBlock() == Blocks.MAGMA
                        || state.getBlock() == Blocks.LAVA || state.getBlock() == Blocks.FLOWING_LAVA) nextPassive = true;
            }
            for (int y = 0; y < tank.getHeight(); y++) {
                BlockPos part = base.add(x, y, z);
                for (EnumFacing side : EnumFacing.VALUES) {
                    BlockPos p = part.offset(side);
                    if (!world.isBlockLoaded(p)) continue;
                    IBlockState state = world.getBlockState(p);
                    if (state.getBlock() instanceof SteamEngineBlock && state.getValue(SteamEngineBlock.FACING) == side) nextEngines++;
                    if (state.getBlock() instanceof com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock && com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock.attachedDirection(state) == side.getOpposite()) nextWhistles++;
                }
            }
        }
        for (int x = -1; x <= tank.getWidth(); x++) for (int z = -1; z <= tank.getWidth(); z++) for (int y = 0; y < tank.getHeight(); y++) {
            BlockPos p = base.add(x, y, z);
            if (world.getTileEntity(p) instanceof com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleTileEntity whistle) {
                BlockPos attached = p.offset(com.cogwork.cogworkbridge.content.fluids.whistle.SteamWhistleBlock.attachedDirection(world.getBlockState(p)));
                if (attached.getX() >= base.getX() && attached.getX() < base.getX()+tank.getWidth() && attached.getZ() >= base.getZ() && attached.getZ() < base.getZ()+tank.getWidth())
                    pitches.add(whistle.size() * 100 + whistle.pitch());
            }
        }
        if (pitches.size() >= 12) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, base, "pipe_organ");
        if (engines != nextEngines || whistles != nextWhistles || heat != nextHeat || passive != nextPassive) {
            boolean becomingBoiler = !active() && (nextEngines > 0 || nextWhistles > 0);
            engines = nextEngines; whistles = nextWhistles; heat = nextHeat; passive = nextPassive; tank.syncStructure();
            if (becomingBoiler && tank.hasWindows()) tank.toggleWindows();
        }
    }
    float efficiency(FluidTankTileEntity tank) {
        if (engines <= 0) return 0F;
        int maxSize = Math.min(18, tank.getWidth() * tank.getWidth() * tank.getHeight() / 4);
        int maxWater = Math.min(18, (int) Math.ceil(waterRate) / 10);
        if (maxSize == 0 || maxWater == 0) return 0F;
        if (heat == 0) return passive ? .125F / engines : 0F;
        int level = Math.min(heat, Math.min(maxSize, maxWater));
        return Math.min(1F, level / (float) engines);
    }
    float gauge(FluidTankTileEntity tank) {
        int size = Math.min(18, tank.getWidth() * tank.getWidth() * tank.getHeight() / 4);
        int water = Math.min(18, (int) Math.ceil(waterRate) / 10);
        if (size == 0 || water == 0) return 0F;
        if (heat == 0 && passive) return .125F;
        return Math.min(heat, Math.min(size, water)) / (float) size;
    }
    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setIntArray("Samples", samples); tag.setInteger("Index", sampleIndex);
        tag.setInteger("Gathered", gathered); tag.setInteger("Engines", engines); tag.setInteger("Whistles", whistles); tag.setInteger("Heat", heat);
        tag.setBoolean("Passive", passive); tag.setFloat("WaterRate", waterRate); return tag;
    }
    void read(NBTTagCompound tag) {
        int[] stored = tag.getIntArray("Samples"); System.arraycopy(stored, 0, samples, 0, Math.min(samples.length, stored.length));
        sampleIndex = Math.floorMod(tag.getInteger("Index"), samples.length); gathered = tag.getInteger("Gathered");
        engines = tag.getInteger("Engines"); whistles = tag.getInteger("Whistles"); heat = tag.getInteger("Heat"); passive = tag.getBoolean("Passive"); waterRate = tag.getFloat("WaterRate");
    }
}
