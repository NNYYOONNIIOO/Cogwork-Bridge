/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/entity/MovingWorldHandlerCommon.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.entity;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.MobileChunk;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashSet;

public abstract class MovingWorldHandlerCommon {

    public MovingWorldHandlerCommon(EntityMovingWorld entityMovingWorld) {
        setMovingWorld(entityMovingWorld);
    }

    public abstract EntityMovingWorld getMovingWorld();

    public abstract void setMovingWorld(EntityMovingWorld movingWorld);

    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        return !player.isSneaking();
    }

    public void onChunkUpdate() {
        MobileChunk chunk = getMovingWorld().getMobileChunk();
        getMovingWorld().getCapabilities().clearBlockCount();
        for (int i = chunk.minX(); i < chunk.maxX(); i++) {
            for (int j = chunk.minY(); j < chunk.maxY(); j++) {
                for (int k = chunk.minZ(); k < chunk.maxZ(); k++) {
                    BlockPos pos = new BlockPos(i, j, k);
                    IBlockState blockState = chunk.getBlockState(pos);
                    if (blockState != null && blockState.getMaterial() != Material.AIR) {
                        getMovingWorld().getCapabilities().onChunkBlockAdded(blockState, pos);
                    }
                }
            }
        }

        getMovingWorld().setSize(Math.max(chunk.maxX() - chunk.minX(), chunk.maxZ() - chunk.minZ()), chunk.maxY() - chunk.minY());
        World.MAX_ENTITY_RADIUS = Math.max(World.MAX_ENTITY_RADIUS, Math.max(getMovingWorld().width, getMovingWorld().height) + 2F);

        try {
            getMovingWorld().fillAirBlocks(new HashSet<>(), new BlockPos(-1, -1, -1));
        } catch (StackOverflowError e) {
            if (com.cogwork.cogworkbridge.util.LogOnce.first("movingworld-initialize", getMovingWorld().getClass(), e)) {
                CogworkBridge.LOGGER.error("Failure during moving world post-initialization", e);
            }
        }

        getMovingWorld().setLayeredBlockVolumeCount(new int[chunk.maxY() - chunk.minY()]);
        for (int y = 0; y < getMovingWorld().getLayeredBlockVolumeCount().length; y++) {
            for (int i = chunk.minX(); i < chunk.maxX(); i++) {
                for (int j = chunk.minZ(); j < chunk.maxZ(); j++) {
                    BlockPos pos = new BlockPos(i, y + chunk.minY(), j);
                    if (chunk.isBlockTakingWaterVolume(pos)) {
                        int[] layeredBlockVolCount = getMovingWorld().getLayeredBlockVolumeCount();
                        layeredBlockVolCount[y]++;
                        getMovingWorld().setLayeredBlockVolumeCount(layeredBlockVolCount);
                    }
                }
            }
        }
        getMovingWorld().setFlying(getMovingWorld().getCapabilities().canFly());
        getMovingWorld().getCapabilities().postBlockAdding();
    }
}
