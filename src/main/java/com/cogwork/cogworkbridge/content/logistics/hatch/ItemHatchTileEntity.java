package com.cogwork.cogworkbridge.content.logistics.hatch;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraftforge.items.ItemHandlerHelper;

public final class ItemHatchTileEntity extends CogworkTileEntity implements ITickable {
    private ItemStack filter = ItemStack.EMPTY;
    private int openTicks;
    public ItemStack getFilter() { return filter.copy(); }
    public boolean isOpen() { return openTicks > 0; }
    public boolean accepts(ItemStack stack) { return ListFilterItem.matchesItem(filter, stack); }
    public void open() { openTicks = 10; changed(); }
    public void selectFilter(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        ItemStack previous = filter;
        filter = held.isEmpty() ? ItemStack.EMPTY : held.copy();
        if (!filter.isEmpty()) filter.setCount(1);
        if (!player.capabilities.isCreativeMode && ListFilterItem.isFilterItem(held)) held.shrink(1);
        if (ListFilterItem.isFilterItem(previous) && (!player.capabilities.isCreativeMode || !hasFilter(player, previous)))
            ItemHandlerHelper.giveItemToPlayer(player, previous);
        changed();
    }
    private static boolean hasFilter(EntityPlayer player, ItemStack filter) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack candidate = player.inventory.getStackInSlot(i);
            if (ItemStack.areItemsEqual(candidate, filter) && ItemStack.areItemStackTagsEqual(candidate, filter)) return true;
        }
        return false;
    }
    private void changed() {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    @Override public void update() {
        if (world == null || world.isRemote || openTicks <= 0) return;
        if (--openTicks == 0) changed();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!filter.isEmpty()) tag.setTag("Filter", filter.writeToNBT(new NBTTagCompound()));
        tag.setInteger("OpenTicks", openTicks);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        filter = tag.hasKey("Filter", 10) ? new ItemStack(tag.getCompoundTag("Filter")) : ItemStack.EMPTY;
        openTicks = tag.getInteger("OpenTicks");
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
