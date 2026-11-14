package com.cogwork.cogworkbridge.content.logistics.packager;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Loaded stock links, indexed by server and network identity across dimensions. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class StockLinkNetwork {
    private static final Map<MinecraftServer, Map<UUID, Set<StockLinkTileEntity>>> LINKS = new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Map<String, List<StockEntry>>>> STOCK_SNAPSHOTS = new WeakHashMap<>();

    private StockLinkNetwork() { }

    public static void add(StockLinkTileEntity tile) {
        MinecraftServer server = tile.getWorld().getMinecraftServer();
        if (server == null) return;
        LINKS.computeIfAbsent(server, ignored -> new java.util.HashMap<>())
                .computeIfAbsent(tile.getNetwork(), ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(tile);
    }

    public static void remove(StockLinkTileEntity tile, UUID network) {
        if (tile.getWorld() == null) return;
        MinecraftServer server = tile.getWorld().getMinecraftServer();
        Map<UUID, Set<StockLinkTileEntity>> networks = LINKS.get(server);
        if (networks == null) return;
        Set<StockLinkTileEntity> links = networks.get(network);
        if (links == null) return;
        links.remove(tile);
        if (links.isEmpty()) networks.remove(network);
        if (networks.isEmpty()) LINKS.remove(server);
    }

    private static List<StockLinkTileEntity> registered(MinecraftServer server, UUID network) {
        Map<UUID, Set<StockLinkTileEntity>> networks = LINKS.get(server);
        if (networks == null) return new ArrayList<>();
        Set<StockLinkTileEntity> links = networks.get(network);
        if (links == null) return new ArrayList<>();
        List<StockLinkTileEntity> result = new ArrayList<>();
        for (StockLinkTileEntity link : links) if (!link.isInvalid()) result.add(link);
        result.sort(java.util.Comparator.comparingInt(StockLinkTileEntity::getPriority));
        return result;
    }

    public static List<StockLinkTileEntity> loaded(MinecraftServer server, UUID network) {
        List<StockLinkTileEntity> result = registered(server, network);
        result.removeIf(link -> link.getPriority() >= 15 || link.getPackager() == null);
        return result;
    }

    public static UUID networkOf(net.minecraft.tileentity.TileEntity tile) {
        if (tile instanceof StockLinkTileEntity link) return link.getNetwork();
        if (tile instanceof StockTickerTileEntity ticker) return ticker.getNetwork();
        if (tile instanceof RedstoneRequesterTileEntity requester) return requester.getNetwork();
        return null;
    }

    /** Stable identity for ordinary inventories, both halves of a chest, and an item vault. */
    private static String inventoryKey(PackagerTileEntity packager) {
        net.minecraft.world.World world = packager.getWorld();
        net.minecraft.util.math.BlockPos pos = packager.inventoryPosition();
        if (!world.isBlockLoaded(pos)) return world.provider.getDimension() + ":" + pos.toLong();
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof com.cogwork.cogworkbridge.content.logistics.vault.ItemVaultTileEntity vault) pos = vault.controllerPos();
        IItemHandler inventory = packager.targetInventory();
        if (inventory instanceof net.minecraftforge.items.VanillaDoubleChestItemHandler chest) {
            net.minecraft.tileentity.TileEntityChest first = chest.getChest(true), second = chest.getChest(false);
            if (first != null && second != null)
                pos = first.getPos().toLong() <= second.getPos().toLong() ? first.getPos() : second.getPos();
        }
        return world.provider.getDimension() + ":" + pos.toLong();
    }

    public static final class StockEntry {
        public final ItemStack item;
        public int count;
        private StockEntry(ItemStack item, int count) { this.item = item; this.count = count; }
    }

    public static List<StockEntry> summary(MinecraftServer server, UUID network) {
        List<StockEntry> entries = new ArrayList<>();
        Set<String> inventories = new java.util.HashSet<>();
        Set<String> observed = new java.util.HashSet<>();
        Map<String, List<StockEntry>> snapshots = STOCK_SNAPSHOTS
                .computeIfAbsent(server, ignored -> new java.util.HashMap<>())
                .computeIfAbsent(network, ignored -> new java.util.HashMap<>());
        for (StockLinkTileEntity link : registered(server, network)) {
            if (link.getPriority() >= 15) continue;
            PackagerTileEntity packager = link.getPackager();
            if (packager == null || packager.getWorld() == null
                    || !packager.getWorld().isBlockLoaded(packager.inventoryPosition())
                    || !inventories.add(inventoryKey(packager))) continue;
            IItemHandler target = packager.targetInventory();
            if (target == null) continue;
            String key = inventoryKey(packager);
            boolean infinite = packager.getWorld().getTileEntity(packager.inventoryPosition()) instanceof com.cogwork.cogworkbridge.content.logistics.crate.CreativeCrateTileEntity;
            List<StockEntry> snapshot = new ArrayList<>();
            for (int i = 0; i < target.getSlots(); i++) {
                ItemStack stack = target.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                addStock(entries, stack, infinite ? Integer.MAX_VALUE : stack.getCount());
                if (!infinite) addStock(snapshot, stack, stack.getCount());
            }
            if (!infinite) {
                observed.add(key);
                List<StockEntry> previous = snapshots.put(key, snapshot);
                if (previous != null) for (StockEntry entry : snapshot) {
                    int before = 0;
                    for (StockEntry old : previous)
                        if (ItemStack.areItemsEqual(old.item, entry.item) && ItemStack.areItemStackTagsEqual(old.item, entry.item)) { before = old.count; break; }
                    if (entry.count > before)
                        com.cogwork.cogworkbridge.content.logistics.factory.FactoryPromises.received(packager.getWorld(), network, entry.item, entry.count - before);
                }
            }
        }
        snapshots.keySet().retainAll(observed);
        return entries;
    }

    private static void addStock(List<StockEntry> entries, ItemStack stack, int count) {
        for (StockEntry entry : entries)
            if (ItemStack.areItemsEqual(entry.item, stack) && ItemStack.areItemStackTagsEqual(entry.item, stack)) {
                entry.count = (int) Math.min(Integer.MAX_VALUE, (long) entry.count + count);
                return;
            }
        ItemStack sample = stack.copy(); sample.setCount(1);
        entries.add(new StockEntry(sample, count));
    }

    public static int request(MinecraftServer server, UUID network, ItemStack item, int amount, String address) {
        return requestOrder(server, network, java.util.List.of(new StockOrder.Line(item, amount)), address, true);
    }

    public static int requestOrder(MinecraftServer server, UUID network, List<StockOrder.Line> requested, String address, boolean partial) {
        return requestOrder(server, network, requested, address, partial, null);
    }
    public static int requestEncodedOrder(MinecraftServer server, UUID network, List<StockOrder.Line> requested, String address,
                                          boolean partial, List<StockOrder.Craft> crafts) {
        List<StockOrder.Craft> valid = StockOrder.readCrafts(StockOrder.writeCrafts(crafts));
        if (!StockOrder.matchesCrafts(requested, valid)) valid.clear();
        return requestNetworks(server, Collections.singletonMap(network, requested), address, partial, null, Collections.emptyList(), valid);
    }
    public static int requestRestock(MinecraftServer server, UUID network, ItemStack item, int amount, String address, PackagerTileEntity destination) {
        return requestOrder(server, network, java.util.List.of(new StockOrder.Line(item, amount)), address, true, inventoryKey(destination));
    }
    private static int requestOrder(MinecraftServer server, UUID network, List<StockOrder.Line> requested, String address, boolean partial, String excludedInventory) {
        return requestNetworks(server, Collections.singletonMap(network, requested), address, partial, excludedInventory, Collections.emptyList());
    }

    /** Stage all input networks together, so a failed recipe request emits no partial order. */
    public static int requestNetworks(MinecraftServer server, Map<UUID, List<StockOrder.Line>> requested, String address) {
        return requestNetworks(server, requested, address, false, null, Collections.emptyList());
    }
    public static int requestNetworks(MinecraftServer server, Map<UUID, List<StockOrder.Line>> requested, String address, List<ItemStack> pattern) {
        return requestNetworks(server, requested, address, false, null, pattern);
    }

    private static int requestNetworks(MinecraftServer server, Map<UUID, List<StockOrder.Line>> requested, String address, boolean partial, String excludedInventory, List<ItemStack> pattern) {
        return requestNetworks(server, requested, address, partial, excludedInventory, pattern,
                pattern.size() == 9 ? List.of(new StockOrder.Craft(pattern, 1)) : Collections.emptyList());
    }
    private static int requestNetworks(MinecraftServer server, Map<UUID, List<StockOrder.Line>> requested, String address, boolean partial,
                                       String excludedInventory, List<ItemStack> pattern, List<StockOrder.Craft> crafts) {
        Map<UUID, List<StockOrder.Line>> orders = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, List<StockOrder.Line>> entry : requested.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) return 0;
            List<StockOrder.Line> lines = StockOrder.normalize(entry.getValue());
            if (lines.isEmpty()) return 0;
            orders.put(entry.getKey(), lines);
        }
        if (orders.isEmpty()) return 0;
        if (!partial) {
            for (Map.Entry<UUID, List<StockOrder.Line>> order : orders.entrySet()) {
                List<StockEntry> stock = summary(server, order.getKey());
                for (StockOrder.Line line : order.getValue()) {
                    int available = 0;
                    for (StockEntry entry : stock)
                        if (ItemStack.areItemsEqual(entry.item, line.item()) && ItemStack.areItemStackTagsEqual(entry.item, line.item())) { available = entry.count; break; }
                    if (available < line.count()) return 0;
                }
            }
        }
        int movedTotal = 0;
        List<ItemStack> fragments = new ArrayList<>();
        List<StockOrder.Line> fulfilled = new ArrayList<>();
        List<PackagerTileEntity> changed = new ArrayList<>();
        Map<PackagerTileEntity, List<ItemStack>> staged = new IdentityHashMap<>();
        boolean complete = true;
        for (Map.Entry<UUID, List<StockOrder.Line>> order : orders.entrySet()) for (StockOrder.Line line : order.getValue()) {
            int remaining = line.count();
            Set<String> inventories = new java.util.HashSet<>();
            for (StockLinkTileEntity link : registered(server, order.getKey())) {
                if (link.getPriority() >= 15) continue;
                PackagerTileEntity packager = link.getPackager();
                if (packager == null || packager.getWorld() == null
                        || !packager.getWorld().isBlockLoaded(packager.inventoryPosition())
                        || inventoryKey(packager).equals(excludedInventory)
                        || !inventories.add(inventoryKey(packager))) continue;
                while (remaining > 0 && packager.pendingPackages() < 50) {
                    int moved = packager.request(line.item(), remaining, address, fragments);
                    if (moved <= 0) break;
                    if (!changed.contains(packager)) changed.add(packager);
                    staged.computeIfAbsent(packager, ignored -> new ArrayList<>()).add(fragments.get(fragments.size() - 1));
                    remaining -= moved; movedTotal += moved;
                }
                if (remaining <= 0) break;
            }
            int moved = line.count() - remaining;
            if (moved > 0) fulfilled.add(new StockOrder.Line(line.item(), moved));
            if (remaining > 0) complete = false;
        }
        if (!partial && !complete) {
            for (Map.Entry<PackagerTileEntity, List<ItemStack>> entry : staged.entrySet()) entry.getKey().rollbackRequests(entry.getValue());
            return 0;
        }
        if (!fragments.isEmpty()) {
            UUID orderId = UUID.randomUUID();
            for (int i = 0; i < fragments.size(); i++)
                com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem.setFragment(fragments.get(i), orderId, i, i == fragments.size() - 1);
            net.minecraft.nbt.NBTTagCompound context = new net.minecraft.nbt.NBTTagCompound();
            context.setTag("Items", StockOrder.write(fulfilled));
            if (!crafts.isEmpty()) context.setTag("Crafts", StockOrder.writeCrafts(crafts));
            if (pattern.size() == 9) context.setTag("Craft", StockOrder.writePattern(pattern));
            com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem.setOrderContext(fragments.get(fragments.size() - 1), context);
            for (PackagerTileEntity packager : changed) packager.markDirty();
        }
        return movedTotal;
    }

    @SubscribeEvent public static void unload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        MinecraftServer server = event.getWorld().getMinecraftServer();
        if (event.getWorld().provider.getDimension() == 0) STOCK_SNAPSHOTS.remove(server);
        Map<UUID, Set<StockLinkTileEntity>> networks = LINKS.get(server);
        if (networks == null) return;
        for (Set<StockLinkTileEntity> links : networks.values())
            links.removeIf(link -> link.getWorld() == event.getWorld());
        networks.values().removeIf(Set::isEmpty);
        if (networks.isEmpty()) LINKS.remove(server);
    }
}
