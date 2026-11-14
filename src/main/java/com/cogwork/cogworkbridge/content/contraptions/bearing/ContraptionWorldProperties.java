package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps carried light sources and the moving chunk's light cache current.
 */
final class ContraptionWorldProperties {

    private final BearingContraptionEntity owner;
    private int lastLightContent = Integer.MIN_VALUE;
    private boolean dirty = true;

    ContraptionWorldProperties(BearingContraptionEntity owner) {
        this.owner = owner;
    }

    void tick() {
        if (owner.world == null || owner.isDead
                || owner.getContraption() == null) {
            if (owner.world != null) ContraptionDynamicLight.update(owner, Map.of());
            dirty = false;
            return;
        }
        updateMovingSources();
        owner.invalidateMovingChunkLight(lastLightContent, dirty);
        lastLightContent = owner.getLightContentKey();
        dirty = false;
    }

    void clear() {
        if (owner.world != null) ContraptionDynamicLight.update(owner, Map.of());
        lastLightContent = Integer.MIN_VALUE;
        dirty = true;
    }

    void markDirty() {
        dirty = true;
    }

    private void updateMovingSources() {
        ContraptionWorld localWorld = owner.getContraptionLocalWorld();
        if (localWorld == null) {
            ContraptionDynamicLight.update(owner, Map.of());
            return;
        }
        Map<BlockPos, Integer> sources = new HashMap<>();
        BlockPos anchor = owner.getAnchor();
        for (BearingContraption.Snapshot snapshot :
                owner.getContraption().blocks().values()) {
            BlockPos local = snapshot.pos().subtract(anchor);
            IBlockState state = localWorld.getBlockState(local);
            int level;
            try {
                level = Math.max(0, Math.min(15,
                        state.getLightValue(localWorld, local)));
            } catch (RuntimeException exception) {
                if (com.cogwork.cogworkbridge.util.LogOnce.first("contraption-update-light", state.getBlock(), exception)) {
                    com.cogwork.cogworkbridge.CogworkBridge.LOGGER.error(
                            "Could not update carried light emission for {}", state, exception);
                }
                continue;
            }
            if (level == 0) continue;
            BlockPos worldPos = new BlockPos(owner.localBlockCenterToWorld(local));
            if (owner.world.isBlockLoaded(worldPos)) {
                sources.merge(worldPos.toImmutable(), level, Math::max);
            }
        }
        ContraptionDynamicLight.update(owner, sources);
    }
}
