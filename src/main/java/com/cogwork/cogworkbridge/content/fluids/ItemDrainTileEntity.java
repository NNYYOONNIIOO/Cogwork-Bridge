package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionWorld;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

public final class ItemDrainTileEntity extends FluidTankTileEntity {
    private static final int PROCESSING_TIME = 20;
    private static final float MOVEMENT_PER_TICK = 1.0F / 8.0F;

    private ItemStack heldItem = ItemStack.EMPTY;
    private EnumFacing travelDirection = EnumFacing.SOUTH;
    private float itemPosition;
    private float previousItemPosition;
    private int processingTicks;
    private final IItemHandler[] itemHandlers = new IItemHandler[6];
    private final IFluidHandler output = new IFluidHandler() {
        @Override public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[]{new FluidTankProperties(getFluid(), getCapacity(), false, true)};
        }
        @Override public int fill(FluidStack resource, boolean doFill) { return 0; }
        @Override public FluidStack drain(FluidStack resource, boolean doDrain) {
            return getFluidHandler().drain(resource, doDrain);
        }
        @Override public FluidStack drain(int maxDrain, boolean doDrain) {
            return getFluidHandler().drain(maxDrain, doDrain);
        }
    };

    public ItemDrainTileEntity() {
        tank.setCapacity(1500);
        for (EnumFacing side : EnumFacing.HORIZONTALS) itemHandlers[side.getIndex()] = new ItemPort(side);
    }

    @Override
    public void update() {
        if (world == null) return;
        previousItemPosition = itemPosition;
        if (heldItem.isEmpty()) {
            processingTicks = 0;
            return;
        }
        if (processingTicks > 0) {
            if (!world.isRemote) {
                FluidStack fluid = fluidIn(heldItem);
                if (processingTicks >= 5 && fluid == null) {
                    processingTicks = 0;
                    syncItem();
                    return;
                }
                if (processingTicks > 5 && tank.fill(fluid, false) != fluid.amount) {
                    processingTicks = PROCESSING_TIME;
                    return;
                }
                if (processingTicks == 5) {
                    ItemStack result = FluidContainerHelper.empty(heldItem, getFluidHandler(), fluid);
                    if (result != null) heldItem = result;
                    else processingTicks = 0;
                    syncItem();
                }
                markDirty();
            }
            if (processingTicks > 0) processingTicks--;
            return;
        }

        itemPosition = Math.min(1.0F, itemPosition + MOVEMENT_PER_TICK);
        if (!world.isRemote) {
            markDirty();
            if (previousItemPosition < 0.5F && itemPosition >= 0.5F && fluidIn(heldItem) != null) {
                itemPosition = 0.5F;
                processingTicks = PROCESSING_TIME;
                syncItem();
            } else if (itemPosition >= 1.0F) {
                exportItem();
            }
        }
    }

    public ItemStack getHeldItemStack() { return heldItem; }
    public EnumFacing getTravelDirection() { return travelDirection; }
    public float getItemPosition(float partialTicks) {
        return previousItemPosition + (itemPosition - previousItemPosition) * partialTicks;
    }

    public boolean tryDrainHeldItem(EntityPlayer player, EnumHand hand) {
        if (world == null || world.isRemote) return false;
        ItemStack held = player.getHeldItem(hand);
        FluidStack fluid = fluidIn(held);
        if (fluid == null || tank.fill(fluid, false) != fluid.amount) return false;
        ItemStack result = FluidContainerHelper.empty(held, getFluidHandler(), fluid);
        if (result == null) return false;
        if (!player.capabilities.isCreativeMode) {
            ItemStack returned = result;
            if (held.getCount() == 1) player.setHeldItem(hand, returned);
            else {
                held.shrink(1);
                ItemHandlerHelper.giveItemToPlayer(player, returned);
            }
        }
        return true;
    }

    public boolean takeHeldItem(EntityPlayer player) {
        if (heldItem.isEmpty()) return false;
        ItemHandlerHelper.giveItemToPlayer(player, heldItem);
        heldItem = ItemStack.EMPTY;
        processingTicks = 0;
        syncItem();
        return true;
    }

    public void tryInsertItemEntity(EntityItem entity) {
        if (world == null || world.isRemote || entity.isDead || !heldItem.isEmpty()) return;
        ItemStack stack = entity.getItem();
        EnumFacing direction = EnumFacing.getFacingFromVector((float) entity.motionX, 0, (float) entity.motionZ);
        if (direction.getAxis().isVertical()) direction = EnumFacing.SOUTH;
        ItemStack remainder = insert(stack, direction, 0.49F, false);
        if (remainder.isEmpty()) entity.setDead();
        else if (remainder.getCount() != stack.getCount()) entity.setItem(remainder);
    }

    public void dropHeldItem() {
        if (world == null || world.isRemote || heldItem.isEmpty()) return;
        if (world instanceof ContraptionWorld local) {
            local.spawnCapturedDrop(pos, heldItem);
        } else if (world instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity) {
            entity.spawnDrop(pos, java.util.Collections.singletonList(heldItem));
        } else if (!(world instanceof FakeWorld)) {
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), heldItem);
        }
        heldItem = ItemStack.EMPTY;
    }

    private ItemStack insert(ItemStack stack, EnumFacing direction, float position, boolean simulate) {
        if (!heldItem.isEmpty() || stack.isEmpty()) return stack;
        int accepted = fluidIn(stack) == null ? Math.min(stack.getCount(), stack.getMaxStackSize()) : 1;
        ItemStack remainder = stack.copy();
        remainder.shrink(accepted);
        if (!simulate) {
            heldItem = stack.copy();
            heldItem.setCount(accepted);
            travelDirection = direction;
            itemPosition = previousItemPosition = position;
            processingTicks = 0;
            syncItem();
        }
        return remainder;
    }

    private void exportItem() {
        if (!world.isBlockLoaded(pos.offset(travelDirection))) return;
        TileEntity target = world.getTileEntity(pos.offset(travelDirection));
        if (target instanceof BeltTileEntity belt) {
            int previousCount = heldItem.getCount();
            ItemStack remainder = belt.insertFrom(travelDirection, heldItem, false);
            if (remainder.getCount() != previousCount) {
                heldItem = remainder;
                syncItem();
            }
            return;
        }
        IItemHandler handler = target == null ? null : target.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, travelDirection.getOpposite());
        if (handler != null) {
            int previousCount = heldItem.getCount();
            ItemStack remainder = ItemHandlerHelper.insertItem(handler, heldItem, false);
            if (remainder.getCount() != previousCount) {
                heldItem = remainder;
                syncItem();
            }
            return;
        }
        if (world.getBlockState(pos.offset(travelDirection)).isSideSolid(
                world, pos.offset(travelDirection), travelDirection.getOpposite())) return;
        Vec3d position = new Vec3d(pos.getX() + 0.5D + travelDirection.getXOffset() * 0.75D,
                pos.getY() + 0.875D, pos.getZ() + 0.5D + travelDirection.getZOffset() * 0.75D);
        Vec3d motion = new Vec3d(travelDirection.getXOffset() * MOVEMENT_PER_TICK, 0.125D,
                travelDirection.getZOffset() * MOVEMENT_PER_TICK);
        net.minecraft.world.World destination = world;
        if (world instanceof FakeWorld fake) {
            if (!(fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity entity)) return;
            position = entity.localToWorld(position.add(entity.getAnchor().getX(),
                    entity.getAnchor().getY(), entity.getAnchor().getZ()));
            motion = entity.localFacingVectorToWorld(travelDirection).scale(MOVEMENT_PER_TICK)
                    .add(entity.localFacingVectorToWorld(EnumFacing.UP).scale(0.125D));
            destination = fake.getParentWorld();
        }
        EntityItem item = new EntityItem(destination, position.x, position.y, position.z, heldItem);
        item.motionX = motion.x;
        item.motionY = motion.y;
        item.motionZ = motion.z;
        item.setDefaultPickupDelay();
        if (destination.spawnEntity(item)) {
            heldItem = ItemStack.EMPTY;
            syncItem();
        }
    }

    @Nullable
    private static FluidStack fluidIn(ItemStack stack) {
        return FluidContainerHelper.contents(stack);
    }

    private void syncItem() {
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }

    public int comparatorLevel() {
        return tank.getFluidAmount() <= 0 ? 0
                : Math.min(15, 1 + (int) Math.floor(14.0D * tank.getFluidAmount() / tank.getCapacity()));
    }

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return side != EnumFacing.UP;
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return side != null && side.getAxis().isHorizontal();
        return super.hasCapability(capability, side);
    }

    @Nullable
    @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
            return side == EnumFacing.UP ? null : CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(output);
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            return side == null || side.getAxis().isVertical() ? null
                    : CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandlers[side.getIndex()]);
        return super.getCapability(capability, side);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!heldItem.isEmpty()) tag.setTag("HeldItem", heldItem.writeToNBT(new NBTTagCompound()));
        tag.setByte("ItemDirection", (byte) travelDirection.getIndex());
        tag.setFloat("ItemPosition", itemPosition);
        tag.setInteger("ProcessingTicks", processingTicks);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        heldItem = tag.hasKey("HeldItem", 10) ? new ItemStack(tag.getCompoundTag("HeldItem")) : ItemStack.EMPTY;
        travelDirection = EnumFacing.byIndex(tag.getByte("ItemDirection"));
        if (travelDirection.getAxis().isVertical()) travelDirection = EnumFacing.SOUTH;
        itemPosition = previousItemPosition = Math.max(0.0F, Math.min(1.0F, tag.getFloat("ItemPosition")));
        processingTicks = Math.max(0, Math.min(PROCESSING_TIME, tag.getInteger("ProcessingTicks")));
    }

    private final class ItemPort implements IItemHandler {
        private final EnumFacing side;
        private ItemPort(EnumFacing side) { this.side = side; }
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? heldItem : ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 ? insert(stack, side.getOpposite(), 0.0F, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || heldItem.isEmpty()) return ItemStack.EMPTY;
            ItemStack extracted = heldItem.copy();
            extracted.setCount(Math.min(amount, heldItem.getCount()));
            if (!simulate) {
                heldItem.shrink(extracted.getCount());
                if (heldItem.isEmpty()) processingTicks = 0;
                syncItem();
            }
            return extracted;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
    }
}
