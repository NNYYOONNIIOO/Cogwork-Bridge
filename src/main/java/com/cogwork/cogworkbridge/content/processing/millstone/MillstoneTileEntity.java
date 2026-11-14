package com.cogwork.cogworkbridge.content.processing.millstone;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;

public final class MillstoneTileEntity extends KineticTileEntity {
    private int remainingTicks;
    private final ItemStackHandler input = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return MillstoneRecipes.find(stack) != null; }
        @Override protected void onContentsChanged(int slot) { remainingTicks = 0; contentsChanged(); }
    };
    private final ItemStackHandler output = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { contentsChanged(); }
    };
    private final IItemHandler inventory = new CombinedInvWrapper(input, output) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && input.isItemValid(0, stack);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 && isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }
    };

    @Override public float getStressImpact() { return 4.0F; }
    @Override protected boolean isGear() { return true; }

    private float workingSpeed() {
        float speed = getSpeed();
        if (speed == 0.0F && world instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
            speed = contraption.getSpeed();
        }
        return speed;
    }

    public float animationAngle(float partialTicks) {
        float speed = workingSpeed();
        if (getContraptionRenderPos() == null) {
            return (float) (((world == null ? 0L : world.getTotalWorldTime()) + partialTicks) * speed * 0.3D % 360.0D);
        }
        return (getPhaseDegrees() + partialTicks * speed * 0.3F) % 360.0F;
    }

    @Override
    public void update() {
        super.update();
        if (world == null || world.isRemote || workingSpeed() == 0.0F) return;
        MillstoneRecipes.Recipe recipe = MillstoneRecipes.find(input.getStackInSlot(0));
        if (recipe == null) {
            remainingTicks = 0;
            return;
        }
        ItemStackHandler planned = new ItemStackHandler(output.getSlots());
        for (int i = 0; i < output.getSlots(); i++) planned.setStackInSlot(i, output.getStackInSlot(i).copy());
        for (ItemStack possible : MillstoneRecipes.possible(input.getStackInSlot(0)))
            if (!ItemHandlerHelper.insertItemStacked(planned, possible, false).isEmpty()) return;
        if (world.getTotalWorldTime() % 10L == 0L)
            world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_STONE_HIT,
                    net.minecraft.util.SoundCategory.BLOCKS, .35F, .8F);
        if (remainingTicks <= 0) remainingTicks = recipe.duration();
        remainingTicks -= MathHelper.clamp((int) (Math.abs(workingSpeed()) / 16.0F), 1, 512);
        if (remainingTicks > 0) {
            if (world.getTotalWorldTime() % 20L == 0L) markDirty();
            return;
        }
        java.util.List<ItemStack> results = MillstoneRecipes.roll(input.getStackInSlot(0), world.rand);
        input.extractItem(0, 1, false);
        for (ItemStack result : results) ItemHandlerHelper.insertItemStacked(output, result, false);
        remainingTicks = 0;
        com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "millstone");
        markDirty();
    }

    public ItemStack insertInput(ItemStack stack) { return inventory.insertItem(0, stack, false); }

    public void giveItemsTo(EntityPlayer player) {
        boolean hadOutput = false;
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack stack = output.extractItem(slot, 64, false);
            if (!stack.isEmpty()) {
                hadOutput = true;
                ItemHandlerHelper.giveItemToPlayer(player, stack);
            }
        }
        if (!hadOutput) {
            ItemStack stack = input.extractItem(0, 64, false);
            if (!stack.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, stack);
        }
    }

    public void dropItems(World world, BlockPos pos) {
        for (ItemStackHandler handler : new ItemStackHandler[]{input, output}) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.extractItem(slot, 64, false);
                if (!stack.isEmpty()) Block.spawnAsEntity(world, pos, stack);
            }
        }
    }

    private void contentsChanged() {
        markDirty();
        if (world != null && !world.isRemote) sync();
    }

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Nullable
    @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeInventory(tag);
        return tag;
    }

    private void writeInventory(NBTTagCompound tag) {
        tag.setTag("MillstoneInput", input.serializeNBT());
        tag.setTag("MillstoneOutput", output.serializeNBT());
        tag.setInteger("MillstoneRemaining", remainingTicks);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readInventory(tag);
    }

    private void readInventory(NBTTagCompound tag) {
        if (tag.hasKey("MillstoneInput", 10)) input.deserializeNBT(tag.getCompoundTag("MillstoneInput"));
        if (tag.hasKey("MillstoneOutput", 10)) output.deserializeNBT(tag.getCompoundTag("MillstoneOutput"));
        remainingTicks = Math.max(0, tag.getInteger("MillstoneRemaining"));
    }

    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        writeInventory(tag);
        return tag;
    }
    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readInventory(tag);
    }
}
