package com.cogwork.cogworkbridge.content.logistics.packager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/** World-persistent ownership for networks shared by multiple links and dimensions. */
public final class LogisticsNetworkSecurity extends WorldSavedData {
    private static final String NAME = "cogworkbridge_logistics_security";
    private record Policy(UUID owner, boolean locked) { }
    private final Map<UUID, Policy> policies = new HashMap<>();
    public LogisticsNetworkSecurity() { super(NAME); }
    public LogisticsNetworkSecurity(String name) { super(name); }

    @Nullable private static LogisticsNetworkSecurity get(World world) {
        MinecraftServer server = world == null ? null : world.getMinecraftServer();
        if (server == null || server.getWorld(0) == null) return null;
        MapStorage storage = server.getWorld(0).getMapStorage();
        LogisticsNetworkSecurity data = (LogisticsNetworkSecurity) storage.getOrLoadData(LogisticsNetworkSecurity.class, NAME);
        if (data == null) { data = new LogisticsNetworkSecurity(); storage.setData(NAME, data); }
        return data;
    }
    public static void claim(World world, UUID network, EntityPlayer player) {
        LogisticsNetworkSecurity data = get(world);
        if (data != null && !data.policies.containsKey(network)) { data.policies.put(network, new Policy(player.getUniqueID(), false)); data.markDirty(); }
    }
    public static boolean mayInteract(World world, UUID network, EntityPlayer player) {
        LogisticsNetworkSecurity data = get(world); Policy policy = data == null ? null : data.policies.get(network);
        return policy == null || !policy.locked() || policy.owner().equals(player.getUniqueID());
    }
    public static boolean mayAdminister(World world, UUID network, EntityPlayer player) {
        LogisticsNetworkSecurity data = get(world); Policy policy = data == null ? null : data.policies.get(network);
        return policy == null || policy.owner().equals(player.getUniqueID());
    }
    public static boolean isLocked(World world, UUID network) {
        LogisticsNetworkSecurity data = get(world); Policy policy = data == null ? null : data.policies.get(network);
        return policy != null && policy.locked();
    }
    public static boolean toggle(World world, UUID network, EntityPlayer player) {
        LogisticsNetworkSecurity data = get(world); if (data == null) return false;
        claim(world, network, player);
        Policy policy = data.policies.get(network);
        if (policy == null || !policy.owner().equals(player.getUniqueID())) return false;
        data.policies.put(network, new Policy(policy.owner(), !policy.locked())); data.markDirty(); return true;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        policies.clear(); NBTTagList list = tag.getTagList("Policies", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.hasUniqueId("Network") && entry.hasUniqueId("Owner"))
                policies.put(entry.getUniqueId("Network"), new Policy(entry.getUniqueId("Owner"), entry.getBoolean("Locked")));
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Policy> entry : policies.entrySet()) {
            NBTTagCompound policy = new NBTTagCompound(); policy.setUniqueId("Network", entry.getKey());
            policy.setUniqueId("Owner", entry.getValue().owner()); policy.setBoolean("Locked", entry.getValue().locked()); list.appendTag(policy);
        }
        tag.setTag("Policies", list); return tag;
    }
}
