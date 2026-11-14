package com.cogwork.cogworkbridge.content.contraptions.elevator;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Contact power is transient; floor names and calls survive chunk reloads. */
public final class RedstoneContactTileEntity extends TileEntity implements ITickable {
    private boolean powering, calling, previousInput, namesInitialized;
    private String shortName = "", longName = "", currentFloor = "";
    private int doorMode;
    private long lastMobileContact = Long.MIN_VALUE;

    public boolean isPowering() { return powering; }
    public boolean isCalling() { return calling; }
    public String getShortName() { return shortName; }
    public String getLongName() { return longName; }
    public String getCurrentFloor() { return currentFloor; }
    public int getDoorMode() { return doorMode; }
    public void configure(String shortName, String longName, int doorMode) {
        this.shortName = shortName.substring(0, Math.min(4, shortName.length()));
        this.longName = longName.substring(0, Math.min(30, longName.length()));
        this.doorMode = Math.floorMod(doorMode, 6);
        changed(false);
    }
    @Override public void update() { if (world != null && !world.isRemote) refresh(); }
    public void refresh() {
        if (world == null || world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstoneContactBlock contact)) return;
        EnumFacing facing = state.getValue(RedstoneContactBlock.FACING);
        if (contact.isElevator() && !(world instanceof FakeWorld) && !namesInitialized) {
            namesInitialized = true;
            if (shortName.trim().isEmpty()) initializeFloorNames(facing);
        }
        IBlockState next = world.getBlockState(pos.offset(facing));
        boolean paired = next.getBlock() instanceof RedstoneContactBlock && next.getValue(RedstoneContactBlock.FACING) == facing.getOpposite();
        boolean mobilePaired = !(world instanceof FakeWorld) && findMobileContact(facing, contact.isElevator());
        if (!(world instanceof FakeWorld)) paired |= mobilePaired;
        else paired |= lastMobileContact != Long.MIN_VALUE && world.getTotalWorldTime() - lastMobileContact <= 2;
        if (powering != paired) {
            powering = paired;
            if (paired) calling = false;
            changed(true);
        }
        if (contact.isElevator() && !(world instanceof FakeWorld)) {
            boolean input = world.isBlockPowered(pos);
            if (input != previousInput) {
                boolean rising = input && !previousInput;
                previousInput = input;
                markDirty();
                if (rising && !powering) callFloor(facing);
            }
        }
    }
    private boolean findMobileContact(EnumFacing facing, boolean elevatorFloor) {
        Vec3d expected = new Vec3d(pos.offset(facing)).add(.5D, .5D, .5D);
        boolean found = false;
        for (BearingContraptionEntity entity : world.getEntitiesWithinAABB(BearingContraptionEntity.class, new AxisAlignedBB(pos).grow(2D))) {
            if (entity.isActorDisabled(com.cogwork.cogworkbridge.registry.AllBlocks.REDSTONE_CONTACT)) continue;
            BlockPos near = entity.localPositionAtWorldPoint(expected).orElse(null);
            if (near == null || entity.getContraption() == null) continue;
            for (BlockPos candidate : BlockPos.getAllInBox(near.add(-1, -1, -1), near.add(1, 1, 1))) {
                BearingContraption.Snapshot snapshot = entity.getContraption().snapshotAtLocal(candidate).orElse(null);
                if (snapshot == null || !(snapshot.state().getBlock() instanceof RedstoneContactBlock)) continue;
                Vec3d normal = entity.localFacingVectorToWorld(snapshot.state().getValue(RedstoneContactBlock.FACING));
                if (EnumFacing.getFacingFromVector((float) normal.x, (float) normal.y, (float) normal.z) != facing.getOpposite()) continue;
                Vec3d activeArea = entity.localBlockCenterToWorld(candidate).add(normal.scale(.65D));
                if (!new BlockPos(activeArea).equals(pos)) continue;
                if (elevatorFloor) {
                    if (!entity.isElevator()) continue;
                    broadcastFloor(facing);
                    BlockPos controller = entity.getElevatorController();
                    if (!world.isBlockLoaded(controller)
                            || !(world.getTileEntity(controller) instanceof ElevatorPulleyTileEntity pulley)
                            || !pulley.isAtFloor(pos.getY())) continue;
                }
                TileEntity mobile = entity.getInteractionWorld().getTileEntity(candidate);
                if (mobile instanceof RedstoneContactTileEntity other) other.touchMobile();
                found = true;
                break;
            }
        }
        return found;
    }
    private void touchMobile() { lastMobileContact = world.getTotalWorldTime(); }
    private void initializeFloorNames(EnumFacing facing) {
        java.util.List<RedstoneContactTileEntity> floors = new java.util.ArrayList<>();
        for (TileEntity tile : new java.util.ArrayList<>(world.loadedTileEntityList)) {
            if (!(tile instanceof RedstoneContactTileEntity other)) continue;
            BlockPos p = other.getPos();
            IBlockState state = world.getBlockState(p);
            if (p.getX() == pos.getX() && p.getZ() == pos.getZ()
                    && state.getBlock() instanceof RedstoneContactBlock contact && contact.isElevator()
                    && state.getValue(RedstoneContactBlock.FACING) == facing) floors.add(other);
        }
        floors.sort(java.util.Comparator.comparingInt(tile -> tile.getPos().getY()));
        Integer previous = null;
        for (int i = 0; i < floors.size(); i++) {
            RedstoneContactTileEntity floor = floors.get(i);
            Integer current = integerName(floor.shortName);
            if (!floor.shortName.trim().isEmpty() && current == null) continue;
            if (previous != null) current = previous + 1;
            Integer next = null;
            for (int j = i + 1; j < floors.size(); j++) {
                RedstoneContactTileEntity later = floors.get(j);
                Integer named = integerName(later.shortName);
                if (named == null) continue;
                if (current != null && current >= named) {
                    later.shortName = "";
                    later.changed(false);
                    break;
                }
                next = named;
                break;
            }
            if (current == null) current = next == null ? 0 : next - 1;
            String name = String.valueOf(current);
            if (!name.equals(floor.shortName)) { floor.shortName = name; floor.changed(false); }
            previous = current;
        }
    }
    private static Integer integerName(String name) {
        try { return Integer.valueOf(name); } catch (NumberFormatException ignored) { return null; }
    }
    private void broadcastFloor(EnumFacing facing) {
        for (TileEntity tile : new java.util.ArrayList<>(world.loadedTileEntityList)) {
            if (!(tile instanceof RedstoneContactTileEntity other)) continue;
            BlockPos p = other.getPos();
            IBlockState state = world.getBlockState(p);
            if (p.getX() != pos.getX() || p.getZ() != pos.getZ()
                    || !(state.getBlock() instanceof RedstoneContactBlock contact) || !contact.isElevator()
                    || state.getValue(RedstoneContactBlock.FACING) != facing || other.currentFloor.equals(shortName)) continue;
            other.currentFloor = shortName;
            other.changed(false);
        }
    }
    private void callFloor(EnumFacing facing) {
        for (TileEntity tile : new java.util.ArrayList<>(world.loadedTileEntityList)) {
            if (!(tile instanceof RedstoneContactTileEntity other)) continue;
            BlockPos p = other.getPos();
            IBlockState state = world.getBlockState(p);
            if (p.getX() != pos.getX() || p.getZ() != pos.getZ() || !(state.getBlock() instanceof RedstoneContactBlock contact)
                    || !contact.isElevator() || state.getValue(RedstoneContactBlock.FACING) != facing) continue;
            boolean value = p.equals(pos);
            if (other.calling != value) { other.calling = value; other.changed(false); }
        }
    }
    private void changed(boolean notifyNeighbors) {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) {
            fake.getMobileChunk().markTileDirty(pos);
            if (notifyNeighbors) world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), false);
        } else {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
            if (notifyNeighbors) {
                world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
                if (state.getBlock() instanceof RedstoneContactBlock block && block.isElevator()) world.checkLight(pos);
            }
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("Powering", powering); tag.setBoolean("Calling", calling);
        tag.setBoolean("PreviousInput", previousInput);
        tag.setString("ShortName", shortName); tag.setString("LongName", longName); tag.setString("CurrentFloor", currentFloor); tag.setInteger("DoorMode", doorMode);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        powering = tag.getBoolean("Powering"); calling = tag.getBoolean("Calling");
        previousInput = tag.getBoolean("PreviousInput");
        shortName = tag.getString("ShortName"); longName = tag.getString("LongName"); currentFloor = tag.getString("CurrentFloor"); doorMode = Math.floorMod(tag.getInteger("DoorMode"), 6);
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
