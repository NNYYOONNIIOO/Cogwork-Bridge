package com.cogwork.cogworkbridge.content.kinetics.steam;

import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.Rotation;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public final class PoweredShaftTileEntity extends ShaftTileEntity {
    public static final float BASE_SPEED = 16F;
    public static final float CAPACITY_SCALE = 1024F;
    private BlockPos engineOffset;
    private float efficiency;
    private int direction = 1;
    @Override public boolean isSource() { return true; }
    @Override public float getStressImpact() { return 0; }
    private int speedModifier() { return 1 + (efficiency >= 1 ? 3 : Math.min(2, (int) Math.floor(efficiency * 4))); }
    @Override protected Rotation computeSourceRotation() { return efficiency > 0 ? Rotation.of(getAxis(), direction * BASE_SPEED * speedModifier()) : Rotation.NONE; }
    @Override public float getStressCapacity() { return efficiency * CAPACITY_SCALE / speedModifier(); }
    public boolean canClaim(BlockPos engine) { return engineOffset == null || pos.subtract(engine).equals(engineOffset); }
    public void power(BlockPos engine, int direction, float efficiency) {
        if (!canClaim(engine)) return;
        BlockPos offset = pos.subtract(engine);
        boolean changed = !offset.equals(engineOffset) || this.direction != direction || Float.compare(this.efficiency, efficiency) != 0;
        engineOffset = offset; this.direction = direction; this.efficiency = Math.max(0, Math.min(1, efficiency));
        if (changed) { KineticNetworkManager.discard(world); markDirty(); sync(); }
    }
    public void release(BlockPos engine) {
        if (world == null || world.isRemote || !canClaim(engine)) return;
        var state = world.getBlockState(pos);
        if (state.getBlock() == AllBlocks.POWERED_SHAFT) {
            world.setBlockState(pos, AllBlocks.SHAFT.getDefaultState().withProperty(KineticBlock.AXIS, state.getValue(KineticBlock.AXIS)), 3);
            KineticNetworkManager.discard(world);
        }
    }
    @Override public void update() {
        if (world != null && !world.isRemote && engineOffset != null) {
            BlockPos engine = pos.subtract(engineOffset);
            if (world.isBlockLoaded(engine) && !(world.getTileEntity(engine) instanceof SteamEngineTileEntity)) { release(engine); return; }
        }
        super.update();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); if (engineOffset != null) tag.setLong("EngineOffset", engineOffset.toLong());
        tag.setFloat("EngineEfficiency", efficiency); tag.setInteger("EngineDirection", direction); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); engineOffset = tag.hasKey("EngineOffset") ? BlockPos.fromLong(tag.getLong("EngineOffset")) : null;
        efficiency = tag.getFloat("EngineEfficiency"); direction = tag.hasKey("EngineDirection") ? tag.getInteger("EngineDirection") : 1;
    }
}
