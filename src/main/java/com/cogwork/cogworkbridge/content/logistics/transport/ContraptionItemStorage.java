package com.cogwork.cogworkbridge.content.logistics.transport;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraption;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import com.cogwork.cogworkbridge.content.logistics.vault.ItemVaultTileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Exposes inventories in a moving structure while retaining each tile as owner. */
public final class ContraptionItemStorage implements IItemHandler {
    private final BearingContraptionEntity entity;
    private final BlockPos excludedPos;
    private long cacheTick = Long.MIN_VALUE;
    private int cachedBlockCount = -1;
    private World cachedWorld;
    private List<Slot> cachedSlots = java.util.Collections.emptyList();

    public ContraptionItemStorage(BearingContraptionEntity entity) { this(entity, null); }
    public ContraptionItemStorage(BearingContraptionEntity entity, BlockPos excludedPos) {
        this.entity = entity;
        this.excludedPos = excludedPos;
    }

    private List<Slot> slots() {
        List<Slot> result = new ArrayList<>();
        if (!entity.isEntityAlive() || entity.getContraption() == null || !entity.isRailwayPortalDataReady()) return result;
        World local = entity.getContraptionLocalWorld();
        if (local == null) return result;
        int blockCount = entity.getContraption().blocks().size();
        long now = local.getTotalWorldTime();
        if (cachedWorld == local && cacheTick == now && cachedBlockCount == blockCount) return cachedSlots;
        Map<IItemHandler, Boolean> seen = new IdentityHashMap<>();
        for (BearingContraption.Snapshot snapshot : entity.getContraption().blocks().values()) {
            BlockPos pos = entity.localPos(snapshot);
            if (pos.equals(excludedPos)) continue;
            TileEntity tile = local.getTileEntity(pos);
            if (tile == null || tile.isInvalid() || tile instanceof PortableItemStorage) continue;
            if (tile instanceof ItemVaultTileEntity vault && !vault.isController()) continue;
            IItemHandler handler = tile instanceof TileEntityChest chest ? chest.getSingleChestHandler()
                    : tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (handler == null || seen.put(handler, Boolean.TRUE) != null) continue;
            for (int index = 0; index < handler.getSlots(); index++) {
                BlockPos owner = tile instanceof ItemVaultTileEntity vault ? vault.positionForSlot(index) : pos;
                if (owner != null) result.add(new Slot(owner, handler, index));
            }
        }
        for (boolean secondary : new boolean[]{false, true}) {
            IItemHandler cartInventory = entity.getMountedItemHandler(secondary);
            if (cartInventory != null && seen.put(cartInventory, Boolean.TRUE) == null)
                for (int index = 0; index < cartInventory.getSlots(); index++)
                    result.add(new Slot(null, cartInventory, index));
        }
        cacheTick = now; cachedWorld = local; cachedBlockCount = blockCount; cachedSlots = result;
        return result;
    }

    private Slot slot(int index) {
        List<Slot> slots = slots();
        return index < 0 || index >= slots.size() ? null : slots.get(index);
    }

    @Override public int getSlots() { return slots().size(); }
    @Override public ItemStack getStackInSlot(int index) {
        Slot slot = slot(index);
        return slot == null ? ItemStack.EMPTY : slot.handler().getStackInSlot(slot.index());
    }
    @Override public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
        Slot slot = slot(index);
        if (slot == null || stack.isEmpty()) return stack;
        ItemStack left = slot.handler().insertItem(slot.index(), stack, simulate);
        if (!simulate && left.getCount() != stack.getCount() && slot.pos() != null)
            entity.persistAndSyncInteractionBlock(slot.pos());
        return left;
    }
    @Override public ItemStack extractItem(int index, int amount, boolean simulate) {
        Slot slot = slot(index);
        if (slot == null || amount <= 0) return ItemStack.EMPTY;
        ItemStack extracted = slot.handler().extractItem(slot.index(), amount, simulate);
        if (!simulate && !extracted.isEmpty() && slot.pos() != null)
            entity.persistAndSyncInteractionBlock(slot.pos());
        return extracted;
    }
    @Override public int getSlotLimit(int index) {
        Slot slot = slot(index);
        return slot == null ? 0 : slot.handler().getSlotLimit(slot.index());
    }

    private record Slot(BlockPos pos, IItemHandler handler, int index) { }
}
