package com.cogwork.cogworkbridge.content.logistics.vault;

import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/** Each member owns its 20 slots; grouping never copies or redistributes inventory. */
public final class ItemVaultTileEntity extends CogworkTileEntity implements ITickable {
    private static final int SLOTS_PER_BLOCK = 20;
    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS_PER_BLOCK) {
        @Override protected void onContentsChanged(int slot) { changed(); }
    };
    private final IItemHandler combined = new CombinedHandler();
    @Nullable private BlockPos controller;
    private int width = 1;
    private int length = 1;
    private boolean dirtyStructure = true;

    public ItemStackHandler ownInventory() { return inventory; }
    public boolean isController() { return controller == null || controller.equals(pos); }
    public BlockPos controllerPos() { return isController() ? pos : controller; }
    public int width() { return width; }
    public int length() { return length; }
    public EnumFacing.Axis axis() {
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof ItemVaultBlock)) return EnumFacing.Axis.Z;
        return world.getBlockState(pos).getValue(ItemVaultBlock.AXIS);
    }
    @Nullable private ItemVaultTileEntity controllerTile() {
        if (isController()) return this;
        if (world == null || !world.isBlockLoaded(controller)) return null;
        TileEntity tile = world.getTileEntity(controller);
        return tile instanceof ItemVaultTileEntity vault && vault.isController() ? vault : null;
    }
    public void requestRebuild() { dirtyStructure = true; }
    void setStructure(BlockPos origin, int width, int length) {
        if (controllerPos().equals(origin) && this.width == width && this.length == length) {
            dirtyStructure = false;
            return;
        }
        controller = origin.equals(pos) ? null : origin.toImmutable();
        this.width = width; this.length = length; dirtyStructure = false; changed();
    }
    @Override public void setPos(BlockPos position) {
        if (controller != null) controller = controller.add(position.subtract(pos));
        super.setPos(position);
    }
    @Override public void update() {
        if (world != null && !world.isRemote && dirtyStructure
                && world.getBlockState(pos).getBlock() instanceof ItemVaultBlock) VaultConnectivity.rebuild(this);
    }
    private void changed() {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            world.updateComparatorOutputLevel(pos, getBlockType());
        }
    }
    public int comparatorLevel() {
        int slots = combined.getSlots();
        if (slots == 0) return 0;
        float fullness = 0;
        int occupied = 0;
        for (int i = 0; i < slots; i++) {
            ItemStack stack = combined.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            occupied++;
            fullness += (float) stack.getCount() / Math.min(stack.getMaxStackSize(), combined.getSlotLimit(i));
        }
        return (int) Math.floor(fullness / slots * 14) + (occupied == 0 ? 0 : 1);
    }
    @Override public boolean hasCapability(Capability<?> cap, @Nullable EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> cap, @Nullable EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                ? CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(combined) : super.getCapability(cap, side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("Inventory", inventory.serializeNBT());
        BlockPos offset = controllerPos().subtract(pos);
        tag.setInteger("ControllerX", offset.getX()); tag.setInteger("ControllerY", offset.getY()); tag.setInteger("ControllerZ", offset.getZ());
        tag.setInteger("Width", width); tag.setInteger("Length", length);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
        BlockPos offset = new BlockPos(tag.getInteger("ControllerX"), tag.getInteger("ControllerY"), tag.getInteger("ControllerZ"));
        controller = offset.equals(BlockPos.ORIGIN) ? null : pos.add(offset);
        width = Math.max(1, Math.min(3, tag.getInteger("Width")));
        length = Math.max(1, Math.min(9, tag.getInteger("Length")));
        dirtyStructure = true;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Nullable public BlockPos positionForSlot(int slot) {
        ItemVaultTileEntity root = controllerTile();
        if (root == null || slot < 0 || slot >= root.width * root.width * root.length * SLOTS_PER_BLOCK) return null;
        int n = slot / SLOTS_PER_BLOCK;
        int along = n / (root.width * root.width), across = n % (root.width * root.width);
        int horizontal = across % root.width, vertical = across / root.width;
        return root.axis() == EnumFacing.Axis.X ? root.pos.add(along, vertical, horizontal) : root.pos.add(horizontal, vertical, along);
    }
    private final class CombinedHandler implements IItemHandler {
        private ItemVaultTileEntity member(int slot) {
            ItemVaultTileEntity root = controllerTile();
            BlockPos memberPos = positionForSlot(slot);
            if (root == null || memberPos == null || !world.isBlockLoaded(memberPos)) return null;
            TileEntity tile = world.getTileEntity(memberPos);
            return tile instanceof ItemVaultTileEntity vault && vault.controllerPos().equals(root.pos) ? vault : null;
        }
        @Override public int getSlots() {
            ItemVaultTileEntity root = controllerTile();
            return root == null ? 0 : root.width * root.width * root.length * SLOTS_PER_BLOCK;
        }
        @Override public ItemStack getStackInSlot(int slot) {
            ItemVaultTileEntity tile = member(slot);
            return tile == null ? ItemStack.EMPTY : tile.inventory.getStackInSlot(slot % SLOTS_PER_BLOCK);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemVaultTileEntity tile = member(slot);
            return tile == null ? stack : tile.inventory.insertItem(slot % SLOTS_PER_BLOCK, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
            ItemVaultTileEntity tile = member(slot);
            return tile == null ? ItemStack.EMPTY : tile.inventory.extractItem(slot % SLOTS_PER_BLOCK, count, simulate);
        }
        @Override public int getSlotLimit(int slot) { return 64; }
    }
}
