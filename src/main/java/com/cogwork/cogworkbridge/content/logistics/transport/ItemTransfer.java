package com.cogwork.cogworkbridge.content.logistics.transport;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.math.Vec3d;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** Shared, sided Forge inventory access for logistic devices. */
public final class ItemTransfer {
    private ItemTransfer() { }

    private record Extracted(int slot, ItemStack stack) { }

    @Nullable
    public static IItemHandler at(World world, BlockPos pos, @Nullable EnumFacing side) {
        if (world == null || !world.isBlockLoaded(pos)) return null;
        TileEntity tile = world.getTileEntity(pos);
        return tile == null ? null : tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
    }

    public static void drop(World world, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || world == null || world.isRemote) return;
        stack = stack.copy();
        if (stack.hasTagCompound() && stack.getTagCompound().isEmpty()) stack.setTagCompound(null);
        if (world instanceof ContraptionWorld local) local.spawnCapturedDrop(pos, stack);
        else if (world instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity)
            entity.spawnDrop(pos, Collections.singletonList(stack));
        else if (!(world instanceof FakeWorld)) InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    }

    /** Launch an item from a moving structure using the structure's current orientation. */
    public static boolean launchFromStructure(World world, BlockPos local, ItemStack stack, EnumFacing direction, double horizontal, double vertical) {
        BearingContraptionEntity structure = null;
        if (world instanceof FakeWorld fake && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity)
            structure = entity;
        else if (world instanceof ContraptionWorld localWorld) structure = localWorld.getBearingContraption();
        if (structure == null || structure.world == null || structure.world.isRemote) return false;
        Vec3d up = structure.localFacingVectorToWorld(EnumFacing.UP);
        Vec3d forward = structure.localFacingVectorToWorld(direction);
        Vec3d point = structure.localBlockCenterToWorld(local).add(up.scale(0.5D));
        Vec3d velocity = forward.scale(horizontal).add(up.scale(vertical));
        EntityItem item = new EntityItem(structure.world, point.x, point.y, point.z, stack.copy());
        item.motionX = velocity.x; item.motionY = velocity.y; item.motionZ = velocity.z; item.setDefaultPickupDelay();
        return structure.world.spawnEntity(item);
    }

    /** Move at most one matching stack. Both ends are simulated before extraction. */
    public static int move(IItemHandler source, IItemHandler target, int limit, Predicate<ItemStack> filter,
                           Consumer<ItemStack> overflow) {
        return move(source, target, limit, filter, overflow, null);
    }
    public static int move(IItemHandler source, IItemHandler target, int limit, Predicate<ItemStack> filter,
                           Consumer<ItemStack> overflow, @Nullable Consumer<ItemStack> onTransfer) {
        if (source == null || target == null || source == target || limit <= 0) return 0;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack offered = source.extractItem(slot, limit, true);
            if (offered.isEmpty() || !filter.test(offered)) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, offered, true);
            int accepted = offered.getCount() - remainder.getCount();
            if (accepted <= 0) continue;
            ItemStack extracted = source.extractItem(slot, accepted, false);
            if (extracted.isEmpty()) continue;
            ItemStack movedItem = extracted.copy();
            int extractedCount = extracted.getCount();
            ItemStack left = ItemHandlerHelper.insertItemStacked(target, extracted, false);
            int moved = extractedCount - left.getCount();
            if (!left.isEmpty()) {
                ItemStack rejected = source.insertItem(slot, left, false);
                if (!rejected.isEmpty()) {
                    rejected = ItemHandlerHelper.insertItemStacked(source, rejected, false);
                    if (!rejected.isEmpty()) overflow.accept(rejected);
                }
            }
            if (moved > 0) {
                if (onTransfer != null) { movedItem.setCount(moved); onTransfer.accept(movedItem); }
                return moved;
            }
        }
        return 0;
    }

    /** Transfer an exact quantity of one item, including matching stacks in separate slots. */
    public static int moveExact(IItemHandler source, IItemHandler target, int amount, Predicate<ItemStack> filter,
                                Consumer<ItemStack> overflow) {
        return moveExact(source, target, amount, filter, overflow, null);
    }
    public static int moveExact(IItemHandler source, IItemHandler target, int amount, Predicate<ItemStack> filter,
                                Consumer<ItemStack> overflow, @Nullable Consumer<ItemStack> onTransfer) {
        if (source == null || target == null || source == target || amount <= 0) return 0;
        for (int seedSlot = 0; seedSlot < source.getSlots(); seedSlot++) {
            ItemStack seed = source.extractItem(seedSlot, amount, true);
            if (seed.isEmpty() || !filter.test(seed)) continue;
            int available = 0;
            for (int slot = 0; slot < source.getSlots() && available < amount; slot++) {
                ItemStack candidate = source.extractItem(slot, amount - available, true);
                if (sameItem(seed, candidate)) available += candidate.getCount();
            }
            if (available < amount) continue;
            ItemStack offer = seed.copy();
            offer.setCount(amount);
            if (!ItemHandlerHelper.insertItemStacked(target, offer, true).isEmpty()) continue;

            List<Extracted> extracted = new ArrayList<>();
            int remaining = amount;
            for (int slot = 0; slot < source.getSlots() && remaining > 0; slot++) {
                ItemStack candidate = source.extractItem(slot, remaining, true);
                if (!sameItem(seed, candidate)) continue;
                ItemStack part = source.extractItem(slot, candidate.getCount(), false);
                if (part.isEmpty()) continue;
                extracted.add(new Extracted(slot, part));
                if (!sameItem(seed, part)) break;
                remaining -= part.getCount();
            }
            if (remaining != 0) {
                for (Extracted part : extracted) restore(source, part.slot(), part.stack(), overflow);
                continue;
            }
            ItemStack left = ItemHandlerHelper.insertItemStacked(target, offer, false);
            int moved = amount - left.getCount();
            if (!left.isEmpty()) {
                for (Extracted part : extracted) {
                    if (left.isEmpty()) break;
                    left = source.insertItem(part.slot(), left, false);
                }
                if (!left.isEmpty()) left = ItemHandlerHelper.insertItemStacked(source, left, false);
                if (!left.isEmpty()) overflow.accept(left);
            }
            if (moved > 0 && onTransfer != null) { ItemStack transferred = seed.copy(); transferred.setCount(moved); onTransfer.accept(transferred); }
            return moved;
        }
        return 0;
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        return !second.isEmpty() && ItemStack.areItemsEqual(first, second)
                && ItemStack.areItemStackTagsEqual(first, second);
    }

    private static void restore(IItemHandler source, int slot, ItemStack stack, Consumer<ItemStack> overflow) {
        ItemStack left = source.insertItem(slot, stack, false);
        if (!left.isEmpty()) left = ItemHandlerHelper.insertItemStacked(source, left, false);
        if (!left.isEmpty()) overflow.accept(left);
    }
}
