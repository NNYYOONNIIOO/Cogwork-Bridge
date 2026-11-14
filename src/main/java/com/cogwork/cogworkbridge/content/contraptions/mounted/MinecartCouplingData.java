package com.cogwork.cogworkbridge.content.contraptions.mounted;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.network.ModNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;

/** Persistent, dimension-local links. UUIDs let an unloaded cart reconnect when its chunk returns. */
public final class MinecartCouplingData extends WorldSavedData {
    private static final String NAME = "cogworkbridge_minecart_couplings";
    private static final int MAX_LINKS = 131072;
    private final List<Link> links = new ArrayList<>();
    private final Map<UUID, Set<Link>> neighbours = new HashMap<>();
    private final Map<UUID, WeakReference<EntityMinecart>> seen = new HashMap<>();

    public MinecartCouplingData() { this(NAME); }
    public MinecartCouplingData(String name) { super(name); }

    public static MinecartCouplingData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        MinecartCouplingData data = (MinecartCouplingData) storage.getOrLoadData(MinecartCouplingData.class, NAME);
        if (data == null) { data = new MinecartCouplingData(); storage.setData(NAME, data); }
        return data;
    }

    public record Link(UUID first, UUID second, int length) {
        public boolean contains(UUID id) { return first.equals(id) || second.equals(id); }
        public UUID other(UUID id) { return first.equals(id) ? second : first; }
    }

    public int degree(UUID id) { return neighbours.getOrDefault(id, Collections.emptySet()).size(); }

    private void index(Link link) {
        neighbours.computeIfAbsent(link.first, ignored -> new LinkedHashSet<>()).add(link);
        neighbours.computeIfAbsent(link.second, ignored -> new LinkedHashSet<>()).add(link);
    }
    private void unindex(Link link) {
        for (UUID id : new UUID[]{link.first, link.second}) {
            Set<Link> entries = neighbours.get(id);
            if (entries != null) { entries.remove(link); if (entries.isEmpty()) neighbours.remove(id); }
        }
    }
    public boolean connected(UUID first, UUID second) { return connected(null, first, second); }
    public boolean connected(@Nullable World world, UUID first, UUID second) {
        Map<UUID, UUID> structural = world == null ? Collections.emptyMap() : structuralLinks(world);
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(first); visited.add(first);
        while (!queue.isEmpty() && visited.size() <= MAX_LINKS * 2) {
            UUID id = queue.removeFirst();
            UUID otherStructure = structural.get(id);
            if (second.equals(otherStructure)) return true;
            if (otherStructure != null && visited.add(otherStructure)) queue.addLast(otherStructure);
            for (Link link : neighbours.getOrDefault(id, Collections.emptySet())) {
                UUID next = link.other(id);
                if (next.equals(second)) return true;
                if (visited.add(next)) queue.addLast(next);
            }
        }
        return false;
    }
    private static Map<UUID,UUID> structuralLinks(World world) {
        Map<UUID,UUID> result = new HashMap<>();
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof BearingContraptionEntity mounted) || mounted.isDead || !mounted.hasCoupledCart()) continue;
            EntityMinecart first = mounted.getMountedCart();
            EntityMinecart second = first == null ? null : mounted.getOtherMountedCart(first.getUniqueID());
            if (first == null || second == null) continue;
            result.put(first.getUniqueID(), second.getUniqueID()); result.put(second.getUniqueID(), first.getUniqueID());
        }
        return result;
    }

    public boolean add(WorldServer world, EntityMinecart first, EntityMinecart second, int length) {
        UUID a = first.getUniqueID(), b = second.getUniqueID();
        if (a.equals(b) || length < 2 || length > 32 || links.size() >= MAX_LINKS
                || degree(a) + structuralDegree(world, a) >= 2 || degree(b) + structuralDegree(world, b) >= 2
                || connected(world, a, b)) return false;
        Link link = new Link(a, b, length);
        links.add(link); index(link); seen.put(a, new WeakReference<>(first)); seen.put(b, new WeakReference<>(second));
        markDirty(); broadcast(world, link, true);
        return true;
    }

    /** A two-cart structure consumes one coupling on each cart, just like a manual link. */
    public boolean canAddStructure(WorldServer world, EntityMinecart first, EntityMinecart second) {
        UUID a = first.getUniqueID(), b = second.getUniqueID();
        int distance = (int) first.getDistance(second);
        return !a.equals(b) && distance >= 2 && distance <= 32
                && degree(a) + structuralDegree(world, a) < 2
                && degree(b) + structuralDegree(world, b) < 2
                && !connected(world, a, b);
    }

    private static int structuralDegree(World world, UUID id) {
        for (Entity entity : world.loadedEntityList)
            if (entity instanceof BearingContraptionEntity mounted && !mounted.isDead
                    && mounted.hasCoupledCart() && mounted.isMountedTo(id)) return 1;
        return 0;
    }

    public int remove(WorldServer world, UUID id) {
        int count = 0;
        for (Iterator<Link> it = links.iterator(); it.hasNext();) {
            Link link = it.next();
            if (!link.contains(id)) continue;
            it.remove(); unindex(link); count++; broadcast(world, link, false);
        }
        if (count != 0) markDirty();
        return count;
    }

    public void sync(EntityPlayerMP player, UUID id) {
        for (Link link : links) if (link.contains(id))
            ModNetwork.CHANNEL.sendTo(new MinecartCouplingSyncPacket(link.first, link.second, true, player.dimension), player);
    }

    @Nullable private EntityMinecart loaded(WorldServer world, UUID id) {
        Entity entity = world.getEntityFromUuid(id);
        if (!(entity instanceof EntityMinecart cart)) return null;
        seen.put(id, new WeakReference<>(cart));
        return cart;
    }

    public void tick(WorldServer world) {
        if (links.isEmpty()) { seen.clear(); return; }
        Map<UUID,UUID> structural = structuralLinks(world);
        for (Iterator<Link> it = links.iterator(); it.hasNext();) {
            Link link = it.next();
            EntityMinecart first = loaded(world, link.first), second = loaded(world, link.second);
            EntityMinecart oldFirst = seen.containsKey(link.first) ? seen.get(link.first).get() : null;
            EntityMinecart oldSecond = seen.containsKey(link.second) ? seen.get(link.second).get() : null;
            if (oldFirst != null && oldFirst.isDead || oldSecond != null && oldSecond.isDead) {
                it.remove(); unindex(link); markDirty(); broadcast(world, link, false);
                EntityMinecart survivor = oldFirst != null && !oldFirst.isDead ? oldFirst
                        : oldSecond != null && !oldSecond.isDead ? oldSecond : null;
                if (survivor == null) survivor = oldFirst != null ? oldFirst : oldSecond;
                if (survivor != null) survivor.entityDropItem(new net.minecraft.item.ItemStack(
                        com.cogwork.cogworkbridge.registry.AllItems.MINECART_COUPLING), 0F);
                continue;
            }
            if (first != null && second != null && !link.second.equals(structural.get(link.first)))
                MountedCartCoupling.tick(first, second, link.length);
        }
        seen.entrySet().removeIf(entry -> entry.getValue().get() == null && degree(entry.getKey()) == 0);
    }

    private static void broadcast(World world, Link link, boolean present) {
        ModNetwork.CHANNEL.sendToDimension(new MinecartCouplingSyncPacket(link.first, link.second, present,
                world.provider.getDimension()), world.provider.getDimension());
    }

    @Override public void readFromNBT(NBTTagCompound root) {
        links.clear(); neighbours.clear();
        NBTTagList list = root.getTagList("Links", 10);
        for (int i = 0; i < list.tagCount() && links.size() < MAX_LINKS; i++) {
            NBTTagCompound nbt = list.getCompoundTagAt(i);
            if (!nbt.hasUniqueId("First") || !nbt.hasUniqueId("Second")) continue;
            UUID first = nbt.getUniqueId("First"), second = nbt.getUniqueId("Second");
            int length = nbt.getInteger("Length");
            if (!first.equals(second) && length >= 2 && length <= 32 && degree(first) < 2 && degree(second) < 2) {
                boolean duplicate = false;
                for (Link existing : neighbours.getOrDefault(first, Collections.emptySet()))
                    if (existing.contains(second)) { duplicate = true; break; }
                if (!duplicate) { Link link = new Link(first, second, length); links.add(link); index(link); }
            }
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound root) {
        NBTTagList list = new NBTTagList();
        for (Link link : links) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("First", link.first); tag.setUniqueId("Second", link.second);
            tag.setInteger("Length", link.length); list.appendTag(tag);
        }
        root.setTag("Links", list);
        return root;
    }
}
