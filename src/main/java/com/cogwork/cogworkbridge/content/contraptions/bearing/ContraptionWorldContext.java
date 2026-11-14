package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Shared world-facing queries for carried blocks.
 *
 * <p>Carried block states live in contraption-local coordinates. Hooks that
 * inspect a neighbour see the captured local state first and only fall back to
 * the real world outside the contraption.</p>
 */
final class ContraptionWorldContext {

    private ContraptionWorldContext() {
    }

    static int strongPower(IBlockAccess access, BlockPos pos,
                           EnumFacing direction) {
        IBlockState state = access.getBlockState(pos);
        return state.getBlock().getStrongPower(state, access, pos, direction);
    }

    static int weakPower(IBlockAccess access, BlockPos pos,
                         EnumFacing direction) {
        IBlockState state = access.getBlockState(pos);
        return state.getBlock().getWeakPower(state, access, pos, direction);
    }

    static boolean canSeeSky(World parent, Vec3d worldCenter) {
        BlockPos pos = new BlockPos(worldCenter);
        return parent.isBlockLoaded(pos) && parent.canSeeSky(pos);
    }

    static boolean isRainingAt(World parent, Vec3d worldCenter) {
        BlockPos pos = new BlockPos(worldCenter);
        return parent.isBlockLoaded(pos) && parent.isRainingAt(pos);
    }
}
