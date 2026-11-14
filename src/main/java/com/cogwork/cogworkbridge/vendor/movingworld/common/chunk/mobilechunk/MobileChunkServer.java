/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/chunk/mobilechunk/MobileChunkServer.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk;

import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.EntityMovingWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MobileChunkServer extends MobileChunk {
    private Set<BlockPos> blockQueue;
    private Set<BlockPos> tileQueue;

    public MobileChunkServer(World world, EntityMovingWorld entityMovingWorld) {
        super(world, entityMovingWorld);
        blockQueue = new HashSet<>();
        tileQueue = new HashSet<>();
    }

    public Collection<BlockPos> getBlockQueue() {
        return blockQueue;
    }

    public Collection<BlockPos> getTileQueue() {
        return tileQueue;
    }

    @Override
    public void clearAllBlocks() {
        super.clearAllBlocks();
        blockQueue.clear();
        tileQueue.clear();
    }

    @Override
    public boolean addBlockWithState(BlockPos pos, IBlockState blockState) {
        if (super.addBlockWithState(pos, blockState)) {
            blockQueue.add(pos);
            return true;
        }
        return false;
    }

    @Override
    public boolean setBlockState(BlockPos pos, IBlockState state) {
        if (super.setBlockState(pos, state)) {
            blockQueue.add(pos);
            return true;
        }
        return false;
    }

    @Override
    public void setTileEntity(BlockPos pos, TileEntity tileentity) {
        tileQueue.add(pos);
        super.setTileEntity(pos, tileentity);
    }

    @Override
    public void removeChunkBlockTileEntity(BlockPos pos) {
        tileQueue.add(pos);
        super.removeChunkBlockTileEntity(pos);
    }

    @Override
    public void markTileDirty(BlockPos pos) {
        tileQueue.add(pos);
        super.markTileDirty(pos);
    }

    @Override
    public Side side() {
        return Side.SERVER;
    }
}
