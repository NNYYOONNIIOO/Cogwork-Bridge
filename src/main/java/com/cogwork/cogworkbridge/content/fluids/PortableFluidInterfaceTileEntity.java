package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PortableFluidInterfaceTileEntity extends CogworkTileEntity implements ITickable {
    private static final int ANIMATION_TICKS = 4;
    private static final int TRANSFER_TIMEOUT = 60;
    private final IFluidHandler interfaceHandler = new InterfaceFluidHandler();
    @Nullable private BearingContraptionEntity connectedEntity;
    @Nullable private IFluidHandler connectedStorage;
    @Nullable private BlockPos connectedLocalPos;
    @Nullable private UUID blockedEntityUntilExit;
    private long connectionCacheTick = Long.MIN_VALUE;
    private long mobileContactTick = Long.MIN_VALUE;
    private int transferTimer;
    private boolean connected;
    private float connectionDistance;
    private float animation;
    private float previousAnimation;

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            previousAnimation = animation;
            float target = connected ? 1.0F : 0.0F;
            animation += Math.max(-0.25F, Math.min(0.25F, target - animation));
            return;
        }
        if (isMobileWorld()) {
            if (connected && (mobileContactTick == Long.MIN_VALUE
                    || world.getTotalWorldTime() - mobileContactTick > 2)) {
                setMobileConnection(false, 0.0F);
            }
            return;
        }
        refreshConnection();
    }

    public void refreshConnection() {
        if (world == null || world.isRemote || isMobileWorld()) return;
        long now = world.getTotalWorldTime();
        if (connectionCacheTick == now) return;
        connectionCacheTick = now;
        boolean wasConnected = connected;
        BearingContraptionEntity previousEntity = connectedEntity;
        BlockPos previousLocalPos = connectedLocalPos;
        float previousDistance = connectionDistance;
        Connection candidate = findConnection();
        if (blockedEntityUntilExit != null
                && (candidate == null || !blockedEntityUntilExit.equals(candidate.entity().getUniqueID())
                || !hasRequestedMotion(candidate.entity()))) {
            blockedEntityUntilExit = null;
        }
        if (connectedEntity != null
                && (!connectedEntity.isEntityAlive() || candidate == null
                || candidate.entity() != connectedEntity || !candidate.localPos().equals(connectedLocalPos))) {
            disconnect();
        }
        if (world.isBlockPowered(pos)) {
            disconnect();
        } else if (connectedEntity != null && transferTimer > 0) {
            transferTimer--;
            if (transferTimer > ANIMATION_TICKS) {
                connectedEntity.holdForFluidInterface();
                updateMobileConnection(true);
            } else {
                blockedEntityUntilExit = connectedEntity.getUniqueID();
                disconnect();
            }
        } else if (candidate != null && !candidate.entity().getUniqueID().equals(blockedEntityUntilExit)) {
            connectedEntity = candidate.entity();
            connectedLocalPos = candidate.localPos();
            connectedStorage = ContraptionFluidStorage.forEntity(connectedEntity);
            connectionDistance = candidate.distance();
            transferTimer = 6 + ANIMATION_TICKS * 2;
            connectedEntity.holdForFluidInterface();
            updateMobileConnection(true);
        }
        connected = connectedStorage != null;
        if (wasConnected != connected || connected && (previousEntity != connectedEntity
                || !java.util.Objects.equals(previousLocalPos, connectedLocalPos)
                || Float.compare(previousDistance, connectionDistance) != 0)) {
            syncConnection();
        }
    }

    private void disconnect() {
        updateMobileConnection(false);
        connectedEntity = null;
        connectedStorage = null;
        connectedLocalPos = null;
        connected = false;
        transferTimer = 0;
    }

    public void stopConnection() {
        boolean wasConnected = connected;
        disconnect();
        if (wasConnected && world != null && !world.isRemote) syncConnection();
    }

    private boolean isMobileWorld() {
        return world instanceof FakeWorld || world instanceof ContraptionWorld;
    }

    private void updateMobileConnection(boolean value) {
        if (connectedEntity == null || connectedLocalPos == null) return;
        net.minecraft.world.World localWorld = connectedEntity.getContraptionLocalWorld();
        if (localWorld == null) return;
        TileEntity tile = localWorld.getTileEntity(connectedLocalPos);
        if (tile instanceof PortableFluidInterfaceTileEntity portable) {
            portable.setMobileConnection(value, connectionDistance);
        }
    }

    private void setMobileConnection(boolean value, float distance) {
        if (world == null || world.isRemote) return;
        if (value) mobileContactTick = world.getTotalWorldTime();
        if (connected == value && (!value || connectionDistance == distance)) return;
        connected = value;
        if (value) connectionDistance = distance;
        markDirty();
    }

    private void syncConnection() {
        markDirty();
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 3);
        world.updateComparatorOutputLevel(pos, state.getBlock());
    }

    void keepAlive() {
        if (connectedEntity == null || transferTimer <= 0) return;
        transferTimer = TRANSFER_TIMEOUT + ANIMATION_TICKS;
        connectedEntity.holdForFluidInterface();
    }

    public boolean isConnected() {
        refreshConnection();
        return connected;
    }

    boolean sharesConnectedStorageWith(PortableFluidInterfaceTileEntity other) {
        refreshConnection();
        other.refreshConnection();
        return connectedEntity != null && connectedEntity == other.connectedEntity;
    }

    private static boolean hasRequestedMotion(BearingContraptionEntity entity) {
        for (BearingContraptionEntity current = entity; current != null; current = current.getParentContraption()) {
            if (current.getSpeed() != 0.0F) return true;
        }
        return false;
    }

    public float getExtensionDistance(float partialTicks) {
        float progress = previousAnimation + (animation - previousAnimation) * partialTicks;
        return progress * progress * connectionDistance * 0.5F;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return super.getRenderBoundingBox().grow(2.0D);
    }

    @Nullable
    private Connection findConnection() {
        IBlockState ownState = world.getBlockState(pos);
        if (!(ownState.getBlock() instanceof PortableFluidInterfaceBlock)) return null;
        EnumFacing ownFacing = ownState.getValue(PortableFluidInterfaceBlock.FACING);
        Vec3d ownNormal = normal(ownFacing);
        Vec3d ownCenter = center(pos);
        AxisAlignedBB search = new AxisAlignedBB(pos).grow(4.0D);

        for (BearingContraptionEntity entity : world.getEntitiesWithinAABB(BearingContraptionEntity.class, search)) {
            if (entity.isActorDisabled(AllBlocks.PORTABLE_FLUID_INTERFACE)) continue;
            BearingContraption contraption = entity.getContraption();
            if (contraption == null || !entity.isEntityAlive()) continue;
            for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
                if (snapshot.state().getBlock() != AllBlocks.PORTABLE_FLUID_INTERFACE) continue;
                BlockPos localPos = entity.localPos(snapshot);
                Vec3d mobileCenter = entity.localBlockCenterToWorld(localPos);
                Vec3d mobileNormal = entity.localFacingVectorToWorld(
                        snapshot.state().getValue(PortableFluidInterfaceBlock.FACING));
                // Upstream activates at the transformed 1.85-block offset and looks for a
                // matching stationary interface in that block or the next block ahead.
                if (mobileNormal.dotProduct(ownNormal) > -0.875D) continue;
                EnumFacing mobileFacing = ownFacing.getOpposite();
                Vec3d activePoint = mobileCenter.add(mobileNormal.scale(1.85D));
                BlockPos activeBlock = new BlockPos(activePoint);
                if (!pos.equals(activeBlock) && !pos.equals(activeBlock.offset(mobileFacing))) continue;

                double projected = ownCenter.subtract(activePoint).dotProduct(normal(mobileFacing));
                float distance = (float) Math.min(2.0D, Math.abs(projected) + 0.85D);
                return new Connection(entity, localPos, distance);
            }
        }
        return null;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(interfaceHandler);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("Connected", connected);
        tag.setFloat("ConnectionDistance", connectionDistance);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        connected = tag.getBoolean("Connected");
        connectionDistance = tag.getFloat("ConnectionDistance");
        connectedEntity = null;
        connectedStorage = null;
        connectedLocalPos = null;
        transferTimer = 0;
        blockedEntityUntilExit = null;
        connectionCacheTick = Long.MIN_VALUE;
        mobileContactTick = Long.MIN_VALUE;
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
    }

    public void handleContraptionUpdate(NBTTagCompound tag) {
        connected = tag.getBoolean("Connected");
        connectionDistance = tag.getFloat("ConnectionDistance");
    }

    @Override
    public void invalidate() {
        if (world != null && !world.isRemote && !isMobileWorld()) stopConnection();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (world != null && !world.isRemote && !isMobileWorld()) stopConnection();
        super.onChunkUnload();
    }

    private final class InterfaceFluidHandler implements IFluidHandler {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            IFluidHandler target = target();
            return target == null ? new IFluidTankProperties[0] : target.getTankProperties();
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            IFluidHandler target = target();
            if (target == null) return 0;
            int filled = target.fill(resource, doFill);
            if (doFill && filled > 0) keepAlive();
            return filled;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            IFluidHandler target = target();
            if (target == null) return null;
            FluidStack drained = target.drain(resource, doDrain);
            if (doDrain && drained != null && drained.amount > 0) keepAlive();
            return drained;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            IFluidHandler target = target();
            if (target == null) return null;
            FluidStack drained = target.drain(maxDrain, doDrain);
            if (doDrain && drained != null && drained.amount > 0) keepAlive();
            return drained;
        }

        @Nullable
        private IFluidHandler target() {
            refreshConnection();
            return connectedStorage;
        }
    }

    private static Vec3d center(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    private static Vec3d normal(EnumFacing facing) {
        return new Vec3d(facing.getXOffset(), facing.getYOffset(), facing.getZOffset());
    }

    private record Connection(BearingContraptionEntity entity, BlockPos localPos, float distance) {}

}

final class ContraptionFluidStorage implements IFluidHandler {
    private final BearingContraptionEntity entity;

    private ContraptionFluidStorage(BearingContraptionEntity entity) {
        this.entity = entity;
    }

    static IFluidHandler forEntity(BearingContraptionEntity entity) {
        return new ContraptionFluidStorage(entity);
    }

    private List<IFluidHandler> handlers() {
        if (!entity.isEntityAlive() || entity.getContraption() == null) return Collections.emptyList();
        net.minecraft.world.World virtualWorld = entity.getContraptionLocalWorld();
        if (virtualWorld == null) return Collections.emptyList();
        List<IFluidHandler> handlers = new ArrayList<>();
        for (BearingContraption.Snapshot snapshot : entity.getContraption().blocks().values()) {
            if (snapshot.state().getBlock() != AllBlocks.FLUID_TANK
                    && snapshot.state().getBlock() != AllBlocks.CREATIVE_FLUID_TANK) continue;
            TileEntity tile = virtualWorld.getTileEntity(entity.localPos(snapshot));
            if (tile instanceof FluidTankTileEntity tank && tank.isController()) {
                handlers.add(tank.getFluidHandler());
            }
        }
        return handlers;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        List<IFluidTankProperties> properties = new ArrayList<>();
        for (IFluidHandler handler : handlers()) {
            IFluidTankProperties[] current = handler.getTankProperties();
            if (current != null) Collections.addAll(properties, current);
        }
        return properties.toArray(new IFluidTankProperties[0]);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) return 0;
        int remaining = resource.amount;
        for (IFluidHandler handler : handlers()) {
            if (remaining <= 0) break;
            FluidStack offered = resource.copy();
            offered.amount = remaining;
            int accepted = Math.max(0, Math.min(remaining, handler.fill(offered, doFill)));
            remaining -= accepted;
        }
        return resource.amount - remaining;
    }

    @Nullable
    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) return null;
        int remaining = resource.amount;
        FluidStack result = null;
        for (IFluidHandler handler : handlers()) {
            if (remaining <= 0) break;
            FluidStack request = resource.copy();
            request.amount = remaining;
            FluidStack simulated = handler.drain(request, false);
            if (simulated == null || simulated.amount <= 0
                    || !resource.isFluidEqual(simulated)) continue;
            FluidStack drained = doDrain ? handler.drain(request, true) : simulated;
            if (drained == null || drained.amount <= 0) continue;
            if (result == null) result = drained.copy();
            else result.amount += drained.amount;
            remaining -= drained.amount;
        }
        return result;
    }

    @Nullable
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) return null;
        FluidStack first = null;
        for (IFluidHandler handler : handlers()) {
            IFluidTankProperties[] properties = handler.getTankProperties();
            if (properties == null) continue;
            for (IFluidTankProperties property : properties) {
                FluidStack content = property.getContents();
                if (content != null && content.amount > 0) {
                    first = content.copy();
                    break;
                }
            }
            if (first != null) break;
        }
        if (first == null) return null;
        first.amount = maxDrain;
        return drain(first, doDrain);
    }
}
