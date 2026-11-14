package com.cogwork.cogworkbridge.content.logistics.arm;

import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockJukebox;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
import xy177.farmersfuturedelight.common.tile.TileEntityCampfire;
import xy177.farmersfuturedelight.api.WorkstationRecipes;
import java.util.Collections;
import java.util.UUID;
import javax.annotation.Nullable;

/** Arm-only interaction points for blocks which expose no inventory capability. */
public final class ArmInteractionHandlers {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("63e88a08-e921-4c74-8bf7-bfa63e04ef07"), "[CogworkArm]");
    private ArmInteractionHandlers() { }

    @Nullable public static IItemHandler at(World world, BlockPos pos, @Nullable EnumFacing side) {
        if (world == null || !world.isBlockLoaded(pos)) return null;
        if (world.getTileEntity(pos) instanceof BlockJukebox.TileEntityJukebox jukebox)
            return new Jukebox(world, pos, jukebox);
        if (world.getTileEntity(pos) instanceof TileEntityCampfire campfire)
            return new Campfire(world, pos, campfire);
        IItemHandler handler = ItemTransfer.at(world, pos, side);
        return handler != null ? handler : ItemTransfer.at(world, pos, null);
    }

    public static boolean hasMusic(World world, BlockPos pos) {
        return world.isBlockLoaded(pos) && world.getTileEntity(pos) instanceof BlockJukebox.TileEntityJukebox tile
                && !tile.getRecord().isEmpty();
    }
    public static boolean depositOnly(World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof TileEntityCampfire;
    }

    private record Jukebox(World world, BlockPos pos, BlockJukebox.TileEntityJukebox tile) implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? tile.getRecord().copy() : ItemStack.EMPTY; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && stack.getItem() instanceof ItemRecord; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack) || stack.isEmpty() || !tile.getRecord().isEmpty()) return stack;
            ItemStack remainder = stack.copy(), record = remainder.splitStack(1);
            if (!simulate) {
                tile.setRecord(record);
                world.setBlockState(pos, world.getBlockState(pos).withProperty(BlockJukebox.HAS_RECORD, true), 3);
                world.playEvent(1010, pos, Item.getIdFromItem(record.getItem()));
                tile.markDirty();
            }
            return remainder;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || tile.getRecord().isEmpty()) return ItemStack.EMPTY;
            ItemStack result = tile.getRecord().copy();
            if (!simulate) {
                world.playEvent(1010, pos, 0);
                tile.setRecord(ItemStack.EMPTY);
                world.setBlockState(pos, world.getBlockState(pos).withProperty(BlockJukebox.HAS_RECORD, false), 3);
                tile.markDirty();
            }
            return result;
        }
    }

    private record Campfire(World world, BlockPos pos, TileEntityCampfire tile) implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && !stack.isEmpty() && WorkstationRecipes.find(WorkstationRecipes.Type.CAMPFIRE_COOKING,
                    Collections.singletonList(stack)) != null;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack;
            boolean room = false;
            for (ItemStack item : tile.getRenderItems()) if (item.isEmpty()) { room = true; break; }
            if (!room) return stack;
            ItemStack remainder = stack.copy();
            if (simulate) { remainder.shrink(1); return remainder; }
            if (world.isRemote) return stack;
            // Virtual contraption worlds need not expose MinecraftServer themselves.
            var parent = world instanceof net.minecraft.world.WorldServer serverWorld ? serverWorld
                    : net.minecraftforge.common.DimensionManager.getWorld(world.provider.getDimension());
            if (parent == null) return stack;
            var player = FakePlayerFactory.get(parent, PROFILE);
            World previous = player.world;
            player.world = world;
            player.setPosition(pos.getX() + .5D, pos.getY() + 1, pos.getZ() + .5D);
            try {
                if (!tile.placeFood(player, remainder)) return stack;
                return remainder;
            } finally {
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    ItemStack container = player.inventory.removeStackFromSlot(i);
                    if (!container.isEmpty()) ItemTransfer.drop(world, pos, container);
                }
                player.world = previous;
            }
        }
    }
}
