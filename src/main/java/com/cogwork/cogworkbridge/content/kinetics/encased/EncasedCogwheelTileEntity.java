package com.cogwork.cogworkbridge.content.kinetics.encased;

import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Saves the two shaft connections independently of block metadata. */
public final class EncasedCogwheelTileEntity extends CogwheelTileEntity {

    public static final String PORTS_KEY = "EncasedCogPorts";
    private volatile int openFaces;

    public boolean isOpen(EnumFacing face) {
        return (openFaces & (1 << face.ordinal())) != 0;
    }

    public int modelPorts(EnumFacing.Axis axis) {
        EnumFacing positive = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis);
        return (isOpen(positive) ? 1 : 0) | (isOpen(positive.getOpposite()) ? 2 : 0);
    }

    public void initializePorts() {
        if (world == null || world.isRemote) {
            return;
        }
        IBlockState ownState = world.getBlockState(pos);
        if (!(ownState.getBlock() instanceof EncasedCogwheelBlock own)) {
            return;
        }
        int mask = 0;
        for (EnumFacing face : EnumFacing.VALUES) {
            if (face.getAxis() != own.getAxis(ownState)) {
                continue;
            }
            BlockPos otherPos = pos.offset(face);
            IBlockState otherState = world.getBlockState(otherPos);
            if (otherState.getBlock() instanceof KineticBlock other
                    && other.canConnectShaftTowards(world, otherPos, otherState, face.getOpposite())) {
                mask |= 1 << face.ordinal();
            }
        }
        openFaces = mask;
        changed();
    }

    public void toggle(EnumFacing face) {
        openFaces ^= 1 << face.ordinal();
        changed();
    }

    public void rotatePorts(EnumFacing.Axis around) {
        int rotated = 0;
        for (EnumFacing face : EnumFacing.VALUES) {
            if (!isOpen(face)) continue;
            EnumFacing next = face.getAxis() == around ? face : face.rotateAround(around);
            rotated |= 1 << next.ordinal();
        }
        openFaces = rotated;
        changed();
    }

    private void changed() {
        KineticNetworkManager.discard(world);
        markDirty();
        sync();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger(PORTS_KEY, openFaces);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        openFaces = tag.getInteger(PORTS_KEY);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger(PORTS_KEY, openFaces);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        int previous = openFaces;
        super.handleUpdateTag(tag);
        openFaces = tag.getInteger(PORTS_KEY);
        if (previous == openFaces || world == null || !world.isRemote) {
            return;
        }
        if (world instanceof FakeWorld fakeWorld) {
            fakeWorld.getMobileChunk().setChunkModified();
        } else {
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }
}
