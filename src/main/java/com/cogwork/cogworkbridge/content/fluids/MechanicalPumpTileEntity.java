package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.nbt.NBTTagCompound;

public final class MechanicalPumpTileEntity extends KineticTileEntity implements OpenEndProvider {
    private final OpenEndStorage openEnds = new OpenEndStorage(this);

    @Override public OpenEndStorage getOpenEnds() { return openEnds; }

    @Override public void onChunkUnload() {
        FluidTransferManager.unregisterPump(world, pos);
        super.onChunkUnload();
    }

    @Override public void invalidate() {
        FluidTransferManager.unregisterPump(world, pos);
        super.invalidate();
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        openEnds.write(tag);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        openEnds.read(tag);
    }

    @Override
    public void update() {
        super.update();
        if (world != null && !world.isRemote) {
            FluidTransferManager.registerPump(world, pos);
            FluidTransferManager.tick(world);
        }
    }

    @Override
    public float getStressImpact() { return 4.0F; }

}
