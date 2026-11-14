package com.cogwork.cogworkbridge.content.redstone;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.UUID;

/** Per-world wireless redstone endpoints and expiring remote inputs. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class RedstoneLinkNetwork {
    private static final int RANGE = 256;
    private static final int REMOTE_TIMEOUT = 30;
    private static final Map<World, Map<Key, Set<RedstoneLinkTileEntity>>> NETWORKS = new IdentityHashMap<>();
    private static final Map<World, Map<UUID, Remote>> REMOTES = new IdentityHashMap<>();
    private RedstoneLinkNetwork() { }
    public static Key key(ItemStack first, ItemStack second) { return new Key(frequency(first), frequency(second)); }
    private static String frequency(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) return "";
        Item item = stack.getItem();
        int variant = item.isDamageable() ? 0 : stack.getMetadata();
        int color = item instanceof ItemArmor armor && armor.hasColor(stack) ? armor.getColor(stack) : -1;
        return item.getRegistryName() + "/" + variant + "/" + color;
    }
    public static final class Key {
        public final String first, second;
        public Key(String first, String second) { this.first = first; this.second = second; }
        @Override public int hashCode() { return 31 * first.hashCode() + second.hashCode(); }
        @Override public boolean equals(Object o) { return o instanceof Key key && key.first.equals(first) && key.second.equals(second); }
    }
    private static final class Remote { final BlockPos pos; final Key[] keys; final int mask; final long expires; Remote(BlockPos pos, Key[] keys, int mask, long expires) { this.pos = pos; this.keys = keys; this.mask = mask; this.expires = expires; } }
    public static void add(RedstoneLinkTileEntity tile) {
        World world = tile.getWorld();
        NETWORKS.computeIfAbsent(world, ignored -> new HashMap<>()).computeIfAbsent(tile.key(), ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(tile);
        update(world, tile.key());
    }
    public static void remove(RedstoneLinkTileEntity tile, Key key) {
        World world = tile.getWorld(); Map<Key, Set<RedstoneLinkTileEntity>> networks = NETWORKS.get(world); if (networks == null) return;
        Set<RedstoneLinkTileEntity> set = networks.get(key); if (set == null) return; set.remove(tile); if (set.isEmpty()) networks.remove(key); update(world, key);
    }
    public static void changed(RedstoneLinkTileEntity tile) { update(tile.getWorld(), tile.key()); }
    /** Schedule conditions query all loaded senders, without the receiver's local range limit. */
    public static boolean hasAnyLoadedPower(ItemStack first,ItemStack second) {
        Key wanted=key(first,second);
        for(var entry:NETWORKS.entrySet())if(!entry.getKey().isRemote)for(var sender:entry.getValue().getOrDefault(wanted,Collections.emptySet()))
            if(!sender.isReceiver()&&!sender.isInvalid()&&sender.getTransmittedPower()>0)return true;
        for(var entry:REMOTES.entrySet())if(!entry.getKey().isRemote)for(var remote:entry.getValue().values())if(remote.expires>entry.getKey().getTotalWorldTime())
            for(int i=0;i<remote.keys.length;i++)if((remote.mask&1<<i)!=0&&wanted.equals(remote.keys[i]))return true;
        return false;
    }
    public static void updateRemote(EntityPlayerMP player, Key[] keys, int mask) {
        World world = player.world; Map<UUID, Remote> remotes = REMOTES.computeIfAbsent(world, ignored -> new HashMap<>());
        Remote old = remotes.remove(player.getUniqueID());
        if (mask != 0) remotes.put(player.getUniqueID(), new Remote(player.getPosition(), keys.clone(), mask, world.getTotalWorldTime() + REMOTE_TIMEOUT));
        if (mask != 0) for (int i = 0; i < keys.length; i++) {
            if ((mask & 1 << i) == 0) continue;
            Map<Key, Set<RedstoneLinkTileEntity>> endpoints = NETWORKS.get(world);
            if (endpoints != null) for (RedstoneLinkTileEntity receiver : endpoints.getOrDefault(keys[i], Collections.emptySet()))
                if (receiver.isReceiver() && !receiver.isInvalid() && receiver.getPos().distanceSq(player.getPosition()) < RANGE * RANGE)
                    com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, "linked_controller");
        }
        if (old != null) for (Key key : old.keys) update(world, key); if (mask != 0) for (Key key : keys) update(world, key);
    }
    public static void clearRemote(World world, UUID user) {
        Map<UUID, Remote> remotes=REMOTES.get(world);if(remotes==null)return;
        Remote old=remotes.remove(user);if(old!=null)for(Key key:old.keys)update(world,key);
        if(remotes.isEmpty())REMOTES.remove(world);
    }
    private static void update(World world, Key key) {
        Map<Key, Set<RedstoneLinkTileEntity>> networks = NETWORKS.get(world); if (networks == null) return;
        Set<RedstoneLinkTileEntity> endpoints = networks.get(key); if (endpoints == null) return;
        for (RedstoneLinkTileEntity listener : new ArrayList<>(endpoints)) {
            if (!listener.isReceiver() || listener.isInvalid()) continue;
            int power = 0;
            for (RedstoneLinkTileEntity sender : endpoints)
                if (!sender.isReceiver() && !sender.isInvalid() && listener.getPos().distanceSq(sender.getPos()) < RANGE * RANGE) power = Math.max(power, sender.getTransmittedPower());
            Map<UUID, Remote> remotes = REMOTES.get(world);
            if (remotes != null) for (Remote remote : remotes.values()) if (listener.getPos().distanceSq(remote.pos) < RANGE * RANGE)
                for (int i = 0; i < remote.keys.length; i++) if ((remote.mask & 1 << i) != 0 && remote.keys[i].equals(key)) power = 15;
            listener.setReceivedPower(power);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return; Map<UUID, Remote> remotes = REMOTES.get(event.world); if (remotes == null) return;
        for (Map.Entry<UUID, Remote> entry : new ArrayList<>(remotes.entrySet())) if (entry.getValue().expires <= event.world.getTotalWorldTime()) { remotes.remove(entry.getKey()); for (Key key : entry.getValue().keys) update(event.world, key); }
        if (remotes.isEmpty()) REMOTES.remove(event.world);
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload event) { NETWORKS.remove(event.getWorld()); REMOTES.remove(event.getWorld()); }
}
