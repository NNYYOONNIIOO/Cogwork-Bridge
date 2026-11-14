package com.cogwork.cogworkbridge.content.logistics.portable;

import com.cogwork.cogworkbridge.content.logistics.transport.ContraptionItemStorage;
import com.cogwork.cogworkbridge.content.logistics.transport.PortableItemStorage;

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
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.UUID;

public final class PortableStorageInterfaceTileEntity extends CogworkTileEntity implements ITickable, PortableItemStorage {
    private static final int ANIMATION_TICKS = 4;
    private static final int TRANSFER_TIMEOUT = 60;
    private final IItemHandler interfaceHandler = new InterfaceItemHandler();
    @Nullable private BearingContraptionEntity connectedEntity;
    @Nullable private IItemHandler connectedStorage;
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
                )) {
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
                updateMobileConnection(true);
            } else {
                blockedEntityUntilExit = connectedEntity.getUniqueID();
                disconnect();
            }
        } else if (candidate != null && !candidate.entity().getUniqueID().equals(blockedEntityUntilExit)) {
            connectedEntity = candidate.entity();
            connectedLocalPos = candidate.localPos();
            connectedStorage = new ContraptionItemStorage(connectedEntity);
            connectionDistance = candidate.distance();
            transferTimer = 6 + ANIMATION_TICKS * 2;
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
        if (tile instanceof PortableStorageInterfaceTileEntity portable) {
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
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
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
        if (!(ownState.getBlock() instanceof PortableStorageInterfaceBlock)) return null;
        EnumFacing ownFacing = ownState.getValue(PortableStorageInterfaceBlock.FACING);
        Vec3d ownNormal = normal(ownFacing);
        Vec3d ownCenter = center(pos);
        AxisAlignedBB search = new AxisAlignedBB(pos).grow(4.0D);

        for (BearingContraptionEntity entity : world.getEntitiesWithinAABB(BearingContraptionEntity.class, search)) {
            if (entity.isActorDisabled(AllBlocks.PORTABLE_STORAGE_INTERFACE)) continue;
            BearingContraption contraption = entity.getContraption();
            if (contraption == null || !entity.isEntityAlive()) continue;
            for (BearingContraption.Snapshot snapshot : contraption.blocks().values()) {
                if (snapshot.state().getBlock() != AllBlocks.PORTABLE_STORAGE_INTERFACE) continue;
                BlockPos localPos = entity.localPos(snapshot);
                Vec3d mobileCenter = entity.localBlockCenterToWorld(localPos);
                Vec3d mobileNormal = entity.localFacingVectorToWorld(
                        snapshot.state().getValue(PortableStorageInterfaceBlock.FACING));
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
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(interfaceHandler);
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


    private final class InterfaceItemHandler implements IItemHandler {
        private IItemHandler target() {
            refreshConnection();
            return connectedStorage;
        }
        @Override public int getSlots() {
            IItemHandler target = target();
            return target == null ? 0 : target.getSlots();
        }
        @Override public ItemStack getStackInSlot(int slot) {
            IItemHandler target = target();
            return target == null || slot < 0 || slot >= target.getSlots()
                    ? ItemStack.EMPTY : target.getStackInSlot(slot);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler target = target();
            if (target == null || slot < 0 || slot >= target.getSlots() || stack.isEmpty()) return stack;
            ItemStack remaining = target.insertItem(slot, stack, simulate);
            if (!simulate && remaining.getCount() < stack.getCount()) {
                keepAlive();
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "portable_storage_interface");
            }
            return remaining;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler target = target();
            if (target == null || slot < 0 || slot >= target.getSlots() || amount <= 0) return ItemStack.EMPTY;
            ItemStack result = target.extractItem(slot, amount, simulate);
            if (!simulate && !result.isEmpty()) {
                keepAlive();
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "portable_storage_interface");
            }
            return result;
        }
        @Override public int getSlotLimit(int slot) {
            IItemHandler target = target();
            return target == null || slot < 0 || slot >= target.getSlots() ? 0 : target.getSlotLimit(slot);
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
