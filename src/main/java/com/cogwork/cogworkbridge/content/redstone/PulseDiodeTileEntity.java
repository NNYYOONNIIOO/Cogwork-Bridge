package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/** The countdown rules follow the three upstream brass diode state machines. */
public final class PulseDiodeTileEntity extends TileEntity implements ITickable {
    private int delay = -1, counter;
    private boolean input, powering, inverted;
    public int getDelay() { return delay < 0 ? defaultDelay() : delay; }
    public boolean isInput() { return input; }
    public boolean isInverted() { return inverted; }
    public boolean isOutput() { return powering ^ inverted; }
    public float getProgress() { return Math.max(0, Math.min(counter, getDelay())) / (float) getDelay(); }
    private int defaultDelay() {
        return world != null && world.getBlockState(pos).getBlock() instanceof PulseDiodeBlock block && block.kind() == PulseDiodeBlock.Kind.TIMER ? 20 : 2;
    }
    public void setDelay(int ticks) {
        delay = Math.max(2, Math.min(72000, ticks));
        counter = Math.max(0, Math.min(counter, delay));
        changed(false);
    }
    public void toggleInverted() { inverted = !inverted; changed(true); }
    public void setInverted(boolean value) {
        if (inverted != value) { inverted = value; changed(true); }
    }
    @Override public void update() {
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof PulseDiodeBlock block)) return;
        int max = getDelay();
        boolean nextInput = world.isRemote ? input : PulseDiodeBlock.readInput(world, pos, world.getBlockState(pos).getValue(PulseDiodeBlock.FACING));
        boolean previousInput = input, previousOutput = isOutput();
        input = nextInput;
        switch (block.kind()) {
            case EXTENDER -> {
                if (counter <= 0 && !input) break;
                if (counter <= 0 || input) { powering = true; counter = max; break; }
                if (counter == 1) { powering = false; counter = 0; break; }
                counter--;
            }
            case REPEATER -> {
                if (counter <= 0 && !input) break;
                if (counter > max + 1) { if (!input && !powering) counter = 0; break; }
                counter++;
                if (counter == max - 1 && !powering) powering = true;
                if (counter == max + 1 && powering) powering = false;
            }
            case TIMER -> {
                if (input || counter >= max - 1) counter = 0;
                else counter++;
                powering = !input && (max == 2 ? counter == 0 : counter <= 1);
            }
        }
        if (previousInput != input || previousOutput != isOutput()) changed(previousOutput != isOutput());
        else if (counter != 0) markDirty();
    }
    private void changed(boolean notify) {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        if (notify) {
            var state = world.getBlockState(pos);
            world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
            if (state.getBlock() instanceof PulseDiodeBlock) world.notifyNeighborsOfStateChange(pos.offset(state.getValue(PulseDiodeBlock.FACING).getOpposite()), state.getBlock(), false);
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("Delay", getDelay()); tag.setInteger("Counter", counter);
        tag.setBoolean("Input", input); tag.setBoolean("Powering", powering); tag.setBoolean("Inverted", inverted); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); delay = tag.hasKey("Delay") ? Math.max(2, Math.min(72000, tag.getInteger("Delay"))) : -1;
        counter = tag.getInteger("Counter"); input = tag.getBoolean("Input"); powering = tag.getBoolean("Powering"); inverted = tag.getBoolean("Inverted");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); if (world != null) world.markBlockRangeForRenderUpdate(pos, pos); }
}
