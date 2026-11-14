package com.cogwork.cogworkbridge.content.logistics.factory;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Outstanding factory output is shared by every gauge on the same network. */
public final class FactoryPromises extends WorldSavedData {
    private static final String NAME = "cogworkbridge_factory_promises";
    private static final class Promise {
        final UUID network;
        final ItemStack item;
        int amount;
        Promise(UUID network, ItemStack item, int amount) {
            this.network = network;
            this.item = item.copy();
            this.item.setCount(1);
            this.amount = amount;
        }
    }
    private final List<Promise> promises = new ArrayList<>();

    public FactoryPromises() { super(NAME); }
    public FactoryPromises(String name) { super(name); }

    @Nullable private static FactoryPromises data(World world) {
        MinecraftServer server = world == null ? null : world.getMinecraftServer();
        if (server == null || server.getWorld(0) == null) return null;
        MapStorage storage = server.getWorld(0).getMapStorage();
        FactoryPromises result = (FactoryPromises) storage.getOrLoadData(FactoryPromises.class, NAME);
        if (result == null) { result = new FactoryPromises(); storage.setData(NAME, result); }
        return result;
    }
    private static boolean matches(Promise promise, UUID network, ItemStack item) {
        return promise.network.equals(network) && ItemStack.areItemsEqual(promise.item, item)
                && ItemStack.areItemStackTagsEqual(promise.item, item);
    }
    public static int get(World world, UUID network, ItemStack item) {
        FactoryPromises data = data(world);
        if (data == null || network == null || item.isEmpty()) return 0;
        long total = 0;
        for (Promise promise : data.promises) if (matches(promise, network, item)) total += promise.amount;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
    public static void add(World world, UUID network, ItemStack item, int amount) {
        FactoryPromises data = data(world);
        if (data == null || network == null || item.isEmpty() || amount <= 0) return;
        data.promises.add(new Promise(network, item, amount));
        data.markDirty();
    }
    public static void received(World world, UUID network, ItemStack item, int amount) {
        FactoryPromises data = data(world);
        if (data == null || network == null || item.isEmpty() || amount <= 0) return;
        boolean changed = false;
        for (var iterator = data.promises.iterator(); iterator.hasNext() && amount > 0;) {
            Promise promise = iterator.next();
            if (!matches(promise, network, item)) continue;
            int used = Math.min(amount, promise.amount);
            promise.amount -= used; amount -= used; changed = true;
            if (promise.amount == 0) iterator.remove();
        }
        if (changed) data.markDirty();
    }
    public static void clear(World world, UUID network, ItemStack item) {
        FactoryPromises data = data(world);
        if (data != null && network != null && !item.isEmpty()
                && data.promises.removeIf(promise -> matches(promise, network, item))) data.markDirty();
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        promises.clear();
        NBTTagList list = tag.getTagList("Promises", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            ItemStack item = new ItemStack(entry.getCompoundTag("Item"));
            int amount = entry.getInteger("Amount");
            if (entry.hasUniqueId("Network") && !item.isEmpty() && amount > 0)
                promises.add(new Promise(entry.getUniqueId("Network"), item, amount));
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Promise promise : promises) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId("Network", promise.network);
            entry.setTag("Item", promise.item.writeToNBT(new NBTTagCompound()));
            entry.setInteger("Amount", promise.amount);
            list.appendTag(entry);
        }
        tag.setTag("Promises", list);
        return tag;
    }
}
