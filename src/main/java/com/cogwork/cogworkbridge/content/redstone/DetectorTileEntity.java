package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.content.fluids.FluidPipeTileEntity;
import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemHolder;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public final class DetectorTileEntity extends TileEntity implements ITickable {
    private ItemStack filter = ItemStack.EMPTY;
    private int onAbove = 128, offBelow = 64, amount = -1, maximum, remaining, delay;
    private boolean inverted, desired, output, inStacks;
    private int targetType;
    public ItemStack getFilter() { return filter.copy(); }
    public void applyClipboardFilter(ItemStack value) { filter = value.copy(); if (!filter.isEmpty()) filter.setCount(1); changed(false); }
    public int getOnAbove() { return onAbove; }
    public int getOffBelow() { return offBelow; }
    public int getAmount() { return amount; }
    public int getMaximum() { return maximum; }
    public boolean isInverted() { return inverted; }
    public boolean isInStacks() { return inStacks; }
    public int targetType() { return targetType; }
    public boolean isPowered() { return output; }
    public int displayLevel() { return maximum <= 0 || amount <= 0 ? 0 : Math.min(5, 1 + (int) ((long) amount * 4 / maximum)); }
    public void configure(int on, int off, boolean reverse, boolean stacks) {
        onAbove = Math.max(1, Math.min(1000000000, on)); offBelow = Math.max(0, Math.min(onAbove - 1, off)); inverted = reverse; inStacks = stacks;
        if (world != null && !world.isRemote) { scanThreshold(); updateOutput(desired ^ inverted); } changed(false);
    }
    public void setFilter(EntityPlayer player, ItemStack stack) {
        ItemStack previous = filter; filter = stack.copy(); filter.setCount(1);
        if (ListFilterItem.isFilterItem(stack) && !player.capabilities.isCreativeMode) stack.shrink(1);
        if (ListFilterItem.isFilterItem(previous)) ItemHandlerHelper.giveItemToPlayer(player, previous);
        changed(false);
    }
    public void activate(int ticks) { remaining = Math.max(remaining, ticks); updateOutput(true); }
    public static void onFunnelTransfer(net.minecraft.world.World world, net.minecraft.util.math.BlockPos funnel, ItemStack stack) {
        if (world == null || world.isRemote || stack.isEmpty()) return;
        for (EnumFacing direction : EnumFacing.values()) {
            var position = funnel.offset(direction);
            var state = world.getBlockState(position);
            if (!(state.getBlock() instanceof DetectorBlock block) || block.isThreshold()
                    || DetectorBlock.target(state) != direction.getOpposite()) continue;
            if (world.getTileEntity(position) instanceof DetectorTileEntity detector && ListFilterItem.matchesItem(detector.filter, stack))
                detector.activate(4);
        }
    }
    public static void onChainPackage(net.minecraft.world.World world, net.minecraft.util.math.BlockPos target, ItemStack stack) {
        if (world == null || world.isRemote || stack.isEmpty()) return;
        for (EnumFacing direction : EnumFacing.values()) {
            var position = target.offset(direction);
            if (!world.isBlockLoaded(position)) continue;
            var state = world.getBlockState(position);
            if (state.getBlock() instanceof DetectorBlock block && !block.isThreshold()
                    && DetectorBlock.target(state) == direction.getOpposite()
                    && world.getTileEntity(position) instanceof DetectorTileEntity detector
                    && ListFilterItem.matchesItem(detector.filter, stack)) detector.activate(6);
        }
    }
    private void updateOutput(boolean next) { if (output == next) return; output = next; changed(true); }
    @Override public void update() {
        if (world == null || world.isRemote || !(world.getBlockState(pos).getBlock() instanceof DetectorBlock block)) return;
        if (block.isThreshold()) {
            if (delay > 0 && --delay == 0) updateOutput(desired ^ inverted);
            if (world.getTotalWorldTime() % 10 == 0) scanThreshold();
        } else {
            if (remaining > 0) --remaining;
            // Recheck the observed target before dropping the signal: sustained
            // detection extends the pulse instead of emitting an off/on edge.
            scanObserver();
            if (remaining == 0) updateOutput(false);
        }
    }
    private TileEntity observed() { var state = world.getBlockState(pos); return world.getTileEntity(pos.offset(DetectorBlock.target(state))); }
    /** Address of the first matching parcel currently visible to a smart observer. */
    public String observedPackageAddress() {
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof DetectorBlock block) || block.isThreshold()) return "";
        EnumFacing direction = DetectorBlock.target(world.getBlockState(pos));
        TileEntity target = observed();
        if (target instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity chain)
            return packageAddress(chain.matchingPackage(filter));
        if (target instanceof BeltTileEntity belt) return packageAddress(belt.getCarried());
        if (target instanceof TransportedItemHolder holder) return packageAddress(holder.getTransportedItem());
        if (target == null) return "";
        IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite());
        if (handler == null) handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler != null) for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem.isPackage(stack)
                    && ListFilterItem.matchesItem(filter, stack)) return packageAddress(stack);
        }
        return "";
    }
    private String packageAddress(ItemStack stack) {
        return com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem.isPackage(stack)
                && ListFilterItem.matchesItem(filter, stack)
                ? com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem.address(stack) : "";
    }
    private void scanObserver() {
        var state = world.getBlockState(pos); EnumFacing direction = DetectorBlock.target(state); var targetPos = pos.offset(direction);
        var targetState = world.getBlockState(targetPos); Item item = Item.getItemFromBlock(targetState.getBlock());
        if (!filter.isEmpty() && item != net.minecraft.init.Items.AIR) {
            ItemStack observedBlock = new ItemStack(item, 1, targetState.getBlock().getMetaFromState(targetState));
            if (world.getTileEntity(targetPos) instanceof net.minecraft.tileentity.TileEntityShulkerBox shulker) {
                NBTTagCompound contents = shulker.writeToNBT(new NBTTagCompound());
                contents.removeTag("x"); contents.removeTag("y"); contents.removeTag("z"); contents.removeTag("id");
                NBTTagCompound root = new NBTTagCompound(); root.setTag("BlockEntityTag", contents); observedBlock.setTagCompound(root);
            }
            if (ListFilterItem.matchesItem(filter, observedBlock)) { activate(3); return; }
        }
        TileEntity target = observed();
        if (target instanceof com.cogwork.cogworkbridge.content.kinetics.chainconveyor.ChainConveyorTileEntity chain) {
            if (!chain.matchingPackage(filter).isEmpty()) activate(6); return;
        }
        if (target instanceof BeltTileEntity belt) {
            ItemStack carried = belt.getCarried(); if (!carried.isEmpty() && Math.abs(belt.renderPosition(1) - .5F) <= .45F && ListFilterItem.matchesItem(filter, carried)) activate(6);
            return;
        }
        if (target instanceof TransportedItemHolder holder) {
            ItemStack stack = holder.getTransportedItem(); if (!stack.isEmpty() && ListFilterItem.matchesItem(filter, stack)) activate(6);
            return;
        }
        if (target instanceof FluidPipeTileEntity pipe) {
            for (EnumFacing side : EnumFacing.values()) { var flow = pipe.getFlow(side, 1); if (flow != null && flow.inbound() && flow.progress() >= 1 && ListFilterItem.matchesFluid(filter, flow.fluid())) { activate(6); return; } }
            return;
        }
        if (target == null) return;
        IItemHandler inventory = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (inventory == null) inventory = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite());
        if (inventory != null) for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.extractItem(i, 1, true);
            if (!stack.isEmpty() && ListFilterItem.matchesItem(filter, stack)) { activate(6); return; }
        }
        IFluidHandler fluid = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
        if (fluid == null) fluid = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, direction.getOpposite());
        if (fluid != null) for (var property : fluid.getTankProperties()) {
            FluidStack stack = property.getContents(); if (stack != null && stack.amount > 0 && ListFilterItem.matchesFluid(filter, stack)) { activate(6); return; }
        }
    }
    private void scanThreshold() {
        TileEntity target = observed(); int current = 0, max = 0, nextType = 0; boolean found = false;
        if (target instanceof ThresholdObservable observable) {
            found = true; nextType = 3; max = Math.max(0, observable.thresholdMaximum() - observable.thresholdMinimum());
            current = Math.max(0, Math.min(max, observable.thresholdValue() - observable.thresholdMinimum()));
        } else if (target != null) {
            IItemHandler inv = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            EnumFacing side = DetectorBlock.target(world.getBlockState(pos)).getOpposite();
            if (inv == null) inv = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            if (inv != null) { found = true; nextType = 1; for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack stack = inv.getStackInSlot(i); max = (int) Math.min(Integer.MAX_VALUE, (long) max + Math.min(stack.isEmpty() ? 64 : stack.getMaxStackSize(), inv.getSlotLimit(i)));
                if (!stack.isEmpty() && ListFilterItem.matchesItem(filter, stack)) current = (int) Math.min(Integer.MAX_VALUE, (long) current + stack.getCount());
            } }
            IFluidHandler fluid = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
            if (fluid == null) fluid = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
            if (fluid != null) { found = true; if (nextType == 0) nextType = 2; for (var property : fluid.getTankProperties()) {
                max = (int) Math.min(Integer.MAX_VALUE, (long) max + property.getCapacity()); FluidStack stack = property.getContents();
                if (stack != null && ListFilterItem.matchesFluid(filter, stack)) current = (int) Math.min(Integer.MAX_VALUE, (long) current + stack.amount);
            } }
        }
        int oldAmount = amount, oldMax = maximum, oldType = targetType; boolean oldDesired = desired;
        targetType = nextType;
        amount = found ? current : -1; maximum = max;
        if (!found || desired && current <= offBelow) desired = false;
        else if (!desired && current >= onAbove) desired = true;
        if (desired != oldDesired) delay = 2;
        if (oldAmount != amount || oldMax != maximum || oldDesired != desired || oldType != targetType) changed(false);
    }
    private void changed(boolean power) {
        markDirty(); if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else { var state = world.getBlockState(pos); world.notifyBlockUpdate(pos, state, state, 2); }
        if (power) world.notifyNeighborsOfStateChange(pos, world.getBlockState(pos).getBlock(), false);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); if (!filter.isEmpty()) tag.setTag("Filter", filter.writeToNBT(new NBTTagCompound())); tag.setInteger("OnAbove", onAbove); tag.setInteger("OffBelow", offBelow); tag.setInteger("Amount", amount); tag.setInteger("Maximum", maximum); tag.setInteger("Remaining", remaining); tag.setInteger("Delay", delay); tag.setBoolean("Inverted", inverted); tag.setBoolean("Desired", desired); tag.setBoolean("Output", output); tag.setBoolean("InStacks", inStacks); tag.setInteger("TargetType", targetType); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); filter = tag.hasKey("Filter", 10) ? new ItemStack(tag.getCompoundTag("Filter")) : ItemStack.EMPTY;
        onAbove = tag.hasKey("OnAbove") ? tag.getInteger("OnAbove") : 128; offBelow = tag.hasKey("OffBelow") ? tag.getInteger("OffBelow") : 64;
        amount = tag.hasKey("Amount") ? tag.getInteger("Amount") : -1; maximum = tag.getInteger("Maximum"); remaining = tag.getInteger("Remaining"); delay = tag.getInteger("Delay"); inverted = tag.getBoolean("Inverted"); desired = tag.getBoolean("Desired"); output = tag.getBoolean("Output"); inStacks = tag.getBoolean("InStacks"); targetType = Math.max(0, Math.min(3, tag.getInteger("TargetType")));
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); if (world != null) world.markBlockRangeForRenderUpdate(pos, pos); }
}
