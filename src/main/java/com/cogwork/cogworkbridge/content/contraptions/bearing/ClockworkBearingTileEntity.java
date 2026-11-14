package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Set;
import java.util.UUID;

public final class ClockworkBearingTileEntity extends KineticTileEntity implements RotaryContraptionController {
    public static final float ASSEMBLED_STRESS_IMPACT = 4F;
    private UUID hourId, minuteId;
    private float hourAngle, minuteAngle, previousSpeed = Float.NaN;
    private boolean running, requestAssembly, recoveryPending;
    private int mode;

    @Override public boolean ownsRotaryContraption(UUID id) { return id != null && (id.equals(hourId) || id.equals(minuteId)); }
    @Override public void onRotaryContraptionRestored(UUID id) {
        if (id == null) return;
        boolean changed = false;
        if (id.equals(hourId)) { hourId = null; changed = true; }
        if (id.equals(minuteId)) { minuteId = null; changed = true; }
        if (!changed) return;
        recoveryPending = hourId != null || minuteId != null;
        if (!recoveryPending) running = false;
        markDirty(); sync();
    }
    @Override public float getStressImpact() { return running ? ASSEMBLED_STRESS_IMPACT : 0F; }
    public boolean isRunning() { return running; }
    public float getHourAngle() { return hourAngle; }
    public float getHourAngle(float partialTicks) {
        BearingContraptionEntity hand = find(hourId);
        return hand == null ? hourAngle : hand.getRenderAngle(partialTicks);
    }
    public int getMode() { return mode; }
    public void setMode(int value) { mode = Math.floorMod(value, 3); markDirty(); sync(); }
    public void toggle() { if (running) disassemble(); else requestAssembly = true; }

    private BearingContraptionEntity find(UUID id) {
        if (id == null || world == null) return null;
        for (net.minecraft.entity.Entity raw : world.loadedEntityList)
            if (raw instanceof BearingContraptionEntity entity && !entity.isDead && id.equals(entity.getUniqueID())) return entity;
        return null;
    }

    private void assemble() {
        if (running || getSpeed() == 0 || !(world.getBlockState(pos).getBlock() instanceof ClockworkBearingBlock)) return;
        EnumFacing facing = world.getBlockState(pos).getValue(MechanicalBearingBlock.FACING);
        BlockPos anchor = pos.offset(facing);
        BearingContraption hour = BearingContraption.capture(world, pos, anchor, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        if (hour == null) return;
        int axialLength = 0;
        for (int i = 0; i < 16; i++) {
            if (!hour.contains(anchor.offset(facing, i))) { axialLength = i; break; }
        }
        BlockPos minuteAnchor = anchor.offset(facing, axialLength);
        BearingContraption minute = null;
        if (axialLength > 0) {
            if (!world.isBlockLoaded(minuteAnchor)) return;
            if (!world.isAirBlock(minuteAnchor)) {
                minute = BearingContraption.capture(world, pos, minuteAnchor, Set.copyOf(hour.blocks().keySet()), positions ->
                        ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
                if (minute == null) return;
            }
        }
        CarriedBearingAssembly hourNested = CarriedBearingAssembly.prepare(world, hour, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        CarriedBearingAssembly minuteNested = minute == null ? null : CarriedBearingAssembly.prepare(world, minute, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        BearingContraptionEntity hourEntity = BearingContraptionEntity.createRotary(world, pos, anchor, facing, hour);
        hourEntity.prepareNestedChildren();
        if (!world.spawnEntity(hourEntity)) return;
        BearingContraptionEntity minuteEntity = minute == null ? null : BearingContraptionEntity.createRotary(world, pos, minuteAnchor, facing, minute);
        if (minuteEntity != null) {
            minuteEntity.prepareNestedChildren();
            if (!world.spawnEntity(minuteEntity)) { hourEntity.discardWithoutRestore(); return; }
        }
        com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(hourEntity, hour);
        if (minuteEntity != null) com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(minuteEntity, minute);
        hourNested.removeFromWorld(); hour.removeFromWorld(world, true);
        if (minute != null) { minuteNested.removeFromWorld(); minute.removeFromWorld(world, true); }
        hourId = hourEntity.getUniqueID(); minuteId = minuteEntity == null ? null : minuteEntity.getUniqueID();
        hourAngle = minuteAngle = 0F; running = true;
        com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "clockwork_bearing");
        com.cogwork.cogworkbridge.content.advancement.AllAdvancements.assembled(world, pos, hour);
        if (minute != null) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.assembled(world, pos, minute);
        markDirty(); sync();
        hourEntity.sendSnapshotToTrackingPlayers(); hourEntity.sendNestedSnapshots();
        if (minuteEntity != null) { minuteEntity.sendSnapshotToTrackingPlayers(); minuteEntity.sendNestedSnapshots(); }
    }

    public void disassemble() {
        if (!running || world == null || world.isRemote) return;
        BearingContraptionEntity hour = find(hourId), minute = find(minuteId);
        if (hourId != null && hour == null || minuteId != null && minute == null) return;
        if (hour != null) { hour.setAngleFromServer(0); hour.restoreAndDiscard(); }
        if (minute != null) { minute.setAngleFromServer(0); minute.restoreAndDiscard(); }
        if (hour != null && !hour.isDead || minute != null && !minute.isDead) return;
        hourId = minuteId = null; hourAngle = minuteAngle = 0; running = false; recoveryPending = false;
        markDirty(); sync();
    }

    private float target(boolean minute, EnumFacing facing) {
        long time = Math.floorMod(world.getWorldTime() * (world.provider.isSurfaceWorld() ? 1L : 24L), 24000L);
        int sign = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1 : -1;
        if (minute) return -sign * 6F * ((time % 1000L) * 60L / 1000L);
        int cycle = mode == 2 ? 24 : 12;
        return -sign * (360F / cycle) * ((time / 1000L + 6L) % cycle);
    }
    private float advance(BearingContraptionEntity entity, float angle, float target, float max) {
        if (entity == null) return angle;
        float difference = MathHelper.wrapDegrees(target - angle);
        float delta = MathHelper.clamp(difference, -max, max);
        entity.setSpeed(delta / .3F);
        entity.advanceToward(angle + delta, delta / .3F);
        entity.sendPoseToTrackingPlayers();
        return entity.getAngle();
    }
    @Override public void update() {
        super.update();
        if (world == null || world.isRemote || world instanceof FakeWorld) return;
        if (recoveryPending) {
            disassemble();
            if (recoveryPending) return;
        }
        float speed = getSpeed();
        if (Float.compare(speed, previousSpeed) != 0) { previousSpeed = speed; requestAssembly = true; }
        if (requestAssembly) {
            requestAssembly = false;
            if (running && speed == 0) disassemble(); else if (!running && speed != 0) assemble();
        }
        if (!running || speed == 0 || !(world.getBlockState(pos).getBlock() instanceof ClockworkBearingBlock)) return;
        BearingContraptionEntity hour = find(hourId), minute = find(minuteId);
        if (hourId != null && hour == null || minuteId != null && minute == null) return;
        EnumFacing facing = world.getBlockState(pos).getValue(MechanicalBearingBlock.FACING);
        float angularSpeed = Math.abs(speed * .3F);
        hourAngle = advance(hour, hourAngle, target(mode == 1, facing), angularSpeed / 2F);
        minuteAngle = advance(minute, minuteAngle, target(mode != 1, facing), angularSpeed);
        markDirty();
        if (world.getTotalWorldTime() % 3L == 0) sync();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (hourId != null) tag.setUniqueId("HourHand", hourId);
        if (minuteId != null) tag.setUniqueId("MinuteHand", minuteId);
        tag.setFloat("HourAngle", hourAngle); tag.setFloat("MinuteAngle", minuteAngle);
        tag.setBoolean("Running", running); tag.setInteger("ClockMode", mode); tag.setBoolean("RecoveryPending", recoveryPending);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        hourId = tag.hasUniqueId("HourHand") ? tag.getUniqueId("HourHand") : null;
        minuteId = tag.hasUniqueId("MinuteHand") ? tag.getUniqueId("MinuteHand") : null;
        hourAngle = tag.getFloat("HourAngle"); minuteAngle = tag.getFloat("MinuteAngle");
        running = tag.getBoolean("Running"); mode = Math.floorMod(tag.getInteger("ClockMode"), 3);
        recoveryPending = tag.getBoolean("RecoveryPending");
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        tag.setFloat("Speed", rotation.getSpeed()); tag.setByte("Axis", (byte) rotation.getAxis().ordinal());
        return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
        super.handleUpdateTag(tag);
    }
}
