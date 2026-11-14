package com.cogwork.cogworkbridge.content.kinetics.transmission;

import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.nbt.NBTTagCompound;

/** Stores redstone state outside the block's orientation-only metadata. */
public final class SplitShaftTileEntity extends KineticTileEntity {

    private volatile boolean powered;

    public boolean isPowered() {
        return powered;
    }

    @Override
    public void update() {
        if (world != null && !world.isRemote) {
            updateRedstone();
        }
        super.update();
    }

    public void updateRedstone() {
        if (world == null || world.isRemote) {
            return;
        }
        boolean next = world.isBlockPowered(pos);
        if (next == powered) {
            return;
        }
        powered = next;
        KineticNetworkManager.discard(world);
        markDirty();
        sync();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("RedstonePowered", powered);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        powered = compound.getBoolean("RedstonePowered");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setBoolean("RedstonePowered", powered);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        boolean oldPowered = powered;
        super.handleUpdateTag(tag);
        powered = tag.getBoolean("RedstonePowered");
        if (oldPowered == powered || world == null || !world.isRemote) {
            return;
        }
        if (world instanceof FakeWorld fakeWorld) {
            fakeWorld.getMobileChunk().setChunkModified();
        } else {
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }
}
