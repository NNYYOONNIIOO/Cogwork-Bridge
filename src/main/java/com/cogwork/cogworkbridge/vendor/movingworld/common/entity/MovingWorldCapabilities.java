/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/entity/MovingWorldCapabilities.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.entity;

import com.cogwork.cogworkbridge.vendor.movingworld.common.util.MaterialDensity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public abstract class MovingWorldCapabilities {

    private float mass;
    private int blockCount;
    private boolean autoCalcMass;
    private float speedLimit;
    private float bankingMultiplier;
    private boolean canFly;

    public MovingWorldCapabilities(EntityMovingWorld movingWorld, boolean autoCalcMass) {
        this.autoCalcMass = autoCalcMass;
        clear();
    }

    public void updateMovingWorldEntities() {
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void onChunkBlockAdded(IBlockState blockState, BlockPos pos) {
        blockCount++;
        if (autoCalcMass)
            mass += MaterialDensity.getDensity(blockState);
    }

    public void postBlockAdding() {
    }

    public abstract boolean mountEntity(Entity entity);

    public void clearBlockCount() {
        blockCount = 0;
    }

    public void clear() {
        clearBlockCount();
        mass = 0;
    }

    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = mass;
    }

    public float getSpeedLimit() {
        return speedLimit;
    }

    public void setSpeedLimit(float speedLimit) {
        this.speedLimit = speedLimit;
    }

    public float getBankingMultiplier() {
        return bankingMultiplier;
    }

    public void setBankingMultiplier(float bankingMultiplier) {
        this.bankingMultiplier = bankingMultiplier;
    }

    public boolean canFly() {
        return canFly;
    }

    public void setCanFly(boolean canFly) {
        this.canFly = canFly;
    }

    public void readFromNBT(NBTTagCompound tag) {
    }

    public void writeToNBT(NBTTagCompound tag) {
    }

}
