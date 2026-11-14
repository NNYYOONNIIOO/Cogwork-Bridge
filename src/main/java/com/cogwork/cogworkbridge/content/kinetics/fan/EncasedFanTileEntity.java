package com.cogwork.cogworkbridge.content.kinetics.fan;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import xy177.farmersfuturedelight.common.block.BlockCampfire;
import xy177.farmersfuturedelight.common.block.BlockSoulFire;
import xy177.farmersfuturedelight.common.registry.FFDBlocks;

public final class EncasedFanTileEntity extends KineticTileEntity {
    public enum Medium { NONE, SPLASHING, BLASTING, SMOKING, HAUNTING }

    private float currentRange;
    private float previousSpeed;
    private EnumFacing previousFacing;
    private int refreshDelay;
    private Medium[] media = new Medium[0];

    @Override public float getStressImpact() { return 2.0F; }

    @Override
    public void update() {
        super.update();
        if (world == null || world instanceof FakeWorld) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof EncasedFanBlock)) {
            return;
        }
        EnumFacing facing = state.getValue(EncasedFanBlock.FACING);
        float speed = getSpeed();
        if (speed == 0) {
            currentRange = 0;
            previousSpeed = 0;
            media = new Medium[0];
            return;
        }
        BlockPos front = pos.offset(facing);
        if (world.getBlockState(front).getBlock() instanceof NozzleBlock) {
            currentRange = 0;
            media = new Medium[0];
            return;
        }
        if (--refreshDelay <= 0 || previousSpeed != speed || previousFacing != facing) {
            refreshDelay = 30;
            previousSpeed = speed;
            previousFacing = facing;
            currentRange = AirFlow.unobstructedRange(world, pos, facing, AirFlow.range(speed));
            rebuildMedia(facing, AirFlow.pushing(speed, facing));
        }
        if (world.isRemote) {
            if (currentRange > 0.0F && world.rand.nextFloat() < 0.5F) {
                CogworkBridge.proxy.spawnFanAirFlow(this, facing, AirFlow.pushing(speed, facing));
            }
            return;
        }
        AirFlow.applyLine(world, pos, facing, speed, currentRange, media);
    }

    public float getAirRange() {
        return currentRange;
    }

    public Medium getMediumAt(double distance) {
        if (media.length == 0 || distance < 0.0D || distance > currentRange) return Medium.NONE;
        return media[Math.min((int) distance, media.length - 1)];
    }

    private void rebuildMedia(EnumFacing facing, boolean pushing) {
        int length = (int) Math.ceil(currentRange);
        media = new Medium[length];
        Medium carried = Medium.NONE;
        for (int i = pushing ? 0 : length - 1; i >= 0 && i < length; i += pushing ? 1 : -1) {
            BlockPos current = pos.offset(facing, i + 1);
            Medium detected = mediumAt(current);
            if (detected != Medium.NONE) carried = detected;
            media[i] = carried;
        }
    }

    private Medium mediumAt(BlockPos current) {
        IBlockState state = world.getBlockState(current);
        if (state.getMaterial() == Material.WATER) return Medium.SPLASHING;
        if (state.getMaterial() == Material.LAVA) return Medium.BLASTING;
        if (state.getBlock() instanceof BlockSoulFire) return Medium.HAUNTING;
        if (isFFDCampfire(state)) {
            if (!BlockCampfire.isLit(state, world, current)) return Medium.NONE;
            return state.getBlock() == FFDBlocks.SOUL_CAMPFIRE ? Medium.HAUNTING : Medium.SMOKING;
        }
        if (world.getTileEntity(current) instanceof BlazeBurnerTileEntity burner) {
            if (!burner.isOccupied() && burner.getEmptyFlame() == BlazeBurnerTileEntity.FlameType.SOUL)
                return Medium.HAUNTING;
            return switch (burner.getHeatLevel()) {
                case NONE -> Medium.NONE;
                case SMOULDERING -> Medium.SMOKING;
                default -> burner.isOccupied() ? Medium.BLASTING : Medium.SMOKING;
            };
        }
        return state.getBlock() == Blocks.FIRE ? Medium.SMOKING : Medium.NONE;
    }

    static boolean isFFDCampfire(IBlockState state) {
        return state.getBlock() instanceof BlockCampfire;
    }
}
