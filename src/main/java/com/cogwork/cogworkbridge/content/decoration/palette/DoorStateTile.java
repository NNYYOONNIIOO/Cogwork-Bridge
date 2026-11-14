package com.cogwork.cogworkbridge.content.decoration.palette;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Non-orientation door state is stored outside block metadata. */
public class DoorStateTile extends TileEntity implements net.minecraft.util.ITickable {
    public boolean open, powered, rightHinge;
    private float animation, previousAnimation;
    private boolean initialized;
    public float animation(float partialTicks) { return previousAnimation + (animation - previousAnimation) * partialTicks; }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos).grow(1D, 1D, 1D).expand(0D, 1D, 0D);
    }
    @Override public void update() {
        if (world == null || !world.isRemote || !(world.getBlockState(pos).getBlock() instanceof PaletteDoor)) return;
        previousAnimation = animation;
        animation = open ? Math.min(1F, animation + .15F) : Math.max(0F, animation - .15F);
    }
    public void changed() {
        markDirty();
        if (world != null) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            world.markBlockRangeForRenderUpdate(pos, pos.up());
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setBoolean("Open", open); tag.setBoolean("Powered", powered); tag.setBoolean("RightHinge", rightHinge); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); open = tag.getBoolean("Open"); powered = tag.getBoolean("Powered"); rightHinge = tag.getBoolean("RightHinge");
        if (!initialized) { animation = previousAnimation = open ? 1F : 0F; initialized = true; }
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager network, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos.up());
    }
}
