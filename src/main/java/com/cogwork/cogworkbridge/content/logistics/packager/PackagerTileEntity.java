package com.cogwork.cogworkbridge.content.logistics.packager;

import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

/** Redstone packing and capability-based unpacking, preserving contents on rejection. */
public class PackagerTileEntity extends CogworkTileEntity implements ITickable, IItemHandler {
    protected ItemStack box = ItemStack.EMPTY;
    protected ItemStack incomingVisual = ItemStack.EMPTY;
    protected final Deque<ItemStack> queued = new ArrayDeque<>();
    protected int animation, cooldown;
    protected boolean animationInward;
    private boolean pendingUnpack;
    private boolean powered;
    public ItemStack getBox() { return box.copy(); }
    public boolean canInteract() { return animation == 0; }
    public int getAnimation() { return animation; }
    public boolean isPowered() { return powered; }
    public boolean isAnimationInward() { return animationInward; }
    public ItemStack getRenderedBox() {
        if (animation <= 0) return box.copy();
        return animationInward ? (animation > 10 ? incomingVisual.copy() : ItemStack.EMPTY)
                : (animation < 10 ? box.copy() : ItemStack.EMPTY);
    }
    public float getTrayOffset(float partial) {
        if (animation <= 0) return 0;
        float cycle = animationInward ? animation - partial : animation - 5 - partial;
        float progress = Math.max(-1F, Math.min(1F, cycle / 15F * 2F - 1F));
        progress = 1F - progress * progress;
        return progress * progress;
    }
    protected void startAnimation(boolean inward, ItemStack visual) {
        animationInward = inward; incomingVisual = inward ? visual.copy() : ItemStack.EMPTY;
        animation = 20; changed();
    }
    public ItemStack takeBox() { ItemStack old = box; box = ItemStack.EMPTY; pendingUnpack = false; changed(); return old; }
    public void updateRedstone() {
        if (world == null || world.isRemote) return;
        boolean now = world.isBlockPowered(pos);
        if (now && !powered) { powered = true; pack(); if (cooldown <= 0) cooldown = 40; }
        else powered = now;
        changed();
    }
    protected IItemHandler target() {
        if (!(world.getBlockState(pos).getBlock() instanceof PackagerBlock)) return null;
        EnumFacing facing = world.getBlockState(pos).getValue(PackagerBlock.FACING);
        IItemHandler result = ItemTransfer.at(world, pos.offset(facing.getOpposite()), facing);
        return result instanceof PackagerTileEntity ? null : result;
    }
    public BlockPos inventoryPosition() {
        return pos.offset(world.getBlockState(pos).getValue(PackagerBlock.FACING).getOpposite());
    }
    @Nullable public IItemHandler targetInventory() { return target(); }
    public int pendingPackages() { return queued.size(); }
    public boolean linked() {
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos other = pos.offset(side);
            if (world.getTileEntity(other) instanceof StockLinkTileEntity
                    && world.getBlockState(other).getBlock() instanceof StockLinkBlock
                    && world.getBlockState(other).getValue(StockLinkBlock.FACING) == side) return true;
        }
        return false;
    }
    protected String signAddress() {
        for (EnumFacing facing : EnumFacing.values()) {
            TileEntity tile = world.getTileEntity(pos.offset(facing));
            if (!(tile instanceof TileEntitySign sign)) continue;
            StringBuilder result = new StringBuilder();
            for (var line : sign.signText) {
                String text = line.getUnformattedText().trim();
                if (!text.isEmpty()) { if (result.length() > 0) result.append(' '); result.append(text); }
            }
            if (result.length() > 0) return result.toString();
        }
        return "";
    }
    protected boolean acceptsAddress(ItemStack parcel) {
        if (!PackageItem.isPackage(parcel) || world == null) return false;
        String address = signAddress();
        return address.isEmpty() || PackageItem.matchAddress(PackageItem.address(parcel), address);
    }
    public void pack() {
        if (world == null || world.isRemote || linked() || !box.isEmpty() || animation > 0 || cooldown > 0) return;
        IItemHandler source = target(); if (source == null) return;
        ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
        ItemStack existingPackage = ItemStack.EMPTY;
        boolean any = false;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack offered = source.extractItem(slot, 64, true);
            if (offered.isEmpty()) continue;
            if (PackageItem.isPackage(offered)) {
                if (any) continue;
                existingPackage = source.extractItem(slot, 1, false);
                break;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(contents, offered, true);
            int accepted = offered.getCount() - remainder.getCount();
            if (accepted <= 0) continue;
            ItemStack extracted = source.extractItem(slot, accepted, false);
            if (extracted.isEmpty()) continue;
            ItemStack left = ItemHandlerHelper.insertItemStacked(contents, extracted, false);
            if (!left.isEmpty()) { left = source.insertItem(slot, left, false); if (!left.isEmpty()) ItemTransfer.drop(world, pos, left); }
            any = true;
        }
        if (existingPackage.isEmpty() && !any) return;
        box = existingPackage.isEmpty() ? PackageItem.create(contents, signAddress(), world.rand) : existingPackage.copy();
        if (!existingPackage.isEmpty()) PackageItem.setAddress(box, signAddress());
        box.setCount(1); startAnimation(false, ItemStack.EMPTY);
        com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "packager");
    }
    public int request(ItemStack requested, int count, String address) {
        return request(requested, count, address, null);
    }
    public int request(ItemStack requested, int count, String address, @Nullable java.util.List<ItemStack> created) {
        if (world == null || world.isRemote || requested.isEmpty() || count <= 0 || queued.size() >= 50) return 0;
        IItemHandler source = target();
        if (source == null) return 0;
        ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
        int extractedTotal = 0;
        for (int slot = 0; slot < source.getSlots() && extractedTotal < count; slot++) {
            ItemStack offered = source.extractItem(slot, count - extractedTotal, true);
            if (offered.isEmpty() || !ItemStack.areItemsEqual(offered, requested)
                    || !ItemStack.areItemStackTagsEqual(offered, requested)) continue;
            ItemStack simulatedLeft = ItemHandlerHelper.insertItemStacked(contents, offered, true);
            int accepted = offered.getCount() - simulatedLeft.getCount();
            if (accepted <= 0) continue;
            ItemStack extracted = source.extractItem(slot, accepted, false);
            if (extracted.isEmpty()) continue;
            if (!ItemStack.areItemsEqual(extracted, requested) || !ItemStack.areItemStackTagsEqual(extracted, requested)) {
                ItemStack left = source.insertItem(slot, extracted, false);
                if (!left.isEmpty()) ItemTransfer.drop(world, pos, left);
                continue;
            }
            int taken = extracted.getCount();
            ItemStack left = ItemHandlerHelper.insertItemStacked(contents, extracted, false);
            extractedTotal += taken - left.getCount();
            if (!left.isEmpty()) {
                left = source.insertItem(slot, left, false);
                if (!left.isEmpty()) ItemTransfer.drop(world, pos, left);
            }
        }
        if (extractedTotal > 0) {
            ItemStack packageStack = PackageItem.create(contents, address, world.rand);
            queued.add(packageStack);
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "packager");
            if (created != null) created.add(packageStack);
            changed();
        }
        return extractedTotal;
    }
    /** Return staged parcels when a complete multi-item order cannot be fulfilled. */
    public void rollbackRequests(java.util.List<ItemStack> staged) {
        IItemHandler inventory = target();
        for (ItemStack parcel : staged) {
            boolean removed = false;
            for (var iterator = queued.iterator(); iterator.hasNext();) {
                if (iterator.next() == parcel) { iterator.remove(); removed = true; break; }
            }
            if (!removed) continue;
            ItemStackHandler contents = PackageItem.contents(parcel);
            for (int i = 0; i < contents.getSlots(); i++) {
                ItemStack stack = contents.getStackInSlot(i).copy();
                if (stack.isEmpty()) continue;
                ItemStack left = inventory == null ? stack : ItemHandlerHelper.insertItemStacked(inventory, stack, false);
                if (!left.isEmpty()) ItemTransfer.drop(world, pos, left);
            }
        }
        changed();
    }
    private boolean canUnpack(ItemStack parcel, IItemHandler target) {
        if (!PackageItem.isPackage(parcel) || target == null || animation > 0) return false;
        ItemStackHandler contents = PackageItem.contents(parcel);
        ItemStack[] virtual = new ItemStack[target.getSlots()];
        for (int i = 0; i < virtual.length; i++) virtual[i] = target.getStackInSlot(i).copy();
        for (int j = 0; j < contents.getSlots(); j++) {
            ItemStack remaining = contents.getStackInSlot(j).copy();
            for (int i = 0; i < virtual.length && !remaining.isEmpty(); i++) {
                ItemStack occupied = virtual[i];
                if (!occupied.isEmpty() && (!ItemStack.areItemsEqual(occupied, remaining) || !ItemStack.areItemStackTagsEqual(occupied, remaining))) continue;
                ItemStack simulated = target.insertItem(i, remaining.copy(), true);
                int permitted = remaining.getCount() - simulated.getCount();
                int space = Math.min(target.getSlotLimit(i), remaining.getMaxStackSize()) - occupied.getCount();
                int amount = Math.min(permitted, Math.max(0, space));
                if (amount <= 0) continue;
                if (occupied.isEmpty()) { occupied = remaining.copy(); occupied.setCount(amount); virtual[i] = occupied; } else occupied.grow(amount);
                remaining.shrink(amount);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }
    public ItemStack acceptPackage(ItemStack parcel, boolean simulate) {
        // A remainder can only represent one particular parcel.  Reject malformed
        // stacked parcels instead of returning one modified tag for several boxes.
        if (!box.isEmpty() || !queued.isEmpty() || parcel.isEmpty() || parcel.getCount() != 1
                || !acceptsAddress(parcel) || animation > 0) return parcel;
        java.util.List<ItemStack> pattern = StockOrder.readPattern(PackageItem.orderContext(parcel).getTagList("Craft", 10));
        if (!pattern.isEmpty() && world.getTileEntity(inventoryPosition()) instanceof com.cogwork.cogworkbridge.content.processing.crafter.MechanicalCrafterTileEntity crafter) {
            if (animation > 0 || !crafter.acceptRecipePackage(pattern, PackageItem.contents(parcel), simulate)) return parcel;
            if (!simulate) startAnimation(true, parcel);
            return ItemStack.EMPTY;
        }
        IItemHandler inventory = target(); if (inventory == null) return parcel;
        if (!simulate) {
            box = parcel.copy();
            pendingUnpack = true;
            if (canUnpack(box, inventory)) unpackPending(inventory);
            startAnimation(true, parcel);
        }
        ItemStack remainder = parcel.copy(); remainder.shrink(1); return remainder;
    }
    private void unpackPending(IItemHandler inventory) {
        ItemStackHandler contents = PackageItem.contents(box);
        boolean leftovers = false;
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(inventory, contents.getStackInSlot(i).copy(), false);
            contents.setStackInSlot(i, remaining);
            leftovers |= !remaining.isEmpty();
        }
        if (leftovers) PackageItem.setContents(box, contents);
        else { box = ItemStack.EMPTY; pendingUnpack = false; }
        changed();
    }
    @Override public void update() {
        if (world == null) return;
        if (world.isRemote) { if (animation > 0) animation--; return; }
        if (animation > 0 && --animation == 0) changed();
        if (cooldown > 0) cooldown--;
        if (pendingUnpack && box.isEmpty()) pendingUnpack = false;
        if (pendingUnpack && animation == 0 && world.getTotalWorldTime() % 10 == 0) {
            IItemHandler inventory = target();
            if (canUnpack(box, inventory)) unpackPending(inventory);
        }
        if (box.isEmpty() && animation == 0 && !queued.isEmpty()) {
            box = queued.removeFirst(); startAnimation(false, ItemStack.EMPTY);
        }
        if (powered && cooldown == 0 && world.getTotalWorldTime() % 10 == 0) pack();
    }
    protected void changed() {
        markDirty(); if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else { var state = world.getBlockState(pos); world.notifyBlockUpdate(pos, state, state, 2); world.updateComparatorOutputLevel(pos, state.getBlock()); }
    }
    @Override public int getSlots() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { return slot == 0 && !pendingUnpack ? box : ItemStack.EMPTY; }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return slot == 0 ? acceptPackage(stack, simulate) : stack; }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return slot != 0 || amount <= 0 || animation > 0 || pendingUnpack || box.isEmpty() ? ItemStack.EMPTY : simulate ? box.copy() : takeBox(); }
    @Override public int getSlotLimit(int slot) { return 1; }
    @Override public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) { return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side); }
    @Nullable @Override public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) { return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this) : super.getCapability(cap, side); }
    public void dropQueued() { while (!queued.isEmpty()) ItemTransfer.drop(world, pos, queued.removeFirst()); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!box.isEmpty()) tag.setTag("Box", box.writeToNBT(new NBTTagCompound()));
        tag.setBoolean("PendingUnpack", pendingUnpack);
        if (!incomingVisual.isEmpty() && animation > 0) tag.setTag("IncomingVisual", incomingVisual.writeToNBT(new NBTTagCompound()));
        NBTTagList pending = new NBTTagList();
        for (ItemStack stack : queued) pending.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("Queued", pending);
        tag.setInteger("Animation", animation); tag.setBoolean("AnimationInward", animationInward); tag.setInteger("Cooldown", cooldown); tag.setBoolean("Powered", powered);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        box = tag.hasKey("Box", 10) ? new ItemStack(tag.getCompoundTag("Box")) : ItemStack.EMPTY;
        pendingUnpack = !box.isEmpty() && (tag.hasKey("PendingUnpack")
                ? tag.getBoolean("PendingUnpack") : tag.getBoolean("AnimationInward"));
        incomingVisual = tag.hasKey("IncomingVisual", 10) ? new ItemStack(tag.getCompoundTag("IncomingVisual")) : ItemStack.EMPTY;
        queued.clear();
        NBTTagList pending = tag.getTagList("Queued", 10);
        for (int i = 0; i < pending.tagCount(); i++) {
            ItemStack stack = new ItemStack(pending.getCompoundTagAt(i));
            if (!stack.isEmpty()) queued.addLast(stack);
        }
        animation = tag.getInteger("Animation"); animationInward = tag.getBoolean("AnimationInward"); cooldown = tag.getInteger("Cooldown"); powered = tag.getBoolean("Powered");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
