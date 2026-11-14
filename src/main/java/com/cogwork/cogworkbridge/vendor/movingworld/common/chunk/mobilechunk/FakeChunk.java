/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/chunk/mobilechunk/FakeChunk.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;

public class FakeChunk extends Chunk {
    private final int originX;
    private final int originZ;

    public FakeChunk(MobileChunk mobileChunk, int chunkX, int chunkZ) {
        super(mobileChunk.getFakeWorld(), chunkX, chunkZ);
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

    @Nullable
    @Override
    public TileEntity getTileEntity(BlockPos pos, EnumCreateEntityType p_177424_2_) {
        return getWorld().getTileEntity(pos);
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

}
