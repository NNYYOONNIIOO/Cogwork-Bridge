package com.cogwork.cogworkbridge.content.contraptions.gantry;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.CarriedBearingAssembly;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionGuiProxy;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionEntityReference;
import com.cogwork.cogworkbridge.content.contraptions.bearing.LinearContraptionController;
import com.cogwork.cogworkbridge.content.contraptions.bearing.SequencedTravel;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

/** Drives captured carriages along a connected, unpowered gantry shaft. */
public final class GantryShaftTileEntity extends KineticTileEntity implements LinearContraptionController {
    private final List<Travel> travellers = new ArrayList<>();
    private final Map<UUID,ContraptionEntityReference> entityReferences=new HashMap<>();
    private boolean visualLocked;
    private record Travel(UUID id, BlockPos anchor, EnumFacing mount, EnumFacing direction, SequencedTravel sequence) { }
    @Override public boolean ownsLinearContraption(UUID id) {
        for (Travel travel : travellers) if (travel.id.equals(id)) return true;
        return false;
    }
    @Override public void onContraptionRestored(UUID id) {
        travellers.removeIf(t -> t.id.equals(id));
        entityReferences.remove(id);
        markDirty();
    }
    private BearingContraptionEntity find(UUID id) {
        return entityReferences.computeIfAbsent(id,ignored->new ContraptionEntityReference()).get(world,id);
    }
    private boolean shaftAt(BlockPos candidate, EnumFacing direction) {
        if (!world.isBlockLoaded(candidate)) return false;
        IBlockState state = world.getBlockState(candidate);
        return state.getBlock() instanceof GantryShaftBlock && state.getValue(GantryShaftBlock.FACING) == direction;
    }
    private boolean locked(BlockPos shaft, EnumFacing direction) {
        if (world.isBlockPowered(shaft)) return true;
        for (EnumFacing scan : new EnumFacing[]{direction, direction.getOpposite()}) {
            for (int i = 1; i <= 128; i++) {
                BlockPos next = shaft.offset(scan, i);
                if (!shaftAt(next, direction)) break;
                if (world.isBlockPowered(next)) return true;
            }
        }
        return false;
    }
    /** Redstone locks the connected rack and couples its pinions to rotational power. */
    public boolean isLocked() {
        if (world == null || pos == null || !(world.getBlockState(pos).getBlock() instanceof GantryShaftBlock)) return false;
        return locked(pos, world.getBlockState(pos).getValue(GantryShaftBlock.FACING));
    }
    public boolean isVisuallyLocked() { return visualLocked; }
    private void capture(BlockPos carriage, EnumFacing mount, EnumFacing direction) {
        float speed = MathHelper.clamp(-getSpeed() / 512F, -.49F, .49F);
        if (speed == 0 || !shaftAt(pos.offset(speed > 0 ? direction : direction.getOpposite()), direction)) return;
        BlockPos front = carriage.offset(mount);
        List<BlockPos> roots = world.isAirBlock(front) ? List.of(carriage) : List.of(carriage, front);
        BearingContraption structure = BearingContraption.captureLinear(world, pos, carriage, roots, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        if (structure == null || structure.isEmpty()) return;
        CarriedBearingAssembly nested = CarriedBearingAssembly.prepare(world, structure, positions ->
                ContraptionGuiProxy.closeCapturedBlock(world.getMinecraftServer(), positions, world.provider.getDimension()));
        BearingContraptionEntity entity = BearingContraptionEntity.createLinear(world, pos, carriage, direction, structure, false);
        entity.prepareNestedChildren();
        if (!world.spawnEntity(entity)) return;
        com.cogwork.cogworkbridge.content.decoration.SeatBlock.transferOccupants(entity, structure);
        nested.removeFromWorld(); structure.removeFromWorld(world, true);
        travellers.add(new Travel(entity.getUniqueID(), carriage.toImmutable(), mount, direction, new SequencedTravel()));
        markDirty(); entity.sendSnapshotToTrackingPlayers(); entity.sendNestedSnapshots();
    }
    private void settle(BearingContraptionEntity entity) {
        float offset = entity.getVerticalOffset();
        if (!entity.moveLinear(Math.round(offset))) return;
        entity.restoreAndDiscard();
    }
    public void releaseAll() {
        for (Travel travel : new ArrayList<>(travellers)) {
            BearingContraptionEntity entity = find(travel.id);
            if (entity != null) settle(entity);
        }
    }
    @Override public void update() {
        super.update();
        if (world == null || world.isRemote || world instanceof FakeWorld
                || !(world.getBlockState(pos).getBlock() instanceof GantryShaftBlock)) return;
        EnumFacing direction = world.getBlockState(pos).getValue(GantryShaftBlock.FACING);
        if ((world.getTotalWorldTime() + pos.getX() + pos.getY() + pos.getZ()) % 4 == 0) {
            boolean locked = locked(pos, direction);
            if (locked != visualLocked) {
                visualLocked = locked;
                markDirty();
                sync();
            }
        }
        if (getSpeed() != 0 && !locked(pos, direction) && world.getTotalWorldTime() % 5 == 0) {
            for (EnumFacing mount : EnumFacing.VALUES) {
                if (mount.getAxis() == direction.getAxis()) continue;
                BlockPos carriage = pos.offset(mount);
                IBlockState state = world.getBlockState(carriage);
                if (state.getBlock() instanceof GantryCarriageBlock && state.getValue(GantryCarriageBlock.FACING) == mount)
                    capture(carriage, mount, direction);
            }
        }
        for (Travel travel : new ArrayList<>(travellers)) {
            BearingContraptionEntity entity = find(travel.id);
            if (entity == null) continue;
            float offset = entity.getVerticalOffset();
            BlockPos shaftPos = travel.anchor.offset(travel.direction, Math.round(offset)).offset(travel.mount.getOpposite());
            if (!shaftAt(shaftPos, travel.direction) || locked(shaftPos, travel.direction)
                    || !(world.getTileEntity(shaftPos) instanceof GantryShaftTileEntity shaft) || shaft.getSpeed() == 0) {
                settle(entity); continue;
            }
            if (shaft != this) {
                // Keep ownership in the same loaded chunk as the carriage.
                // The next shaft performs its next movement step on its own tick.
                travellers.remove(travel);
                entityReferences.remove(travel.id);
                if (!shaft.ownsLinearContraption(travel.id)) shaft.travellers.add(travel);
                entity.transferLinearController(shaftPos);
                markDirty(); shaft.markDirty();
                continue;
            }
            float delta = travel.sequence.limit(shaft, MathHelper.clamp(-shaft.getSpeed() / 512F, -.49F, .49F));
            float next = offset + delta;
            int nextCell = Math.round(next);
            BlockPos nextShaft = travel.anchor.offset(travel.direction, nextCell).offset(travel.mount.getOpposite());
            if (!shaftAt(nextShaft, travel.direction)) { settle(entity); continue; }
            boolean moved = entity.moveLinear(next);
            travel.sequence.moved(entity.getVerticalOffset() - offset);
            markDirty();
            if (!moved && !entity.isLinearActorBlocking()) settle(entity);
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagList list = new NBTTagList();
        for (Travel travel : travellers) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setUniqueId("Id", travel.id);
            entry.setLong("Anchor", travel.anchor.toLong()); entry.setByte("Mount", (byte) travel.mount.getIndex());
            entry.setByte("Direction", (byte) travel.direction.getIndex()); entry.setTag("SequencedTravel", travel.sequence.write()); list.appendTag(entry);
        }
        tag.setTag("Travellers", list); tag.setBoolean("GantryLocked", visualLocked); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); visualLocked = tag.getBoolean("GantryLocked"); travellers.clear();entityReferences.clear(); NBTTagList list = tag.getTagList("Travellers", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.hasUniqueId("Id")) {
                SequencedTravel sequence = new SequencedTravel(); sequence.read(entry.getCompoundTag("SequencedTravel"));
                travellers.add(new Travel(entry.getUniqueId("Id"), BlockPos.fromLong(entry.getLong("Anchor")),
                        EnumFacing.byIndex(entry.getByte("Mount")), EnumFacing.byIndex(entry.getByte("Direction")), sequence));
            }
        }
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setBoolean("GantryLocked", visualLocked);
        return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        boolean previous = visualLocked;
        super.handleUpdateTag(tag);
        visualLocked = tag.getBoolean("GantryLocked");
        if (previous != visualLocked && world != null && world.isRemote) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
