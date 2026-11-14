package com.cogwork.cogworkbridge.content.logistics.belt;

import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BrassTunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.tunnel.BeltTunnelTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.PortableItemStorage;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemHolder;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemProcessor;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.fan.FanItemProcessing;
import com.cogwork.cogworkbridge.content.kinetics.fan.FanProcessingHolder;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.fluids.FluidUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** One belt segment; adjacent segments share rotation but retain their transported stack. */
public final class BeltTileEntity extends KineticTileEntity implements IItemHandler, PortableItemStorage, TransportedItemHolder, FanProcessingHolder {
    private BlockPos step = BlockPos.ORIGIN;
    private int index;
    private int length;
    private boolean pulley;
    private EnumDyeColor color;
    private CasingType casing = CasingType.NONE;
    private ItemStack carried = ItemStack.EMPTY;
    private final java.util.Map<Integer, TutorialItem> tutorialItems = new java.util.LinkedHashMap<>();
    public record TutorialItem(ItemStack stack, float previous, float position, float angle, float offset) {
        public float position(float partial) { return previous + (position - previous) * partial; }
    }
    public java.util.Collection<TutorialItem> tutorialItems() { return java.util.Collections.unmodifiableCollection(tutorialItems.values()); }
    public TutorialItem tutorialItem(int id) { return tutorialItems.get(id); }
    public void setTutorialItem(int id, ItemStack stack, float previous, float position) {
        if (stack.isEmpty()) { tutorialItems.remove(id); return; }
        TutorialItem before = tutorialItems.get(id);
        float angle = before == null ? Math.floorMod(id * 53, 360) : before.angle;
        float offset = before == null ? (Math.floorMod(id * 17, 13) - 6) / 96F : before.offset;
        tutorialItems.put(id, new TutorialItem(stack.copy(), previous, position, angle, offset));
    }
    public void clearTutorialItems() { tutorialItems.clear(); }
    private float carriedAngle;
    private float carriedSideOffset;
    private float position = 0.5F;
    private float previousPosition = 0.5F;
    private int tunnelSideMask;
    private int routedDirection;
    private boolean processingHeld;
    private final List<ItemStack> processingOutputs = new ArrayList<>();
    private ItemStack processingOutput = ItemStack.EMPTY;
    private float outputPosition = 0.5F;
    private float previousOutputPosition = 0.5F;
    private float outputAngle;
    private float outputSideOffset;
    private final FanItemProcessing.Progress fanProgress = new FanItemProcessing.Progress();
    private final Map<Entity, Passenger> passengers = new IdentityHashMap<>();

    private record Passenger(BlockPos contactPos, int ticksSinceCollision) { }

    public enum CasingType { NONE, ANDESITE, BRASS }

    public void initialize(BlockPos origin, BlockPos step, int index, int length, boolean pulley) {
        this.step = step.toImmutable();
        this.index = index;
        this.length = length;
        this.pulley = pulley;
        markDirty();
        sync();
    }
    /** Preserve the world-space position of transported stacks when a chain is reversed. */
    void reverseTransportCoordinates() {
        position = 1F - position;
        previousPosition = 1F - previousPosition;
        carriedSideOffset = -carriedSideOffset;
        outputPosition = 1F - outputPosition;
        previousOutputPosition = 1F - previousOutputPosition;
        outputSideOffset = -outputSideOffset;
        tunnelSideMask = 0;
        routedDirection = 0;
    }
    public BlockPos step() { return step; }
    public EnumFacing.Axis shaftAxis() { return getAxis(); }
    public int length() { return length; }
    public int index() { return index; }
    public boolean hasPulley() { return pulley; }
    public void setPulley(boolean pulley) {
        if (this.pulley == pulley) return;
        this.pulley = pulley;
        changed();
    }
    public EnumDyeColor color() { return color; }
    void setSegmentColor(@Nullable EnumDyeColor next) {
        if (color == next) return;
        color = next;
        changed();
    }
    public CasingType casing() { return casing; }
    public boolean applyColor(EnumDyeColor next) {
        if (color == next) return false;
        if (world == null || world.isRemote) return true;
        BeltTileEntity controller = this;
        if (length <= 1) {
            color = next;
            changed();
            return true;
        }
        for (int i = 0; i < length; i++) {
            BlockPos member = origin().add(step.getX() * i, step.getY() * i, step.getZ() * i);
            if (world.getTileEntity(member) instanceof BeltTileEntity belt && belt.sameChain(controller)) {
                belt.color = next;
                belt.changed();
            }
        }
        return true;
    }
    public void setCasing(CasingType next) {
        if (next == null || casing == next) return;
        casing = next;
        changed();
    }
    public void removeCasing() {
        if (casing == CasingType.NONE) return;
        casing = CasingType.NONE;
        changed();
    }
    public BlockPos origin() { return pos.add(-step.getX() * index, -step.getY() * index, -step.getZ() * index); }
    public boolean sameChain(BeltTileEntity other) {
        return other != null && length > 1 && length == other.length && step.equals(other.step) && origin().equals(other.origin());
    }
    public boolean connectedTo(BeltTileEntity other) { return sameChain(other) && Math.abs(index - other.index) == 1; }
    public void trackPassenger(Entity entity) {
        if (world == null || entity instanceof EntityItem || !entity.isEntityAlive() || entity.noClip) return;
        if (world.isRemote && (!(entity instanceof EntityPlayer player) || !player.isUser())) return;
        if (entity instanceof EntityPlayer player && (player.isSneaking() || player.capabilities.isFlying)) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BeltBlock beltBlock) || !isOnBelt(entity, beltBlock, state)) return;
        if (index != 0) {
            TileEntity controller = world.getTileEntity(origin());
            if (controller instanceof BeltTileEntity belt && belt != this && belt.sameChain(this)) {
                belt.trackPassenger(entity, this);
            }
            return;
        }
        trackPassenger(entity, this);
    }

    private void trackPassenger(Entity entity, BeltTileEntity contact) {
        if (world == null || entity instanceof EntityItem || !entity.isEntityAlive() || entity.noClip) return;
        if (world.isRemote && (!(entity instanceof EntityPlayer player) || !player.isUser())) return;
        if (entity instanceof EntityPlayer player && (player.isSneaking() || player.capabilities.isFlying)) return;
        passengers.put(entity, new Passenger(contact.pos.toImmutable(), 0));
    }
    public List<BlockPos> beltNeighbors() {
        List<BlockPos> result = new ArrayList<>(2);
        if (length < 2) return result;
        if (index > 0) result.add(pos.add(-step.getX(), -step.getY(), -step.getZ()));
        if (index + 1 < length) result.add(pos.add(step));
        return result;
    }
    public ItemStack getCarried() { return carried.copy(); }
    public ItemStack takeCarried() {
        if (carried.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = carried;
        carried = ItemStack.EMPTY;
        carriedAngle = 0;
        carriedSideOffset = 0;
        fanProgress.reset();
        tunnelSideMask = 0;
        routedDirection = 0;
        processingHeld = false;
        changed();
        return result;
    }
    @Override public ItemStack getTransportedItem() {
        return Math.abs(position - 0.5F) < 0.001F ? carried.copy() : ItemStack.EMPTY;
    }
    @Override public void replaceOneWith(ItemStack result) {
        replaceOneWith(result.isEmpty() ? java.util.Collections.emptyList() : java.util.Collections.singletonList(result));
    }
    @Override public void replaceOneWith(List<ItemStack> results) {
        if (carried.isEmpty()) return;
        carried.shrink(1);
        if (carried.isEmpty()) processingHeld = false;
        fanProgress.reset();
        for (ItemStack result : results) if (!result.isEmpty()) processingOutputs.add(result.copy());
        changed();
    }
    public List<ItemStack> takeProcessingOutputs() {
        List<ItemStack> result = new ArrayList<>(processingOutputs);
        processingOutputs.clear();
        if (!processingOutput.isEmpty()) {
            result.add(0, processingOutput);
            processingOutput = ItemStack.EMPTY;
        }
        if (!result.isEmpty()) changed();
        return result;
    }
    public ItemStack getProcessingOutput() { return processingOutput.copy(); }
    public ItemStack takeProcessingOutput() {
        ItemStack result = processingOutput;
        processingOutput = ItemStack.EMPTY;
        if (!result.isEmpty()) changed();
        return result;
    }
    public ItemStack takeAll() {
        ItemStack result = carried;
        carried = ItemStack.EMPTY;
        carriedAngle = 0;
        carriedSideOffset = 0;
        fanProgress.reset();
        tunnelSideMask = 0;
        routedDirection = 0;
        if (!result.isEmpty()) changed();
        return result;
    }
    public float renderPosition(float partialTicks) { return previousPosition + (position - previousPosition) * partialTicks; }
    public float carriedAngle() { return carriedAngle; }
    public float carriedSideOffset() { return carriedSideOffset; }
    public float renderOutputPosition(float partialTicks) { return previousOutputPosition + (outputPosition - previousOutputPosition) * partialTicks; }
    public float outputAngle() { return outputAngle; }
    public float outputSideOffset() { return outputSideOffset; }
    public String modelKey() {
        String part = index == 0 ? "start" : index == length - 1 ? "end" : "middle";
        String slope = getAxis() == EnumFacing.Axis.Y ? "sideways" : step.getY() == 0 ? "flat"
                : step.getX() == 0 && step.getZ() == 0 ? "vertical" : step.getY() > 0 ? "up" : "down";
        String direction = step.getX() > 0 ? "east" : step.getX() < 0 ? "west"
                : step.getZ() > 0 ? "south" : step.getZ() < 0 ? "north"
                : getAxis() == EnumFacing.Axis.X ? (step.getY() > 0 ? "south" : "north")
                : (step.getY() > 0 ? "east" : "west");
        return part + "_" + slope + "_" + direction;
    }
    private boolean transportsItems() { return step.getY() == 0 || step.getX() != 0 || step.getZ() != 0; }
    public boolean supportsItemTransport() { return transportsItems() && getAxis() != EnumFacing.Axis.Y; }
    private float pathMotion() {
        if (!transportsItems() || getAxis() == EnumFacing.Axis.Y) return 0;
        int component = step.getX() != 0 ? step.getX() : step.getZ();
        float speed = getSpeed() / 480.0F;
        if (step.getX() != 0) speed = -speed;
        return speed * component;
    }
    @Nullable public EnumFacing movementFacing() {
        float motion = pathMotion();
        if (motion == 0) return null;
        return EnumFacing.getFacingFromVector(step.getX() * Math.signum(motion), 0,
                step.getZ() * Math.signum(motion));
    }
    public boolean canReceiveFrom(EnumFacing direction) {
        float motion = pathMotion();
        if (motion == 0 || direction.getAxis().isVertical()) return false;
        int dx = motion > 0 ? step.getX() : -step.getX();
        int dz = motion > 0 ? step.getZ() : -step.getZ();
        return dx != -direction.getXOffset() || dz != -direction.getZOffset();
    }
    /** Insert from the direction in which the incoming item is travelling. */
    public ItemStack insertFrom(EnumFacing travel, ItemStack stack, boolean simulate) {
        if (travel == null || travel.getAxis().isVertical()) return stack;
        if (pathMotion() != 0 && !canReceiveFrom(travel)) return stack;
        int along = travel.getXOffset() * step.getX() + travel.getZOffset() * step.getZ();
        ItemStack rest = insertAt(stack, simulate, along > 0 ? 0F : along < 0 ? 1F : 0.5F);
        if (!simulate && rest.getCount() < stack.getCount()) flapTunnelEntry(travel);
        return rest;
    }
    public boolean alreadySplitTo(EnumFacing side) { return (tunnelSideMask & (1 << side.getIndex())) != 0; }
    public void markSplitTo(EnumFacing side) { tunnelSideMask |= 1 << side.getIndex(); changed(); }
    public ItemStack restoreSplitItem(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (carried.isEmpty()) {
            carried = stack.copy();
            changed();
            return ItemStack.EMPTY;
        }
        if (!ItemStack.areItemsEqual(carried, stack) || !ItemStack.areItemStackTagsEqual(carried, stack)) return stack;
        int restored = Math.min(stack.getCount(), carried.getMaxStackSize() - carried.getCount());
        if (restored <= 0) return stack;
        carried.grow(restored);
        ItemStack rest = stack.copy();
        rest.shrink(restored);
        changed();
        return rest;
    }
    private void changed() { markDirty(); if (world != null && !world.isRemote) sync(); }

    @Override public void applyFanProcessing(EncasedFanTileEntity.Medium medium, BlockPos fanPos) {
        if (world == null || world.isRemote || carried.isEmpty()) return;
        if (!fanProgress.tick(medium, carried, world.getTotalWorldTime())) {
            if (world.getTotalWorldTime() % 20L == 0L) markDirty();
            return;
        }
        List<ItemStack> results = FanItemProcessing.results(medium, carried, world.rand);
        carried = ItemStack.EMPTY;
        processingHeld = false;
        processingOutputs.addAll(results);
        fanProgress.reset();
        changed();
        com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, fanPos, "fan_processing");
    }

    @Override public void update() {
        if (world == null) return;
        super.update();
        previousPosition = position;
        previousOutputPosition = outputPosition;
        float motion = pathMotion();
        if (motion != 0 && !(world instanceof FakeWorld) && !(world instanceof ContraptionWorld)) {
            transportEntities(motion);
        } else if (motion == 0 && index == 0) {
            passengers.clear();
        }
        updateProcessingOutput(motion);
        if (carried.isEmpty() || motion == 0) return;
        if (!world.isRemote && step.getY() == 0) {
            float next = position + motion;
            TileEntity crusherTile = world.getTileEntity(pos.up());
            if (crusherTile instanceof com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelControllerTileEntity crusher
                    && crusher.isActive() && movementFacing() == world.getBlockState(pos.up()).getValue(
                    com.cogwork.cogworkbridge.content.kinetics.crusher.CrushingWheelControllerBlock.FACING)) {
                float entry = motion > 0 ? .101F : .899F;
                boolean entering = motion > 0 ? next >= entry && position < .899F : next <= entry && position > .101F;
                if (entering) {
                    position = entry;
                    ItemStack remainder = crusher.acceptBeltItem(carried);
                    if (remainder.getCount() != carried.getCount()) {
                        carried = remainder;
                        if (carried.isEmpty()) fanProgress.reset();
                    }
                    changed();
                    return;
                }
            }
            boolean crossesCenter = position <= 0.5F && next >= 0.5F || position >= 0.5F && next <= 0.5F;
            boolean hold = false;
            float oldPosition = position;
            if (crossesCenter || processingHeld) {
                // Route while the stack is on the tunnel segment, before the
                // next segment's occupancy can prevent side distribution.
                TileEntity tunnelTile = world.getTileEntity(pos.up());
                EnumFacing travel = movementFacing();
                if (travel != null && tunnelTile instanceof BrassTunnelTileEntity tunnel) {
                    int direction = motion > 0 ? 1 : -1;
                    if (routedDirection != direction) {
                        BrassTunnelTileEntity.Route route = tunnel.route(this, travel);
                        if (route == BrassTunnelTileEntity.Route.HOLD) {
                            position = 0.5F;
                            changed();
                            return;
                        }
                        if (route == BrassTunnelTileEntity.Route.TRANSFERRED) return;
                        routedDirection = direction;
                        changed();
                    }
                } else if (travel != null && tunnelTile instanceof BeltTunnelTileEntity tunnel
                        && tunnel.transferToSide(this, travel)) {
                    position = 0.5F;
                    changed();
                    return;
                }
                TileEntity machine = world.getTileEntity(pos.up(2));
                if (machine instanceof TransportedItemProcessor processor) {
                    position = 0.5F;
                    hold = processor.holdTransportedItem(this);
                    if (!hold) position = oldPosition;
                }
            }
            if (hold != processingHeld || hold && oldPosition != position) {
                processingHeld = hold;
                changed();
            }
            if (hold) return;
        } else if (world.isRemote && processingHeld) return;
        // Keep the remaining stack behind its finished products if the exit is blocked.
        if (!processingOutput.isEmpty() || !processingOutputs.isEmpty()) return;
        position += motion;
        if (world.isRemote) {
            position = Math.max(0, Math.min(1, position));
            return;
        }
        if (position > 1 && !transfer(true)) position = 0.98F;
        if (position < 0 && !transfer(false)) position = 0.02F;
    }

    /** Products leave independently while the remaining input stays beneath the processor. */
    private void updateProcessingOutput(float motion) {
        if (!world.isRemote && processingOutput.isEmpty() && !processingOutputs.isEmpty()) {
            processingOutput = processingOutputs.remove(0);
            outputPosition = previousOutputPosition = position;
            boolean upright = isUprightItem(processingOutput);
            outputAngle = upright ? 180.0F : world.rand.nextInt(360);
            outputSideOffset = upright ? 0F : carriedSideOffset;
            changed();
        }
        if (processingOutput.isEmpty() || motion == 0F) return;
        outputPosition += motion;
        if (world.isRemote) {
            outputPosition = Math.max(0F, Math.min(1F, outputPosition));
            return;
        }
        if (outputPosition > 1F && !transferProcessingOutput(true)) outputPosition = 0.98F;
        if (outputPosition < 0F && !transferProcessingOutput(false)) outputPosition = 0.02F;
    }

    private boolean transferProcessingOutput(boolean forward) {
        int sign = forward ? 1 : -1;
        boolean endOfSlope = step.getY() != 0 && (step.getX() != 0 || step.getZ() != 0)
                && (forward ? index == length - 1 : index == 0);
        int targetY = endOfSlope ? 0 : step.getY() * sign;
        BlockPos target = pos.add(step.getX() * sign, targetY, step.getZ() * sign);
        EnumFacing outward = step.getX() != 0 ? (step.getX() * sign > 0 ? EnumFacing.EAST : EnumFacing.WEST)
                : step.getZ() != 0 ? (step.getZ() * sign > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH)
                : (step.getY() * sign > 0 ? EnumFacing.UP : EnumFacing.DOWN);
        TileEntity tile = world.getTileEntity(target);
        if (tile instanceof BeltTileEntity belt) {
            ItemStack before = processingOutput;
            ItemStack rest = connectedTo(belt)
                    ? belt.insertAt(before.copy(), false, forward ? 0F : 1F)
                    : belt.insertFrom(outward, before.copy(), false);
            if (rest.getCount() == before.getCount()) return false;
            if (connectedTo(belt)) belt.flapTunnelEntry(outward);
            belt.carriedAngle = outputAngle;
            int alignment = step.getX() * belt.step.getX() + step.getZ() * belt.step.getZ();
            belt.carriedSideOffset = alignment < 0 ? -outputSideOffset : outputSideOffset;
            belt.changed();
            processingOutput = rest;
            changed();
            if (!rest.isEmpty()) return false;
            belt.splitAtTunnelEntry();
            return transferFinished(outward);
        }
        IItemHandler handler = ItemTransfer.at(world, target, outward.getOpposite());
        if (handler != null) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, processingOutput.copy(), false);
            if (rest.getCount() == processingOutput.getCount()) return false;
            processingOutput = rest;
            changed();
            return rest.isEmpty() && transferFinished(outward);
        }
        if (world.getBlockState(target).isSideSolid(world, target, outward.getOpposite())) return false;
        ItemStack dropped = processingOutput.copy();
        if (world instanceof FakeWorld || world instanceof ContraptionWorld) ItemTransfer.drop(world, pos, dropped);
        else {
            EntityItem entity = new EntityItem(world, pos.getX() + 0.5D + outward.getXOffset() * 0.6D,
                    pos.getY() + 0.8D + targetY * 0.5D,
                    pos.getZ() + 0.5D + outward.getZOffset() * 0.6D, dropped);
            entity.motionX = outward.getXOffset() * 0.05D;
            entity.motionZ = outward.getZOffset() * 0.05D;
            entity.setDefaultPickupDelay();
            if (!world.spawnEntity(entity)) return false;
        }
        processingOutput = ItemStack.EMPTY;
        changed();
        return transferFinished(outward);
    }

    private boolean transfer(boolean forward) {
        int sign = forward ? 1 : -1;
        boolean endOfSlope = step.getY() != 0 && (step.getX() != 0 || step.getZ() != 0)
                && (forward ? index == length - 1 : index == 0);
        int targetY = endOfSlope ? 0 : step.getY() * sign;
        BlockPos target = pos.add(step.getX() * sign, targetY, step.getZ() * sign);
        EnumFacing outward = step.getX() != 0 ? (step.getX() * sign > 0 ? EnumFacing.EAST : EnumFacing.WEST)
                : step.getZ() != 0 ? (step.getZ() * sign > 0 ? EnumFacing.SOUTH : EnumFacing.NORTH)
                : (step.getY() * sign > 0 ? EnumFacing.UP : EnumFacing.DOWN);
        TileEntity tile = world.getTileEntity(target);
        if (tile instanceof BeltTileEntity belt && connectedTo(belt)) {
            ItemStack before = carried;
            ItemStack rest = belt.insertAt(before, false, forward ? 0 : 1);
            if (rest.getCount() == before.getCount()) return false;
            belt.flapTunnelEntry(outward);
            if (rest.isEmpty()) { belt.fanProgress.copyFrom(fanProgress); fanProgress.reset(); }
            belt.carriedAngle = carriedAngle;
            belt.carriedSideOffset = carriedSideOffset;
            belt.changed();
            carried = rest;
            changed();
            if (!rest.isEmpty()) return false;
            belt.splitAtTunnelEntry();
            return transferFinished(outward);
        }
        if (tile instanceof BeltTileEntity belt) {
            int count = carried.getCount();
            ItemStack rest = belt.insertFrom(outward, carried, false);
            if (rest.getCount() == count) return false;
            if (rest.isEmpty()) { belt.fanProgress.copyFrom(fanProgress); fanProgress.reset(); }
            belt.carriedAngle = carriedAngle;
            int alignment = step.getX() * belt.step.getX() + step.getZ() * belt.step.getZ();
            belt.carriedSideOffset = alignment < 0 ? -carriedSideOffset : carriedSideOffset;
            belt.changed();
            carried = rest;
            changed();
            if (!rest.isEmpty()) return false;
            belt.splitAtTunnelEntry();
            return transferFinished(outward);
        }
        if (carried.isEmpty()) return transferFinished(outward);
        IItemHandler handler = ItemTransfer.at(world, target, outward.getOpposite());
        if (handler != null) {
            int count = carried.getCount();
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, carried, false);
            if (rest.getCount() == count) return false;
            carried = rest;
            changed();
            if (!rest.isEmpty()) return false;
            return transferFinished(outward);
        }
        IBlockState state = world.getBlockState(target);
        if (state.isSideSolid(world, target, outward.getOpposite())) return false;
        ItemStack dropped = takeAll();
        if (world instanceof FakeWorld || world instanceof ContraptionWorld) ItemTransfer.drop(world, pos, dropped);
        else {
            EntityItem entity = new EntityItem(world, pos.getX() + 0.5D + outward.getXOffset() * 0.6D,
                    pos.getY() + 0.8D + targetY * 0.5D,
                    pos.getZ() + 0.5D + outward.getZOffset() * 0.6D, dropped);
            entity.motionX = outward.getXOffset() * 0.05D;
            entity.motionZ = outward.getZOffset() * 0.05D;
            entity.setDefaultPickupDelay();
            if (!world.spawnEntity(entity)) { carried = dropped; changed(); return false; }
        }
        return transferFinished(outward);
    }
    private boolean transferFinished(EnumFacing side) {
        if (world.getTileEntity(pos.up()) instanceof BeltTunnelTileEntity tunnel) tunnel.flap(side);
        return true;
    }
    private void flapTunnelEntry(EnumFacing travel) {
        if (world != null && !world.isRemote && travel.getAxis().isHorizontal()
                && world.getTileEntity(pos.up()) instanceof BeltTunnelTileEntity tunnel)
            tunnel.flap(travel.getOpposite());
    }
    private void splitAtTunnelEntry() {
        if (world == null || world.isRemote || step.getY() != 0 || carried.getCount() <= 1) return;
        EnumFacing travel = movementFacing();
        if (travel == null || !(world.getTileEntity(pos.up()) instanceof BeltTunnelTileEntity tunnel)
                || tunnel instanceof BrassTunnelTileEntity) return;
        // Process the incoming stack at the junction, as the upstream belt
        // inventory does, rather than waiting until it reaches the far end.
        if (tunnel.transferToSide(this, travel)) {
            position = previousPosition = 0.5F;
            changed();
        }
    }
    private void transportEntities(float motion) {
        if (Math.abs(getSpeed()) < 1) return;
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BeltBlock belt)) return;
        AxisAlignedBB area = new AxisAlignedBB(pos.getX(), pos.getY() - 0.25D, pos.getZ(),
                pos.getX() + 1, pos.getY() + 2.5D, pos.getZ() + 1);
        for (Entity entity : world.getEntitiesWithinAABB(Entity.class, area)) {
            if (!isOnBelt(entity, belt, state)) continue;
            if (entity instanceof EntityItem) {
                if (!world.isRemote) touch(entity, motion);
            } else trackPassenger(entity);
        }
        if (index == 0) {
            Iterator<Map.Entry<Entity, Passenger>> iterator = passengers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Entity, Passenger> passenger = iterator.next();
                Entity entity = passenger.getKey();
                boolean ignored = !entity.isEntityAlive() || entity.noClip
                        || entity instanceof EntityPlayer player && (player.isSneaking() || player.capabilities.isFlying);
                Passenger info = passenger.getValue();
                TileEntity contactTile = world.getTileEntity(info.contactPos());
                if (ignored || !(contactTile instanceof BeltTileEntity contact)
                        || !contact.sameChain(this)
                        || info.ticksSinceCollision() > (contact.step.getY() == 0 ? 1 : 3)) {
                    iterator.remove();
                    continue;
                }
                passenger.setValue(new Passenger(info.contactPos(), info.ticksSinceCollision() + 1));
                touch(entity, motion, contact);
            }
        }
    }
    private boolean isOnBelt(Entity entity, BeltBlock belt, IBlockState state) {
        if (!entity.isEntityAlive() || entity.noClip) return false;
        if (entity instanceof EntityPlayer player && (player.isSneaking() || player.capabilities.isFlying)) return false;
        double x = entity.posX - pos.getX(), z = entity.posZ - pos.getZ();
        if (x < 0 || x >= 1 || z < 0 || z >= 1) return false;
        if (step.getX() != 0 && (z < 1 / 16D || z > 15 / 16D)) return false;
        if (step.getZ() != 0 && (x < 1 / 16D || x > 15 / 16D)) return false;
        AxisAlignedBB bounds = entity.getEntityBoundingBox();
        double highest = highestSurface(belt, state, pos, bounds);
        for (BlockPos neighbor : beltNeighbors()) {
            if (bounds.maxX <= neighbor.getX() || bounds.minX >= neighbor.getX() + 1
                    || bounds.maxZ <= neighbor.getZ() || bounds.minZ >= neighbor.getZ() + 1
                    || !world.isBlockLoaded(neighbor)) continue;
            if (!(world.getTileEntity(neighbor) instanceof BeltTileEntity other) || !connectedTo(other)) continue;
            IBlockState otherState = world.getBlockState(neighbor);
            if (otherState.getBlock() instanceof BeltBlock otherBlock)
                highest = Math.max(highest, highestSurface(otherBlock, otherState, neighbor, bounds));
        }
        return highest != Double.NEGATIVE_INFINITY && bounds.minY >= highest - 0.12D
                && bounds.minY <= highest + 0.075D;
    }
    private double highestSurface(BeltBlock belt, IBlockState state, BlockPos beltPos, AxisAlignedBB bounds) {
        double highest = Double.NEGATIVE_INFINITY;
        for (AxisAlignedBB shape : belt.shapes(state, world, beltPos)) {
            double top = Math.min(19 / 16D, shape.maxY);
            if (top <= Math.max(0, shape.minY)) continue;
            if (bounds.maxX <= beltPos.getX() + shape.minX || bounds.minX >= beltPos.getX() + shape.maxX
                    || bounds.maxZ <= beltPos.getZ() + shape.minZ || bounds.minZ >= beltPos.getZ() + shape.maxZ) continue;
            highest = Math.max(highest, beltPos.getY() + top);
        }
        return highest;
    }
    private void touch(Entity entity, float motion) {
        touch(entity, motion, this);
    }

    private void touch(Entity entity, float motion, BeltTileEntity segment) {
        if (entity instanceof EntityItem item) {
            ItemStack rest = insertAt(item.getItem(), false, 0.5F);
            if (rest.isEmpty()) item.setDead();
            else item.setItem(rest);
            return;
        }
        double movement = motion;
        double along = segment.step.getX() != 0
                ? (entity.posX - segment.pos.getX() - 0.5D) * segment.step.getX()
                : (entity.posZ - segment.pos.getZ() - 0.5D) * segment.step.getZ();
        boolean onSlope = segment.step.getY() != 0 && (segment.index != 0 || along >= 0)
                && (segment.index != segment.length - 1 || along <= 0);
        double vertical = onSlope && movement != 0
                ? Math.copySign(Math.abs(movement), movement * segment.step.getY()) : 0;
        if (onSlope && vertical > 0 && world.getBlockState(segment.pos).getBlock() instanceof BeltBlock belt) {
            AxisAlignedBB nextBounds = entity.getEntityBoundingBox().offset(
                    segment.step.getX() * movement, 0, segment.step.getZ() * movement);
            double nextSurface = segment.highestSurface(belt, world.getBlockState(segment.pos), segment.pos, nextBounds);
            for (BlockPos neighbor : segment.beltNeighbors()) {
                if (!(world.getTileEntity(neighbor) instanceof BeltTileEntity other) || !segment.connectedTo(other)) continue;
                IBlockState otherState = world.getBlockState(neighbor);
                if (otherState.getBlock() instanceof BeltBlock otherBlock)
                    nextSurface = Math.max(nextSurface, segment.highestSurface(otherBlock, otherState, neighbor, nextBounds));
            }
            if (nextSurface != Double.NEGATIVE_INFINITY)
                vertical = Math.max(vertical, nextSurface - entity.getEntityBoundingBox().minY + 0.001D);
        }
        float oldStepHeight = entity.stepHeight;
        if (vertical > 0 && entity.stepHeight < 1.0F) entity.stepHeight = 1.0F;
        entity.move(MoverType.SELF, segment.step.getX() * movement, vertical, segment.step.getZ() * movement);
        entity.stepHeight = oldStepHeight;
        entity.onGround = true;
        entity.fallDistance = 0;
        if (entity instanceof EntityPlayerMP) entity.velocityChanged = true;
    }
    private ItemStack insertAt(ItemStack stack, boolean simulate, float start) {
        // A stopped belt still accepts an item and keeps it in place until kinetic motion resumes.
        if (stack.isEmpty() || !carried.isEmpty() || !processingOutput.isEmpty() || !processingOutputs.isEmpty()
                || length <= 0 || step.equals(BlockPos.ORIGIN)) return stack;
        int count = Math.min(stack.getCount(), stack.getMaxStackSize());
        ItemStack rest = stack.copy();
        rest.shrink(count);
        if (!simulate) {
            carried = stack.copy();
            carried.setCount(count);
            boolean upright = isUprightItem(carried);
            carriedAngle = upright ? 180.0F : world.rand.nextInt(360);
            carriedSideOffset = (carriedAngle - 180.0F) / 1080.0F;
            fanProgress.reset();
            tunnelSideMask = 0;
            routedDirection = 0;
            position = previousPosition = start;
            if (world != null && !world.isRemote && world.getTileEntity(pos.up()) instanceof BeltTunnelTileEntity tunnel)
                tunnel.onTransportedItemsReceived(count);
            changed();
        }
        return rest;
    }
    private static boolean isUprightItem(ItemStack stack) {
        if (FluidUtil.getFluidHandler(stack) != null) return true;
        return stack.getItem() == Items.GLASS_BOTTLE || stack.getItem() == Items.POTIONITEM
                || stack.getItem() == Items.CAKE
                || stack.getItem() == Items.BOWL || stack.getItem() == Items.MUSHROOM_STEW
                || stack.getItem() == Items.RABBIT_STEW || stack.getItem() == Items.BEETROOT_SOUP;
    }
    @Override public int getSlots() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? carried : ItemStack.EMPTY; }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return slot == 0 ? insertAt(stack, simulate, 0.5F) : stack;
    }
    @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
        if (slot != 0 || count <= 0 || carried.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = carried.copy();
        result.setCount(Math.min(count, carried.getCount()));
        if (!simulate) {
            carried.shrink(result.getCount());
            if (carried.isEmpty()) { fanProgress.reset(); carriedAngle = 0; carriedSideOffset = 0; routedDirection = 0; }
            changed();
        }
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
        if (!tutorialItems.isEmpty()) {
            NBTTagList items = new NBTTagList();
            tutorialItems.forEach((id, item) -> {
                NBTTagCompound data = new NBTTagCompound();
                data.setInteger("Id", id); data.setTag("Item", item.stack.writeToNBT(new NBTTagCompound()));
                data.setFloat("Previous", item.previous); data.setFloat("Position", item.position);
                data.setFloat("Angle", item.angle); data.setFloat("Offset", item.offset); items.appendTag(data);
            });
            tag.setTag("TutorialItems", items);
        }
        tag.setInteger("StepX", step.getX()); tag.setInteger("StepY", step.getY()); tag.setInteger("StepZ", step.getZ());
        tag.setInteger("Index", index); tag.setInteger("Length", length); tag.setBoolean("Pulley", pulley);
        if (color != null) tag.setByte("Color", (byte) color.getMetadata());
        tag.setByte("Casing", (byte) casing.ordinal());
        if (!carried.isEmpty()) tag.setTag("Carried", carried.writeToNBT(new NBTTagCompound()));
        tag.setFloat("CarriedAngle", carriedAngle);
        tag.setFloat("CarriedSideOffset", carriedSideOffset);
        tag.setFloat("ItemPosition", position);
        tag.setInteger("TunnelSideMask", tunnelSideMask);
        tag.setInteger("TunnelRoutedDirection", routedDirection);
        tag.setBoolean("ProcessingHeld", processingHeld);
        fanProgress.write(tag);
        NBTTagList outputs = new NBTTagList();
        for (ItemStack output : processingOutputs) outputs.appendTag(output.writeToNBT(new NBTTagCompound()));
        tag.setTag("ProcessingOutputs", outputs);
        if (!processingOutput.isEmpty()) tag.setTag("ProcessingOutput", processingOutput.writeToNBT(new NBTTagCompound()));
        tag.setFloat("ProcessingOutputPosition", outputPosition);
        tag.setFloat("ProcessingOutputAngle", outputAngle);
        tag.setFloat("ProcessingOutputSideOffset", outputSideOffset);
    }
    private void load(NBTTagCompound tag) {
        tutorialItems.clear();
        NBTTagList tutorial = tag.getTagList("TutorialItems", 10);
        for (int i = 0; i < tutorial.tagCount(); i++) {
            NBTTagCompound data = tutorial.getCompoundTagAt(i);
            tutorialItems.put(data.getInteger("Id"), new TutorialItem(new ItemStack(data.getCompoundTag("Item")),
                    data.getFloat("Previous"), data.getFloat("Position"), data.getFloat("Angle"), data.getFloat("Offset")));
        }
        step = new BlockPos(tag.getInteger("StepX"), tag.getInteger("StepY"), tag.getInteger("StepZ"));
        index = tag.getInteger("Index"); length = tag.getInteger("Length"); pulley = tag.getBoolean("Pulley");
        color = tag.hasKey("Color") ? EnumDyeColor.byMetadata(tag.getByte("Color")) : null;
        int casingId = tag.getByte("Casing");
        casing = casingId >= 0 && casingId < CasingType.values().length ? CasingType.values()[casingId] : CasingType.NONE;
        carried = tag.hasKey("Carried", 10) ? new ItemStack(tag.getCompoundTag("Carried")) : ItemStack.EMPTY;
        carriedAngle = tag.getFloat("CarriedAngle");
        carriedSideOffset = tag.getFloat("CarriedSideOffset");
        position = previousPosition = tag.hasKey("ItemPosition") ? tag.getFloat("ItemPosition") : 0.5F;
        tunnelSideMask = tag.getInteger("TunnelSideMask");
        routedDirection = Math.max(-1, Math.min(1, tag.getInteger("TunnelRoutedDirection")));
        processingHeld = tag.getBoolean("ProcessingHeld");
        fanProgress.read(tag);
        processingOutputs.clear();
        NBTTagList outputs = tag.getTagList("ProcessingOutputs", 10);
        for (int i = 0; i < outputs.tagCount(); i++) {
            ItemStack output = new ItemStack(outputs.getCompoundTagAt(i));
            if (!output.isEmpty()) processingOutputs.add(output);
        }
        processingOutput = tag.hasKey("ProcessingOutput", 10)
                ? new ItemStack(tag.getCompoundTag("ProcessingOutput")) : ItemStack.EMPTY;
        outputPosition = previousOutputPosition = tag.hasKey("ProcessingOutputPosition")
                ? tag.getFloat("ProcessingOutputPosition") : 0.5F;
        outputAngle = tag.getFloat("ProcessingOutputAngle");
        outputSideOffset = tag.getFloat("ProcessingOutputSideOffset");
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); save(tag); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); load(tag); }
    @Override public NBTTagCompound getUpdateTag() { NBTTagCompound tag = super.getUpdateTag(); save(tag); return tag; }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        EnumFacing previousMovement = movementFacing();
        BlockPos previousStep = step;
        super.handleUpdateTag(tag);
        load(tag);
        if (world != null && world.isRemote) {
            world.markBlockRangeForRenderUpdate(pos, pos);
            if (previousMovement != movementFacing() || !previousStep.equals(step)) {
                world.markBlockRangeForRenderUpdate(pos.up(), pos.up());
            }
        }
    }
}
