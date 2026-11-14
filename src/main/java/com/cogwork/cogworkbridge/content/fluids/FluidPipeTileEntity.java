package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;

import javax.annotation.Nullable;


public class FluidPipeTileEntity extends CogworkTileEntity implements ITickable, OpenEndProvider {
    private final com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment bracket = new com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment();
    public com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment bracket() { return bracket; }
    private static final int CONNECTION_MASK = 0x3F;
    private int connectionMask;
    private Axis preferredAxis = Axis.Z;
    private boolean connectionsDirty = true;
    @Nullable private FluidStack displayedFluid;
    private final OpenEndStorage openEnds = new OpenEndStorage(this);

    @Override public OpenEndStorage getOpenEnds() { return openEnds; }

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            openEnds.tickClient();
            return;
        }
        FluidTransferManager.registerPipe(world, pos);
        if (connectionsDirty) updateConnections();
        FluidTransferManager.tick(world);
    }

    public boolean hasConnection(EnumFacing side) {
        return (connectionMask & (1 << side.getIndex())) != 0;
    }

    @Nullable
    public Axis getStraightAxis() {
        for (Axis axis : Axis.values()) {
            EnumFacing positive = EnumFacing.getFacingFromAxis(EnumFacing.AxisDirection.POSITIVE, axis);
            int expected = (1 << positive.getIndex()) | (1 << positive.getOpposite().getIndex());
            if (connectionMask == expected) return axis;
        }
        return null;
    }

    @Nullable
    public FlowView getFlow(EnumFacing side, float partialTicks) {
        OpenEndStorage.Flow flow = openEnds.getFlow(side);
        if (flow == null) return null;
        float progress = flow.previousProgress + (flow.progress - flow.previousProgress) * partialTicks;
        return new FlowView(flow.fluid.copy(), flow.inbound, progress);
    }

    public record FlowView(FluidStack fluid, boolean inbound, float progress) {}

    void setDisplayedFluid(@Nullable FluidStack next) {
        if (displayedFluid == null ? next == null : next != null && displayedFluid.isFluidEqual(next)) return;
        displayedFluid = next == null ? null : next.copy();
        if (displayedFluid != null) displayedFluid.amount = 1;
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    public void markConnectionsDirty() {
        connectionsDirty = true;
    }

    @Override public void onChunkUnload() {
        FluidTransferManager.unregisterPipe(world, pos);
        super.onChunkUnload();
    }

    @Override public void invalidate() {
        FluidTransferManager.unregisterPipe(world, pos);
        super.invalidate();
    }

    public void setPreferredAxis(Axis axis) {
        if (axis == null || preferredAxis == axis) return;
        preferredAxis = axis;
        connectionsDirty = true;
        if (world != null && !world.isRemote) updateConnections();
    }

    private void updateConnections() {
        connectionsDirty = false;
        if (world == null || world.isRemote) return;
        int nextMask = 0;
        IBlockState ownState = world.getBlockState(pos);
        if (ownState.getBlock() instanceof SmartFluidPipeBlock || ownState.getBlock() instanceof GlassFluidPipeBlock) {
            Axis axis = ownState.getBlock() instanceof SmartFluidPipeBlock
                    ? SmartFluidPipeBlock.getPipeAxis(ownState) : ownState.getValue(GlassFluidPipeBlock.AXIS);
            for (EnumFacing side : EnumFacing.values()) {
                if (side.getAxis() == axis) nextMask |= 1 << side.getIndex();
            }
        } else {
            for (EnumFacing side : EnumFacing.values()) {
                if (canConnectToNeighbor(pos.offset(side), side)) {
                    nextMask |= 1 << side.getIndex();
                }
            }
        }

        if (Integer.bitCount(nextMask) == 1) {
            EnumFacing connected = EnumFacing.byIndex(Integer.numberOfTrailingZeros(nextMask));
            nextMask |= 1 << connected.getOpposite().getIndex();
        } else if (nextMask == 0) {
            if (getStraightAxis() != null) {
                nextMask = connectionMask;
            } else {
                for (EnumFacing side : EnumFacing.values()) {
                    if (side.getAxis() == preferredAxis) nextMask |= 1 << side.getIndex();
                }
            }
        }

        if (connectionMask == nextMask) return;
        connectionMask = nextMask & CONNECTION_MASK;
        markDirty();
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        FluidTransferManager.markDirty(world);
    }

    private boolean canConnectToNeighbor(BlockPos neighborPos, EnumFacing side) {
        if (!world.isBlockLoaded(neighborPos)) return false;
        IBlockState state = world.getBlockState(neighborPos);
        if (state.getBlock() instanceof FluidPipeBlock pipe) {
            return pipe.allowsAxis(state, side.getAxis());
        }
        if (state.getBlock() instanceof FluidValveBlock valve) {
            return FluidValveBlock.getPipeAxis(state) == side.getAxis();
        }
        if (state.getBlock() instanceof MechanicalPumpBlock pump) {
            return state.getValue(MechanicalPumpBlock.FACING).getAxis() == side.getAxis();
        }
        TileEntity neighbor = world.getTileEntity(neighborPos);
        if (neighbor != null && neighbor.hasCapability(
                CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite())) return true;
        if (state.getBlock() == Blocks.CAULDRON) return true;
        if (state.getBlock() instanceof IFluidBlock fluid) return fluid.canDrain(world, neighborPos);
        return isSourceFluid(state);
    }

    private static boolean isSourceFluid(IBlockState state) {
        if (state.getBlock() != Blocks.WATER && state.getBlock() != Blocks.FLOWING_WATER
                && state.getBlock() != Blocks.LAVA && state.getBlock() != Blocks.FLOWING_LAVA) return false;
        return !state.getPropertyKeys().contains(BlockLiquid.LEVEL)
                || state.getValue(BlockLiquid.LEVEL) == 0;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setByte("Connections", (byte) connectionMask);
        tag.setInteger("PreferredAxis", preferredAxis.ordinal());
        if (displayedFluid != null) tag.setTag("DisplayedFluid", displayedFluid.writeToNBT(new NBTTagCompound()));
        openEnds.write(tag);
        bracket.write(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        connectionMask = tag.getByte("Connections") & CONNECTION_MASK;
        int axis = tag.getInteger("PreferredAxis");
        preferredAxis = Axis.values()[Math.floorMod(axis, Axis.values().length)];
        displayedFluid = tag.hasKey("DisplayedFluid", 10)
                ? FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("DisplayedFluid")) : null;
        openEnds.read(tag);
        bracket.read(tag);
        connectionsDirty = true;
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
        if (world != null && world.isRemote) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
