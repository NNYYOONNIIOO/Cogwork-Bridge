package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public final class LatchTileEntity extends TileEntity {
    private boolean input, sideInput, output;
    public boolean isInput() { return input; }
    public boolean isSideInput() { return sideInput; }
    public boolean isOutput() { return output; }
    public void toggle() { output = !output; changed(true); }
    public void updateInput(boolean nextInput, boolean nextSide, boolean toggle) {
        boolean oldInput = input, oldSide = sideInput, oldOutput = output;
        if (toggle) {
            if (nextInput && !input) output = !output;
        } else {
            if (oldInput != nextInput) {
                if (nextInput) output = true;
                else if (oldSide) output = false;
            }
            if (oldSide != nextSide) {
                if (nextSide) output = false;
                else if (oldInput) output = true;
            }
        }
        input = nextInput; sideInput = nextSide;
        if (oldInput != input || oldSide != sideInput || oldOutput != output) changed(oldOutput != output);
    }
    private void changed(boolean notify) {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        if (notify) {
            var state = world.getBlockState(pos);
            world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
            world.notifyNeighborsOfStateChange(pos.offset(state.getValue(LatchBlock.FACING).getOpposite()), state.getBlock(), false);
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); tag.setBoolean("Input", input); tag.setBoolean("SideInput", sideInput); tag.setBoolean("Output", output); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); input = tag.getBoolean("Input"); sideInput = tag.getBoolean("SideInput"); output = tag.getBoolean("Output"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); if (world != null) world.markBlockRangeForRenderUpdate(pos, pos); }
}
