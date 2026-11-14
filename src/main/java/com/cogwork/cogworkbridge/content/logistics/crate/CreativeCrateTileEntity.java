package com.cogwork.cogworkbridge.content.logistics.crate;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

public final class CreativeCrateTileEntity extends CogworkTileEntity {
    private ItemStack selection = ItemStack.EMPTY;
    private final IItemHandler handler = new BottomlessHandler();

    public ItemStack getSelection() { return selection.copy(); }
    public void setSelection(ItemStack stack) {
        selection = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!selection.isEmpty()) selection.setCount(1);
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler) : super.getCapability(capability, side);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!selection.isEmpty()) tag.setTag("Selection", selection.writeToNBT(new NBTTagCompound()));
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        selection = tag.hasKey("Selection", 10) ? new ItemStack(tag.getCompoundTag("Selection")) : ItemStack.EMPTY;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }

    private final class BottomlessHandler implements IItemHandler {
        @Override public int getSlots() { return 2; }
        @Override public ItemStack getStackInSlot(int slot) {
            if (slot != 0 || selection.isEmpty()) return ItemStack.EMPTY;
            ItemStack copy = selection.copy();
            copy.setCount(copy.getMaxStackSize());
            return copy;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot >= 0 && slot < 2 ? ItemStack.EMPTY : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || selection.isEmpty()) return ItemStack.EMPTY;
            ItemStack copy = selection.copy();
            copy.setCount(Math.min(amount, copy.getMaxStackSize()));
            return copy;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
    }
}
