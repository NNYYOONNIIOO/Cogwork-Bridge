package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public final class SmartFluidPipeTileEntity extends FluidPipeTileEntity {
    @Nullable private FluidStack filter;
    private ItemStack filterItem = ItemStack.EMPTY;

    @Nullable
    public FluidStack getFilter() { return filter == null ? null : filter.copy(); }

    public ItemStack getFilterItem() { return filterItem.copy(); }

    public boolean allows(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return false;
        if (filterItem.getItem() instanceof ListFilterItem) return ListFilterItem.matchesFluid(filterItem, fluid);
        if (!filterItem.isEmpty()) return filter != null && filter.isFluidEqual(fluid);
        // Older saves may contain a fluid filter before the displayed item was stored.
        return filter == null || filter.isFluidEqual(fluid);
    }

    public void setFilterItem(ItemStack item) {
        filterItem = item.copy();
        if (!filterItem.isEmpty()) filterItem.setCount(1);
        FluidStack fluid = FluidContainerHelper.contents(filterItem);
        filter = fluid == null || fluid.amount <= 0 ? null : fluid.copy();
        if (filter != null) filter.amount = 1;
        markDirty();
        if (world != null) {
            world.notifyBlockUpdate(pos, getBlockType().getDefaultState(), world.getBlockState(pos), 3);
            FluidTransferManager.markDirty(world);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (filter != null) tag.setTag("FluidFilter", filter.writeToNBT(new NBTTagCompound()));
        if (!filterItem.isEmpty()) tag.setTag("FluidFilterItem", filterItem.writeToNBT(new NBTTagCompound()));
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        filter = tag.hasKey("FluidFilter", 10) ? FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("FluidFilter")) : null;
        filterItem = tag.hasKey("FluidFilterItem", 10) ? new ItemStack(tag.getCompoundTag("FluidFilterItem")) : ItemStack.EMPTY;
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
        if (world != null && world.isRemote) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { handleUpdateTag(packet.getNbtCompound()); }
}
