package com.cogwork.cogworkbridge.content.processing.burner;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;

public final class BlazeBurnerTileEntity extends TileEntity implements ITickable {
    private static final int MAX_FUEL = 10000;
    private static final int AUTO_FUEL_THRESHOLD = 500;
    private static final int BLAZE_CAKE_DURATION = 3200;
    private static final int COOLDOWN_DURATION = 5000;

    public enum HeatLevel { NONE, SMOULDERING, FADING, KINDLED, SEETHING }
    public enum FlameType { NONE, REGULAR, SOUL }

    private final FuelInventory inventory = new FuelInventory();
    private int remainingFuel;
    private boolean specialFuel;
    private boolean creativeFuel;
    private FlameType emptyFlame = FlameType.NONE;
    private boolean goggles;
    private boolean tutorialConductor;
    public boolean isTutorialConductor() { return tutorialConductor; }

    public boolean hasGoggles() { return goggles; }
    public void setGoggles(boolean equipped) {
        if (goggles == equipped) return;
        goggles = equipped;
        changed(getHeatLevel());
    }

    public boolean isOccupied() {
        return world != null && world.getBlockState(pos).getBlock() instanceof BlazeBurnerBlock burner
                && burner.isOccupied();
    }

    public HeatLevel getHeatLevel() {
        if (!isOccupied()) return isEmptyLit() ? HeatLevel.KINDLED : HeatLevel.NONE;
        if (creativeFuel || specialFuel && remainingFuel > 0) return HeatLevel.SEETHING;
        if (remainingFuel <= 0) return HeatLevel.SMOULDERING;
        return remainingFuel <= AUTO_FUEL_THRESHOLD ? HeatLevel.FADING : HeatLevel.KINDLED;
    }

    /** Empty lit cages provide light, but do not provide processing heat. */
    public int getProcessingHeat() {
        if (!isOccupied()) return 0;
        return switch (getHeatLevel()) {
            case SEETHING -> 2;
            case KINDLED, FADING -> 1;
            default -> 0;
        };
    }

    public boolean isEmptyLit() { return emptyFlame != FlameType.NONE; }
    public FlameType getEmptyFlame() { return emptyFlame; }

    public void setEmptyFlame(FlameType flame) {
        if (emptyFlame == flame || isOccupied()) return;
        emptyFlame = flame;
        changed(true);
    }

    public boolean canFeed(ItemStack stack) {
        if (!isOccupied() || stack.isEmpty()) return false;
        if (stack.getItem() == AllItems.CREATIVE_BLAZE_CAKE) return !creativeFuel;
        if (creativeFuel) return false;
        if (stack.getItem() == AllItems.BLAZE_CAKE) return !specialFuel || remainingFuel < MAX_FUEL;
        return !specialFuel && remainingFuel < MAX_FUEL && TileEntityFurnace.getItemBurnTime(stack) > 0;
    }

    public boolean feed(ItemStack stack) {
        if (!canFeed(stack)) return false;
        HeatLevel previous = getHeatLevel();
        if (stack.getItem() == AllItems.CREATIVE_BLAZE_CAKE) {
            creativeFuel = true;
            specialFuel = true;
            remainingFuel = 0;
        } else if (stack.getItem() == AllItems.BLAZE_CAKE) {
            remainingFuel = Math.min(MAX_FUEL, (specialFuel ? remainingFuel : 0) + BLAZE_CAKE_DURATION);
            specialFuel = true;
        } else {
            remainingFuel = Math.min(MAX_FUEL, remainingFuel + TileEntityFurnace.getItemBurnTime(stack));
        }
        changed(previous);
        return true;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote || !isOccupied()) return;
        HeatLevel previous = getHeatLevel();
        if (!creativeFuel && remainingFuel > 0) {
            remainingFuel--;
            if (remainingFuel == 0 && specialFuel) {
                specialFuel = false;
                remainingFuel = COOLDOWN_DURATION;
            }
            if (world.getTotalWorldTime() % 20L == 0L) markDirty();
        }
        if (previous != getHeatLevel()) changed(previous);
        if (!creativeFuel && remainingFuel <= AUTO_FUEL_THRESHOLD && world.getTotalWorldTime() % 20L == 0L) {
            consumeStoredFuel();
        }
    }

    private void consumeStoredFuel() {
        ItemStack stack = inventory.getStackInSlot(0);
        if (!canFeed(stack)) return;
        ItemStack container = stack.getItem().getContainerItem(stack);
        if (!container.isEmpty() && !inventory.insertContainer(container, true).isEmpty()) return;
        ItemStack one = stack.copy();
        one.setCount(1);
        if (!feed(one)) return;
        inventory.extractItem(0, 1, false);
        if (!container.isEmpty()) inventory.insertContainer(container, false);
    }

    public void dropContents(World world, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.extractItem(slot, 64, false);
            if (!stack.isEmpty()) Block.spawnAsEntity(world, pos, stack);
        }
    }

    private void changed(HeatLevel previous) {
        changed(previous != getHeatLevel());
    }

    private void changed(boolean lightingChanged) {
        markDirty();
        if (world == null || world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        if (lightingChanged) {
            world.checkLight(pos);
            world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
        }
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && isOccupied()
                || super.hasCapability(capability, side);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && isOccupied()) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Fuel", remainingFuel);
        tag.setBoolean("SpecialFuel", specialFuel);
        tag.setBoolean("CreativeFuel", creativeFuel);
        tag.setBoolean("EmptyLit", isEmptyLit());
        tag.setBoolean("EmptySoulFire", emptyFlame == FlameType.SOUL);
        tag.setBoolean("Goggles", goggles);
        if (tutorialConductor) tag.setBoolean("TutorialConductor", true);
        tag.setTag("Inventory", inventory.serializeNBT());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        remainingFuel = Math.max(0, Math.min(MAX_FUEL, tag.getInteger("Fuel")));
        specialFuel = tag.getBoolean("SpecialFuel");
        creativeFuel = tag.getBoolean("CreativeFuel");
        emptyFlame = !tag.getBoolean("EmptyLit") ? FlameType.NONE
                : tag.getBoolean("EmptySoulFire") ? FlameType.SOUL : FlameType.REGULAR;
        goggles = tag.getBoolean("Goggles");
        tutorialConductor = tag.getBoolean("TutorialConductor");
        if (tag.hasKey("Inventory", 10)) inventory.deserializeNBT(tag.getCompoundTag("Inventory"));
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        FlameType previousFlame = emptyFlame;
        readFromNBT(tag);
        if (world != null && world.isRemote && previousFlame != emptyFlame) {
            world.checkLight(pos);
            if (world instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fake) {
                fake.getMobileChunk().setChunkModified();
            } else {
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
        }
    }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { handleUpdateTag(packet.getNbtCompound()); }

    private final class FuelInventory extends ItemStackHandler {
        private FuelInventory() { super(2); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) return stack;
            if (stack.getItem() != AllItems.BLAZE_CAKE && stack.getItem() != AllItems.CREATIVE_BLAZE_CAKE
                    && TileEntityFurnace.getItemBurnTime(stack) <= 0) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        private ItemStack insertContainer(ItemStack stack, boolean simulate) {
            return super.insertItem(1, stack, simulate);
        }

        @Override protected void onContentsChanged(int slot) { markDirty(); }
    }
}
