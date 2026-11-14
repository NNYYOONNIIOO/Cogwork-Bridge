package com.cogwork.cogworkbridge.client;

import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Optional tutorial-only model flags; contains no dependency on a tutorial runtime. */
public final class TutorialRenderState {
    private static final Map<IBlockAccess, Set<BlockPos>> OPEN = new WeakHashMap<>();
    private TutorialRenderState() {}
    public static boolean isOpen(IBlockAccess world, BlockPos pos) { Set<BlockPos> positions = OPEN.get(world); return positions != null && positions.contains(pos); }
    public static void setOpen(IBlockAccess world, BlockPos pos, boolean open) {
        if (world == null) return;
        if (open) OPEN.computeIfAbsent(world, ignored -> new HashSet<>()).add(pos.toImmutable());
        else { Set<BlockPos> positions = OPEN.get(world); if (positions != null) { positions.remove(pos); if (positions.isEmpty()) OPEN.remove(world); } }
    }
}
