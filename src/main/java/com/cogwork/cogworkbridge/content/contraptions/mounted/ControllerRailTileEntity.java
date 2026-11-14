package com.cogwork.cogworkbridge.content.contraptions.mounted;

import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.ITickable;

/** Stores the rail's four-bit signal separately because metadata stores its orientation. */
public final class ControllerRailTileEntity extends CogworkTileEntity implements ITickable {
    private volatile int signalLevel;
    private boolean initialUpdate = true;

    public int signalLevel() { return signalLevel; }

    void recalculate() {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof ControllerRailBlock)) return;
        int next = ControllerRailBlock.calculatePower(world, pos);
        if (next == signalLevel) return;
        signalLevel = next;
        markDirty();
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 2);
        world.updateComparatorOutputLevel(pos, state.getBlock());
    }

    @Override public void update() {
        if (world == null || world.isRemote) return;
        // Also refresh after chunks load or sources change without a neighbour update.
        if (initialUpdate || Math.floorMod(world.getTotalWorldTime() + (pos.toLong() & 255L), 20L) == 0) {
            initialUpdate = false;
            recalculate();
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("SignalLevel", signalLevel);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        signalLevel = Math.max(0, Math.min(15, tag.getInteger("SignalLevel")));
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) {
        int before = signalLevel;
        readFromNBT(packet.getNbtCompound());
        if (world != null && world.isRemote && before != signalLevel) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
