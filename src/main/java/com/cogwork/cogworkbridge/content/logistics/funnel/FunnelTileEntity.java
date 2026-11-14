package com.cogwork.cogworkbridge.content.logistics.funnel;

import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.content.logistics.depot.DepotTileEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.ItemTransfer;
import com.cogwork.cogworkbridge.content.logistics.transport.ContraptionItemStorage;
import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.logistics.transport.PortableItemStorage;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/** Funnels keep no inventory: all insertions and extractions address the attached inventory. */
public class FunnelTileEntity extends CogworkTileEntity implements ITickable, IItemHandler, PortableItemStorage {
    private ItemStack filter = ItemStack.EMPTY;
    private boolean extracting = true;
    private boolean extended;
    private boolean powered;
    private int amount = 1;
    private boolean exact;
    private int cooldown;
    private int flapSerial;
    @Nullable private BlockPos lastMovingPosition;
    private float flap, previousFlap;
    public float flapValue(float partial) { return previousFlap + (flap - previousFlap) * partial; }
    private void transferred() { flapSerial++; com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "funnel"); }
    private void notifyObserver(ItemStack stack) {
        com.cogwork.cogworkbridge.content.redstone.DetectorTileEntity.onFunnelTransfer(world, pos, stack);
    }

    public boolean isExtracting() {
        BeltTileEntity belt = beltBelow();
        EnumFacing movement = belt == null ? null : belt.movementFacing();
        if (movement == null) return extracting;
        EnumFacing face = facing();
        return movement.getAxis() == face.getAxis() ? movement == face : movement.rotateY() == face;
    }
    public boolean isExtended() { return extended; }
    public void toggleExtended() { extended = !extended; changed(); }
    @Nullable public BeltTileEntity beltBelow() {
        if (world == null || pos == null || !facing().getAxis().isHorizontal()) return null;
        TileEntity below = world.getTileEntity(pos.down());
        return below instanceof BeltTileEntity belt && belt.supportsItemTransport() ? belt : null;
    }
    @Nullable public BeltTileEntity alignedBelt() {
        BeltTileEntity belt = beltBelow();
        if (belt == null) return null;
        BlockPos step = belt.step();
        if (step.getX() == 0 && step.getZ() == 0) return null;
        EnumFacing.Axis path = step.getX() != 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
        return path == facing().getAxis() ? belt : null;
    }
    public boolean isPowered() { return powered; }
    public ItemStack getFilter() { return filter.copy(); }
    public void setFilter(ItemStack value) { filter = value.copy(); if (!filter.isEmpty()) filter.setCount(1); changed(); }
    public int getAmount() { return amount; }
    public boolean isExact() { return exact; }
    public void toggleExact() { exact = !exact; changed(); }
    private boolean brass() {
        return world != null && world.getBlockState(pos).getBlock() instanceof FunnelBlock block && block.isBrass();
    }
    private EnumFacing facing() {
        if (world == null || pos == null) return EnumFacing.NORTH;
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof FunnelBlock ? state.getValue(FunnelBlock.FACING) : EnumFacing.NORTH;
    }
    @Nullable private IItemHandler attached() {
        if (world == null || pos == null) return null;
        EnumFacing face = facing();
        return ItemTransfer.at(world, pos.offset(face.getOpposite()), face);
    }
    public void setExtracting(boolean value) { extracting = value; changed(); }
    public void toggleExtraction() { setExtracting(!extracting); }
    public void setAmount(int value, boolean exact) {
        amount = Math.max(1, Math.min(64, value));
        this.exact = exact;
        changed();
    }
    public void selectFilter(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        ItemStack old = filter;
        filter = held.isEmpty() ? ItemStack.EMPTY : held.copy();
        if (!filter.isEmpty()) filter.setCount(1);
        if (!player.capabilities.isCreativeMode && ListFilterItem.isFilterItem(held)) held.shrink(1);
        if (ListFilterItem.isFilterItem(old) && (!player.capabilities.isCreativeMode || !hasFilter(player, old)))
            ItemHandlerHelper.giveItemToPlayer(player, old);
        changed();
    }
    private static boolean hasFilter(EntityPlayer player, ItemStack filter) {
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack candidate = player.inventory.getStackInSlot(slot);
            if (ItemStack.areItemsEqual(candidate, filter) && ItemStack.areItemStackTagsEqual(candidate, filter)) return true;
        }
        return false;
    }
    private boolean accepts(ItemStack stack) { return !brass() || ListFilterItem.matchesItem(filter, stack); }
    private int extractionLimit() { return !brass() ? 64 : filter.isEmpty() ? 1 : amount; }
    private boolean exactExtraction() { return brass() && !filter.isEmpty() && exact; }
    private void changed() {
        markDirty();
        if (world == null || world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().markTileDirty(pos);
        else world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }
    public void acceptEntity(EntityItem entity) {
        if (entity.isDead || isExtracting() || powered || entity.getItem().isEmpty()) return;
        EnumFacing face = facing();
        double dx = entity.posX - (pos.getX() + 0.5D);
        double dy = entity.posY - (pos.getY() + 0.5D);
        double dz = entity.posZ - (pos.getZ() + 0.5D);
        if (dx * face.getXOffset() + dy * face.getYOffset() + dz * face.getZOffset() < -0.25D) return;
        ItemStack rest = insertItem(0, entity.getItem(), false);
        if (rest.isEmpty()) entity.setDead();
        else entity.setItem(rest);
    }
    @Override public void update() {
        if (world == null) return;
        if (world.isRemote) { previousFlap = flap; flap = Math.max(0, flap - 0.125F); return; }
        if (world instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity) {
            updateMoving(entity);
            return;
        }
        boolean nextPowered = world.isBlockPowered(pos);
        if (powered != nextPowered) { powered = nextPowered; changed(); }
        if (powered) { cooldown = 0; return; }
        if (cooldown > 0) { cooldown--; return; }
        TileEntity surface = world.getTileEntity(pos.down());
        if (facing().getAxis().isHorizontal() && (surface instanceof BeltTileEntity || surface instanceof DepotTileEntity)) {
            if (surface instanceof BeltTileEntity belt && belt.movementFacing() == null) return;
            IItemHandler inventory = attached();
            if (inventory == null) return;
            IItemHandler transport = (IItemHandler) surface;
            int limit = extractionLimit();
            boolean extractingNow = isExtracting();
            if (!extractingNow && surface instanceof BeltTileEntity belt && Math.abs(belt.renderPosition(1) - 0.5F) > 0.2F) return;
            IItemHandler source = extractingNow ? inventory : transport;
            IItemHandler target = extractingNow ? transport : inventory;
            int moved = exactExtraction()
                    ? ItemTransfer.moveExact(source, target, limit, this::accepts, stack -> ItemTransfer.drop(world, pos, stack), this::notifyObserver)
                    : ItemTransfer.move(source, target, limit, this::accepts, stack -> ItemTransfer.drop(world, pos, stack), this::notifyObserver);
            if (moved > 0) {
                cooldown = 8; transferred(); changed();
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "funnel");
            }
            return;
        }
        if (!extracting) return;
        IItemHandler source = attached();
        if (source == null) return;
        EnumFacing direction = facing();
        BlockPos output = pos.offset(direction);
        IItemHandler target = ItemTransfer.at(world, output, direction.getOpposite());
        int limit = extractionLimit();
        if (target != null) {
            int moved = exactExtraction()
                    ? ItemTransfer.moveExact(source, target, limit, this::accepts, stack -> ItemTransfer.drop(world, pos, stack), this::notifyObserver)
                    : ItemTransfer.move(source, target, limit, this::accepts, stack -> ItemTransfer.drop(world, pos, stack), this::notifyObserver);
            if (moved > 0) {
                cooldown = 8; transferred(); changed();
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "funnel");
            }
            return;
        }
        AxisAlignedBB outputArea = new AxisAlignedBB(pos).expand(direction.getXOffset() * 0.5D,
                direction.getYOffset() * 0.5D, direction.getZOffset() * 0.5D);
        if (!world.getEntitiesWithinAABB(EntityItem.class, outputArea).isEmpty()) return;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack candidate = source.extractItem(slot, limit, true);
            if (candidate.isEmpty() || !accepts(candidate) || exactExtraction() && candidate.getCount() < amount) continue;
            ItemStack extracted = source.extractItem(slot, candidate.getCount(), false);
            if (extracted.isEmpty()) continue;
            if (world instanceof FakeWorld || world instanceof com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld)
                ItemTransfer.drop(world, pos, extracted);
            else {
                EntityItem dropped = new EntityItem(world, pos.getX() + 0.5D + direction.getXOffset() * 0.35D,
                        pos.getY() + 0.5D + direction.getYOffset() * 0.35D,
                        pos.getZ() + 0.5D + direction.getZOffset() * 0.35D, extracted);
                dropped.setDefaultPickupDelay();
                dropped.motionY = direction == EnumFacing.UP ? 0.25D : 0;
                if (!world.spawnEntity(dropped)) {
                    ItemStack rest = source.insertItem(slot, extracted, false);
                    if (!rest.isEmpty()) ItemTransfer.drop(world, pos, rest);
                    return;
                }
            }
            cooldown = 8;
            notifyObserver(extracted);
            transferred(); changed();
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "funnel");
            break;
        }
    }

    private void updateMoving(BearingContraptionEntity entity) {
        if (entity.isActorDisabled(world.getBlockState(pos).getBlock())) {
            lastMovingPosition = null;
            return;
        }
        boolean moving = false;
        for (BearingContraptionEntity current = entity; current != null; current = current.getParentContraption()) {
            if (current.hasMotion()) moving = true;
        }
        if (!moving || entity.isStalled() || world.isBlockPowered(pos)) {
            lastMovingPosition = null;
            return;
        }
        EnumFacing direction = facing();
        Vec3d point = entity.localBlockCenterToWorld(pos)
                .add(entity.localFacingVectorToWorld(direction).scale(extracting ? 0.15D : 0.65D));
        BlockPos target = new BlockPos(point);
        if (target.equals(lastMovingPosition)) return;
        lastMovingPosition = target.toImmutable();
        World realWorld = entity.world;
        if (!realWorld.isBlockLoaded(target)) return;
        IItemHandler storage = new ContraptionItemStorage(entity);
        if (storage.getSlots() == 0) return;
        if (!extracting) {
            for (EntityItem item : realWorld.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(target))) {
                if (item.isDead || item.getItem().isEmpty() || !accepts(item.getItem())) continue;
                ItemStack original = item.getItem();
                ItemStack observed = original.copy();
                ItemStack remaining = ItemHandlerHelper.insertItemStacked(storage, original.copy(), false);
                if (remaining.getCount() == original.getCount()) continue;
                observed.setCount(original.getCount() - remaining.getCount()); notifyObserver(observed);
                if (remaining.isEmpty()) item.setDead(); else item.setItem(remaining);
                transferred(); changed();
            }
            return;
        }
        if (realWorld.getBlockState(target).getCollisionBoundingBox(realWorld, target) != null
                || !realWorld.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(target)).isEmpty()) return;
        ItemStackHandler buffer = new ItemStackHandler(1);
        int limit = brass() ? extractionLimit() : 1;
        int moved = exactExtraction()
                ? ItemTransfer.moveExact(storage, buffer, limit, this::accepts, stack -> ItemTransfer.drop(realWorld, target, stack))
                : ItemTransfer.move(storage, buffer, limit, this::accepts, stack -> ItemTransfer.drop(realWorld, target, stack));
        if (moved <= 0) return;
        ItemStack extracted = buffer.getStackInSlot(0).copy();
        Vec3d dropPoint = direction == EnumFacing.DOWN ? point : point.add(0, -0.5D, 0);
        EntityItem drop = new EntityItem(realWorld, dropPoint.x, dropPoint.y, dropPoint.z, extracted);
        drop.setPickupDelay(5);
        if (!realWorld.spawnEntity(drop)) {
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(storage, extracted, false);
            if (!remaining.isEmpty()) ItemTransfer.drop(realWorld, target, remaining);
            return;
        }
        notifyObserver(extracted);
        transferred(); changed();
    }
    @Override public int getSlots() { IItemHandler handler = attached(); return handler == null ? 0 : handler.getSlots(); }
    @Override public ItemStack getStackInSlot(int slot) {
        IItemHandler handler = attached();
        return isExtracting() && !powered && handler != null && slot >= 0 && slot < handler.getSlots()
                && accepts(handler.getStackInSlot(slot)) ? handler.getStackInSlot(slot) : ItemStack.EMPTY;
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (isExtracting() || powered || stack.isEmpty() || !accepts(stack)) return stack;
        IItemHandler target = attached();
        if (target == null) return stack;
        int before = stack.getCount(); ItemStack observed = stack.copy();
        ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack, simulate);
        if (!simulate && rest.getCount() < before) {
            observed.setCount(before - rest.getCount()); notifyObserver(observed); transferred(); changed();
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "funnel");
        }
        return rest;
    }
    @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
        if (!isExtracting() || powered || count <= 0) return ItemStack.EMPTY;
        IItemHandler source = attached();
        if (source == null || slot < 0 || slot >= source.getSlots()) return ItemStack.EMPTY;
        int limit = !brass() ? count : Math.min(count, extractionLimit());
        ItemStack candidate = source.extractItem(slot, limit, true);
        if (candidate.isEmpty() || !accepts(candidate) || exactExtraction() && candidate.getCount() < amount)
            return ItemStack.EMPTY;
        ItemStack result = simulate ? candidate : source.extractItem(slot, limit, false);
        if (!simulate && !result.isEmpty()) {
            cooldown = 8; notifyObserver(result); transferred(); changed();
            com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "funnel");
        }
        return result;
    }
    @Override public int getSlotLimit(int slot) { return 64; }
    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && (side == null || side != facing().getOpposite())
                || super.hasCapability(capability, side);
    }
    @Nullable @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && (side == null || side != facing().getOpposite()))
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this);
        return super.getCapability(capability, side);
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!filter.isEmpty()) tag.setTag("Filter", filter.writeToNBT(new NBTTagCompound()));
        tag.setBoolean("Extracting", extracting);
        tag.setBoolean("Extended", extended);
        tag.setBoolean("Powered", powered);
        tag.setInteger("Amount", amount);
        tag.setBoolean("Exact", exact);
        tag.setInteger("Cooldown", cooldown); tag.setInteger("FlapSerial", flapSerial);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        filter = tag.hasKey("Filter", 10) ? new ItemStack(tag.getCompoundTag("Filter")) : ItemStack.EMPTY;
        extracting = !tag.hasKey("Extracting") || tag.getBoolean("Extracting");
        extended = tag.getBoolean("Extended");
        powered = tag.getBoolean("Powered");
        int savedAmount = tag.getInteger("Amount");
        amount = !tag.hasKey("Amount") || savedAmount == 0 ? 1 : Math.max(1, Math.min(64, savedAmount));
        exact = tag.getBoolean("Exact");
        cooldown = tag.getInteger("Cooldown");
        int serial = tag.getInteger("FlapSerial");
        if (world != null && world.isRemote && serial != flapSerial) flap = previousFlap = 1;
        flapSerial = serial;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
