package com.cogwork.cogworkbridge.content.logistics.tunnel;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import java.util.ArrayList;
import java.util.List;

/** Distributes carried items to the open belt/inventory exits of connected brass tunnels. */
public final class BrassTunnelTileEntity extends BeltTunnelTileEntity {
    public enum Route { PASS, HOLD, TRANSFERRED }
    private final ItemStack[] filters = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private int mode, previous;
    private boolean syncReady, syncReleased;
    private record Exit(BrassTunnelTileEntity tunnel, EnumFacing side, IItemHandler handler, boolean forward) { }
    public ItemStack[] filters() { return filters.clone(); }
    public void setFilter(EnumFacing side, ItemStack value) {
        filters[side.getIndex()] = value.copy();
        if (!filters[side.getIndex()].isEmpty()) filters[side.getIndex()].setCount(1);
        changed();
    }
    public void selectFilter(EntityPlayer player, EnumHand hand, EnumFacing side) {
        ItemStack held = player.getHeldItem(hand), old = filters[side.getIndex()];
        filters[side.getIndex()] = held.isEmpty() ? ItemStack.EMPTY : held.copy();
        if (!filters[side.getIndex()].isEmpty()) filters[side.getIndex()].setCount(1);
        if (!player.capabilities.isCreativeMode && ListFilterItem.isFilterItem(held)) held.shrink(1);
        if (ListFilterItem.isFilterItem(old)) ItemHandlerHelper.giveItemToPlayer(player, old);
        changed();
    }
    public int getMode() { return mode; }
    public void setMode(int value) {
        int next = Math.max(0, Math.min(6, value));
        if (mode == next) return;
        mode = next; syncReady = syncReleased = false; changed();
        if (world != null && world.getTileEntity(pos.down()) instanceof BeltTileEntity belt) {
            EnumFacing across = belt.step().getX() != 0 ? EnumFacing.NORTH : EnumFacing.EAST;
            for (EnumFacing direction : new EnumFacing[]{across, across.getOpposite()}) for (int n = 1; n <= 16; n++) {
                BlockPos neighbor = pos.offset(direction, n); if (!world.isBlockLoaded(neighbor)) break;
                if (!(world.getTileEntity(neighbor) instanceof BrassTunnelTileEntity other)
                        || !(world.getTileEntity(neighbor.down()) instanceof BeltTileEntity otherBelt)
                        || otherBelt.step().getY() != 0
                        || otherBelt.step().getX() * belt.step().getX() + otherBelt.step().getZ() * belt.step().getZ() == 0) break;
                other.mode = mode; other.syncReady = other.syncReleased = false; other.changed();
            }
        }
    }
    private void changed() { markDirty(); if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3); }
    public Route route(BeltTileEntity source, EnumFacing forward) {
        if (world == null || world.isRemote || source.getCarried().isEmpty()) return Route.PASS;
        List<BrassTunnelTileEntity> group = new ArrayList<>(); group.add(this);
        EnumFacing across = forward.rotateY();
        for (EnumFacing dir : new EnumFacing[]{across, across.getOpposite()}) {
            for (int n = 1; n <= 16; n++) {
                BlockPos nextPos = pos.offset(dir, n);
                if (!world.isBlockLoaded(nextPos)) break;
                TileEntity next = world.getTileEntity(nextPos);
                if (!(next instanceof BrassTunnelTileEntity tunnel)
                        || !(world.getTileEntity(tunnel.pos.down()) instanceof BeltTileEntity b)
                        || b.step().getY() != 0 || b.step().getX() * source.step().getX() + b.step().getZ() * source.step().getZ() == 0) break;
                group.add(tunnel);
            }
        }
        List<Exit> exits = new ArrayList<>(); ItemStack carried = source.getCarried();
        for (BrassTunnelTileEntity tunnel : group) {
            for (EnumFacing side : EnumFacing.HORIZONTALS) {
                if (side == forward.getOpposite() || !ListFilterItem.matchesItem(tunnel.filters[side.getIndex()], carried)) continue;
                BlockPos base = tunnel.pos.down();
                if (side != forward && !BeltTunnelBlock.hasExit(world, base, side)) continue;
                if (world.getTileEntity(tunnel.pos.offset(side)) instanceof BrassTunnelTileEntity && side.getAxis() == across.getAxis()) continue;
                boolean pass = tunnel == this && side == forward;
                if (!pass && world.getTileEntity(base.offset(side)) instanceof BeltTileEntity belt && !belt.canReceiveFrom(side)) continue;
                IItemHandler output = ItemTransfer.at(world, base.offset(side), side.getOpposite());
                if (!pass && (output == null || output == source)) continue;
                exits.add(new Exit(tunnel, side, output, pass));
            }
        }
        if (exits.isEmpty()) return Route.HOLD;
        boolean anyFiltered = false;
        for (Exit exit : exits) if (!exit.tunnel.filters[exit.side.getIndex()].isEmpty()) anyFiltered = true;
        if (anyFiltered) exits.removeIf(exit -> exit.tunnel.filters[exit.side.getIndex()].isEmpty());
        if (mode == 6 && group.size() > 1) {
            syncReady = true;
            if (!syncReleased) {
                for (BrassTunnelTileEntity tunnel : group)
                    if (!tunnel.syncReady || !(world.getTileEntity(tunnel.pos.down()) instanceof BeltTileEntity belt)
                            || belt.getCarried().isEmpty()) return Route.HOLD;
                for (BrassTunnelTileEntity tunnel : group) tunnel.syncReleased = true;
            }
        }
        if (exits.size() == 1 && exits.get(0).forward) { syncReady = syncReleased = false; return Route.PASS; }
        int start = mode == 4 || mode == 6 ? 0 : mode == 5 ? world.rand.nextInt(exits.size()) : Math.floorMod(previous, exits.size());
        if (mode == 0 || mode == 1) {
            int original = carried.getCount(), count = exits.size();
            if (mode == 1) for (int i = 0; i < count; i++) {
                int share = original / count + (i < original % count ? 1 : 0);
                if (share > 0 && capacity(exits.get((start + i) % count), carried, share) < share) return Route.HOLD;
            }
            int reservedForward = 0;
            for (int i = 0; i < count && !source.getCarried().isEmpty(); i++) {
                Exit exit = exits.get((start + i) % count);
                int share = original / count + (i < original % count ? 1 : 0);
                if (share == 0) continue;
                if (exit.forward) { reservedForward += share; continue; }
                int moved = transfer(source, exit, share);
                if (moved > 0) exit.tunnel.flap(exit.side);
            }
            previous = (start + 1) % count; changed();
            if (source.getCarried().isEmpty()) { syncReady = syncReleased = false; return Route.TRANSFERRED; }
            if (reservedForward > 0 && (mode == 0 || source.getCarried().getCount() <= reservedForward)) {
                syncReady = syncReleased = false; return Route.PASS;
            }
            return Route.HOLD;
        }
        for (int i = 0; i < exits.size(); i++) {
            int index = (start + i) % exits.size(); Exit exit = exits.get(index);
            if (!available(exit, carried)) { if (mode == 3) return Route.HOLD; continue; }
            previous = (index + 1) % exits.size(); changed();
            if (exit.forward) { syncReady = syncReleased = false; return Route.PASS; }
            int moved = transfer(source, exit, 64);
            if (moved > 0) exit.tunnel.flap(exit.side);
            if (moved > 0 && source.getCarried().isEmpty()) { syncReady = syncReleased = false; return Route.TRANSFERRED; }
            return Route.HOLD;
        }
        return Route.HOLD;
    }
    private boolean available(Exit exit, ItemStack stack) {
        return capacity(exit, stack, stack.getCount()) > 0;
    }
    private int transfer(BeltTileEntity source, Exit exit, int limit) {
        if (!(exit.handler instanceof BeltTileEntity target))
            return ItemTransfer.move(source, exit.handler, limit, stack -> true,
                    stack -> ItemTransfer.drop(world, source.getPos(), stack));
        ItemStack offered = source.extractItem(0, limit, true);
        if (offered.isEmpty()) return 0;
        ItemStack simulated = target.insertFrom(exit.side, offered, true);
        int accepted = offered.getCount() - simulated.getCount();
        if (accepted <= 0) return 0;
        ItemStack extracted = source.extractItem(0, accepted, false);
        if (extracted.isEmpty()) return 0;
        int count = extracted.getCount();
        ItemStack remaining = target.insertFrom(exit.side, extracted, false);
        int moved = count - remaining.getCount();
        if (!remaining.isEmpty()) {
            ItemStack overflow = source.restoreSplitItem(remaining);
            if (!overflow.isEmpty()) ItemTransfer.drop(world, source.getPos(), overflow);
        }
        return moved;
    }
    private int capacity(Exit exit, ItemStack stack, int count) {
        if (exit.forward) {
            BlockPos target = exit.tunnel.pos.down().offset(exit.side);
            IItemHandler handler = ItemTransfer.at(world, target, exit.side.getOpposite());
            if (handler == null) return world.getBlockState(target).isSideSolid(world, target, exit.side.getOpposite()) ? 0 : count;
            ItemStack offer = stack.copy(); offer.setCount(Math.min(count, stack.getCount()));
            return offer.getCount() - ItemHandlerHelper.insertItemStacked(handler, offer, true).getCount();
        }
        if (exit.handler == null) return 0;
        ItemStack offer = stack.copy(); offer.setCount(Math.min(count, stack.getCount()));
        ItemStack remainder = exit.handler instanceof BeltTileEntity belt
                ? belt.insertFrom(exit.side, offer, true)
                : ItemHandlerHelper.insertItemStacked(exit.handler, offer, true);
        return offer.getCount() - remainder.getCount();
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setInteger("Mode", mode); tag.setInteger("Previous", previous);
        NBTTagList list = new NBTTagList(); for (int i = 0; i < filters.length; i++) if (!filters[i].isEmpty()) {
            NBTTagCompound entry = filters[i].writeToNBT(new NBTTagCompound()); entry.setByte("Side", (byte) i); list.appendTag(entry); }
        tag.setTag("Filters", list); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); mode = Math.floorMod(tag.getInteger("Mode"), 7); previous = tag.getInteger("Previous");
        for (int i = 0; i < filters.length; i++) filters[i] = ItemStack.EMPTY;
        NBTTagList list = tag.getTagList("Filters", 10); for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i); int side = entry.getByte("Side");
            if (side >= 0 && side < filters.length) filters[side] = new ItemStack(entry);
        }
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
}
