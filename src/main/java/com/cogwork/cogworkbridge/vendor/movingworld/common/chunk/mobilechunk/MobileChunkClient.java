/*
 * Derived from MovingWorld (https://github.com/darkevilmac/MovingWorld).
 * Upstream: src/main/java/com/elytradev/movingworld/common/chunk/mobilechunk/MobileChunkClient.java
 * Revision: 400225200ea4f913047f67dbfb44e882fcf997d8.
 * Modified by Cogwork Bridge contributors: relocated packages and adapted
 * for integration with Cogwork Bridge contraptions and their lifecycle.
 * Licensed under the Apache License, Version 2.0; see
 * META-INF/licenses/MovingWorld-LICENSE.txt and THIRD-PARTY-NOTICES.txt.
 */
package com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk;

import com.cogwork.cogworkbridge.vendor.movingworld.client.render.MobileChunkRenderer;
import com.cogwork.cogworkbridge.vendor.movingworld.common.entity.EntityMovingWorld;
import com.google.common.collect.Maps;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class MobileChunkClient extends MobileChunk {
    public Map<BlockPos, TileEntity> fastTESRS = Maps.newHashMap();
    public Map<BlockPos, TileEntity> normalTESRS = Maps.newHashMap();
    private MobileChunkRenderer renderer;

    public MobileChunkClient(World world, EntityMovingWorld movingWorld) {
        super(world, movingWorld);
        renderer = new MobileChunkRenderer(this);
    }

    public MobileChunkRenderer getRenderer() {
        return renderer;
    }

    @Override
    public void clearAllBlocks() {
        super.clearAllBlocks();
        fastTESRS.clear();
        normalTESRS.clear();
    }

    @Override
    public void onChunkUnload() {
        List<TileEntity> iterator = new ArrayList<>(chunkTileEntityMap.values());
        for (TileEntity te : iterator) {
            removeChunkBlockTileEntity(te.getPos());
        }
        super.onChunkUnload();
        renderer.markRemoved();
    }

    @Override
    public void setChunkModified() {
        super.setChunkModified();
        renderer.markDirty();
    }

    @Override
    public void setTileEntity(BlockPos pos, TileEntity tileentity) {
        super.setTileEntity(pos, tileentity);

        if (tileentity == null) {
            // Removed a tile, remove it from the render lists as well.
            if (fastTESRS.containsKey(pos))
                fastTESRS.remove(pos);
            if (normalTESRS.containsKey(pos))
                normalTESRS.remove(pos);
        } else {
            // Add a tesr, after confirming it has one, and that it's fast.
            if (TileEntityRendererDispatcher.instance.getRenderer(tileentity) != null) {
                if (tileentity.hasFastRenderer()) {
                    fastTESRS.put(pos, tileentity);
                } else {
                    normalTESRS.put(pos, tileentity);
                }
            }
        }
    }

    @Override
    public Side side() {
        return Side.CLIENT;
    }
}
