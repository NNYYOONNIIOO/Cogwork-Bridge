package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.content.logistics.transport.ContraptionItemStorage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/** Collects block drops in carried inventories before dropping any remainder. */
public final class ContraptionItemCollector {
    private ContraptionItemCollector() {
    }

    public static void destroyAndCollect(BearingContraptionEntity contraption, BlockPos pos) {
        harvestAndCollect(contraption, pos, ItemStack.EMPTY, false);
    }

    public static void destroyAndCollect(BearingContraptionEntity contraption, BlockPos pos, float dropChance) {
        harvestAndCollect(contraption, pos, ItemStack.EMPTY, false, dropChance);
    }

    public static boolean hasMatchingItem(BearingContraptionEntity contraption, ItemStack filter) {
        return useMatchingItem(contraption, filter, null, null);
    }

    public static ItemStack extractItem(BearingContraptionEntity contraption,
                                        BlockPos excludedPos, ItemStack filter) {
        if (contraption.world.isRemote) return ItemStack.EMPTY;
        IItemHandler handler = new ContraptionItemStorage(contraption, excludedPos);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack simulated = handler.extractItem(slot, 64, true);
            if (!matchesFilter(simulated, filter)) continue;
            ItemStack extracted = handler.extractItem(slot, Math.min(64, simulated.getCount()), false);
            if (!extracted.isEmpty() && matchesFilter(extracted, filter)) return extracted;
        }
        return ItemStack.EMPTY;
    }

    public static boolean placeMatchingItem(BearingContraptionEntity contraption, ItemStack filter,
                                            BlockPos target, IBlockState state) {
        return useMatchingItem(contraption, filter, target, state);
    }

    private static boolean useMatchingItem(BearingContraptionEntity contraption, ItemStack filter,
                                           BlockPos target, IBlockState state) {
        if (filter.isEmpty() || contraption.world.isRemote) return false;
        IItemHandler handler = new ContraptionItemStorage(contraption);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack simulated = handler.extractItem(slot, 1, true);
                if (!matches(simulated, filter)) {
                    continue;
                }
                if (target == null) {
                    return true;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!matches(extracted, filter)
                        || !contraption.world.setBlockState(target, state, 3)) {
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, extracted, false);
                    if (!remainder.isEmpty()) {
                        net.minecraft.block.Block.spawnAsEntity(contraption.world, target, remainder);
                    }
                    return false;
                }
                return true;
        }
        return false;
    }

    private static boolean matches(ItemStack candidate, ItemStack filter) {
        return !candidate.isEmpty() && candidate.isItemEqual(filter)
                && ItemStack.areItemStackTagsEqual(candidate, filter);
    }

    private static boolean matchesFilter(ItemStack candidate, ItemStack filter) {
        return !candidate.isEmpty() && com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter, candidate);
    }

    /** Removes a block, optionally retaining one replanting item from its drops. */
    public static boolean harvestAndCollect(BearingContraptionEntity contraption, BlockPos pos,
                                            ItemStack replantingItem, boolean shear) {
        return harvestAndCollect(contraption, pos, replantingItem, shear, 1.0F);
    }

    private static boolean harvestAndCollect(BearingContraptionEntity contraption, BlockPos pos,
                                             ItemStack replantingItem, boolean shear, float dropChance) {
        World world = contraption.world;
        if (world.isRemote || !world.isBlockLoaded(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        boolean canDrop = world.getGameRules().getBoolean("doTileDrops")
                && !world.restoringBlockSnapshots;
        List<ItemStack> drops = new ArrayList<>();
        if (canDrop) {
            ItemStack shears = new ItemStack(Items.SHEARS);
            if (shear && state.getBlock() instanceof IShearable shearable
                    && shearable.isShearable(shears, world, pos)) {
                drops.addAll(shearable.onSheared(shears, world, pos, 0));
            } else {
                drops.addAll(state.getBlock().getDrops(world, pos, state, 0));
            }
        }
        float chance = canDrop
                ? ForgeEventFactory.fireBlockHarvesting(drops, world, pos, state,
                0, dropChance, false, null) : 0.0F;
        if (!world.destroyBlock(pos, false)) {
            return false;
        }
        IItemHandler storage = new ContraptionItemStorage(contraption);
        boolean reserved = replantingItem.isEmpty();
        for (ItemStack drop : drops) {
            if (drop.isEmpty() || world.rand.nextFloat() > chance) {
                continue;
            }
            if (!reserved && drop.isItemEqual(replantingItem)) {
                drop.shrink(1);
                reserved = true;
                if (drop.isEmpty()) {
                    continue;
                }
            }
            ItemStack remainder = drop;
            remainder = ItemHandlerHelper.insertItemStacked(storage, remainder, false);
            if (!remainder.isEmpty()) {
                net.minecraft.block.Block.spawnAsEntity(world, pos, remainder);
            }
        }
        return true;
    }
}
