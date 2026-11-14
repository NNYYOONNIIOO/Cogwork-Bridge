package com.cogwork.cogworkbridge.content.logistics.chute;

import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.kinetics.fan.AirFlow;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanBlock;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.content.logistics.transport.PortableItemStorage;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/** Stores one transported stack and moves it through connected chutes or inventories. */
public class ChuteTileEntity extends CogworkTileEntity implements ITickable, IItemHandler, PortableItemStorage {
    private ItemStack item = ItemStack.EMPTY;
    private ItemStack filter = ItemStack.EMPTY;
    private float itemPosition = 1;
    private float previousPosition = 1;
    private float motion = -0.2F;
    private float pullForce, pushForce;
    private boolean window;
    private boolean powered;
    private int airflowCooldown;
    private int entitySearchCooldown;
    private float bottomPullDistance;
    private int amount;
    private boolean exact;

    private boolean smart() {
        return world != null && world.getBlockState(pos).getBlock() instanceof ChuteBlock chute && chute.isSmart();
    }
    private EnumFacing facing() {
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof ChuteBlock ? state.getValue(ChuteBlock.FACING) : EnumFacing.DOWN;
    }
    private BlockPos targetPos() {
        EnumFacing facing = facing();
        return facing == EnumFacing.DOWN ? pos.down() : pos.down().offset(facing.getOpposite());
    }
    public ItemStack getItem() { return item.copy(); }
    public ItemStack getFilter() { return filter.copy(); }
    public void setFilter(ItemStack value) { filter = value.copy(); if (!filter.isEmpty()) filter.setCount(1); changed(); }
    public boolean hasWindow() { return window; }
    public boolean isPowered() { return powered; }
    public int getAmount() { return amount; }
    public boolean isExact() { return exact; }
    public float getPullForce() { return pullForce; }
    public float getPushForce() { return pushForce; }
    public float getItemMotion() { return motion; }
    public void setAmount(int value) { amount = Math.max(0, Math.min(64, value)); changed(); }
    public void toggleExact() { exact = !exact; changed(); }
    public float renderPosition(float partialTicks) { return previousPosition + (itemPosition - previousPosition) * partialTicks; }

    public void neighborsChanged() { airflowCooldown = 0; }
    public void toggleWindow() {
        if (smart() || facing() != EnumFacing.DOWN) return;
        window = !window;
        changed();
        world.markBlockRangeForRenderUpdate(pos, pos);
    }
    public void selectFilter(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        ItemStack old = filter;
        filter = held.isEmpty() ? ItemStack.EMPTY : held.copy();
        if (!filter.isEmpty()) filter.setCount(1);
        if (!player.capabilities.isCreativeMode && ListFilterItem.isFilterItem(held)) held.shrink(1);
        if (ListFilterItem.isFilterItem(old) && (!player.capabilities.isCreativeMode || !hasFilter(player, old)))
            ItemHandlerHelper.giveItemToPlayer(player, old);
        changed();
    }
    private static boolean hasFilter(EntityPlayer player, ItemStack filter) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack present = player.inventory.getStackInSlot(slot);
            if (ItemStack.areItemsEqual(present, filter) && ItemStack.areItemStackTagsEqual(present, filter)) return true;
        }
        return false;
    }
    public ItemStack takeAll() {
        ItemStack previous = item;
        item = ItemStack.EMPTY;
        if (!previous.isEmpty()) changed();
        return previous;
    }
    public void acceptEntity(EntityItem entity) {
        if (entity.isDead || !canAccept(entity.getItem())) return;
        ItemStack left = insertItem(0, entity.getItem(), false);
        if (left.isEmpty()) entity.setDead();
        else entity.setItem(left);
    }
    private boolean canAccept(ItemStack stack) {
        return !stack.isEmpty() && item.isEmpty() && (!smart() || !powered && ListFilterItem.matchesItem(filter, stack));
    }
    private void changed() {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    @Override public void update() {
        if (world == null) return;
        previousPosition = itemPosition;
        if (world.isRemote) {
            if (!item.isEmpty()) itemPosition = Math.max(0, Math.min(1, itemPosition + motion));
            return;
        }
        boolean nextPowered = smart() && world.isBlockPowered(pos);
        if (powered != nextPowered) {
            powered = nextPowered;
            changed();
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
        if (--airflowCooldown <= 0) {
            airflowCooldown = 20;
            float nextPull = pull(this, new HashSet<>());
            float nextPush = push(this, new HashSet<>());
            float nextMotion = calculateMotion(nextPull + nextPush);
            updateBottomPullDistance(nextPull - nextPush, nextMotion);
            if (Float.compare(motion, nextMotion) != 0 || Float.compare(pullForce, nextPull) != 0
                    || Float.compare(pushForce, nextPush) != 0) {
                motion = nextMotion; pullForce = nextPull; pushForce = nextPush; changed();
            }
        }
        if (powered) return;
        if (item.isEmpty()) {
            pullFromNeighbor();
            if (item.isEmpty() && motion > 0) pullFromBelow();
            if (item.isEmpty() && motion < 0 && facing() == EnumFacing.DOWN) {
                AxisAlignedBB above = new AxisAlignedBB(pos.up()).grow(-0.12D, -0.25D, -0.12D);
                for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, above)) {
                    acceptEntity(entity);
                    if (!item.isEmpty()) break;
                }
            }
            return;
        }
        itemPosition += motion;
        if (motion <= 0 && itemPosition <= 0) {
            if (!output(false)) itemPosition = 0.5F;
        } else if (motion > 0 && itemPosition >= 1) {
            if (!output(true)) itemPosition = 0.5F;
        }
    }

    private void updateBottomPullDistance(float speed, float itemMotion) {
        bottomPullDistance = 0;
        if (itemMotion <= 0 || speed <= 0 || world.getTileEntity(pos.down()) instanceof ChuteTileEntity) return;
        float maximum = speed >= 128 ? 3 : speed >= 64 ? 2 : speed >= 32 ? 1 : speed / 32;
        bottomPullDistance = AirFlow.unobstructedRange(world, pos, EnumFacing.DOWN, maximum);
    }

    private void pullFromBelow() {
        if (bottomPullDistance <= 0) return;
        for (int distance = 1; distance <= bottomPullDistance + 1; distance++) {
            BlockPos below = pos.down(distance);
            if (!world.isBlockLoaded(below)) break;
            if (world.getTileEntity(below) instanceof BeltTileEntity belt) {
                ItemTransfer.move(belt, this, 64, this::canAccept,
                        remainder -> ChuteBlock.drop(world, pos, remainder));
                if (!item.isEmpty()) return;
                break;
            }
        }
        if (--entitySearchCooldown > 0) return;
        entitySearchCooldown = 5;
        AxisAlignedBB area = new AxisAlignedBB(pos.getX() + 0.05D, pos.getY() - bottomPullDistance - 0.45D,
                pos.getZ() + 0.05D, pos.getX() + 0.95D, pos.getY() + 0.45D, pos.getZ() + 0.95D);
        for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, area)) {
            acceptEntity(entity);
            if (!item.isEmpty()) break;
        }
    }

    private void pullFromNeighbor() {
        EnumFacing input = motion > 0 ? EnumFacing.DOWN : EnumFacing.UP;
        BlockPos otherPos = pos.offset(input);
        if (world.getTileEntity(otherPos) instanceof ChuteTileEntity) return;
        IItemHandler source = ItemTransfer.at(world, otherPos, input.getOpposite());
        if (source == null) return;
        int max = smart() ? (amount == 0 ? 64 : amount) : 16;
        if (smart() && exact && amount > 0)
            ItemTransfer.moveExact(source, this, max, stack -> ListFilterItem.matchesItem(filter, stack),
                    remainder -> ChuteBlock.drop(world, pos, remainder));
        else
            ItemTransfer.move(source, this, max, stack -> !smart() || ListFilterItem.matchesItem(filter, stack),
                    remainder -> ChuteBlock.drop(world, pos, remainder));
    }

    private boolean output(boolean upward) {
        if (upward) {
            ChuteTileEntity best = null;
            for (ChuteTileEntity input : inputs(this)) {
                if (input.motion <= 0 || !input.canAccept(item)) continue;
                if (best == null || input.motion > best.motion) best = input;
            }
            if (best != null) {
                ItemStack before = item;
                ItemStack rest = best.insertItem(0, before, false);
                if (rest.getCount() != before.getCount()) {
                    item = rest;
                    changed();
                    return true;
                }
            }
        }
        BlockPos target = upward ? pos.up() : targetPos();
        TileEntity tile = world.getTileEntity(target);
        if (tile instanceof ChuteTileEntity chute) {
            ItemStack before = item;
            ItemStack rest = chute.insertItem(0, before, false);
            if (rest.getCount() == before.getCount()) return false;
            item = rest;
            changed();
            return true;
        }
        IItemHandler handler = ItemTransfer.at(world, target, upward ? EnumFacing.DOWN : EnumFacing.UP);
        if (handler != null) {
            int count = item.getCount();
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, item, false);
            if (rest.getCount() == count) return false;
            item = rest;
            changed();
            return true;
        }
        if (!upward && facing() != EnumFacing.DOWN) return false;
        IBlockState state = world.getBlockState(target);
        if (state.isSideSolid(world, target, upward ? EnumFacing.DOWN : EnumFacing.UP)) return false;
        ItemStack dropped = takeAll();
        if (world instanceof ContraptionWorld || world instanceof FakeWorld) ChuteBlock.drop(world, pos, dropped);
        else {
            EntityItem entity = new EntityItem(world, pos.getX() + 0.5D,
                    pos.getY() + (upward ? 1.05D : -0.25D), pos.getZ() + 0.5D, dropped);
            entity.motionY = upward ? Math.max(0.1F, motion * 2) : -0.25D;
            entity.setDefaultPickupDelay();
            if (!world.spawnEntity(entity)) { item = dropped; changed(); return false; }
        }
        return true;
    }

    /** Match the upstream pull-from-above and divided push-from-below propagation. */
    private float calculateMotion(float force) {
        float speed = Math.max(-20.0F, Math.min(20.0F, force / 64.0F));
        return (speed + (speed <= 0 ? -4.0F : 0)) / 20.0F;
    }
    private float pull(ChuteTileEntity chute, Set<BlockPos> visited) {
        if (visited.size() >= 128 || !visited.add(chute.pos)) return 0;
        BlockPos above = chute.pos.up();
        if (world.isBlockLoaded(above)) {
            IBlockState state = world.getBlockState(above);
            if (state.getBlock() instanceof EncasedFanBlock && state.getValue(EncasedFanBlock.FACING) == EnumFacing.DOWN
                    && world.getTileEntity(above) instanceof EncasedFanTileEntity fan) return fan.getSpeed();
        }
        float result = 0;
        for (ChuteTileEntity input : inputs(chute)) result += pull(input, visited);
        return result;
    }
    private float push(ChuteTileEntity chute, Set<BlockPos> visited) {
        if (visited.size() >= 128 || !visited.add(chute.pos)) return 0;
        BlockPos below = chute.pos.down();
        if (world.isBlockLoaded(below)) {
            IBlockState state = world.getBlockState(below);
            if (state.getBlock() instanceof EncasedFanBlock && state.getValue(EncasedFanBlock.FACING) == EnumFacing.UP
                    && world.getTileEntity(below) instanceof EncasedFanTileEntity fan) return fan.getSpeed();
        }
        BlockPos next = chute.targetPos();
        if (!world.isBlockLoaded(next) || !(world.getTileEntity(next) instanceof ChuteTileEntity target)) return 0;
        int branches = Math.max(1, inputs(target).size());
        return push(target, visited) / branches;
    }
    private java.util.List<ChuteTileEntity> inputs(ChuteTileEntity target) {
        java.util.List<ChuteTileEntity> result = new java.util.ArrayList<>();
        BlockPos above = target.pos.up();
        if (world.isBlockLoaded(above) && world.getTileEntity(above) instanceof ChuteTileEntity chute
                && chute.targetPos().equals(target.pos)) result.add(chute);
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            BlockPos candidate = above.offset(side);
            if (world.isBlockLoaded(candidate) && world.getTileEntity(candidate) instanceof ChuteTileEntity chute
                    && chute.targetPos().equals(target.pos)) result.add(chute);
        }
        return result;
    }

    @Override public int getSlots() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? item : ItemStack.EMPTY; }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0 || !canAccept(stack)) return stack;
        int count = Math.min(stack.getCount(), stack.getMaxStackSize());
        ItemStack rest = stack.copy();
        rest.shrink(count);
        if (!simulate) {
            item = stack.copy();
            item.setCount(count);
            itemPosition = previousPosition = motion > 0 ? 0 : 1;
            changed();
        }
        return rest;
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != 0 || amount <= 0 || item.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = item.copy();
        result.setCount(Math.min(amount, item.getCount()));
        if (!simulate) { item.shrink(result.getCount()); changed(); }
        return result;
    }
    @Override public int getSlotLimit(int slot) { return 64; }
    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this) : super.getCapability(capability, side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!item.isEmpty()) tag.setTag("Item", item.writeToNBT(new NBTTagCompound()));
        if (!filter.isEmpty()) tag.setTag("Filter", filter.writeToNBT(new NBTTagCompound()));
        tag.setFloat("ItemPosition", itemPosition);
        tag.setFloat("Motion", motion);
        tag.setFloat("PullForce", pullForce); tag.setFloat("PushForce", pushForce);
        tag.setBoolean("Window", window);
        tag.setBoolean("Powered", powered); tag.setInteger("Amount", amount); tag.setBoolean("Exact", exact);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        item = tag.hasKey("Item", 10) ? new ItemStack(tag.getCompoundTag("Item")) : ItemStack.EMPTY;
        filter = tag.hasKey("Filter", 10) ? new ItemStack(tag.getCompoundTag("Filter")) : ItemStack.EMPTY;
        itemPosition = previousPosition = tag.hasKey("ItemPosition") ? tag.getFloat("ItemPosition") : 1;
        motion = tag.hasKey("Motion") ? tag.getFloat("Motion") : -0.2F;
        pullForce = tag.getFloat("PullForce"); pushForce = tag.getFloat("PushForce");
        window = tag.getBoolean("Window");
        powered = tag.getBoolean("Powered"); amount = Math.max(0, Math.min(64, tag.getInteger("Amount"))); exact = tag.getBoolean("Exact");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
