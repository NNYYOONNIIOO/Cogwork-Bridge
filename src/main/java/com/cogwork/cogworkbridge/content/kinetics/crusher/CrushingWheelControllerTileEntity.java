package com.cogwork.cogworkbridge.content.kinetics.crusher;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

import javax.annotation.Nullable;
import java.util.UUID;

public final class CrushingWheelControllerTileEntity extends TileEntity implements ITickable {
    private static final DamageSource CRUSH_DAMAGE = new DamageSource("cogworkbridge.crushing_wheel");

    private final ItemStackHandler inventory = new ItemStackHandler(10) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0; }
        @Override protected void onContentsChanged(int slot) { changed(); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 ? super.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 0 ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }
    };

    private float crushingSpeed;
    private int recipeProgress;
    private Entity processingEntity;
    private UUID processingEntityId;
    private boolean heldItem, itemHadNoGravity;

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (crushingSpeed == 0.0F) { if (processingEntity != null) clearProcessing(); return; }
        tickRecipe();
        ejectResults();
        resolveProcessingEntity();
        if (processingEntity == null) {
            EnumFacing facing = world.getBlockState(pos).getValue(CrushingWheelControllerBlock.FACING);
            AxisAlignedBB captureBounds = CrushingWheelShapes.bounds(facing).offset(pos).grow(0.1D);
            for (Entity candidate : world.getEntitiesWithinAABBExcludingEntity(null, captureBounds)) {
                startCrushing(candidate);
                if (processingEntity != null) break;
            }
        }
        if (processingEntity == null) return;
        if (!processingEntity.isEntityAlive()
                || !processingEntity.getEntityBoundingBox().intersects(new AxisAlignedBB(pos).grow(0.6D))) {
            clearProcessing();
            return;
        }

        if (processingEntity instanceof EntityItem item) {
            captureItem(item);
            return;
        }

        EnumFacing facing = world.getBlockState(pos).getValue(CrushingWheelControllerBlock.FACING);
        double speed = Math.min(0.5D, Math.max(0.04D, crushingSpeed / 4.0D));
        processingEntity.motionX = facing.getXOffset() * speed;
        processingEntity.motionY = facing.getYOffset() * speed;
        processingEntity.motionZ = facing.getZOffset() * speed;
        centerOnPassage(facing);

        processingEntity.attackEntityFrom(CRUSH_DAMAGE, 4.0F);
        if (!processingEntity.isEntityAlive()) clearProcessing();
    }

    private void tickRecipe() {
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty()) { recipeProgress = 0; return; }
        net.minecraftforge.items.ItemStackHandler available = new net.minecraftforge.items.ItemStackHandler(9);
        for (int i = 0; i < 9; i++) available.setStackInSlot(i, inventory.getStackInSlot(i + 1).copy());
        if (!CrushingRecipes.accepts(input)) {
            if (!ItemHandlerHelper.insertItemStacked(available, input.copy(), false).isEmpty()) return;
            inventory.setStackInSlot(0, ItemStack.EMPTY);
            for (int i = 0; i < 9; i++) inventory.setStackInSlot(i + 1, available.getStackInSlot(i).copy());
            recipeProgress = 0;
            return;
        }
        // Reserve room for all possible products before rolling them.
        for (ItemStack possible : CrushingRecipes.possible(input))
            if (!ItemHandlerHelper.insertItemStacked(available, possible, false).isEmpty()) return;
        if (world.getTotalWorldTime() % 10L == 0L)
            world.playSound(null, pos, SoundEvents.BLOCK_GRAVEL_HIT, SoundCategory.BLOCKS, .4F, .8F);
        recipeProgress += Math.max(1, (int) (crushingSpeed * 16));
        if (recipeProgress < CrushingRecipes.duration(input)) return;
        java.util.List<ItemStack> results = CrushingRecipes.results(input, world.rand);
        net.minecraftforge.items.ItemStackHandler planned = new net.minecraftforge.items.ItemStackHandler(9);
        for (int i = 0; i < 9; i++) planned.setStackInSlot(i, inventory.getStackInSlot(i + 1).copy());
        for (ItemStack result : results) ItemHandlerHelper.insertItemStacked(planned, result, false);
        input.shrink(1); inventory.setStackInSlot(0, input.isEmpty() ? ItemStack.EMPTY : input);
        for (int i = 0; i < 9; i++) inventory.setStackInSlot(i + 1, planned.getStackInSlot(i).copy());
        recipeProgress = 0; changed();
    }

    private void ejectResults() {
        EnumFacing facing = world.getBlockState(pos).getValue(CrushingWheelControllerBlock.FACING);
        BlockPos targetPos = facing.getAxis().isHorizontal() ? pos.offset(facing).down() : pos.offset(facing);
        if (!world.isBlockLoaded(targetPos)) return;
        TileEntity target = world.getTileEntity(targetPos);
        IItemHandler receiver = target == null ? null : target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, facing.getOpposite());
        for (int slot = 1; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (receiver != null) {
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(receiver, stack.copy(), false);
                if (remainder.getCount() != stack.getCount()) inventory.setStackInSlot(slot, remainder);
                continue;
            }
            EntityItem result = new EntityItem(world, pos.getX() + .5D + facing.getXOffset() * .65D,
                    pos.getY() + .5D + facing.getYOffset() * .65D, pos.getZ() + .5D + facing.getZOffset() * .65D, stack.copy());
            result.motionX = facing.getXOffset() * .25D;
            result.motionY = facing == EnumFacing.UP ? .5D : 0D;
            result.motionZ = facing.getZOffset() * .25D;
            result.getEntityData().setLong("CogworkBridgeCrusherBypass", pos.toLong());
            result.getEntityData().setLong("CogworkBridgeCrusherBypassUntil", world.getTotalWorldTime() + 20L);
            result.setDefaultPickupDelay();
            if (world.spawnEntity(result)) inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    private void centerOnPassage(EnumFacing facing) {
        if (facing.getAxis() != EnumFacing.Axis.Y) {
            processingEntity.motionY = 0.0D;
            processingEntity.motionX += (pos.getX() + 0.5D - processingEntity.posX) * 0.05D;
            processingEntity.motionZ += (pos.getZ() + 0.5D - processingEntity.posZ) * 0.05D;
        }
    }

    private void captureItem(EntityItem item) {
        if (!heldItem) { itemHadNoGravity = item.hasNoGravity(); heldItem = true; item.setNoGravity(true); changed(); }
        if (item.motionX != 0D || item.motionY != 0D || item.motionZ != 0D) item.velocityChanged = true;
        item.motionX = item.motionY = item.motionZ = 0;
        if (!inventory.getStackInSlot(0).isEmpty()) return;
        ItemStack original = item.getItem();
        ItemStack one = original.copy(); one.setCount(1);
        if (!inventory.insertItem(0, one, false).isEmpty()) return;
        ItemStack remainder = original.copy(); remainder.shrink(1);
        if (remainder.isEmpty()) { item.setDead(); clearProcessing(); }
        else item.setItem(remainder);
        world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 0.5F, 0.8F);
    }

    public ItemStack acceptBeltItem(ItemStack stack) {
        if (!isActive() || stack.isEmpty() || !inventory.getStackInSlot(0).isEmpty()) return stack;
        ItemStack one = stack.copy();
        one.setCount(1);
        if (!inventory.insertItem(0, one, false).isEmpty()) return stack;
        ItemStack remainder = stack.copy();
        remainder.shrink(1);
        return remainder;
    }

    public void startCrushing(Entity entity) {
        if (crushingSpeed == 0.0F || processingEntity != null || entity instanceof EntityPlayer
                && ((EntityPlayer) entity).capabilities.isCreativeMode
                || world.getDifficulty() == EnumDifficulty.PEACEFUL && entity instanceof EntityPlayer) return;
        if (entity instanceof EntityItem && (!inventory.getStackInSlot(0).isEmpty()
                || entity.getEntityData().hasKey("CogworkBridgeCrusherBypassUntil")
                && entity.getEntityData().getLong("CogworkBridgeCrusherBypass") == pos.toLong()
                && world.getTotalWorldTime() <= entity.getEntityData().getLong("CogworkBridgeCrusherBypassUntil"))) return;
        processingEntity = entity;
        processingEntityId = entity.getUniqueID();
        changed();
    }

    private void resolveProcessingEntity() {
        if (processingEntity != null || processingEntityId == null) return;
        Entity entity=world instanceof net.minecraft.world.WorldServer level?level.getEntityFromUuid(processingEntityId):null;
        if(entity!=null) {
            processingEntity=entity;
            return;
        }
        processingEntityId = null;
        changed();
    }

    private void clearProcessing() {
        if (heldItem && processingEntity != null) processingEntity.setNoGravity(itemHadNoGravity);
        heldItem = false;
        processingEntity = null;
        processingEntityId = null;
        changed();
    }

    public boolean isProcessing(Entity entity) { return processingEntity == entity; }

    @Override public void invalidate() { clearProcessing(); super.invalidate(); }
    @Override public void onChunkUnload() { clearProcessing(); super.onChunkUnload(); }

    public boolean isActive() { return crushingSpeed > 0.0F; }

    public void setCrushingSpeed(float speed) {
        float next = Float.isFinite(speed) ? Math.max(0.0F, speed) : 0.0F;
        if (Float.compare(crushingSpeed, next) == 0) return;
        crushingSpeed = next;
        changed();
    }

    public void giveItemsTo(EntityPlayer player) {
        boolean gaveItems = false;
        for (int slot = 1; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.extractItem(slot, 64, false);
            if (!stack.isEmpty()) {
                gaveItems = true;
                ItemHandlerHelper.giveItemToPlayer(player, stack);
            }
        }
        if (!gaveItems) {
            ItemStack input = inventory.getStackInSlot(0).copy();
            inventory.setStackInSlot(0, ItemStack.EMPTY);
            if (!input.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, input);
        }
        changed();
    }

    public void dropItems(World dropWorld, BlockPos dropPos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot).copy();
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            if (!stack.isEmpty()) Block.spawnAsEntity(dropWorld, dropPos, stack);
        }
    }

    private void changed() {
        markDirty();
        if (world != null) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag("CrusherInventory", inventory.serializeNBT());
        tag.setFloat("CrusherSpeed", crushingSpeed);
        tag.setInteger("CrusherRecipeProgress", recipeProgress);
        tag.setBoolean("HeldItem", heldItem); tag.setBoolean("ItemHadNoGravity", itemHadNoGravity);
        if (processingEntityId != null) tag.setString("ProcessingEntity", processingEntityId.toString());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        heldItem = tag.getBoolean("HeldItem"); itemHadNoGravity = tag.getBoolean("ItemHadNoGravity");
        if (tag.hasKey("CrusherInventory", 10)) inventory.deserializeNBT(tag.getCompoundTag("CrusherInventory"));
        crushingSpeed = Math.max(0.0F, tag.getFloat("CrusherSpeed"));
        recipeProgress = Math.max(0, tag.getInteger("CrusherRecipeProgress"));
        try {
            processingEntityId = tag.hasKey("ProcessingEntity") ? UUID.fromString(tag.getString("ProcessingEntity")) : null;
        } catch (IllegalArgumentException ignored) {
            processingEntityId = null;
        }
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public void handleUpdateTag(NBTTagCompound tag) { readFromNBT(tag); }

    @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        }
        return super.getCapability(capability, side);
    }
}
