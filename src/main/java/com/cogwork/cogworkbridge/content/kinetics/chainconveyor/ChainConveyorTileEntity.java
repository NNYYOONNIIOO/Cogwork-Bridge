package com.cogwork.cogworkbridge.content.kinetics.chainconveyor;

import com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem;
import com.cogwork.cogworkbridge.content.logistics.port.PackagePortTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.kinetic.KineticNetworkManager;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.items.ItemHandlerHelper;
import javax.annotation.Nullable;
import java.util.*;

/** Persistent suspended chains, kinetic links and parcels. */
public final class ChainConveyorTileEntity extends KineticTileEntity {
    public static final int MAX_CONNECTIONS = 4, CAPACITY = 20;
    public static final double MAX_LENGTH = 32;
    public enum ConnectionResult { SUCCESS, INVALID, TOO_CLOSE, TOO_FAR, TOO_STEEP, LIMIT, ALREADY_CONNECTED, PROTECTED, NOT_ENOUGH_CHAINS }
    private final Map<BlockPos, ItemStack> connections = new LinkedHashMap<>();
    private final Set<BlockPos> ports = new HashSet<>();
    private final List<Parcel> packages = new ArrayList<>();
    private final Map<String, Optional<Route>> routeCache = new HashMap<>();
    private long routeCacheTick = Long.MIN_VALUE;
    private boolean suppressDrops;

    public static final class Parcel {
        public ItemStack item;
        @Nullable public BlockPos target;
        public float position, previous;
        public boolean reversed;
        Parcel(ItemStack item, @Nullable BlockPos target, float position, boolean reversed) {
            this.item = item.copy(); this.item.setCount(1); this.target = target;
            this.position = this.previous = position; this.reversed = reversed;
        }
    }
    public record Geometry(Vec3d start, Vec3d end, float length, float angle, float endAngle) { }
    private record Route(@Nullable BlockPos hop, BlockPos port, int distance, boolean wildcard) { }

    public Set<BlockPos> connections() { return Collections.unmodifiableSet(connections.keySet()); }
    @Override public void setPos(BlockPos newPos) {
        BlockPos old = this.pos;
        if (old != null && newPos != null && !old.equals(newPos) && connections != null) {
            BlockPos delta = newPos.subtract(old);
            Map<BlockPos, ItemStack> moved = new LinkedHashMap<>();
            for (Map.Entry<BlockPos, ItemStack> entry : connections.entrySet()) moved.put(entry.getKey().add(delta), entry.getValue());
            connections.clear(); connections.putAll(moved);
            if (packages != null) for (Parcel box : packages) if (box.target != null) box.target = box.target.add(delta);
            if (ports != null) ports.clear();
        }
        super.setPos(newPos);
    }
    public List<Parcel> parcels() { return Collections.unmodifiableList(packages); }
    public boolean connectedTo(ChainConveyorTileEntity other) { return connections.containsKey(other.pos) && other.connections.containsKey(pos); }
    public void registerPort(BlockPos port) { if (ports.add(port.toImmutable())) routeCache.clear(); }
    public void unregisterPort(BlockPos port) { if (ports.remove(port)) routeCache.clear(); }
    public boolean hasCapacity() { return packages.size() < CAPACITY; }
    public void suppressChainDrops() { suppressDrops = true; }
    public static float angle(double x, double z) { return wrap((float) Math.toDegrees(Math.atan2(x, z))); }
    private static float wrap(float angle) { angle %= 360F; return angle < 0 ? angle + 360F : angle; }
    public static int chainCost(BlockPos first, BlockPos second) { return Math.max(1, (int) Math.round(Math.sqrt(first.distanceSq(second)) / 2.5D)); }

    public ConnectionResult connectTo(ChainConveyorTileEntity other, EntityPlayer player, ItemStack material) {
        if (world == null || world.isRemote || other == null || other == this || other.world != world || !ChainConveyorBlock.isChain(material)) return ConnectionResult.INVALID;
        double dx = other.pos.getX() - pos.getX(), dy = other.pos.getY() - pos.getY(), dz = other.pos.getZ() - pos.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz), horizontal = Math.sqrt(dx * dx + dz * dz) - 1.5D;
        if (distance < 2.5D) return ConnectionResult.TOO_CLOSE;
        if (distance >= MAX_LENGTH || player.getDistanceSq(pos) > (MAX_LENGTH + 16) * (MAX_LENGTH + 16)) return ConnectionResult.TOO_FAR;
        if (horizontal <= 0 || Math.abs(dy) > horizontal) return ConnectionResult.TOO_STEEP;
        if (connections.size() >= MAX_CONNECTIONS || other.connections.size() >= MAX_CONNECTIONS) return ConnectionResult.LIMIT;
        if (connections.containsKey(other.pos) || other.connections.containsKey(pos)) return ConnectionResult.ALREADY_CONNECTED;
        if (!world.isBlockModifiable(player, pos) || !world.isBlockModifiable(player, other.pos)) return ConnectionResult.PROTECTED;
        int cost = chainCost(pos, other.pos);
        ItemStack reference = material.copy(); reference.setCount(1);
        if (!player.capabilities.isCreativeMode && !consumeChains(player, material, cost)) return ConnectionResult.NOT_ENOUGH_CHAINS;
        connections.put(other.pos.toImmutable(), reference); other.connections.put(pos.toImmutable(), reference.copy());
        topologyChanged(); other.topologyChanged(); return ConnectionResult.SUCCESS;
    }

    private static boolean consumeChains(EntityPlayer player, ItemStack material, int amount) {
        int count = 0;
        for (ItemStack stack : player.inventory.mainInventory) if (ItemStack.areItemsEqual(stack, material)) count += stack.getCount();
        for (ItemStack stack : player.inventory.offHandInventory) if (ItemStack.areItemsEqual(stack, material)) count += stack.getCount();
        if (count < amount) return false;
        for (ItemStack stack : player.inventory.mainInventory) if (ItemStack.areItemsEqual(stack, material) && amount > 0) {
            int used = Math.min(amount, stack.getCount()); stack.shrink(used); amount -= used;
        }
        for (ItemStack stack : player.inventory.offHandInventory) if (ItemStack.areItemsEqual(stack, material) && amount > 0) {
            int used = Math.min(amount, stack.getCount()); stack.shrink(used); amount -= used;
        }
        player.inventory.markDirty(); return true;
    }

    public boolean disconnect(BlockPos target, @Nullable EntityPlayer player) {
        if (world == null || world.isRemote || player != null && (!world.isBlockModifiable(player, pos) || !world.isBlockModifiable(player, target))) return false;
        ItemStack material = connections.remove(target);
        if (material == null) return false;
        if (world.isBlockLoaded(target) && world.getTileEntity(target) instanceof ChainConveyorTileEntity other) {
            other.removeOneWay(pos); other.topologyChanged();
        }
        dropTravelling(target);
        if (player == null || !player.capabilities.isCreativeMode) {
            ItemStack refund = material.copy(); refund.setCount(chainCost(pos, target));
            if (player == null) ItemTransfer.drop(world, pos, refund); else ItemHandlerHelper.giveItemToPlayer(player, refund);
        }
        topologyChanged(); return true;
    }
    private void removeOneWay(BlockPos target) { connections.remove(target); dropTravelling(target); }
    private void dropTravelling(BlockPos target) {
        for (Iterator<Parcel> it = packages.iterator(); it.hasNext();) {
            Parcel box = it.next(); if (!target.equals(box.target)) continue;
            ItemTransfer.drop(world, new BlockPos(packagePosition(box, 1)), box.item); it.remove();
        }
    }
    public void destroy() {
        if (world == null || world.isRemote) return;
        for (BlockPos target : new ArrayList<>(connections.keySet())) {
            ItemStack material = connections.remove(target);
            if (world.isBlockLoaded(target) && world.getTileEntity(target) instanceof ChainConveyorTileEntity other) {
                other.removeOneWay(pos); other.topologyChanged();
            }
            if (!suppressDrops) { ItemStack drop = material.copy(); drop.setCount(chainCost(pos, target)); ItemTransfer.drop(world, pos, drop); }
        }
        for (Parcel box : packages) ItemTransfer.drop(world, new BlockPos(packagePosition(box, 1)), box.item);
        packages.clear(); KineticNetworkManager.discard(world);
    }
    private void topologyChanged() { routeCache.clear(); KineticNetworkManager.discard(world); markDirty(); sync(); }
    private void changed() { markDirty(); sync(); }

    public boolean addLoopPackage(ItemStack stack, float angle) {
        if (!PackageItem.isPackage(stack) || !hasCapacity()) return false;
        packages.add(new Parcel(stack, null, wrap(angle), getSpeed() < 0)); changed(); return true;
    }
    public boolean addTravelPackage(ItemStack stack, BlockPos target, float progress) {
        if (!PackageItem.isPackage(stack) || !connections.containsKey(target) || !hasCapacity()) return false;
        packages.add(new Parcel(stack, target, Math.max(0, Math.min(geometry(target).length(), progress)), getSpeed() < 0));
        changed(); return true;
    }
    public void takeNearest(EntityPlayer player, float angle) {
        Parcel best = null; float diff = 40F;
        for (Parcel p : packages) if (p.target == null) {
            float d = Math.abs((wrap(p.position - angle + 180) - 180));
            if (d < diff) { best = p; diff = d; }
        }
        if (best != null) { ItemHandlerHelper.giveItemToPlayer(player, best.item.copy()); packages.remove(best); changed(); }
    }
    public void takeParcel(EntityPlayer player, Parcel box) {
        if (world != null && !world.isRemote && packages.remove(box)) {
            ItemHandlerHelper.giveItemToPlayer(player, box.item.copy()); changed();
        }
    }
    public Geometry geometry(BlockPos target) {
        double dx = target.getX() - pos.getX(), dz = target.getZ() - pos.getZ();
        float sign = getSpeed() < 0 ? -1 : 1;
        float a = wrap(angle(dx, dz) - 35F * sign), endAngle = wrap(a + 180F + 70F * sign);
        Vec3d start = ringPoint(pos, a, 1.25D), end = ringPoint(target, endAngle, 1.25D);
        return new Geometry(start, end, (float) start.distanceTo(end), a, endAngle);
    }
    private static Vec3d ringPoint(BlockPos center, float degrees, double radius) {
        double radians = Math.toRadians(degrees);
        return new Vec3d(center.getX() + .5 + Math.sin(radians) * radius, center.getY() + 6D/16D,
                center.getZ() + .5 + Math.cos(radians) * radius);
    }
    public Vec3d packagePosition(Parcel box, float partial) {
        float progress = box.previous + (box.position - box.previous) * partial;
        if (box.target == null) return ringPoint(pos, progress, .875D);
        Geometry g = geometry(box.target);
        double ratio = g.length() <= 0 ? 0 : Math.max(0, Math.min(1, progress / g.length()));
        return g.start().add(g.end().subtract(g.start()).scale(ratio));
    }
    public ItemStack matchingPackage(net.minecraft.item.ItemStack filter) {
        for (Parcel p : packages) if (com.cogwork.cogworkbridge.content.fluids.ListFilterItem.matchesItem(filter, p.item)) return p.item;
        return ItemStack.EMPTY;
    }

    @Override public void update() {
        if (world == null) return;
        super.update();
        if (!world.isRemote && world.getTotalWorldTime() % 40 == 0) validateConnections();
        float speed = getSpeed();
        if (speed == 0) return;
        boolean backwards = speed < 0;
        float degrees = speed / (float)(Math.PI * 1.5D), travel = Math.abs(speed) / 360F;
        boolean edited = false;
        for (Parcel box : new ArrayList<>(packages)) {
            box.previous = box.position;
            if (box.target != null) {
                if (!connections.containsKey(box.target)) continue;
                if (box.reversed != backwards && !world.isRemote) {
                    if (!world.isBlockLoaded(box.target) || !(world.getTileEntity(box.target) instanceof ChainConveyorTileEntity other) || !other.hasCapacity()) continue;
                    float length = geometry(box.target).length();
                    other.packages.add(new Parcel(box.item, pos, Math.max(0, length - box.position), backwards));
                    other.changed(); packages.remove(box); edited = true; continue;
                }
                Geometry geometry = geometry(box.target);
                box.position = Math.min(geometry.length(), box.position + travel);
                if (!world.isRemote) {
                    notifyObservers(box);
                    boolean received = false;
                    for (BlockPos portPos : new ArrayList<>(ports)) {
                        if (!world.isBlockLoaded(portPos) || !(world.getTileEntity(portPos) instanceof PackagePortTileEntity port)
                                || !pos.equals(port.chainTarget())) { ports.remove(portPos); continue; }
                        if (box.target.equals(port.chainEnd()) && box.previous <= port.chainAngle()
                                && box.position >= port.chainAngle() && port.acceptFromChain(box.item)) {
                            packages.remove(box); edited = true; received = true; break;
                        }
                    }
                    if (received) continue;
                    if (box.position >= geometry.length() && world.isBlockLoaded(box.target)
                            && world.getTileEntity(box.target) instanceof ChainConveyorTileEntity other && other.hasCapacity()) {
                        other.packages.add(new Parcel(box.item, null, geometry.endAngle(), backwards));
                        other.changed(); packages.remove(box); edited = true;
                    }
                }
            } else {
                float start = box.position;
                box.position = wrap(box.position + degrees);
                // Preserve a smooth interpolation across the angle wrap.
                box.previous = box.position - degrees;
                if (world.isRemote) continue;
                notifyObservers(box);
                Route route = findRoute(box.item);
                if (route == null) continue;
                if (route.hop() == null) {
                    TileEntity tile = world.isBlockLoaded(route.port()) ? world.getTileEntity(route.port()) : null;
                    if (tile instanceof PackagePortTileEntity port && crossed(start, degrees, port.chainAngle()) && port.acceptFromChain(box.item)) {
                        packages.remove(box); edited = true;
                    }
                } else {
                    Geometry g = geometry(route.hop());
                    if (crossed(start, degrees, g.angle()) && world.isBlockLoaded(route.hop())
                            && world.getTileEntity(route.hop()) instanceof ChainConveyorTileEntity other && other.hasCapacity()) {
                        box.target = route.hop(); box.position = box.previous = 0; box.reversed = backwards; edited = true;
                    }
                }
            }
        }
        if (!world.isRemote && (edited || !packages.isEmpty() && world.getTotalWorldTime() % 10 == 0)) changed();
    }
    private static boolean crossed(float from, float delta, float target) {
        if (Math.abs(delta) >= 360) return true;
        return delta >= 0 ? wrap(target - from) <= delta : wrap(from - target) <= -delta;
    }
    private void notifyObservers(Parcel box) {
        Vec3d at = packagePosition(box, 1);
        com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity.onChainPackage(world, new BlockPos(at), box.item);
    }
    private void validateConnections() {
        for (BlockPos target : new ArrayList<>(connections.keySet())) {
            if (!world.isBlockLoaded(target)) continue;
            if (!(world.getTileEntity(target) instanceof ChainConveyorTileEntity other) || !other.connections.containsKey(pos)) {
                removeOneWay(target); topologyChanged();
            }
        }
    }
    @Nullable private Route findRoute(ItemStack box) {
        long interval = world.getTotalWorldTime() / 10;
        if (routeCacheTick != interval) { routeCacheTick = interval; routeCache.clear(); }
        String address = PackageItem.address(box);
        Optional<Route> cached = routeCache.get(address);
        if (cached != null) return cached.orElse(null);
        ArrayDeque<ChainConveyorTileEntity> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> hops = new HashMap<>(); Map<BlockPos, Integer> distances = new HashMap<>();
        queue.add(this); distances.put(pos, 0); Route best = null;
        while (!queue.isEmpty() && distances.size() <= 1024) {
            ChainConveyorTileEntity wheel = queue.remove(); int distance = distances.get(wheel.pos);
            for (BlockPos portPos : new ArrayList<>(wheel.ports)) {
                if (!world.isBlockLoaded(portPos) || !(world.getTileEntity(portPos) instanceof PackagePortTileEntity port)
                        || !wheel.pos.equals(port.chainTarget())) { wheel.ports.remove(portPos); continue; }
                if (!port.matchesIncoming(box)) continue;
                boolean wildcard = "*".equals(port.address());
                BlockPos hop = wheel == this && port.chainEnd() != null ? port.chainEnd() : hops.get(wheel.pos);
                if (hop != null && !wheel.connections.containsKey(hop) && wheel == this) continue;
                Route candidate = new Route(hop, portPos, distance, wildcard);
                if (best == null || best.wildcard() && !wildcard || best.wildcard() == wildcard && distance < best.distance()) best = candidate;
            }
            for (BlockPos next : wheel.connections.keySet()) {
                if (distances.containsKey(next) || !world.isBlockLoaded(next)
                        || !(world.getTileEntity(next) instanceof ChainConveyorTileEntity other) || !other.connections.containsKey(wheel.pos)) continue;
                distances.put(next, distance + 1); hops.put(next, wheel == this ? next : hops.get(wheel.pos)); queue.add(other);
            }
        }
        routeCache.put(address, Optional.ofNullable(best)); return best;
    }

    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(MAX_LENGTH + 2); }
    @Override public double getMaxRenderDistanceSquared() { return 256D * 256D; }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); writeContents(tag); return tag;
    }
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        writeContents(tag);
        return tag;
    }
    private void writeContents(NBTTagCompound tag) {
        NBTTagList links = new NBTTagList();
        for (Map.Entry<BlockPos, ItemStack> e : connections.entrySet()) {
            NBTTagCompound n = new NBTTagCompound(); n.setLong("Offset", e.getKey().subtract(pos).toLong()); n.setTag("Material", e.getValue().writeToNBT(new NBTTagCompound())); links.appendTag(n);
        }
        NBTTagList boxes = new NBTTagList(); for (Parcel p : packages) {
            NBTTagCompound n = new NBTTagCompound(); n.setTag("Item", p.item.writeToNBT(new NBTTagCompound())); n.setFloat("Progress", p.position);
            n.setBoolean("Reversed", p.reversed); if (p.target != null) n.setLong("TargetOffset", p.target.subtract(pos).toLong()); boxes.appendTag(n);
        }
        tag.setTag("ChainConnections", links); tag.setTag("ChainPackages", boxes);
    }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); readContents(tag); }
    @Override public void handleUpdateTag(NBTTagCompound tag) { super.handleUpdateTag(tag); readContents(tag); }
    private void readContents(NBTTagCompound tag) {
        connections.clear(); NBTTagList links = tag.getTagList("ChainConnections", 10);
        for (int i = 0; i < links.tagCount(); i++) { NBTTagCompound n = links.getCompoundTagAt(i); ItemStack item = new ItemStack(n.getCompoundTag("Material"));
            if (!item.isEmpty()) connections.put(n.hasKey("Offset") ? pos.add(BlockPos.fromLong(n.getLong("Offset"))) : BlockPos.fromLong(n.getLong("Pos")), item); }
        packages.clear(); NBTTagList boxes = tag.getTagList("ChainPackages", 10);
        for (int i = 0; i < boxes.tagCount(); i++) { NBTTagCompound n = boxes.getCompoundTagAt(i); ItemStack item = new ItemStack(n.getCompoundTag("Item"));
            if (PackageItem.isPackage(item)) packages.add(new Parcel(item, n.hasKey("TargetOffset") ? pos.add(BlockPos.fromLong(n.getLong("TargetOffset")))
                    : n.hasKey("Target") ? BlockPos.fromLong(n.getLong("Target")) : null, n.getFloat("Progress"), n.getBoolean("Reversed"))); }
    }
}
