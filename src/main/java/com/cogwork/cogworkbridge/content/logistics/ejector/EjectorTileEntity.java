package com.cogwork.cogworkbridge.content.logistics.ejector;

import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.content.logistics.transport.PortableItemStorage;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

public final class EjectorTileEntity extends KineticTileEntity implements IItemHandler, PortableItemStorage {
    private ItemStack stack = ItemStack.EMPTY;
    private int horizontal = 1;
    private int vertical;
    private int maxStack;
    private float lid = 1;
    private float previousLid = 1;
    private boolean triggered;
    private boolean powered;
    private final List<Flight> flights = new ArrayList<>();
    public record FlightView(ItemStack stack, double x, double y, double z) { }
    public record LaunchTrajectory(double horizontalMotion, double verticalMotion, double duration) {
        public Vec3d position(double tick, EnumFacing facing, BlockPos start) {
            double time = Math.max(0, Math.min(duration, tick));
            double horizontalDecay = Math.pow(0.91D, time);
            double verticalDecay = Math.pow(0.98D, time);
            double h = horizontalMotion * 0.91D * (1D - horizontalDecay) / 0.09D;
            double v = verticalMotion * 0.98D * (1D - verticalDecay) / 0.02D
                    - 0.0784D / 0.02D * (time - (1D - verticalDecay) / 0.02D);
            return new Vec3d(start.getX() + 0.5D + facing.getXOffset() * h,
                    start.getY() + 1.02D + v, start.getZ() + 0.5D + facing.getZOffset() * h);
        }
    }
    private static final class Flight {
        ItemStack stack;
        double x, y, z, oldX, oldY, oldZ, vx, vy, vz;
        int age, targetX, targetY, targetZ, duration;
        Flight(ItemStack stack, double vx, double vy, double vz, BlockPos target, int duration) {
            this.stack = stack; this.x = oldX = 0.5; this.y = oldY = 1.02; this.z = oldZ = 0.5;
            this.vx = vx; this.vy = vy; this.vz = vz;
            targetX = target.getX(); targetY = target.getY(); targetZ = target.getZ(); this.duration = duration;
        }
    }
    public List<FlightView> flightViews(float partial) {
        List<FlightView> result = new ArrayList<>(flights.size());
        for (Flight f : flights) result.add(new FlightView(f.stack.copy(), f.oldX + (f.x-f.oldX)*partial,
                f.oldY + (f.y-f.oldY)*partial, f.oldZ + (f.z-f.oldZ)*partial));
        return result;
    }

    @Override public AxisAlignedBB getRenderBoundingBox() { return flights.isEmpty() ? super.getRenderBoundingBox() : new AxisAlignedBB(pos).grow(33); }
    public void setTarget(int horizontal, int vertical) {
        this.horizontal = Math.max(1, Math.min(32, horizontal));
        this.vertical = Math.max(-32, Math.min(32, vertical));
        changed();
    }
    public int getMaxStack() { return maxStack; }
    public BlockPos targetPosition() { return pos.offset(facing(), horizontal).up(vertical); }
    public LaunchTrajectory trajectory() { return trajectory(horizontal, vertical); }
    public void setMaxStack(int maxStack) { this.maxStack = Math.max(1, Math.min(64, maxStack)); changed(); }
    public float lidProgress(float partialTicks) { return previousLid + (lid - previousLid) * partialTicks; }
    public void requestLaunch() { if (lid <= 0 && !powered) triggered = true; }
    private void changed() { markDirty(); if (world != null && !world.isRemote) sync(); }
    private EnumFacing facing() {
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof EjectorBlock ? state.getValue(EjectorBlock.FACING) : EnumFacing.NORTH;
    }
    public ItemStack takeAll() {
        ItemStack result = stack; stack = ItemStack.EMPTY;
        if (!result.isEmpty()) changed();
        return result;
    }
    public void acceptEntity(EntityItem item) {
        if (item.isDead || item.posY < pos.getY() + 0.7D) return;
        ItemStack rest = insertItem(0, item.getItem(), false);
        if (rest.isEmpty()) item.setDead(); else item.setItem(rest);
    }
    public void interact(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!stack.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, takeAll());
        if (!held.isEmpty()) {
            ItemStack rest = insertItem(0, held, false);
            player.setHeldItem(hand, rest);
        }
    }
    @Override public void update() {
        if (world == null) return;
        super.update();
        tickFlights();
        previousLid = lid;
        if (world.isRemote) {
            if (lid > 0) lid = Math.max(0, lid - windUpSpeed());
            return;
        }
        powered = world.isBlockPowered(pos);
        if (lid > 0) {
            float old = lid;
            lid = Math.max(0, lid - windUpSpeed());
            if (old > 0 && lid == 0) changed();
            return;
        }
        if (powered) { triggered = false; return; }
        boolean itemCountMatches = stack.isEmpty() || maxStack == 0 || stack.getCount() == maxStack;
        if (!stack.isEmpty() && itemCountMatches) triggered = true;
        if (triggered) {
            triggered = false;
            if (itemCountMatches) launch();
        }
    }
    private float windUpSpeed() {
        double distance = Math.sqrt(horizontal * horizontal + vertical * vertical);
        return (float) (Math.abs(getSpeed()) / 256.0D / Math.max(1, distance));
    }
    private void launch() {
        EnumFacing facing = facing();
        BlockPos targetPos = pos.offset(facing, horizontal).up(vertical);
        IItemHandler target = ItemTransfer.at(world, targetPos, EnumFacing.UP);
        if (!stack.isEmpty() && target != null
                && ItemHandlerHelper.insertItemStacked(target, stack.copy(), true).getCount() == stack.getCount()) return;
        LaunchTrajectory trajectory = trajectory(horizontal, vertical);
        double yMotion = trajectory.verticalMotion();
        double xMotion = trajectory.horizontalMotion();
        double flight = trajectory.duration();
        if (!stack.isEmpty()) {
            ItemStack item = takeAll();
            if (world instanceof FakeWorld || world instanceof ContraptionWorld) {
                if (!ItemTransfer.launchFromStructure(world, pos, item, facing, xMotion * 0.91D, yMotion * 0.98D)) {
                    stack = item; changed(); return;
                }
            } else {
                flights.add(new Flight(item, facing.getXOffset() * xMotion * 0.91D,
                        yMotion * 0.98D, facing.getZOffset() * xMotion * 0.91D,
                        targetPos.subtract(pos), Math.max(3, (int) Math.ceil(flight))));
            }
        }
        AxisAlignedBB area = new AxisAlignedBB(pos).grow(-1 / 16D, 0, -1 / 16D).expand(0, 1, 0);
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(null, area)) {
            if (!entity.isEntityAlive() || entity instanceof EntityItem || entity.isSneaking()) continue;
            entity.motionX = facing.getXOffset() * xMotion * 0.91D;
            entity.motionY = yMotion * 0.98D;
            entity.motionZ = facing.getZOffset() * xMotion * 0.91D;
            entity.velocityChanged = true;
            entity.fallDistance = 0;
            if (entity instanceof EntityPlayer player) {
                player.getEntityData().setTag("CogworkEjectorStart", net.minecraft.nbt.NBTUtil.createPosTag(new BlockPos(player)));
                player.getEntityData().setLong("CogworkEjectorTime", world.getTotalWorldTime());
            }
        }
        lid = 1;
        changed();
    }
    public static LaunchTrajectory trajectory(int horizontal, int vertical) {
        double peak = Math.max(0, vertical + (horizontal + 0.5D) / 8D) + (horizontal <= 1 ? 1 : 4);
        double yMotion = Math.sqrt(2 * peak / 13D) + 0.015D * peak;
        double peakTick = Math.log(98D / (25D * yMotion + 98D)) / (2 * Math.log(7D) - 2 * Math.log(5D) - Math.log(2D));
        double flight = Math.sqrt(Math.max(0, peak - yMotion)) * 4.87D + 0.115D * (peak - yMotion) + peakTick;
        double error = -0.0007D * Math.pow(horizontal + 0.5D, 2) + 0.484D
                - Math.min(5, vertical) / 5D * Math.min(1, 0.076D * horizontal - 0.0014D * horizontal * horizontal);
        double xMotion = (horizontal - error + 0.5D) / (-10.6033D * (-1 + Math.pow(0.91D, flight)));
        return new LaunchTrajectory(xMotion, yMotion, flight);
    }
    private void tickFlights() {
        if (flights.isEmpty()) return;
        boolean changed = false;
        for (Iterator<Flight> iterator = flights.iterator(); iterator.hasNext();) {
            Flight f = iterator.next(); f.oldX = f.x; f.oldY = f.y; f.oldZ = f.z;
            double nextX = f.x + f.vx, nextY = f.y + f.vy, nextZ = f.z + f.vz;
            RayTraceResult hit = world.isRemote || f.age <= 2 ? null : world.rayTraceBlocks(
                    new Vec3d(pos.getX()+f.x, pos.getY()+f.y, pos.getZ()+f.z),
                    new Vec3d(pos.getX()+nextX, pos.getY()+nextY, pos.getZ()+nextZ), false, true, false);
            if (!world.isRemote && hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                IItemHandler target = ItemTransfer.at(world, hit.getBlockPos(), hit.sideHit);
                if (target != null) f.stack = ItemHandlerHelper.insertItemStacked(target, f.stack, false);
                if (f.stack.isEmpty() || spawnFlightRemainder(f, hit.hitVec)) { iterator.remove(); changed = true; }
                continue;
            }
            f.x = nextX; f.y = nextY; f.z = nextZ;
            f.vx *= 0.91D; f.vy = (f.vy - 0.08D) * 0.98D; f.vz *= 0.91D; f.age++;
            if (!world.isRemote && f.age >= f.duration) {
                BlockPos destination = pos.add(f.targetX, f.targetY, f.targetZ);
                IItemHandler target = ItemTransfer.at(world, destination, EnumFacing.UP);
                Vec3d location = new Vec3d(pos.getX() + f.x, pos.getY() + f.y, pos.getZ() + f.z);
                Vec3d destinationCenter = new Vec3d(destination.getX() + 0.5D, destination.getY() + 1D, destination.getZ() + 0.5D);
                if (target != null && location.squareDistanceTo(destinationCenter) <= 2.25D) {
                    int previousCount = f.stack.getCount();
                    f.stack = ItemHandlerHelper.insertItemStacked(target, f.stack, false);
                    if (f.stack.getCount() != previousCount) changed = true;
                    if (f.stack.isEmpty()) { iterator.remove(); changed = true; continue; }
                }
                // A planned arrival is not a collision. Keep the item flying until
                // it reaches a real surface; only fall back on a normal entity at
                // the actual position if it leaves the world or travels too long.
                if ((location.y < -64D || f.age > 600) && spawnFlightRemainder(f, location)) {
                    iterator.remove(); changed = true;
                }
            }
        }
        if (changed) changed();
    }
    private boolean spawnFlightRemainder(Flight f, Vec3d position) {
        EntityItem item = new EntityItem(world, position.x, position.y, position.z, f.stack.copy());
        item.motionX = f.vx * 0.25D; item.motionY = Math.max(0.05D, f.vy * 0.25D); item.motionZ = f.vz * 0.25D;
        item.setDefaultPickupDelay(); return world.spawnEntity(item);
    }
    public void dropFlights() {
        if (world == null || world.isRemote) return;
        for (Flight f : flights) ItemTransfer.drop(world, pos, f.stack);
        flights.clear(); changed();
    }
    @Override public int getSlots() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? stack : ItemStack.EMPTY; }
    @Override public ItemStack insertItem(int slot, ItemStack incoming, boolean simulate) {
        if (slot != 0 || incoming.isEmpty() || lid > 0) return incoming;
        if (!stack.isEmpty() && (!ItemStack.areItemsEqual(stack, incoming) || !ItemStack.areItemStackTagsEqual(stack, incoming))) return incoming;
        int max = Math.min(incoming.getMaxStackSize(), 64);
        int count = Math.min(incoming.getCount(), max - stack.getCount());
        if (count <= 0) return incoming;
        ItemStack rest = incoming.copy(); rest.shrink(count);
        if (!simulate) {
            if (stack.isEmpty()) { stack = incoming.copy(); stack.setCount(count); }
            else stack.grow(count);
            changed();
        }
        return rest;
    }
    @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
        if (slot != 0 || count <= 0 || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.copy(); result.setCount(Math.min(count, stack.getCount()));
        if (!simulate) { stack.shrink(result.getCount()); changed(); }
        return result;
    }
    @Override public int getSlotLimit(int slot) { return 64; }
    @Override public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this) : super.getCapability(cap, side);
    }
    private void save(NBTTagCompound tag) {
        if (!stack.isEmpty()) tag.setTag("Item", stack.writeToNBT(new NBTTagCompound()));
        tag.setInteger("Horizontal", horizontal); tag.setInteger("Vertical", vertical);
        tag.setInteger("MaxStack", maxStack); tag.setFloat("Lid", lid); tag.setBoolean("Powered", powered);
        NBTTagList list = new NBTTagList();
        for (Flight f : flights) {
            NBTTagCompound e = new NBTTagCompound(); e.setTag("Stack", f.stack.writeToNBT(new NBTTagCompound()));
            e.setDouble("X", f.x); e.setDouble("Y", f.y); e.setDouble("Z", f.z);
            e.setDouble("VX", f.vx); e.setDouble("VY", f.vy); e.setDouble("VZ", f.vz); e.setInteger("Age", f.age);
            e.setInteger("TargetX", f.targetX); e.setInteger("TargetY", f.targetY); e.setInteger("TargetZ", f.targetZ);
            e.setInteger("Duration", f.duration); list.appendTag(e);
        }
        tag.setTag("Flights", list);
    }
    private void load(NBTTagCompound tag) {
        stack = tag.hasKey("Item", 10) ? new ItemStack(tag.getCompoundTag("Item")) : ItemStack.EMPTY;
        horizontal = Math.max(1, Math.min(32, tag.getInteger("Horizontal")));
        vertical = Math.max(-32, Math.min(32, tag.getInteger("Vertical")));
        maxStack = Math.max(0, Math.min(64, tag.getInteger("MaxStack")));
        lid = previousLid = tag.hasKey("Lid") ? tag.getFloat("Lid") : 1;
        powered = tag.getBoolean("Powered");
        flights.clear(); NBTTagList list = tag.getTagList("Flights", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i); ItemStack item = new ItemStack(e.getCompoundTag("Stack"));
            if (item.isEmpty()) continue;
            Flight f = new Flight(item, e.getDouble("VX"), e.getDouble("VY"), e.getDouble("VZ"),
                    new BlockPos(e.getInteger("TargetX"), e.getInteger("TargetY"), e.getInteger("TargetZ")),
                    Math.max(3, e.getInteger("Duration")));
            f.x = f.oldX = e.getDouble("X"); f.y = f.oldY = e.getDouble("Y"); f.z = f.oldZ = e.getDouble("Z");
            f.age = e.getInteger("Age"); flights.add(f);
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); save(tag); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); load(tag); }
    @Override public NBTTagCompound getUpdateTag() { NBTTagCompound tag = super.getUpdateTag(); save(tag); return tag; }
    @Override public void handleUpdateTag(NBTTagCompound tag) { super.handleUpdateTag(tag); load(tag); }
}
