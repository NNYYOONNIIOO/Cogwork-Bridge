package com.cogwork.cogworkbridge.content.contraptions.chassis;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Mutable chassis configuration kept out of block metadata. */
public final class ChassisTileEntity extends CogworkTileEntity {
    public static final int MAX_RANGE = 16;
    private int range = 8;
    private int stickyMask;

    public int getRange() { return range; }
    public boolean isSticky(EnumFacing side) { return (stickyMask & (1 << side.getIndex())) != 0; }
    public int stickyMask() { return stickyMask; }
    public void setSticky(EnumFacing side, boolean sticky) {
        int next = sticky ? stickyMask | 1 << side.getIndex() : stickyMask & ~(1 << side.getIndex());
        if (next == stickyMask) return;
        stickyMask = next;
        changed();
    }
    public void setRange(int value) {
        int next = Math.max(1, Math.min(MAX_RANGE, value));
        if (next == range) return;
        range = next;
        changed();
    }

    /** Returns the connected chassis group used by Ctrl+wrench bulk settings. */
    public List<ChassisTileEntity> collectChassisGroup() {
        List<ChassisTileEntity> result = new ArrayList<>();
        if (world == null) return result;
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        frontier.add(pos);
        while (!frontier.isEmpty() && visited.size() < 2048) {
            BlockPos current = frontier.removeFirst();
            if (!visited.add(current) || !world.isBlockLoaded(current)) continue;
            TileEntity tile = world.getTileEntity(current);
            if (!(tile instanceof ChassisTileEntity chassis)) continue;
            IBlockState state = world.getBlockState(current);
            if (!(state.getBlock() instanceof ChassisBlock)) continue;
            result.add(chassis);
            EnumFacing.Axis axis = state.getValue(ChassisBlock.AXIS);
            for (EnumFacing side : EnumFacing.VALUES) {
                BlockPos next = current.offset(side);
                if (!world.isBlockLoaded(next)) continue;
                IBlockState nextState = world.getBlockState(next);
                if (nextState.getBlock() != state.getBlock()
                        || nextState.getValue(ChassisBlock.AXIS) != axis) continue;
                if (state.getBlock() instanceof ChassisBlock currentChassis
                        && currentChassis.isRadial() && side.getAxis() != axis) continue;
                frontier.addLast(next);
            }
        }
        return result;
    }
    public void rotateSticky(EnumFacing.Axis axis) {
        int rotated = 0;
        for (EnumFacing side : EnumFacing.VALUES) {
            if (!isSticky(side)) continue;
            EnumFacing next = side.getAxis() == axis ? side : side.rotateAround(axis);
            rotated |= 1 << next.getIndex();
        }
        if (rotated != stickyMask) {
            stickyMask = rotated;
            changed();
        }
    }
    private void changed() {
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Range", range);
        tag.setInteger("StickyMask", stickyMask);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        range = Math.max(1, Math.min(MAX_RANGE, tag.hasKey("Range") ? tag.getInteger("Range") : 8));
        stickyMask = tag.getInteger("StickyMask") & 63;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
