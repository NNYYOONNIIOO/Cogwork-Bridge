package com.cogwork.cogworkbridge.content.logistics.depot;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemHolder;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.fan.FanItemProcessing;
import com.cogwork.cogworkbridge.content.kinetics.fan.FanProcessingHolder;
import com.cogwork.cogworkbridge.content.logistics.arm.MechanicalArmItem;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.Collections;

/** The presented stack and processing outputs share the upstream depot's nine item slots. */
public final class DepotTileEntity extends CogworkTileEntity implements TransportedItemHolder, IItemHandler, FanProcessingHolder {
    private ItemStack presented = ItemStack.EMPTY;
    private final FanItemProcessing.Progress fanProgress = new FanItemProcessing.Progress();
    private final ItemStackHandler output = new ItemStackHandler(8) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };

    @Override public ItemStack getTransportedItem() { return presented; }

    @Override
    public void replaceOneWith(ItemStack result) {
        replaceOneWith(result.isEmpty() ? Collections.emptyList() : Collections.singletonList(result));
    }

    @Override
    public void replaceOneWith(java.util.List<ItemStack> results) {
        if (presented.isEmpty()) return;
        presented.shrink(1);
        fanProgress.reset();
        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            if (presented.isEmpty()) presented = result.copy();
            else {
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(output, result.copy(), false);
                if (!remainder.isEmpty()) drop(remainder);
            }
        }
        changed();
    }

    @Override public void applyFanProcessing(EncasedFanTileEntity.Medium medium) {
        if (world == null || world.isRemote || presented.isEmpty()) return;
        if (!fanProgress.tick(medium, presented, world.getTotalWorldTime())) {
            if (world.getTotalWorldTime() % 20L == 0L) markDirty();
            return;
        }
        ItemStackHandler capacity = new ItemStackHandler(output.getSlots() + 1);
        for (int i = 0; i < output.getSlots(); i++) capacity.setStackInSlot(i, output.getStackInSlot(i).copy());
        for (ItemStack possible : FanItemProcessing.possible(medium, presented))
            if (!ItemHandlerHelper.insertItemStacked(capacity, possible, false).isEmpty()) return;
        java.util.List<ItemStack> results = FanItemProcessing.results(medium, presented, world.rand);
        ItemStackHandler planned = new ItemStackHandler(output.getSlots());
        for (int i = 0; i < output.getSlots(); i++) planned.setStackInSlot(i, output.getStackInSlot(i).copy());
        for (int i = 1; i < results.size(); i++)
            if (!ItemHandlerHelper.insertItemStacked(planned, results.get(i), false).isEmpty()) return;
        presented = results.isEmpty() ? ItemStack.EMPTY : results.get(0);
        for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, planned.getStackInSlot(i).copy());
        fanProgress.reset();
        changed();
    }

    public void acceptItemEntity(EntityItem entity) {
        if (world == null || world.isRemote || entity.isDead || entity.getItem().isEmpty()) return;
        ItemStack remainder = insertItem(0, entity.getItem(), false);
        if (remainder.isEmpty()) entity.setDead();
        else entity.setItem(remainder);
    }

    public void interact(EntityPlayer player, EnumHand hand) {
        if (world == null || world.isRemote) return;
        ItemStack incoming = player.getHeldItem(hand).copy();
        if (incoming.getItem() instanceof MechanicalArmItem) incoming = ItemStack.EMPTY;
        if (!incoming.isEmpty()) player.setHeldItem(hand, ItemStack.EMPTY);
        if (!presented.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, presented.copy());
        presented = ItemStack.EMPTY;
        fanProgress.reset();
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack stack = output.extractItem(i, 64, false);
            if (!stack.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, stack);
        }
        if (!incoming.isEmpty()) {
            presented = incoming;
        }
        changed();
    }

    public void dropContents() {
        if (!presented.isEmpty()) drop(presented);
        presented = ItemStack.EMPTY;
        fanProgress.reset();
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack stack = output.extractItem(i, 64, false);
            if (!stack.isEmpty()) drop(stack);
        }
    }

    private void drop(ItemStack stack) {
        if (world == null || world.isRemote || stack.isEmpty()) return;
        if (world instanceof ContraptionWorld local) local.spawnCapturedDrop(pos, stack);
        else if (world instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity)
            entity.spawnDrop(pos, Collections.singletonList(stack));
        else if (!(world instanceof FakeWorld))
            InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    }

    public int comparatorLevel() {
        int count = presented.getCount();
        for (int i = 0; i < output.getSlots(); i++) count += output.getStackInSlot(i).getCount();
        return count == 0 ? 0 : Math.min(15, 1 + (int) (14.0D * count / 64.0D));
    }

    private boolean outputsEmpty() {
        for (int i = 0; i < output.getSlots(); i++) if (!output.getStackInSlot(i).isEmpty()) return false;
        return true;
    }

    private void changed() {
        markDirty();
        if (world == null || world.isRemote) return;
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        world.updateComparatorOutputLevel(pos, getBlockType());
    }

    @Override public int getSlots() { return 9; }
    @Override public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? presented : slot > 0 && slot <= 8 ? output.getStackInSlot(slot - 1) : ItemStack.EMPTY;
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0 || stack.isEmpty() || !presented.isEmpty() || !outputsEmpty()) return stack;
        int accepted = Math.min(stack.getCount(), stack.getMaxStackSize());
        ItemStack remainder = stack.copy();
        remainder.shrink(accepted);
        if (!simulate) {
            presented = stack.copy();
            presented.setCount(accepted);
            fanProgress.reset();
            changed();
        }
        return remainder;
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        if (slot > 0 && slot <= 8) return output.extractItem(slot - 1, amount, simulate);
        if (slot != 0 || presented.isEmpty()) return ItemStack.EMPTY;
        ItemStack extracted = presented.copy();
        extracted.setCount(Math.min(amount, presented.getCount()));
        if (!simulate) {
            presented.shrink(extracted.getCount());
            if (presented.isEmpty()) fanProgress.reset();
            changed();
        }
        return extracted;
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
        if (!presented.isEmpty()) tag.setTag("Presented", presented.writeToNBT(new NBTTagCompound()));
        tag.setTag("Outputs", output.serializeNBT());
        fanProgress.write(tag);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        presented = tag.hasKey("Presented", 10) ? new ItemStack(tag.getCompoundTag("Presented")) : ItemStack.EMPTY;
        output.deserializeNBT(tag.getCompoundTag("Outputs"));
        fanProgress.read(tag);
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
