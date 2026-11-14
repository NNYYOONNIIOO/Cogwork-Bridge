package com.cogwork.cogworkbridge.content.fluids;

import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemHolder;
import com.cogwork.cogworkbridge.content.logistics.transport.TransportedItemProcessor;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public final class FluidSpoutTileEntity extends FluidTankTileEntity implements TransportedItemProcessor {
    private static final int PROCESSING_TIME = 20;
    private int processingTicks = -1;
    @Nullable private UUID targetItem;
    private boolean targetBlock;
    private boolean targetTransport;
    private ItemStack targetTransportItem = ItemStack.EMPTY;

    public FluidSpoutTileEntity() {
        tank.setCapacity(1000);
    }

    @Override
    public void update() {
        if (world == null) return;
        if (processingTicks >= 0) {
            if (!world.isRemote) {
                if (targetItem != null) {
                    EntityItem item = findItem(targetItem);
                    if (item != null) {
                        item.motionX = 0;
                        item.motionZ = 0;
                        item.setPickupDelay(10);
                    }
                }
                if (processingTicks == 5) finishProcessing();
                markDirty();
            } else if (processingTicks >= 8) {
                spawnFluidParticle();
            }
            processingTicks--;
            if (processingTicks < 0) {
                targetItem = null;
                targetBlock = false;
                targetTransport = false;
                targetTransportItem = ItemStack.EMPTY;
            }
            return;
        }
        if (world.isRemote || tank.getFluidAmount() <= 0 || !world.isBlockLoaded(pos.down(2))) return;

        if (getBlockFillAmount() > 0) {
            targetBlock = true;
            startProcessing();
            return;
        }
        TileEntity tile = world.getTileEntity(pos.down(2));
        if (tile instanceof TransportedItemHolder holder && canFill(holder.getTransportedItem())) {
            targetTransport = true;
            targetTransportItem = holder.getTransportedItem().copy();
            startProcessing();
            return;
        }
        for (EntityItem item : itemsInWorkArea()) {
            if (!item.isDead && canFill(item.getItem())) {
                targetItem = item.getUniqueID();
                startProcessing();
                return;
            }
        }
    }

    public float getProcessingTicks(float partialTicks) {
        return processingTicks < 0 ? -1.0F : processingTicks - partialTicks;
    }

    @Override public boolean holdTransportedItem(TransportedItemHolder holder) {
        if (world == null || world.isRemote) return false;
        ItemStack item = holder.getTransportedItem();
        if (!FluidContainerHelper.canPossiblyFill(item) && !FillingRecipes.isCandidate(item)) return false;
        FluidStack fluid = tank.getFluid();
        if (fluid == null || fluid.amount <= 0) return true;
        if (!canFill(item)) {
            FluidTank fullTank = new FluidTank(tank.getCapacity());
            FluidStack full = fluid.copy(); full.amount = tank.getCapacity();
            fullTank.setFluid(full);
            return FluidContainerHelper.canFill(item, fullTank, tank.getCapacity()) || FillingRecipes.canFill(item, fullTank);
        }
        if (processingTicks >= 0) return true;
        targetTransport = true;
        targetTransportItem = item.copy();
        startProcessing();
        return true;
    }

    private void startProcessing() {
        processingTicks = PROCESSING_TIME;
        syncProcessing();
        world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW,
                SoundCategory.BLOCKS, 0.4F, 1.1F);
    }

    private void finishProcessing() {
        if (targetBlock) {
            int amount = getBlockFillAmount();
            if (amount <= 0) return;
            BlockPos target = pos.down(2);
            IBlockState state = world.getBlockState(target);
            IBlockState next;
            if (state.getBlock() == Blocks.CAULDRON) {
                if (tank.getFluid() != null && tank.getFluid().getFluid() == FluidRegistry.LAVA) {
                    Block lavaCauldron = Block.getBlockFromName("farmers_future_delight:lava_cauldron");
                    if (lavaCauldron == null || lavaCauldron == Blocks.AIR) return;
                    next = lavaCauldron.getDefaultState();
                } else {
                    next = state.withProperty(BlockCauldron.LEVEL, state.getValue(BlockCauldron.LEVEL) + 1);
                }
            } else if (state.getBlock() == Blocks.FARMLAND) {
                next = state.withProperty(BlockFarmland.MOISTURE, state.getValue(BlockFarmland.MOISTURE) + 1);
            } else return;
            if (world.setBlockState(target, next, 3)) {
                tank.drain(amount, true);
                syncProcessing();
            }
            return;
        }

        if (targetTransport) {
            TileEntity tile = world.getTileEntity(pos.down(2));
            if (!(tile instanceof TransportedItemHolder holder)) return;
            ItemStack original = holder.getTransportedItem();
            if (original.isEmpty() || targetTransportItem.isEmpty()
                    || !ItemStack.areItemsEqual(original, targetTransportItem)
                    || !ItemStack.areItemStackTagsEqual(original, targetTransportItem) || !canFill(original)) return;
            ItemStack result = fillItem(original);
            if (result != null) {
                holder.replaceOneWith(result);
                syncProcessing();
            }
            return;
        }

        EntityItem item = targetItem == null ? null : findItem(targetItem);
        if (item == null || !canFill(item.getItem())) return;
        ItemStack original = item.getItem();
        ItemStack result = fillItem(original);
        if (result == null) return;
        if (original.getCount() == 1) {
            item.setItem(result);
        } else {
            original.shrink(1);
            item.setItem(original);
            EntityItem output = new EntityItem(world, item.posX, item.posY, item.posZ, result);
            output.setDefaultPickupDelay();
            world.spawnEntity(output);
        }
        syncProcessing();
    }

    private boolean canFill(ItemStack stack) {
        return !stack.isEmpty() && (FillingRecipes.canFill(stack, getFluidHandler())
                || FluidContainerHelper.canFill(stack, getFluidHandler(), tank.getCapacity()));
    }

    @Nullable private ItemStack fillItem(ItemStack stack) {
        if (FillingRecipes.canFill(stack, getFluidHandler())) {
            ItemStack result = FillingRecipes.fill(stack, getFluidHandler());
            return result.isEmpty() ? null : result;
        }
        return FluidContainerHelper.fill(stack, getFluidHandler(), tank.getCapacity());
    }

    public boolean canInteractWithContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        FluidStack contents = FluidContainerHelper.contents(stack);
        return contents != null || FluidContainerHelper.canFill(stack, getFluidHandler(), tank.getCapacity());
    }

    @Nullable
    public ItemStack interactWithContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FluidStack contents = FluidContainerHelper.contents(stack);
        if (contents != null) return FluidContainerHelper.empty(stack, getFluidHandler(), contents);
        return FluidContainerHelper.fill(stack, getFluidHandler(), tank.getCapacity());
    }

    private int getBlockFillAmount() {
        FluidStack fluid = tank.getFluid();
        if (fluid == null) return 0;
        IBlockState state = world.getBlockState(pos.down(2));
        if (fluid.getFluid() == FluidRegistry.LAVA && state.getBlock() == Blocks.CAULDRON
                && state.getValue(BlockCauldron.LEVEL) == 0 && fluid.amount >= 1000) {
            Block lavaCauldron = Block.getBlockFromName("farmers_future_delight:lava_cauldron");
            return lavaCauldron == null || lavaCauldron == Blocks.AIR ? 0 : 1000;
        }
        if (fluid.getFluid() != FluidRegistry.WATER) return 0;
        if (state.getBlock() == Blocks.CAULDRON
                && state.getValue(BlockCauldron.LEVEL) < 3 && fluid.amount >= 250) return 250;
        if (state.getBlock() == Blocks.FARMLAND
                && state.getValue(BlockFarmland.MOISTURE) < 7 && fluid.amount >= 100) return 100;
        return 0;
    }

    private List<EntityItem> itemsInWorkArea() {
        return world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos.down(2)).grow(0.35D, 0.15D, 0.35D));
    }

    @Nullable
    private EntityItem findItem(UUID id) {
        for (EntityItem item : itemsInWorkArea()) {
            if (!item.isDead && item.getUniqueID().equals(id)) return item;
        }
        return null;
    }

    private void spawnFluidParticle() {
        FluidStack fluid = tank.getFluid();
        if (fluid == null) return;
        com.cogwork.cogworkbridge.CogworkBridge.proxy.spawnFluidParticle(world, fluid,
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, -0.1D, 0);
    }

    private void syncProcessing() {
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        return super.getRenderBoundingBox().expand(0, -2, 0);
    }

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && facing == EnumFacing.DOWN) return false;
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && facing == EnumFacing.DOWN) return null;
        return super.getCapability(capability, facing);
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("ProcessingTicks", processingTicks);
        tag.setBoolean("ProcessingBlock", targetBlock);
        tag.setBoolean("ProcessingTransport", targetTransport);
        if (!targetTransportItem.isEmpty())
            tag.setTag("ProcessingTransportItem", targetTransportItem.writeToNBT(new NBTTagCompound()));
        if (targetItem != null) tag.setUniqueId("ProcessingItem", targetItem);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        processingTicks = tag.hasKey("ProcessingTicks")
                ? Math.max(-1, Math.min(PROCESSING_TIME, tag.getInteger("ProcessingTicks"))) : -1;
        targetBlock = tag.getBoolean("ProcessingBlock");
        targetTransport = tag.getBoolean("ProcessingTransport");
        targetTransportItem = tag.hasKey("ProcessingTransportItem", 10)
                ? new ItemStack(tag.getCompoundTag("ProcessingTransportItem")) : ItemStack.EMPTY;
        targetItem = tag.hasUniqueId("ProcessingItem") ? tag.getUniqueId("ProcessingItem") : null;
    }
}
