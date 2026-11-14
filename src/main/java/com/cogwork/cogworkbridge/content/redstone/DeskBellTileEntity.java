package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

public final class DeskBellTileEntity extends TileEntity implements ITickable {
    private int remaining, sequence;
    private float animation, previousAnimation;
    public boolean isPressed() { return remaining > 0; }
    public float animation(float partial) { return previousAnimation + (animation - previousAnimation) * partial; }
    public void press() { remaining = 20; sequence++; changed(); }
    @Override public void update() {
        previousAnimation = animation;
        if (world == null) return;
        if (world.isRemote) animation = Math.max(0F, animation - .05F);
        else if (remaining > 0 && --remaining == 0) changed();
    }
    private void changed() {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        DeskBellBlock.notifyOutputs(world, pos, world.getBlockState(pos));
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("Remaining", remaining); tag.setInteger("Sequence", sequence); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); remaining = Math.max(0, tag.getInteger("Remaining")); sequence = tag.getInteger("Sequence");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) {
        int oldSequence = sequence;
        readFromNBT(packet.getNbtCompound());
        if (sequence != oldSequence) animation = previousAnimation = 1F;
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
