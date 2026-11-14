package com.cogwork.cogworkbridge.content.schematics;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import javax.annotation.Nullable;

public abstract class SchematicInventoryTile extends TileEntity {
    protected final ItemStackHandler inventory;
    protected SchematicInventoryTile(int size) {
        inventory = new ItemStackHandler(size) {
            @Override protected void onContentsChanged(int slot) { changed(); }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return SchematicInventoryTile.this.accepts(slot, stack); }
        };
    }
    public ItemStackHandler items() { return inventory; }
    public abstract boolean accepts(int slot, ItemStack stack);
    public void changed() {
        markDirty();
        if (world != null && !world.isRemote && world.isBlockLoaded(pos))
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setTag("Inventory", inventory.serializeNBT()); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); if (tag.hasKey("Inventory", 10)) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
    }
    @Override public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory) : super.getCapability(cap, side);
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
