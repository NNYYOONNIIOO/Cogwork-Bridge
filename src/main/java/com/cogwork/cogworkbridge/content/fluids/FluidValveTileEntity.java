package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;

public final class FluidValveTileEntity extends KineticTileEntity implements OpenEndProvider {
    private boolean open;
    private float pointer;
    private float previousPointer;
    private final OpenEndStorage openEnds = new OpenEndStorage(this);

    @Override public OpenEndStorage getOpenEnds() { return openEnds; }

    @Override public void onChunkUnload() {
        FluidTransferManager.unregisterPipe(world, pos);
        super.onChunkUnload();
    }

    @Override public void invalidate() {
        FluidTransferManager.unregisterPipe(world, pos);
        super.invalidate();
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        openEnds.write(tag);
        tag.setBoolean("ValveOpen", open);
        tag.setFloat("ValvePointer", pointer);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        openEnds.read(tag);
        readValveState(tag);
    }

    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setBoolean("ValveOpen", open);
        tag.setFloat("ValvePointer", pointer);
        return tag;
    }

    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readValveState(tag);
        if (world != null && world.isRemote) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    private void readValveState(NBTTagCompound tag) {
        open = tag.getBoolean("ValveOpen");
        pointer = MathHelper.clamp(tag.getFloat("ValvePointer"), 0.0F, 1.0F);
        previousPointer = pointer;
    }

    @Override
    public void update() {
        super.update();
        if (world == null) return;
        if (!world.isRemote) FluidTransferManager.registerPipe(world, pos);
        previousPointer = pointer;
        float speed = getSpeed();
        float step = Math.min(1.0F, Math.abs(speed) / 320.0F);
        if (speed > 0) pointer = Math.min(1.0F, pointer + step);
        if (speed < 0) pointer = Math.max(0.0F, pointer - step);
        if (world.isRemote) return;
        if (Float.compare(pointer, previousPointer) != 0) markDirty();
        boolean next = pointer == 1.0F || open && pointer > 0.0F;
        if (open != next) {
            open = next;
            markDirty();
            FluidTransferManager.markDirty(world);
            sync();
        }
    }

    public boolean isOpen() { return open; }
    public float getPointer(float partialTicks) {
        return previousPointer + (pointer - previousPointer) * partialTicks;
    }
}
