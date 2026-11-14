package com.cogwork.cogworkbridge.content.schematics;

import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.content.equipment.clipboard.ClipboardData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Server-side printer. Files and progress survive unloading; ingredients come from adjacent inventories. */
public final class SchematicannonTile extends SchematicInventoryTile implements ITickable {
    private static final int SHOTS_PER_POWDER = 400;
    private SchematicData loaded;
    private String loadedKey = "";
    private String progressKey = "";
    private int cursor, fuel, cooldown, shots;
    private boolean running, skipMissing, replaceBlockEntities;
    private int replaceMode = 2;
    private String status = "idle";
    private BlockPos target = BlockPos.ORIGIN;
    private boolean hasTarget;
    private IBlockState lastShotState = Blocks.AIR.getDefaultState();
    private int visualFlight, shotDuration = 10;
    private BlockPos previousTarget = BlockPos.ORIGIN;
    private final List<Flight> flights = new ArrayList<>();
    // Buckets cannot be partially drained. Keep their unused fluid for later shots.
    private final Map<String, Integer> fluidReserve = new LinkedHashMap<>();
    private final IFluidHandler reserveOutput = new IFluidHandler() {
        @Override public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> properties = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : fluidReserve.entrySet()) {
                Fluid fluid = FluidRegistry.getFluid(entry.getKey());
                if (fluid != null && entry.getValue() > 0)
                    properties.add(new FluidTankProperties(new FluidStack(fluid, entry.getValue()), entry.getValue(), false, true));
            }
            return properties.toArray(new IFluidTankProperties[0]);
        }
        @Override public int fill(FluidStack resource, boolean doFill) { return 0; }
        @Nullable @Override public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0 || resource.getFluid() == null) return null;
            String key = resource.getFluid().getName();
            int available = fluidReserve.getOrDefault(key, 0);
            int amount = Math.min(available, resource.amount);
            if (amount <= 0) return null;
            if (doDrain) {
                if (amount == available) fluidReserve.remove(key); else fluidReserve.put(key, available - amount);
                changed();
            }
            return new FluidStack(resource.getFluid(), amount);
        }
        @Nullable @Override public FluidStack drain(int maxDrain, boolean doDrain) {
            if (maxDrain <= 0) return null;
            for (Map.Entry<String, Integer> entry : fluidReserve.entrySet()) {
                Fluid fluid = FluidRegistry.getFluid(entry.getKey());
                if (fluid != null && entry.getValue() > 0) return drain(new FluidStack(fluid, maxDrain), doDrain);
            }
            return null;
        }
    };
    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                ? CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(reserveOutput) : super.getCapability(capability, side);
    }
    public SchematicannonTile() { super(5); }
    @Override public boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> stack.getItem() == AllItems.SCHEMATIC;
            case 2 -> stack.getItem() == Items.BOOK || stack.getItem() == Items.WRITABLE_BOOK
                    || stack.getItem() == Items.WRITTEN_BOOK || stack.getItem() == Item.getItemFromBlock(AllBlocks.CLIPBOARD);
            case 4 -> stack.getItem() == Items.GUNPOWDER;
            default -> false;
        };
    }
    public boolean isRunning() { return running; }
    public boolean skipMissing() { return skipMissing; }
    public int replaceMode() { return replaceMode; }
    public boolean replaceBlockEntities() { return replaceBlockEntities; }
    public int progress() { return cursor; }
    public int total() { return loaded == null ? totalCached : loaded.blocks.size() + loaded.entities.size()
            + loaded.containedFluids.size() + (replaceMode == 3 ? loaded.missingAir().size() : 0); }
    public int fuel() { return fuel; }
    public String status() { return status; }
    public BlockPos target() { return target; }
    public boolean hasTarget() { return hasTarget; }
    public BlockPos previousTarget() { return previousTarget; }
    public IBlockState lastShotState() { return lastShotState; }
    public int visualFlight() { return visualFlight; }
    public int shotDuration() { return shotDuration; }
    public List<Flight> flights() { return Collections.unmodifiableList(flights); }
    private int totalCached;
    public void action(int action) {
        if (action == 0) { running = !running; status = running ? "searching" : "paused"; }
        if (action == 1) { running = false; cursor = 0; progressKey = ""; status = "stopped"; }
        if (action == 2) { replaceMode = (replaceMode + 1) % 4; loaded = null; }
        if (action == 3) skipMissing = !skipMissing;
        if (action == 4) replaceBlockEntities = !replaceBlockEntities;
        if (action == 5) { running = true; status = "searching"; }
        if (action == 6) { running = false; status = "paused"; }
        if (action >= 7 && action <= 10) { replaceMode = action - 7; loaded = null; }
        changed();
    }
    private String key(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return "";
        NBTTagCompound n = stack.getTagCompound();
        return n.getString("FileId") + ":" + n.getLong("Anchor") + ":" + n.getInteger("Rotation") + ":" + SchematicData.mirror(stack).name() + ":" + replaceMode;
    }
    private boolean load() {
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack.isEmpty() || !stack.hasTagCompound()) {
            loaded = null; loadedKey = ""; status = "not_deployed"; return false;
        }
        String key = key(stack);
        if (loaded != null && loadedKey.equals(key)) return true;
        Path file = SchematicData.serverFile(world, stack);
        try {
            if (file == null) throw new IOException("missing file");
            loaded = SchematicData.read(SchematicData.readFile(file));
        } catch (IOException ex) {
            loaded = null; loadedKey = ""; status = "invalid"; return false;
        }
        loadedKey = key;
        totalCached = loaded.blocks.size() + loaded.entities.size() + loaded.containedFluids.size()
                + (replaceMode == 3 ? loaded.missingAir().size() : 0);
        if (!key.equals(progressKey)) { progressKey = key; cursor = 0; }
        if (cursor < 0 || cursor > totalCached) cursor = 0;
        return true;
    }
    @Override public void update() {
        if (world == null) return;
        if (world.isRemote) {
            if (visualFlight > 0) visualFlight--;
            for (Flight flight : flights) if (flight.ticks > 0) flight.ticks--;
            return;
        }
        tickFlights();
        if (!inventory.getStackInSlot(2).isEmpty() && inventory.getStackInSlot(3).isEmpty()) makeChecklist();
        if (!running) return;
        if (!load()) { running = false; changed(); return; }
        if (!inventory.getStackInSlot(0).getTagCompound().getBoolean("Deployed")) {
            running = false; status = "not_deployed"; changed(); return;
        }
        if (cooldown > 0) { cooldown--; return; }
        ItemStack plan = inventory.getStackInSlot(0);
        int scanned = 0;
        while (replaceMode != 3 && cursor < loaded.blocks.size() && scanned++ < 512
                && loaded.blocks.get(cursor).state.getMaterial() == Material.AIR) cursor++;
        if (cursor >= totalCached) { if (flights.isEmpty()) finish(); return; }
        if (scanned > 512) { markDirty(); return; }
        int blockCount = loaded.blocks.size() + (replaceMode == 3 ? loaded.missingAir().size() : 0);
        int entityEnd = blockCount + loaded.entities.size();
        if (cursor >= entityEnd) {
            // Do not inject fluid into a host whose block is still in flight.
            if (cursor == entityEnd && !flights.isEmpty()) return;
            printContainedFluid(plan, loaded.containedFluids.get(cursor - entityEnd));
            return;
        }
        if (cursor >= blockCount) { printEntity(plan, loaded.entities.get(cursor - blockCount)); return; }
        SchematicData.Entry entry = cursor < loaded.blocks.size() ? loaded.blocks.get(cursor)
                : loaded.missingAir().get(cursor - loaded.blocks.size());
        Rotation rotation = SchematicData.rotation(plan);
        Mirror mirror = SchematicData.mirror(plan);
        BlockPos at = entry.target(SchematicData.anchor(plan), mirror, rotation);
        if (at.getY() < 0 || at.getY() >= world.getHeight() || pos.distanceSq(at) > 256D * 256D) {
            running = false; status = "out_of_range"; changed(); return;
        }
        if (!world.isBlockLoaded(at)) { status = "unloaded"; cooldown = 20; changed(); return; }
        IBlockState desired;
        try { desired = entry.transformedState(mirror, rotation); }
        catch (RuntimeException ex) { advance("unsupported"); return; }
        if (desired.getBlock() == Blocks.STRUCTURE_VOID) { advance("searching"); return; }
        boolean air = desired.getMaterial() == Material.AIR;
        if (air && replaceMode != 3) { advance("searching"); return; }
        IBlockState existing = world.getBlockState(at);
        Fluid fluid = SchematicFluids.fluidOf(desired);
        int fluidAmount = fluid == null ? 0 : SchematicFluids.requiredAmount(desired);
        if (fluid != null && awaitingEarlierFluidStage(fluidAmount)) return;
        if (existing.equals(desired)) { advance("searching"); return; }
        if (fluid != null && SchematicFluids.alreadyPlaced(existing, desired)) { advance("searching"); return; }
        if (pos.distanceSq(at) < 4D || !world.getWorldBorder().contains(at)
                || existing.getBlockHardness(world, at) < 0 || !replaceBlockEntities && world.getTileEntity(at) != null
                || replaceMode == 0 && existing.isFullCube()
                || replaceMode == 1 && !desired.isFullCube() && existing.isFullCube()) {
            advance("skipped"); return;
        }
        ItemStack required = air || fluid != null ? ItemStack.EMPTY : itemFor(desired, at);
        if (required == null) {
            if (skipMissing) advance("skipped");
            else { status = "unsupported"; running = false; changed(); }
            return;
        }
        boolean plant = fluid == null && usesPlantingInteraction(desired, required);
        if (plant && samePlant(existing, desired)) { advance("searching"); return; }
        List<ItemStack> requirements = requirementsFor(desired, entry.tile, required);
        if (requirements == null) { advance("unsupported"); return; }
        FluidStack fluidRequired = fluidAmount > 0 ? new FluidStack(fluid, fluidAmount) : null;
        if (fluidRequired != null && !creativeSupply() && !hasFluid(fluidRequired)
                || takeRequirements(requirements, true) == null) {
            status = "missing"; cooldown = 20;
            if (skipMissing) advance("skipped"); else changed();
            return;
        }
        if (fuel <= 0 && !creativeSupply()) {
            ItemStack powder = inventory.extractItem(4, 1, true);
            if (!powder.isEmpty()) { inventory.extractItem(4, 1, false); fuel = SHOTS_PER_POWDER; }
            else if (takeFromAdjacent(new ItemStack(Items.GUNPOWDER), true)) {
                takeFromAdjacent(new ItemStack(Items.GUNPOWDER), false); fuel = SHOTS_PER_POWDER;
            } else { status = "no_powder"; cooldown = 20; changed(); return; }
        }
        boolean suppliedCreatively = creativeSupply();
        ItemStack launchedMaterial = ItemStack.EMPTY;
        List<ItemStack> paid = Collections.emptyList();
        FluidStack cargo = null;
        if (fluidRequired != null && !suppliedCreatively) {
            FluidSupply supply = takeFluid(fluidRequired);
            if (supply == null) { status = "missing"; cooldown = 20; changed(); return; }
            launchedMaterial = supply.item; cargo = supply.fluid;
        } else if (plant) {
            launchedMaterial = suppliedCreatively ? required.copy() : extractPlantMaterial(required);
            if (launchedMaterial.isEmpty()) { status = "missing"; cooldown = 20; changed(); return; }
            if (!suppliedCreatively) {
                List<ItemStack> extras = requirements.subList(required.isEmpty() ? 0 : 1, requirements.size());
                paid = takeRequirements(extras, false);
                if (paid == null) { returnMaterial(launchedMaterial); status = "missing"; cooldown = 20; changed(); return; }
            }
        } else if (!suppliedCreatively) {
            paid = takeRequirements(requirements, false);
            if (paid == null) { status = "missing"; cooldown = 20; changed(); return; }
        }
        shotDuration = flightDuration(at);
        Flight flight = new Flight(at, desired, entry.tile, mirror, rotation, null, shotDuration,
                launchedMaterial, (plant || fluidRequired != null) && !suppliedCreatively, cursor);
        flight.fluidCargo = cargo;
        flight.paidMaterials.addAll(paid);
        flights.add(flight);
        if (!suppliedCreatively) fuel--;
        shots++; target = at; hasTarget = true; lastShotState = desired;
        world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, .25F, 1.5F);
        advance("placing");
    }
    private boolean awaitingEarlierFluidStage(int currentAmount) {
        for (Flight flight : flights) {
            if (flight.state == null || flight.entity != null) continue;
            Fluid earlier = SchematicFluids.fluidOf(flight.state);
            if (earlier == null || currentAmount == 0 && SchematicFluids.requiredAmount(flight.state) > 0) return true;
        }
        return false;
    }

    private void printContainedFluid(ItemStack plan, SchematicData.Entry entry) {
        Rotation rotation = SchematicData.rotation(plan); Mirror mirror = SchematicData.mirror(plan);
        BlockPos at = entry.target(SchematicData.anchor(plan), mirror, rotation);
        if (at.getY() < 0 || at.getY() >= world.getHeight() || pos.distanceSq(at) > 256D * 256D) {
            running = false; status = "out_of_range"; changed(); return;
        }
        if (!world.isBlockLoaded(at)) { status = "unloaded"; cooldown = 20; changed(); return; }
        IBlockState desired = entry.containedFluid;
        IBlockState host;
        try { host = entry.transformedState(mirror, rotation); }
        catch (RuntimeException ex) { advance("unsupported"); return; }
        if (!world.getWorldBorder().contains(at) || world.getBlockState(at).getBlock() != host.getBlock()) {
            if (skipMissing) advance("skipped");
            else { running = false; status = "blocked"; changed(); }
            return;
        }
        if (SchematicFluids.alreadyContained(world, at, desired)) { advance("searching"); return; }
        Fluid fluid = SchematicFluids.fluidOf(desired);
        int amount = SchematicFluids.requiredAmount(desired);
        FluidStack required = amount > 0 ? new FluidStack(fluid, amount) : null;
        boolean creative = creativeSupply();
        if (required != null && !creative && !hasFluid(required)) {
            status = "missing"; cooldown = 20;
            if (skipMissing) advance("skipped"); else changed();
            return;
        }
        if (fuel <= 0 && !creative) {
            if (!inventory.extractItem(4, 1, true).isEmpty()) {
                inventory.extractItem(4, 1, false); fuel = SHOTS_PER_POWDER;
            } else if (takeFromAdjacent(new ItemStack(Items.GUNPOWDER), true)) {
                takeFromAdjacent(new ItemStack(Items.GUNPOWDER), false); fuel = SHOTS_PER_POWDER;
            } else { status = "no_powder"; cooldown = 20; changed(); return; }
        }
        FluidSupply supply = required != null && !creative ? takeFluid(required) : null;
        if (required != null && !creative && supply == null) { status = "missing"; cooldown = 20; changed(); return; }
        shotDuration = flightDuration(at);
        Flight flight = new Flight(at, desired, null, mirror, rotation, null, shotDuration,
                supply == null ? ItemStack.EMPTY : supply.item, required != null && !creative, cursor);
        flight.contained = true; flight.storedFluid = entry.storedFluid; flight.expectedHost = host;
        flight.fluidCargo = supply == null ? null : supply.fluid;
        flights.add(flight);
        if (!creative) fuel--;
        shots++; target = at; hasTarget = true; lastShotState = desired;
        world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, .25F, 1.5F);
        advance("placing");
    }

    private void printEntity(ItemStack plan, SchematicData.EntityEntry entry) {
        Rotation rotation = SchematicData.rotation(plan); Mirror mirror = SchematicData.mirror(plan);
        net.minecraft.util.math.Vec3d at = entry.target(SchematicData.anchor(plan), mirror, rotation);
        BlockPos targetPos = new BlockPos(at);
        if (targetPos.getY() < 0 || targetPos.getY() >= world.getHeight() || pos.distanceSq(targetPos) > 256D * 256D) {
            running = false; status = "out_of_range"; changed(); return;
        }
        if (!world.isBlockLoaded(targetPos)) { status = "unloaded"; cooldown = 20; changed(); return; }
        if (!world.getWorldBorder().contains(targetPos)) { advance("skipped"); return; }
        List<ItemStack> required = entry.requirements();
        for (ItemStack item : required) {
            if (!takeFromAdjacent(item, true, true)) {
                status = "missing"; cooldown = 20;
                if (skipMissing) advance("skipped"); else changed();
                return;
            }
        }
        if (fuel <= 0 && !creativeSupply()) {
            if (!inventory.extractItem(4, 1, true).isEmpty()) { inventory.extractItem(4, 1, false); fuel = SHOTS_PER_POWDER; }
            else if (takeFromAdjacent(new ItemStack(Items.GUNPOWDER), true)) {
                takeFromAdjacent(new ItemStack(Items.GUNPOWDER), false); fuel = SHOTS_PER_POWDER;
            } else { status = "no_powder"; cooldown = 20; changed(); return; }
        }
        NBTTagCompound data = entry.data.copy();
        NBTTagList location = new NBTTagList();
        location.appendTag(new NBTTagDouble(at.x)); location.appendTag(new NBTTagDouble(at.y)); location.appendTag(new NBTTagDouble(at.z));
        data.setTag("Pos", location); data.setUniqueId("UUID", UUID.randomUUID());
        BlockPos hanging = SchematicData.transformed(entry.blockPos, mirror, rotation).add(SchematicData.anchor(plan));
        data.setInteger("TileX", hanging.getX()); data.setInteger("TileY", hanging.getY()); data.setInteger("TileZ", hanging.getZ());
        net.minecraft.entity.Entity entity;
        try {
            entity = net.minecraft.entity.EntityList.createEntityFromNBT(data, world);
            if (entity == null) { advance("unsupported"); return; }
            float yaw = entity.getMirroredYaw(mirror);
            yaw += entity.rotationYaw - entity.getRotatedYaw(rotation);
            entity.setLocationAndAngles(at.x, at.y, at.z, yaw, entity.rotationPitch);
        } catch (RuntimeException ex) { advance("unsupported"); return; }
        for (ItemStack item : required) takeFromAdjacent(item, false, true);
        NBTTagCompound entityData = entity.writeToNBT(new NBTTagCompound());
        entityData.setString("id", entry.data.getString("id"));
        shotDuration = flightDuration(targetPos);
        flights.add(new Flight(targetPos, null, null, Mirror.NONE, Rotation.NONE, entityData, shotDuration,
                ItemStack.EMPTY, false, -1));
        if (!creativeSupply()) fuel--;
        shots++; target = targetPos; hasTarget = true; lastShotState = Blocks.AIR.getDefaultState();
        world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, .25F, 1.5F);
        advance("placing");
    }
    private void advance(String next) { cursor++; cooldown = "placing".equals(next) ? 10 : 0; status = next; changed(); }

    private int flightDuration(BlockPos at) {
        return (int) Math.max(10D, Math.sqrt(Math.sqrt(pos.distanceSq(at))) * 4D);
    }

    /** Launched blocks continue travelling even when the printer is paused or reloaded. */
    private void tickFlights() {
        if (flights.isEmpty()) return;
        boolean landed = false;
        for (Iterator<Flight> it = flights.iterator(); it.hasNext();) {
            Flight flight = it.next();
            if (flight.ticks > 0) { flight.ticks--; continue; }
            if (!world.isBlockLoaded(flight.target)) continue;
            boolean alreadyPlanted = flight.planting && flight.state != null
                    && samePlant(world.getBlockState(flight.target), flight.state);
            boolean alreadyFluid = flight.state != null && SchematicFluids.fluidOf(flight.state) != null
                    && (flight.contained ? SchematicFluids.alreadyContained(world, flight.target, flight.state)
                    : SchematicFluids.alreadyPlaced(world.getBlockState(flight.target), flight.state));
            if (alreadyPlanted || alreadyFluid) {
                for (ItemStack paid : flight.paidMaterials) returnMaterial(paid);
                if (flight.refundable) { returnMaterial(flight.material); returnFluid(flight.fluidCargo); }
            } else if (!flight.land(world)) {
                for (ItemStack paid : flight.paidMaterials) returnMaterial(paid);
                if (flight.refundable) { returnMaterial(flight.material); returnFluid(flight.fluidCargo); }
                if (flight.sourceIndex >= 0) cursor = Math.min(cursor, flight.sourceIndex);
                status = "blocked"; running = false;
            } else if (flight.refundable) {
                if (!flight.remainder.isEmpty()) returnMaterial(flight.remainder);
                returnFluid(flight.remainderFluid);
            }
            it.remove(); landed = true;
        }
        if (landed) changed(); else markDirty();
    }

    public static final class Flight {
        final BlockPos target;
        @Nullable final IBlockState state;
        @Nullable final NBTTagCompound tile, entity;
        final Mirror mirror;
        final Rotation rotation;
        final ItemStack material;
        final boolean planting, refundable;
        final int sourceIndex;
        boolean contained, storedFluid;
        @Nullable IBlockState expectedHost;
        @Nullable FluidStack fluidCargo, remainderFluid;
        ItemStack remainder = ItemStack.EMPTY;
        final List<ItemStack> paidMaterials = new ArrayList<>();
        int ticks;
        final int duration;
        Flight(BlockPos target, @Nullable IBlockState state, @Nullable NBTTagCompound tile,
                Mirror mirror, Rotation rotation, @Nullable NBTTagCompound entity, int ticks,
                ItemStack material, boolean refundable, int sourceIndex) {
            this.target = target.toImmutable(); this.state = state;
            this.tile = tile == null ? null : tile.copy(); this.entity = entity == null ? null : entity.copy();
            this.mirror = mirror; this.rotation = rotation; this.ticks = ticks; this.duration = ticks;
            this.material = material.copy(); this.planting = state != null && usesPlantingInteraction(state, material);
            this.refundable = refundable; this.sourceIndex = sourceIndex;
        }
        public BlockPos target() { return target; }
        @Nullable public IBlockState state() { return state; }
        public ItemStack material() { return material.copy(); }
        public boolean armorStand() { return entity != null && "minecraft:armor_stand".equals(entity.getString("id")); }
        public int ticks() { return ticks; }
        public int duration() { return duration; }
        boolean land(net.minecraft.world.World world) {
            if (!world.getWorldBorder().contains(target) || target.getY() < 0 || target.getY() >= world.getHeight()) return false;
            if (entity != null) {
                try {
                    net.minecraft.entity.Entity spawned = net.minecraft.entity.EntityList.createEntityFromNBT(entity, world);
                    return spawned != null && world.spawnEntity(spawned);
                } catch (RuntimeException ex) { return false; }
            }
            if (state == null || world.getBlockState(target).getBlockHardness(world, target) < 0) return false;
            if (contained) return placeFluid(world);
            if (world.getBlockState(target).equals(state)) return true;
            if (SchematicFluids.fluidOf(state) != null) return placeFluid(world);
            if (planting) return plant(world);
            if (!world.setBlockState(target, state, 3)) return false;
            if (tile != null) {
                TileEntity placed = world.getTileEntity(target);
                if (placed != null) {
                    NBTTagCompound data = tile.copy();
                    data.setInteger("x", target.getX()); data.setInteger("y", target.getY()); data.setInteger("z", target.getZ());
                    try { placed.readFromNBT(data); placed.mirror(mirror); placed.rotate(rotation); placed.markDirty(); }
                    catch (RuntimeException ignored) { }
                    world.notifyBlockUpdate(target, state, state, 3);
                }
            }
            return true;
        }
        private boolean placeFluid(net.minecraft.world.World world) {
            Fluid fluid = SchematicFluids.fluidOf(state);
            if (fluid == null || fluid == FluidRegistry.WATER && world.provider.doesWaterVaporize()) return false;
            int amount = SchematicFluids.requiredAmount(state);
            IFluidHandlerItem container = null;
            FluidStack drained = null;
            if (refundable && amount > 0) {
                if (fluidCargo != null) {
                    if (fluidCargo.getFluid() != fluid || fluidCargo.amount != amount) return false;
                } else {
                    ItemStack copy = material.copy(); copy.setCount(1);
                    container = FluidUtil.getFluidHandler(copy);
                    if (container == null) return false;
                    drained = drainContainer(container, new FluidStack(fluid, amount), true);
                    if (drained == null || drained.getFluid() != fluid || drained.amount < amount) return false;
                }
            }
            boolean placed = contained
                    ? expectedHost != null && SchematicFluids.placeContained(world, target, expectedHost, state, storedFluid)
                    : world.setBlockState(target, state, 3)
                            && SchematicFluids.alreadyPlaced(world.getBlockState(target), state);
            if (!placed) return false;
            if (container != null) {
                remainder = container.getContainer().copy();
                if (drained.amount > amount) remainderFluid = new FluidStack(fluid, drained.amount - amount);
            }
            return true;
        }
        private boolean plant(net.minecraft.world.World world) {
            if (!(world instanceof net.minecraft.world.WorldServer serverWorld)) return false;
            ItemStack seed = material.copy();
            if (seed.isEmpty()) {
                try { seed = state.getBlock().getItem(world, target, state); }
                catch (RuntimeException ignored) { return false; }
            }
            if (seed.isEmpty()) return false;
            IBlockState previous = world.getBlockState(target);
            if (!previous.getBlock().isReplaceable(world, target)) return false;
            EnumFacing face = EnumFacing.UP;
            BlockPos support = target.down();
            if (state.getBlock() instanceof BlockCocoa) {
                EnumFacing towardSupport = state.getValue(BlockCocoa.FACING);
                support = target.offset(towardSupport); face = towardSupport.getOpposite();
            }
            if (!world.isBlockLoaded(support)) return false;
            FakePlayer fake = FakePlayerFactory.getMinecraft(serverWorld);
            ItemStack oldHand = fake.getHeldItemMainhand().copy();
            double oldX = fake.posX, oldY = fake.posY, oldZ = fake.posZ;
            boolean oldSneaking = fake.isSneaking(), oldCreative = fake.capabilities.isCreativeMode;
            try {
                fake.capabilities.isCreativeMode = false;
                fake.setSneaking(true);
                fake.setPosition(support.getX() + .5D, support.getY() + 1D, support.getZ() + .5D);
                fake.setHeldItem(EnumHand.MAIN_HAND, seed);
                fake.interactionManager.processRightClickBlock(fake, world, fake.getHeldItemMainhand(),
                        EnumHand.MAIN_HAND, support, face, .5F, .5F, .5F);
                if (world.getBlockState(target).getBlock() != state.getBlock()) return false;
                remainder = fake.getHeldItemMainhand().copy();
                return true;
            } catch (RuntimeException ignored) {
                return false;
            } finally {
                fake.setHeldItem(EnumHand.MAIN_HAND, oldHand);
                fake.setPosition(oldX, oldY, oldZ);
                fake.setSneaking(oldSneaking); fake.capabilities.isCreativeMode = oldCreative;
            }
        }
        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound(); tag.setLong("Target", target.toLong()); tag.setInteger("Ticks", ticks);
            tag.setInteger("Duration", duration);
            tag.setInteger("Mirror", mirror.ordinal()); tag.setInteger("Rotation", rotation.ordinal());
            if (state != null) tag.setTag("State", NBTUtil.writeBlockState(new NBTTagCompound(), state));
            if (tile != null) tag.setTag("Tile", tile.copy());
            if (entity != null) tag.setTag("Entity", entity.copy());
            if (!material.isEmpty()) tag.setTag("Material", material.writeToNBT(new NBTTagCompound()));
            NBTTagList paid = new NBTTagList();
            for (ItemStack item : paidMaterials) paid.appendTag(item.writeToNBT(new NBTTagCompound()));
            tag.setTag("PaidMaterials", paid);
            tag.setBoolean("Refundable", refundable); tag.setInteger("SourceIndex", sourceIndex);
            if (fluidCargo != null) tag.setTag("FluidCargo", fluidCargo.writeToNBT(new NBTTagCompound()));
            if (contained && expectedHost != null) {
                tag.setBoolean("ContainedFluid", true); tag.setBoolean("StoredFluid", storedFluid);
                tag.setTag("ExpectedHost", NBTUtil.writeBlockState(new NBTTagCompound(), expectedHost));
            }
            return tag;
        }
        static Flight read(NBTTagCompound tag) {
            IBlockState state = tag.hasKey("State", 10) ? NBTUtil.readBlockState(tag.getCompoundTag("State")) : null;
            NBTTagCompound tile = tag.hasKey("Tile", 10) ? tag.getCompoundTag("Tile") : null;
            NBTTagCompound entity = tag.hasKey("Entity", 10) ? tag.getCompoundTag("Entity") : null;
            ItemStack material = tag.hasKey("Material", 10) ? new ItemStack(tag.getCompoundTag("Material")) : ItemStack.EMPTY;
            Mirror mirror = Mirror.values()[Math.floorMod(tag.getInteger("Mirror"), Mirror.values().length)];
            Rotation rotation = Rotation.values()[Math.floorMod(tag.getInteger("Rotation"), Rotation.values().length)];
            Flight flight = new Flight(BlockPos.fromLong(tag.getLong("Target")), state, tile, mirror, rotation, entity,
                    Math.max(1, tag.hasKey("Duration") ? tag.getInteger("Duration") : tag.getInteger("Ticks")),
                    material, tag.getBoolean("Refundable"), tag.hasKey("SourceIndex") ? tag.getInteger("SourceIndex") : -1);
            flight.ticks = Math.max(0, tag.getInteger("Ticks"));
            NBTTagList paid = tag.getTagList("PaidMaterials", 10);
            for (int i = 0; i < paid.tagCount(); i++) {
                ItemStack item = new ItemStack(paid.getCompoundTagAt(i));
                if (!item.isEmpty()) flight.paidMaterials.add(item);
            }
            if (tag.hasKey("FluidCargo", 10)) flight.fluidCargo = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("FluidCargo"));
            flight.contained = tag.getBoolean("ContainedFluid");
            flight.storedFluid = tag.getBoolean("StoredFluid");
            if (flight.contained && tag.hasKey("ExpectedHost", 10))
                flight.expectedHost = NBTUtil.readBlockState(tag.getCompoundTag("ExpectedHost"));
            return flight;
        }
    }
    private void finish() {
        if (!inventory.getStackInSlot(1).isEmpty()) { running = false; status = "output_full"; changed(); return; }
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        inventory.setStackInSlot(1, new ItemStack(AllItems.EMPTY_SCHEMATIC));
        loaded = null; loadedKey = ""; progressKey = ""; cursor = 0; totalCached = 0;
        running = false; status = "finished"; changed();
    }
    private static boolean usesPlantingInteraction(IBlockState state, ItemStack material) {
        if (!material.isEmpty() && material.getItem() instanceof IPlantable) return true;
        Block block = state.getBlock();
        return block instanceof BlockCocoa || block instanceof IPlantable && ageProperty(state) != null;
    }
    @Nullable private static PropertyInteger ageProperty(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys())
            if (property instanceof PropertyInteger age && "age".equals(age.getName()) && age.getAllowedValues().contains(0)) return age;
        return null;
    }
    private static boolean samePlant(IBlockState existing, IBlockState desired) {
        if (existing.getBlock() != desired.getBlock()) return false;
        PropertyInteger age = ageProperty(desired);
        return age == null ? existing.equals(desired)
                : existing.withProperty(age, 0).equals(desired.withProperty(age, 0));
    }
    /** Returns null for an unsupported state, EMPTY for the non-consuming half of a multiblock. */
    @Nullable private ItemStack itemFor(IBlockState state, BlockPos at) {
        Block block = state.getBlock();
        Fluid fluid = SchematicFluids.fluidOf(state);
        if (fluid != null) {
            int amount = SchematicFluids.requiredAmount(state);
            if (amount == 0) return ItemStack.EMPTY;
            ItemStack container = SchematicFluids.displayContainer(fluid);
            return container.isEmpty() ? findFluidMaterial(new FluidStack(fluid, amount), true) : container;
        }
        if (block instanceof BlockDoor && state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER) return ItemStack.EMPTY;
        if (block instanceof BlockBed && state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD) return ItemStack.EMPTY;
        if (block == Blocks.FARMLAND || block == Blocks.GRASS_PATH) return new ItemStack(Blocks.DIRT);
        ItemStack picked;
        try { picked = block.getItem(world, at, state); }
        catch (RuntimeException ex) { picked = ItemStack.EMPTY; }
        if (picked.isEmpty()) {
            Item item = Item.getItemFromBlock(block);
            if (item == Items.AIR) return null;
            picked = new ItemStack(item, 1, block.damageDropped(state));
        }
        int amount = block instanceof net.minecraft.block.BlockSlab slab && slab.isDouble() ? 2
                : block instanceof net.minecraft.block.BlockSnow ? state.getValue(net.minecraft.block.BlockSnow.LAYERS) : 1;
        picked.setCount(amount); return picked;
    }
    private List<IItemHandler> adjacent() {
        List<IItemHandler> result = new ArrayList<>();
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos other = pos.offset(side);
            if (!world.isBlockLoaded(other)) continue;
            TileEntity tile = world.getTileEntity(other);
            if (tile == null) continue;
            IItemHandler handler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite());
            if (handler != null) result.add(handler);
        }
        return result;
    }
    @Nullable private List<ItemStack> requirementsFor(IBlockState state, @Nullable NBTTagCompound data, ItemStack base) {
        List<ItemStack> result = new ArrayList<>();
        if (!base.isEmpty()) result.add(base.copy());
        try {
            if (state.getBlock() instanceof SchematicMaterialProvider provider)
                for (ItemStack extra : provider.schematicMaterials(state, data)) if (!extra.isEmpty()) result.add(extra.copy());
        } catch (RuntimeException ex) { return null; }
        return result;
    }
    private record MaterialExtraction(IItemHandler handler, int slot, ItemStack expected) { }
    /** Simulate the whole set against shared slot reservations before consuming any of it. */
    @Nullable private List<ItemStack> takeRequirements(List<ItemStack> required, boolean simulate) {
        if (creativeSupply()) return Collections.emptyList();
        List<IItemHandler> handlers = adjacent();
        Map<IItemHandler, Map<Integer, Integer>> reserved = new IdentityHashMap<>();
        List<MaterialExtraction> plan = new ArrayList<>();
        for (ItemStack requirement : required) {
            int remaining = requirement.getCount();
            for (IItemHandler handler : handlers) for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                Map<Integer, Integer> slots = reserved.computeIfAbsent(handler, h -> new HashMap<>());
                int used = slots.getOrDefault(slot, 0);
                ItemStack offered = handler.extractItem(slot, used + remaining, true);
                if (offered.isEmpty() || !ItemStack.areItemsEqual(offered, requirement)
                        || requirement.hasTagCompound() && !ItemStack.areItemStackTagsEqual(offered, requirement)) continue;
                int count = Math.min(remaining, Math.max(0, offered.getCount() - used));
                if (count == 0) continue;
                ItemStack expected = offered.copy(); expected.setCount(count);
                plan.add(new MaterialExtraction(handler, slot, expected));
                slots.put(slot, used + count); remaining -= count;
            }
            if (remaining > 0) return null;
        }
        List<ItemStack> taken = new ArrayList<>();
        if (simulate) return taken;
        for (MaterialExtraction extraction : plan) {
            ItemStack actual = extraction.handler().extractItem(extraction.slot(), extraction.expected().getCount(), false);
            if (!actual.isEmpty()) taken.add(actual.copy());
            if (!ItemStack.areItemStacksEqual(actual, extraction.expected())) {
                for (ItemStack refund : taken) returnMaterial(refund);
                return null;
            }
        }
        return taken;
    }
    private boolean creativeSupply() {
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos adjacent = pos.offset(side);
            if (world.isBlockLoaded(adjacent) && world.getBlockState(adjacent).getBlock() == AllBlocks.CREATIVE_CRATE)
                return true;
        }
        return false;
    }
    private static final class Extraction {
        final IItemHandler handler; final int slot, count;
        Extraction(IItemHandler handler, int slot, int count) { this.handler = handler; this.slot = slot; this.count = count; }
    }
    private boolean takeFromAdjacent(ItemStack required, boolean simulate) {
        return takeFromAdjacent(required, simulate, false);
    }
    private boolean takeFromAdjacent(ItemStack required, boolean simulate, boolean strict) {
        if (creativeSupply()) return true;
        int remaining = required.getCount();
        List<Extraction> plan = new ArrayList<>();
        for (IItemHandler handler : adjacent()) for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack test = handler.extractItem(slot, remaining, true);
            if (test.isEmpty() || !ItemStack.areItemsEqual(test, required)
                    || (strict || required.hasTagCompound()) && !ItemStack.areItemStackTagsEqual(test, required)) continue;
            int count = Math.min(remaining, test.getCount()); remaining -= count;
            plan.add(new Extraction(handler, slot, count));
        }
        if (remaining > 0) return false;
        if (!simulate) for (Extraction extraction : plan)
            extraction.handler.extractItem(extraction.slot, extraction.count, false);
        return true;
    }
    private ItemStack extractPlantMaterial(ItemStack required) {
        for (IItemHandler handler : adjacent()) for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack candidate = handler.extractItem(slot, 1, true);
            if (candidate.isEmpty() || !ItemStack.areItemsEqual(candidate, required)
                    || required.hasTagCompound() && !ItemStack.areItemStackTagsEqual(candidate, required)) continue;
            return handler.extractItem(slot, 1, false);
        }
        return ItemStack.EMPTY;
    }
    private static FluidStack drainContainer(IFluidHandlerItem handler, FluidStack required, boolean drain) {
        FluidStack available = handler.drain(required, false);
        int request = required.amount;
        if ((available == null || !available.isFluidEqual(required) || available.amount < request)
                && request < Fluid.BUCKET_VOLUME) {
            request = Fluid.BUCKET_VOLUME;
            available = handler.drain(new FluidStack(required.getFluid(), request), false);
        }
        if (available == null || !available.isFluidEqual(required) || available.amount < required.amount) return null;
        return drain ? handler.drain(new FluidStack(required.getFluid(), request), true) : available;
    }
    private ItemStack findFluidMaterial(FluidStack required, boolean simulate) {
        for (IItemHandler handler : adjacent()) for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack candidate = handler.extractItem(slot, 1, true);
            if (candidate.isEmpty()) continue;
            candidate = candidate.copy(); candidate.setCount(1);
            IFluidHandlerItem contents = FluidUtil.getFluidHandler(candidate);
            if (contents == null || drainContainer(contents, required, false) == null) continue;
            return simulate ? candidate : handler.extractItem(slot, 1, false);
        }
        return ItemStack.EMPTY;
    }
    private static final class FluidSupply {
        final ItemStack item;
        @Nullable final FluidStack fluid;
        FluidSupply(ItemStack item, @Nullable FluidStack fluid) { this.item = item; this.fluid = fluid; }
    }
    private boolean hasFluid(FluidStack required) {
        return fluidReserve.getOrDefault(required.getFluid().getName(), 0) >= required.amount
                || takeAdjacentFluid(required, true) != null || !findFluidMaterial(required, true).isEmpty();
    }
    @Nullable private FluidSupply takeFluid(FluidStack required) {
        String key = required.getFluid().getName();
        int stored = fluidReserve.getOrDefault(key, 0);
        if (stored >= required.amount) {
            if (stored == required.amount) fluidReserve.remove(key); else fluidReserve.put(key, stored - required.amount);
            return new FluidSupply(ItemStack.EMPTY, required.copy());
        }
        FluidStack drained = takeAdjacentFluid(required, false);
        if (drained != null) return new FluidSupply(ItemStack.EMPTY, drained);
        ItemStack item = findFluidMaterial(required, false);
        return item.isEmpty() ? null : new FluidSupply(item, null);
    }
    @Nullable private FluidStack takeAdjacentFluid(FluidStack required, boolean simulate) {
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos other = pos.offset(side);
            if (!world.isBlockLoaded(other)) continue;
            TileEntity tile = world.getTileEntity(other);
            if (tile == null) continue;
            IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
            if (handler == null) continue;
            FluidStack preview = handler.drain(required, false);
            if (preview == null || !preview.isFluidEqual(required) || preview.amount < required.amount) continue;
            if (simulate) return required.copy();
            FluidStack actual = handler.drain(required, true);
            if (actual == null) continue;
            if (!actual.isFluidEqual(required) || actual.amount < required.amount) {
                returnFluid(actual); continue;
            }
            if (actual.amount > required.amount)
                returnFluid(new FluidStack(actual.getFluid(), actual.amount - required.amount));
            return required.copy();
        }
        return null;
    }
    private void returnFluid(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0 || fluid.getFluid() == null) return;
        String name = fluid.getFluid().getName();
        fluidReserve.merge(name, fluid.amount, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        markDirty();
    }
    private void returnMaterial(ItemStack returned) {
        ItemStack remaining = returned.copy();
        for (IItemHandler handler : adjacent()) for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++)
            remaining = handler.insertItem(slot, remaining, false);
        if (!remaining.isEmpty()) {
            net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(world,
                    pos.getX() + .5D, pos.getY() + 1D, pos.getZ() + .5D, remaining);
            item.setDefaultPickupDelay(); world.spawnEntity(item);
        }
    }
    private void makeChecklist() {
        if (!load()) return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        Map<String, ItemStack> examples = new LinkedHashMap<>();
        for (SchematicData.Entry entry : loaded.blocks) {
            if (entry.state.getMaterial() == Material.AIR) continue;
            ItemStack base = itemFor(entry.state, pos);
            if (base == null) continue;
            List<ItemStack> required = requirementsFor(entry.state, entry.tile, base);
            if (required == null) continue;
            for (ItemStack item : required) {
                String key = checklistKey(item);
                counts.put(key, counts.getOrDefault(key, 0) + item.getCount());
                names.putIfAbsent(key, item.getDisplayName());
                examples.putIfAbsent(key, item.copy());
            }
        }
        for (SchematicData.Entry entry : loaded.containedFluids) {
            if (SchematicFluids.requiredAmount(entry.containedFluid) <= 0) continue;
            ItemStack item = SchematicFluids.displayContainer(SchematicFluids.fluidOf(entry.containedFluid));
            if (item.isEmpty()) continue;
            String key = checklistKey(item);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
            names.putIfAbsent(key, item.getDisplayName()); examples.putIfAbsent(key, item.copy());
        }
        for (SchematicData.EntityEntry entity : loaded.entities) for (ItemStack item : entity.requirements()) {
            String key = checklistKey(item);
            counts.put(key, counts.getOrDefault(key, 0) + item.getCount()); names.putIfAbsent(key, item.getDisplayName());
            examples.putIfAbsent(key, item.copy());
        }
        if (inventory.getStackInSlot(2).getItem() == Item.getItemFromBlock(AllBlocks.CLIPBOARD)) {
            Map<String, Integer> available = new HashMap<>();
            for (IItemHandler handler : adjacent()) for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) available.merge(checklistKey(stack), stack.getCount(), Integer::sum);
            }
            boolean creative = creativeSupply();
            List<String> keys = new ArrayList<>(counts.keySet());
            keys.sort(Comparator.comparing((String key) -> creative || available.getOrDefault(key, 0) >= counts.get(key))
                    .thenComparing(key -> names.get(key).toLowerCase(Locale.ROOT)));
            NBTTagCompound data = new NBTTagCompound();
            int pages = Math.max(1, Math.min(ClipboardData.MAX_PAGES, (keys.size() + 6) / 7));
            for (int page = 0; page < pages; page++) {
                String[] lines = new String[ClipboardData.ROWS];
                ItemStack[] icons = new ItemStack[ClipboardData.ROWS];
                Arrays.fill(lines, ""); Arrays.fill(icons, ItemStack.EMPTY);
                int checked = 0;
                for (int row = 0; row < 7 && page * 7 + row < keys.size(); row++) {
                    String key = keys.get(page * 7 + row);
                    int required = counts.get(key), present = creative ? required : available.getOrDefault(key, 0);
                    boolean complete = present >= required;
                    lines[row] = names.get(key) + " x" + (complete ? required : required - present);
                    icons[row] = examples.get(key);
                    if (complete) checked |= 1 << row;
                }
                ClipboardData.setPage(data, page, lines, checked, icons);
            }
            data.setInteger("LastPage", 0);
            data.setBoolean("ReadOnly", true);
            ItemStack clipboard = new ItemStack(AllBlocks.CLIPBOARD);
            ClipboardData.toItem(clipboard, data);
            inventory.extractItem(2, 1, false); inventory.setStackInSlot(3, clipboard); changed();
            return;
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("title", "Material Checklist"); tag.setString("author", "Cogwork Bridge");
        NBTTagList pages = new NBTTagList();
        StringBuilder page = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String line = names.get(entry.getKey()) + " x" + entry.getValue() + "\n";
            if (page.length() + line.length() > 220) {
                pages.appendTag(new NBTTagString(net.minecraft.util.text.ITextComponent.Serializer.componentToJson(new TextComponentString(page.toString()))));
                page.setLength(0); if (pages.tagCount() >= 49) break;
            }
            page.append(line);
        }
        pages.appendTag(new NBTTagString(net.minecraft.util.text.ITextComponent.Serializer.componentToJson(new TextComponentString(page.toString()))));
        tag.setTag("pages", pages); book.setTagCompound(tag);
        inventory.extractItem(2, 1, false); inventory.setStackInSlot(3, book); changed();
    }
    private static String checklistKey(ItemStack stack) {
        return Item.REGISTRY.getNameForObject(stack.getItem()) + ":" + stack.getMetadata() + ":" + String.valueOf(stack.getTagCompound());
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); tag.setString("ProgressKey", progressKey); tag.setInteger("Cursor", cursor);
        tag.setInteger("Total", totalCached); tag.setInteger("Fuel", fuel); tag.setInteger("Shots", shots);
        tag.setInteger("Cooldown", cooldown); tag.setBoolean("Running", running); tag.setBoolean("SkipMissing", skipMissing);
        tag.setBoolean("ReplaceBlockEntities", replaceBlockEntities);
        tag.setInteger("ReplaceMode", replaceMode); tag.setString("Status", status);
        tag.setBoolean("HasTarget", hasTarget); tag.setLong("Target", target.toLong());
        tag.setInteger("ShotDuration", shotDuration);
        NBTTagList flying = new NBTTagList(); for (Flight flight : flights) flying.appendTag(flight.write());
        tag.setTag("Flights", flying);
        NBTTagList reserve = new NBTTagList();
        for (Map.Entry<String, Integer> entry : fluidReserve.entrySet()) {
            Fluid fluid = FluidRegistry.getFluid(entry.getKey());
            if (fluid != null && entry.getValue() > 0)
                reserve.appendTag(new FluidStack(fluid, entry.getValue()).writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("FluidReserve", reserve);
        tag.setTag("ShotState", NBTUtil.writeBlockState(new NBTTagCompound(), lastShotState)); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        int previousShots = shots; BlockPos oldTarget = target;
        super.readFromNBT(tag); progressKey = tag.getString("ProgressKey"); cursor = tag.getInteger("Cursor");
        totalCached = tag.getInteger("Total"); fuel = tag.getInteger("Fuel"); shots = tag.getInteger("Shots");
        cooldown = tag.getInteger("Cooldown"); running = tag.getBoolean("Running"); skipMissing = tag.getBoolean("SkipMissing");
        replaceMode = tag.hasKey("ReplaceMode") ? Math.floorMod(tag.getInteger("ReplaceMode"), 4) : 2;
        replaceBlockEntities = tag.getBoolean("ReplaceBlockEntities"); status = tag.getString("Status");
        hasTarget = tag.getBoolean("HasTarget"); target = BlockPos.fromLong(tag.getLong("Target"));
        shotDuration = tag.hasKey("ShotDuration") ? Math.max(1, tag.getInteger("ShotDuration")) : 10;
        fluidReserve.clear();
        NBTTagList reserve = tag.getTagList("FluidReserve", 10);
        for (int i = 0; i < reserve.tagCount(); i++) {
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(reserve.getCompoundTagAt(i));
            if (fluid != null && fluid.amount > 0)
                fluidReserve.merge(fluid.getFluid().getName(), fluid.amount,
                        (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        }
        flights.clear(); NBTTagList flying = tag.getTagList("Flights", 10);
        for (int i = 0; i < flying.tagCount(); i++) {
            try { flights.add(Flight.read(flying.getCompoundTagAt(i))); } catch (RuntimeException ignored) { }
        }
        if (tag.hasKey("ShotState", 10)) lastShotState = NBTUtil.readBlockState(tag.getCompoundTag("ShotState"));
        if (world != null && world.isRemote && shots != previousShots) { previousTarget = oldTarget; visualFlight = shotDuration; }
        loaded = null; loadedKey = "";
    }
}
