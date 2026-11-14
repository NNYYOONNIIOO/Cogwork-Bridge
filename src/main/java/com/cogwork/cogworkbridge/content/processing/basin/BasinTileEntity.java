package com.cogwork.cogworkbridge.content.processing.basin;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import com.cogwork.cogworkbridge.core.CogworkTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;
import java.util.List;

public final class BasinTileEntity extends CogworkTileEntity implements ITickable {
    private final BasinItems inputItems = new BasinItems();
    private final BasinItems outputItems = new BasinItems();
    private final IItemHandler items = new CombinedInvWrapper(inputItems, outputItems) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot >= inputItems.getSlots() ? stack : super.insertItem(slot, stack, simulate);
        }
    };
    private final BasinTank[] inputTanks = {new BasinTank(), new BasinTank()};
    private final BasinTank[] outputTanks = {new BasinTank(), new BasinTank()};
    private final IFluidHandler fluids = new BasinFluidHandler();
    private EnumFacing outputSide = EnumFacing.DOWN;
    @Nullable private EnumFacing preferredOutputSide;
    private int disabledOutputSides;

    public ItemStackHandler getInputItems() { return inputItems; }
    public ItemStackHandler getOutputItems() { return outputItems; }
    public IFluidHandler getFluidHandler() { return fluids; }
    public EnumFacing getOutputSide() { return outputSide; }

    /** Potion recipes must inspect inputs without accidentally consuming finished outputs. */
    public List<FluidStack> getInputFluids() {
        List<FluidStack> result = new java.util.ArrayList<>();
        for (BasinTank tank : inputTanks) {
            FluidStack fluid = tank.getFluid();
            if (fluid != null && fluid.amount > 0) result.add(fluid.copy());
        }
        return result;
    }

    /** Inserts all results together or leaves both inventories unchanged. */
    public boolean processItems(int slot, ItemStack expected, int count, List<ItemStack> results, boolean simulate) {
        if (slot < 0 || slot >= inputItems.getSlots() || count <= 0 || expected.isEmpty()) return false;
        ItemStack input = inputItems.getStackInSlot(slot);
        if (input.getCount() < count || !ItemStack.areItemsEqual(input, expected)
                || !ItemStack.areItemStackTagsEqual(input, expected)) return false;
        BasinItems planned = new BasinItems(true);
        for (int i = 0; i < outputItems.getSlots(); i++) {
            planned.setStackInSlot(i, outputItems.getStackInSlot(i).copy());
        }
        for (ItemStack result : results) {
            if (!result.isEmpty() && !ItemHandlerHelper.insertItemStacked(planned, result.copy(), false).isEmpty()) {
                return false;
            }
        }
        if (!simulate) {
            inputItems.extractItem(slot, count, false);
            for (int i = 0; i < outputItems.getSlots(); i++) {
                ItemStack next = planned.getStackInSlot(i);
                if (!ItemStack.areItemStacksEqual(outputItems.getStackInSlot(i), next)) {
                    outputItems.setStackInSlot(i, next.copy());
                }
            }
        }
        return true;
    }

    public int drainInputFluid(net.minecraftforge.fluids.FluidStack requested, boolean execute) {
        if (requested == null || requested.amount <= 0) return 0;
        for (BasinTank tank : inputTanks) {
            net.minecraftforge.fluids.FluidStack present = tank.getFluid();
            if (present != null && present.isFluidEqual(requested) && present.amount >= requested.amount) {
                if (execute) tank.drain(requested.amount, true);
                return requested.amount;
            }
        }
        return 0;
    }

    /** Applies an entire basin recipe only after every input and output has been checked. */
    public boolean processBatch(int[] counts, List<ItemStack> results, boolean simulate) {
        if (counts.length != inputItems.getSlots()) return false;
        for (int i = 0; i < counts.length; i++)
            if (counts[i] < 0 || inputItems.getStackInSlot(i).getCount() < counts[i]) return false;
        BasinItems planned = new BasinItems(true);
        for (int i = 0; i < outputItems.getSlots(); i++) planned.setStackInSlot(i, outputItems.getStackInSlot(i).copy());
        for (ItemStack result : results)
            if (!result.isEmpty() && !ItemHandlerHelper.insertItemStacked(planned, result.copy(), false).isEmpty()) return false;
        if (!simulate) {
            for (int i = 0; i < counts.length; i++) if (counts[i] > 0) inputItems.extractItem(i, counts[i], false);
            for (int i = 0; i < outputItems.getSlots(); i++) outputItems.setStackInSlot(i, planned.getStackInSlot(i).copy());
        }
        return true;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote || world.getTotalWorldTime() % 8 != 0) return;
        EnumFacing next = findOutputSide();
        if (next != outputSide) {
            outputSide = next;
            contentsChanged();
        }
        if (outputSide != EnumFacing.DOWN) transferOutputs(outputSide);
    }

    private EnumFacing findOutputSide() {
        EnumFacing next = EnumFacing.DOWN;
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            if (isEnabledOutput(side)) next = side;
        }
        if (preferredOutputSide != null && canOutputTo(preferredOutputSide)) return preferredOutputSide;
        return next;
    }

    private boolean isEnabledOutput(@Nullable EnumFacing side) {
        return side != null && (disabledOutputSides & 1 << side.getIndex()) == 0 && canOutputTo(side);
    }

    public void wrench(EnumFacing side) {
        if (world == null || world.isRemote) return;
        int mask = 1 << side.getIndex();
        disabledOutputSides &= ~mask;
        if (outputSide == side) {
            if (preferredOutputSide == side) preferredOutputSide = null;
            disabledOutputSides |= mask;
        } else {
            preferredOutputSide = side;
        }
        outputSide = findOutputSide();
        contentsChanged();
        if (outputSide.getAxis().isHorizontal()) transferOutputs(outputSide);
    }

    private boolean canOutputTo(EnumFacing side) {
        if (side == null || !side.getAxis().isHorizontal()) return false;
        BlockPos opening = pos.offset(side);
        BlockPos target = opening.down();
        if (!world.isBlockLoaded(opening) || !world.isBlockLoaded(target)
                || world.getBlockState(opening).getCollisionBoundingBox(world, opening) != Block.NULL_AABB) {
            return false;
        }
        TileEntity tile = world.getTileEntity(target);
        return tile != null && (tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite())
                || tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite()));
    }

    private void transferOutputs(EnumFacing side) {
        TileEntity target = world.getTileEntity(pos.offset(side).down());
        if (target == null) return;
        IItemHandler receiver = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite());
        if (receiver != null) {
            for (int slot = 0; slot < outputItems.getSlots(); slot++) {
                ItemStack offered = outputItems.extractItem(slot, 64, true);
                if (offered.isEmpty()) continue;
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(receiver, offered, true);
                int accepted = offered.getCount() - remainder.getCount();
                if (accepted <= 0) continue;
                ItemStack taken = outputItems.extractItem(slot, accepted, false);
                ItemStack unaccepted = ItemHandlerHelper.insertItemStacked(receiver, taken, false);
                if (!unaccepted.isEmpty()) {
                    ItemStack left = ItemHandlerHelper.insertItemStacked(outputItems, unaccepted, false);
                    if (!left.isEmpty()) Block.spawnAsEntity(world, pos, left);
                }
            }
        }
        IFluidHandler fluidReceiver = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side.getOpposite());
        if (fluidReceiver != null) {
            for (BasinTank tank : outputTanks) {
                FluidStack offered = tank.drain(1000, false);
                if (offered == null) continue;
                int accepted = Math.min(offered.amount, fluidReceiver.fill(offered, false));
                if (accepted <= 0) continue;
                FluidStack taken = tank.drain(accepted, true);
                if (taken == null) continue;
                int inserted = Math.max(0, Math.min(taken.amount, fluidReceiver.fill(taken, true)));
                if (inserted < taken.amount) {
                    FluidStack remainder = taken.copy();
                    remainder.amount -= inserted;
                    tank.fill(remainder, true);
                }
            }
        }
    }

    public int fillOutput(FluidStack fluid, boolean execute) {
        return fillTanks(outputTanks, fluid, execute);
    }

    public boolean isItemsEmpty() {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    public void giveItemsTo(EntityPlayer player) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.extractItem(slot, 64, false);
            if (!stack.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, stack);
        }
    }

    public void dropItems(World world, BlockPos pos) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.extractItem(slot, 64, false);
            if (!stack.isEmpty()) Block.spawnAsEntity(world, pos, stack);
        }
    }

    private void contentsChanged() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                || super.hasCapability(capability, side);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(items);
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluids);
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("InputItems", inputItems.serializeNBT());
        tag.setTag("OutputItems", outputItems.serializeNBT());
        tag.setInteger("OutputSide", outputSide.getIndex());
        tag.setInteger("PreferredOutputSide", preferredOutputSide == null ? -1 : preferredOutputSide.getIndex());
        tag.setInteger("DisabledOutputSides", disabledOutputSides);
        for (int i = 0; i < inputTanks.length; i++) {
            tag.setTag("InputFluid" + i, inputTanks[i].writeToNBT(new NBTTagCompound()));
            tag.setTag("OutputFluid" + i, outputTanks[i].writeToNBT(new NBTTagCompound()));
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("InputItems", 10)) inputItems.deserializeNBT(tag.getCompoundTag("InputItems"));
        if (tag.hasKey("OutputItems", 10)) outputItems.deserializeNBT(tag.getCompoundTag("OutputItems"));
        int side = tag.hasKey("OutputSide") ? tag.getInteger("OutputSide") : EnumFacing.DOWN.getIndex();
        EnumFacing savedSide = EnumFacing.byIndex(side);
        outputSide = savedSide.getAxis().isHorizontal() ? savedSide : EnumFacing.DOWN;
        int preferred = tag.hasKey("PreferredOutputSide") ? tag.getInteger("PreferredOutputSide") : -1;
        preferredOutputSide = preferred >= 0 && preferred < EnumFacing.VALUES.length
                ? EnumFacing.byIndex(preferred) : null;
        disabledOutputSides = tag.getInteger("DisabledOutputSides");
        for (int i = 0; i < inputTanks.length; i++) {
            inputTanks[i].readFromNBT(tag.getCompoundTag("InputFluid" + i));
            outputTanks[i].readFromNBT(tag.getCompoundTag("OutputFluid" + i));
        }
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        EnumFacing previous = outputSide;
        readFromNBT(tag);
        if (world != null && world.isRemote && previous != outputSide) {
            if (world instanceof com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld fake) {
                fake.getMobileChunk().setChunkModified();
            } else {
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
        }
    }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { handleUpdateTag(packet.getNbtCompound()); }

    private final class BasinItems extends ItemStackHandler {
        private final boolean simulated;
        private BasinItems() { this(false); }
        private BasinItems(boolean simulated) { super(9); this.simulated = simulated; }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return stack;
            int firstEmpty = -1;
            for (int i = 0; i < getSlots(); i++) {
                ItemStack existing = getStackInSlot(i);
                if (i != slot && ItemStack.areItemsEqual(existing, stack)
                        && ItemStack.areItemStackTagsEqual(existing, stack)) return stack;
                if (existing.isEmpty() && firstEmpty < 0) firstEmpty = i;
            }
            if (getStackInSlot(slot).isEmpty() && firstEmpty != slot) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override protected void onContentsChanged(int slot) { if (!simulated) contentsChanged(); }
    }

    private final class BasinTank extends FluidTank {
        private BasinTank() { super(1000); }
        @Override protected void onContentsChanged() { contentsChanged(); }
    }

    private static int fillTanks(BasinTank[] tanks, FluidStack fluid, boolean execute) {
        if (fluid == null || fluid.amount <= 0) return 0;
        BasinTank empty = null;
        for (BasinTank tank : tanks) {
            FluidStack current = tank.getFluid();
            if (current != null && current.isFluidEqual(fluid)) return tank.fill(fluid, execute);
            if (current == null && empty == null) empty = tank;
        }
        return empty == null ? 0 : empty.fill(fluid, execute);
    }

    private final class BasinFluidHandler implements IFluidHandler {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            IFluidTankProperties[] properties = new IFluidTankProperties[4];
            for (int i = 0; i < 2; i++) {
                properties[i] = new FluidTankProperties(inputTanks[i].getFluid(), 1000, true, true);
                properties[i + 2] = new FluidTankProperties(outputTanks[i].getFluid(), 1000, false, true);
            }
            return properties;
        }

        @Override public int fill(FluidStack resource, boolean execute) { return fillTanks(inputTanks, resource, execute); }

        @Nullable
        @Override
        public FluidStack drain(FluidStack requested, boolean execute) {
            if (requested == null || requested.amount <= 0) return null;
            for (BasinTank[] group : new BasinTank[][] {outputTanks, inputTanks}) {
                for (BasinTank tank : group) {
                    FluidStack current = tank.getFluid();
                    if (current != null && current.isFluidEqual(requested)) return tank.drain(requested, execute);
                }
            }
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean execute) {
            if (maxDrain <= 0) return null;
            for (BasinTank[] group : new BasinTank[][] {outputTanks, inputTanks}) {
                for (BasinTank tank : group) {
                    if (tank.getFluidAmount() > 0) return tank.drain(maxDrain, execute);
                }
            }
            return null;
        }
    }
}
