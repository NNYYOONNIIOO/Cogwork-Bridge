package com.cogwork.cogworkbridge.content.kinetics.chain;

import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Keeps the selected chain line and analog redstone signal outside metadata. */
public final class ChainDriveTileEntity extends KineticTileEntity {
    private EnumFacing.Axis chainAxis;
    private int links;
    private int signal;
    private volatile int modelState;

    public boolean connects(EnumFacing side) {
        int bit = side.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE ? 1 : 2;
        return side.getAxis() == chainAxis && (links & bit) != 0;
    }

    public float chainModifier() {
        return signal == 0 ? 1.0F : 1.0F + (signal + 1) / 16.0F;
    }

    public int getModelState() {
        return modelState;
    }

    @Override
    public void update() {
        if (world != null && !world.isRemote) {
            refreshConnections();
        }
        super.update();
    }

    public void refreshConnections() {
        if (world == null || world.isRemote || pos == null) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChainDriveBlock block)) {
            return;
        }
        EnumFacing.Axis shaftAxis = block.getAxis(state);
        EnumFacing.Axis selected = chainAxis == shaftAxis ? null : chainAxis;
        int newLinks = selected == null ? 0 : linksAlong(selected, shaftAxis);
        if (newLinks == 0) {
            selected = null;
            for (EnumFacing.Axis candidate : EnumFacing.Axis.values()) {
                if (candidate == shaftAxis) {
                    continue;
                }
                int candidateLinks = linksAlong(candidate, shaftAxis);
                if (candidateLinks != 0) {
                    selected = candidate;
                    newLinks = candidateLinks;
                    break;
                }
            }
        }
        int newSignal = block.isAdjustable()
                ? Math.max(0, Math.min(15, world.getRedstonePowerFromNeighbors(pos))) : 0;
        if (selected == chainAxis && newLinks == links && newSignal == signal) {
            return;
        }
        chainAxis = selected;
        links = newLinks;
        signal = newSignal;
        updateModelState();
        KineticNetworkManager.discard(world);
        markDirty();
        sync();
    }

    private int linksAlong(EnumFacing.Axis axis, EnumFacing.Axis shaftAxis) {
        if (axis == shaftAxis) {
            return 0;
        }
        int result = 0;
        for (EnumFacing.AxisDirection sign : EnumFacing.AxisDirection.values()) {
            EnumFacing side = EnumFacing.getFacingFromAxis(sign, axis);
            int bit = sign == EnumFacing.AxisDirection.NEGATIVE ? 1 : 2;
            BlockPos otherPos = pos.offset(side);
            if (!world.isBlockLoaded(otherPos)) {
                if (axis == chainAxis) {
                    result |= links & bit;
                }
                continue;
            }
            IBlockState otherState = world.getBlockState(otherPos);
            TileEntity otherTile = world.getTileEntity(otherPos);
            if (!(otherState.getBlock() instanceof ChainDriveBlock otherBlock)
                    || !(otherTile instanceof ChainDriveTileEntity other)
                    || otherBlock.getAxis(otherState) == axis
                    || other.chainAxis != null && other.chainAxis != axis) {
                continue;
            }
            result |= bit;
        }
        return result;
    }

    private void updateModelState() {
        int axis = chainAxis == null ? 0 : chainAxis.ordinal() + 1;
        modelState = axis * 8 + links * 2 + (signal == 0 ? 0 : 1);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ChainAxis", chainAxis == null ? -1 : chainAxis.ordinal());
        tag.setInteger("ChainLinks", links);
        tag.setInteger("ChainSignal", signal);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readChain(tag);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("ChainAxis", chainAxis == null ? -1 : chainAxis.ordinal());
        tag.setInteger("ChainLinks", links);
        tag.setInteger("ChainSignal", signal);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        int oldModel = modelState;
        super.handleUpdateTag(tag);
        readChain(tag);
        if (oldModel != modelState && world != null && world.isRemote) {
            if (world instanceof FakeWorld fakeWorld) {
                fakeWorld.getMobileChunk().setChunkModified();
            } else {
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
        }
    }

    private void readChain(NBTTagCompound tag) {
        int axis = tag.hasKey("ChainAxis") ? tag.getInteger("ChainAxis") : -1;
        chainAxis = axis >= 0 && axis < EnumFacing.Axis.values().length
                ? EnumFacing.Axis.values()[axis] : null;
        links = chainAxis == null ? 0 : tag.getInteger("ChainLinks") & 3;
        signal = Math.max(0, Math.min(15, tag.getInteger("ChainSignal")));
        updateModelState();
    }
}
