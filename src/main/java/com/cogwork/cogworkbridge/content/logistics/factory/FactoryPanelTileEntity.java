package com.cogwork.cogworkbridge.content.logistics.factory;

import com.cogwork.cogworkbridge.content.logistics.packager.PackagerTileEntity;
import com.cogwork.cogworkbridge.content.logistics.packager.StockLinkNetwork;
import com.cogwork.cogworkbridge.content.logistics.packager.StockOrder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.items.IItemHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Four independently tuned stock panels, including a packager restock target. */
public final class FactoryPanelTileEntity extends TileEntity implements ITickable {
    private final UUID[] network = new UUID[4];
    private final ItemStack[] filter = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final int[] target = new int[4], current = new int[4], promised = new int[4], lastTargetCount = new int[4];
    private final int[] displayedPromises = new int[4];
    private final int[] recipeOutput = {1, 1, 1, 1};
    private final List<List<Connection>> inputs = java.util.Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private final List<List<ItemStack>> patterns = java.util.Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private final long[] expires = new long[4];
    private final String[] address = {"", "", "", ""};
    public record Connection(BlockPos pos, int slot, int amount, int bendMode) {
        public Connection(BlockPos pos, int slot, int amount) { this(pos, slot, amount, -1); }
    }
    public boolean active(int slot) { return slot >= 0 && slot < 4 && network[slot] != null; }
    public UUID network(int slot) { return active(slot) ? network[slot] : null; }
    public ItemStack filter(int slot) { return active(slot) ? filter[slot] : ItemStack.EMPTY; }
    public int target(int slot) { return target[slot]; }
    public int current(int slot) { return current[slot]; }
    public int promised(int slot) { return displayedPromises[slot]; }
    public int recipeOutput(int slot) { return recipeOutput[slot]; }
    public boolean crafting(int slot) { return active(slot) && patterns.get(slot).size() == 9; }
    public List<Connection> inputs(int slot) { return List.copyOf(inputs.get(slot)); }
    public String address(int slot) { return address[slot]; }
    @Override public AxisAlignedBB getRenderBoundingBox() {
        AxisAlignedBB bounds = super.getRenderBoundingBox();
        for (List<Connection> list : inputs)
            for (Connection connection : list) bounds = bounds.union(new AxisAlignedBB(connection.pos()));
        return bounds;
    }
    public boolean add(int slot, UUID frequency) {
        if (slot < 0 || slot >= 4 || active(slot) || frequency == null) return false;
        network[slot] = frequency; changed(); return true;
    }
    public void setFilter(int slot, ItemStack stack) {
        if (!active(slot)) return;
        filter[slot] = stack.copy(); filter[slot].setCount(1); patterns.get(slot).clear(); promised[slot] = 0; displayedPromises[slot] = 0; lastTargetCount[slot] = 0; changed();
    }
    public void configure(int slot, int count, String destination, int output, boolean crafting) {
        if (!active(slot)) return;
        target[slot] = Math.max(0, Math.min(4096, count));
        address[slot] = destination.substring(0, Math.min(25, destination.length()));
        recipeOutput[slot] = Math.max(1, Math.min(64, output));
        if (!crafting) patterns.get(slot).clear();
        else if (patterns.get(slot).isEmpty()) enableCrafting(slot);
        changed();
    }
    private void enableCrafting(int slot) {
        List<ItemStack> samples = new ArrayList<>();
        for (Connection connection : inputs.get(slot)) {
            if (!world.isBlockLoaded(connection.pos()) || !(world.getTileEntity(connection.pos()) instanceof FactoryPanelTileEntity source)
                    || !source.active(connection.slot())) return;
            samples.add(source.filter(connection.slot()));
        }
        FactoryPanelRecipes.Match match = FactoryPanelRecipes.find(world, samples, filter[slot]);
        if (match == null) return;
        for (int i = 0; i < match.amounts().length; i++) {
            Connection old = inputs.get(slot).get(i);
            inputs.get(slot).set(i, new Connection(old.pos(), old.slot(), match.amounts()[i], old.bendMode()));
        }
        patterns.get(slot).addAll(match.pattern()); recipeOutput[slot] = Math.min(64, match.output());
    }
    public boolean connect(int destinationSlot, BlockPos sourcePos, int sourceSlot, int amount) {
        if (world == null || world.isRemote || !active(destinationSlot) || filter[destinationSlot].isEmpty()
                || sourceSlot < 0 || sourceSlot >= 4 || amount < 1 || amount > 64 || !world.isBlockLoaded(sourcePos)
                || pos.distanceSq(sourcePos) >= 256 || pos.equals(sourcePos) && destinationSlot == sourceSlot
                || !(world.getTileEntity(sourcePos) instanceof FactoryPanelTileEntity source)
                || !source.active(sourceSlot) || source.filter(sourceSlot).isEmpty()) return false;
        var state = world.getBlockState(pos); var other = world.getBlockState(sourcePos);
        if (!(state.getBlock() instanceof FactoryPanelBlock) || !(other.getBlock() instanceof FactoryPanelBlock)) return false;
        EnumFacing facing = state.getValue(FactoryPanelBlock.FACING);
        if (other.getValue(FactoryPanelBlock.FACING) != facing || sourcePos.getX() != pos.getX() && facing.getAxis() == EnumFacing.Axis.X
                || sourcePos.getY() != pos.getY() && facing.getAxis() == EnumFacing.Axis.Y
                || sourcePos.getZ() != pos.getZ() && facing.getAxis() == EnumFacing.Axis.Z
                || world.getTileEntity(pos.offset(facing.getOpposite())) instanceof PackagerTileEntity) return false;
        List<Connection> list = inputs.get(destinationSlot);
        for (int i = 0; i < list.size(); i++) {
            Connection existing = list.get(i);
            if (existing.pos().equals(sourcePos) && existing.slot() == sourceSlot) {
                list.set(i, new Connection(sourcePos.toImmutable(), sourceSlot, amount, existing.bendMode())); patterns.get(destinationSlot).clear(); changed(); return true;
            }
        }
        if (list.size() >= 9) return false;
        list.add(new Connection(sourcePos.toImmutable(), sourceSlot, amount)); patterns.get(destinationSlot).clear(); changed(); return true;
    }
    public void clearConnections(int slot) { if (active(slot) && !inputs.get(slot).isEmpty()) { inputs.get(slot).clear(); patterns.get(slot).clear(); changed(); } }
    public boolean cycleOutgoingPaths(int slot) {
        if (world == null || world.isRemote || !active(slot)) return false;
        int shared = -1; boolean any = false;
        for (TileEntity tile : new ArrayList<>(world.loadedTileEntityList)) {
            if (!(tile instanceof FactoryPanelTileEntity other) || pos.distanceSq(other.pos) >= 256) continue;
            for (List<Connection> list : other.inputs) for (int i=0;i<list.size();i++) {
                Connection old=list.get(i);
                if (!old.pos().equals(pos) || old.slot()!=slot) continue;
                if (shared < 0) shared=(old.bendMode()+1)%4;
                list.set(i,new Connection(old.pos(),old.slot(),old.amount(),shared)); other.changed(); any=true;
            }
        }
        return any;
    }
    public void clearPromises(int slot) {
        if (world == null || world.isRemote || !active(slot) || !(world.getBlockState(pos).getBlock() instanceof FactoryPanelBlock)) return;
        EnumFacing facing = world.getBlockState(pos).getValue(FactoryPanelBlock.FACING);
        if (world.getTileEntity(pos.offset(facing.getOpposite())) instanceof PackagerTileEntity) promised[slot] = 0;
        else FactoryPromises.clear(world, network[slot], filter[slot]);
        displayedPromises[slot] = 0;
        changed();
    }
    private static boolean matches(ItemStack a, ItemStack b) { return !a.isEmpty() && !b.isEmpty() && ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b); }
    private static int count(IItemHandler inventory, ItemStack item) {
        if (inventory == null) return 0;
        int result = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (matches(stack, item)) result = (int) Math.min(Integer.MAX_VALUE, (long) result + stack.getCount());
        }
        return result;
    }
    @Override public void update() {
        if (world == null || world.isRemote || world.getMinecraftServer() == null || world.getTotalWorldTime() % 20 != 0
                || !(world.getBlockState(pos).getBlock() instanceof FactoryPanelBlock)) return;
        var facing = world.getBlockState(pos).getValue(FactoryPanelBlock.FACING);
        TileEntity back = world.getTileEntity(pos.offset(facing.getOpposite()));
        PackagerTileEntity destination = back instanceof PackagerTileEntity packager ? packager : null;
        IItemHandler destinationInventory = destination == null ? null : destination.targetInventory();
        boolean dirty = false;
        for (int i = 0; i < 4; i++) {
            if (!active(i) || filter[i].isEmpty()) continue;
            int value = 0;
            if (destination != null) value = count(destinationInventory, filter[i]);
            else for (StockLinkNetwork.StockEntry entry : StockLinkNetwork.summary(world.getMinecraftServer(), network[i]))
                if (matches(entry.item, filter[i])) { value = entry.count; break; }
            if (value != current[i]) { current[i] = value; dirty = true; }
            if (destination != null && destinationInventory == null) continue;
            int outstanding;
            if (destination != null) {
                if (value > lastTargetCount[i]) promised[i] = Math.max(0, promised[i] - (value - lastTargetCount[i]));
                lastTargetCount[i] = value;
                if (world.getTotalWorldTime() >= expires[i]) promised[i] = 0;
                outstanding = promised[i];
            } else {
                if (promised[i] > 0) {
                    FactoryPromises.add(world, network[i], filter[i], promised[i]);
                    promised[i] = 0;
                    dirty = true;
                }
                outstanding = FactoryPromises.get(world, network[i], filter[i]);
            }
            if (displayedPromises[i] != outstanding) { displayedPromises[i] = outstanding; dirty = true; }
            if ((long) target[i] <= (long) value + outstanding || address[i].trim().isEmpty() || world.isBlockPowered(pos)) continue;
            if (destination != null) {
                int requested = Math.min(576, target[i] - value - outstanding);
                int accepted = StockLinkNetwork.requestRestock(world.getMinecraftServer(), network[i], filter[i], requested, address[i], destination);
                if (accepted > 0) { promised[i] += accepted; displayedPromises[i] = promised[i]; expires[i] = world.getTotalWorldTime() + 1200; dirty = true; }
            } else if (!inputs.get(i).isEmpty()) {
                Map<UUID, List<StockOrder.Line>> order = new LinkedHashMap<>();
                int expected = 0;
                for (Connection connection : inputs.get(i)) {
                    if (!world.isBlockLoaded(connection.pos()) || !(world.getTileEntity(connection.pos()) instanceof FactoryPanelTileEntity source)
                            || !source.active(connection.slot()) || source.filter(connection.slot()).isEmpty()) { expected = -1; break; }
                    order.computeIfAbsent(source.network[connection.slot()], ignored -> new ArrayList<>())
                            .add(new StockOrder.Line(source.filter(connection.slot()), connection.amount()));
                    expected += connection.amount();
                }
                List<ItemStack> pattern = patterns.get(i);
                if (!pattern.isEmpty() && (!FactoryPanelRecipes.matches(world, pattern, filter[i]) || !StockOrder.matchesPattern(order, pattern))) continue;
                if (expected > 0 && StockLinkNetwork.requestNetworks(world.getMinecraftServer(), order, address[i], pattern) == expected) {
                    FactoryPromises.add(world, network[i], filter[i], recipeOutput[i]);
                    displayedPromises[i] = FactoryPromises.get(world, network[i], filter[i]); dirty = true;
                }
            }
        }
        if (dirty) changed();
    }
    private void changed() { markDirty(); if (world != null && !world.isRemote) world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); NBTTagList panels = new NBTTagList();
        for (int i = 0; i < 4; i++) {
            NBTTagCompound data = new NBTTagCompound();
            if (network[i] != null) data.setUniqueId("Network", network[i]);
            if (!filter[i].isEmpty()) data.setTag("Filter", filter[i].writeToNBT(new NBTTagCompound()));
            data.setInteger("Target", target[i]); data.setInteger("Current", current[i]); data.setInteger("Promised", promised[i]);
            data.setInteger("DisplayedPromises", displayedPromises[i]);
            data.setInteger("RecipeOutput", recipeOutput[i]); NBTTagList connections = new NBTTagList();
            for (Connection connection : inputs.get(i)) {
                NBTTagCompound entry = new NBTTagCompound(); entry.setLong("Pos", connection.pos().toLong());
                entry.setInteger("Slot", connection.slot()); entry.setInteger("Amount", connection.amount()); entry.setInteger("ArrowBend", connection.bendMode()); connections.appendTag(entry);
            }
            data.setTag("Inputs", connections);
            if (patterns.get(i).size() == 9) data.setTag("Craft", StockOrder.writePattern(patterns.get(i)));
            data.setInteger("LastTargetCount", lastTargetCount[i]); data.setLong("Expires", expires[i]); data.setString("Address", address[i]); panels.appendTag(data);
        }
        tag.setTag("Panels", panels); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); NBTTagList panels = tag.getTagList("Panels", 10);
        for (int i = 0; i < 4; i++) {
            NBTTagCompound data = i < panels.tagCount() ? panels.getCompoundTagAt(i) : new NBTTagCompound();
            network[i] = data.hasUniqueId("Network") ? data.getUniqueId("Network") : null;
            filter[i] = data.hasKey("Filter", 10) ? new ItemStack(data.getCompoundTag("Filter")) : ItemStack.EMPTY;
            target[i] = Math.max(0, Math.min(4096, data.getInteger("Target"))); current[i] = Math.max(0, data.getInteger("Current"));
            promised[i] = Math.max(0, data.getInteger("Promised")); lastTargetCount[i] = Math.max(0, data.getInteger("LastTargetCount"));
            displayedPromises[i] = Math.max(0, data.getInteger("DisplayedPromises"));
            recipeOutput[i] = Math.max(1, Math.min(64, data.getInteger("RecipeOutput")));
            inputs.get(i).clear(); NBTTagList connections = data.getTagList("Inputs", 10);
            patterns.get(i).clear(); patterns.get(i).addAll(StockOrder.readPattern(data.getTagList("Craft", 10)));
            for (int j = 0; j < Math.min(9, connections.tagCount()); j++) {
                NBTTagCompound entry = connections.getCompoundTagAt(j); int slot = entry.getInteger("Slot"), amount = entry.getInteger("Amount");
                if (slot >= 0 && slot < 4 && amount > 0 && amount <= 64)
                    inputs.get(i).add(new Connection(BlockPos.fromLong(entry.getLong("Pos")), slot, amount,
                            entry.hasKey("ArrowBend") ? Math.max(-1, Math.min(3, entry.getInteger("ArrowBend"))) : -1));
            }
            expires[i] = data.getLong("Expires"); address[i] = data.getString("Address");
            if (address[i].length() > 25) address[i] = address[i].substring(0, 25);
        }
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
