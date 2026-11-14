package com.cogwork.cogworkbridge.content.contraptions.chassis;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/** Server state and client interpolation for a redstone sticker. */
public final class StickerTileEntity extends TileEntity implements ITickable {
    private boolean powered;
    private boolean extended;
    private boolean powerInitialized;
    private float progress;
    private float previousProgress;

    public boolean isPowered() { return powered; }
    public boolean isExtended() { return extended; }
    public float getProgress(float partialTicks) {
        return previousProgress + (progress - previousProgress) * partialTicks;
    }

    public void initializePower(boolean value) {
        if (powerInitialized) return;
        powered = value;
        powerInitialized = true;
        changed();
    }

    public void setPowered(boolean value) {
        if (!powerInitialized) {
            initializePower(value);
            return;
        }
        if (powered == value) return;
        powered = value;
        if (powered) extended = !extended;
        changed();
    }

    @Override
    public void update() {
        if (world != null && !world.isRemote) setPowered(world.isBlockPowered(pos));
        previousProgress = progress;
        float target = extended ? 1.0F : 0.0F;
        if (progress < target) progress = Math.min(target, progress + 0.4F);
        else if (progress > target) progress = Math.max(target, progress - 0.4F);
    }

    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("Powered", powered);
        tag.setBoolean("Extended", extended);
        tag.setBoolean("PowerInitialized", powerInitialized);
        tag.setFloat("Progress", progress);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        powered = tag.getBoolean("Powered");
        extended = tag.getBoolean("Extended");
        powerInitialized = tag.getBoolean("PowerInitialized");
        progress = Math.max(0.0F, Math.min(1.0F, tag.hasKey("Progress") ? tag.getFloat("Progress") : extended ? 1.0F : 0.0F));
        previousProgress = progress;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
