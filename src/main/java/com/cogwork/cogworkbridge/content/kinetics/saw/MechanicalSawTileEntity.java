package com.cogwork.cogworkbridge.content.kinetics.saw;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionItemCollector;
import com.cogwork.cogworkbridge.content.kinetics.drill.DrillBreakingProgress;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockReed;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.item.ItemStack;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.Recipe;
import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.Output;
import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Cuts sawable blocks in front of a powered or moving saw. */
public final class MechanicalSawTileEntity extends KineticTileEntity {
    private final DrillBreakingProgress breaker = new DrillBreakingProgress();
    private int cuttingTicks;
    private int exportDelay;
    private int recipeIndex;
    private int recipeDuration = 100;
    private Recipe activeRecipe;
    private ItemStack filter = ItemStack.EMPTY;
    private final java.util.List<ItemStack> pendingOutputs = new java.util.ArrayList<>();
    private boolean changingInventory;
    private final net.minecraftforge.items.ItemStackHandler cuttingInventory = new net.minecraftforge.items.ItemStackHandler(2) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && !SawRecipes.matching(stack, filter).isEmpty(); }
        @Override public net.minecraft.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 ? super.extractItem(slot, amount, simulate) : net.minecraft.item.ItemStack.EMPTY;
        }
        @Override public net.minecraft.item.ItemStack insertItem(int slot, net.minecraft.item.ItemStack stack, boolean simulate) {
            return slot == 0 && getStackInSlot(0).isEmpty() && getStackInSlot(1).isEmpty()
                    && pendingOutputs.isEmpty() && isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
        }
        @Override protected void onContentsChanged(int slot) {
            if (!changingInventory) { markDirty(); if (world != null && !world.isRemote) sync(); }
        }
    };
    @Override public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, @javax.annotation.Nullable EnumFacing side) {
        return capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }
    @Override public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @javax.annotation.Nullable EnumFacing side) {
        return capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                ? net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(cuttingInventory) : super.getCapability(capability, side);
    }
    public void acceptItem(EntityItem entity) {
        if (world == null || world.isRemote || getSpeed() == 0 || entity.isDead || world.getBlockState(pos).getValue(MechanicalSawBlock.FACING) != EnumFacing.UP) return;
        net.minecraft.item.ItemStack rest = cuttingInventory.insertItem(0, entity.getItem(), false);
        if (rest.isEmpty()) entity.setDead(); else entity.setItem(rest);
    }
    private void tickCutting() {
        fillOutput();
        if (!cuttingInventory.getStackInSlot(1).isEmpty()) {
            if (exportDelay > 0) { exportDelay--; if (exportDelay % 4 == 0) sync(); }
            else exportCutting();
            if (!cuttingInventory.getStackInSlot(1).isEmpty() || !pendingOutputs.isEmpty()) return;
        }
        ItemStack input = cuttingInventory.getStackInSlot(0);
        if (input.isEmpty()) { cuttingTicks = 0; activeRecipe = null; return; }
        if (activeRecipe == null) {
            var recipes = SawRecipes.matching(input, filter);
            if (recipes.isEmpty()) {
                // A changed filter must never trap or destroy an already inserted stack.
                pendingOutputs.add(input.copy());
                cuttingInventory.setStackInSlot(0, ItemStack.EMPTY);
                cuttingTicks = 0; exportDelay = 10; fillOutput(); sync();
                return;
            }
            activeRecipe = recipes.get(Math.floorMod(recipeIndex, recipes.size()));
            recipeDuration = Math.max(1, activeRecipe.duration()) * Math.max(1, input.getCount() / 5);
            cuttingTicks = 0;
            world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_WOOD_HIT,
                    net.minecraft.util.SoundCategory.BLOCKS, .5F, 1.25F);
        }
        cuttingTicks += processingSpeed();
        if (cuttingTicks < recipeDuration) { if (world.getTotalWorldTime() % 5 == 0) sync(); return; }
        // Roll once per input and persist the actual results, including blocked outputs.
        // A queue avoids the old single output slot silently discarding extra products.
        changingInventory = true;
        try {
            ItemStack single = input.copy(); single.setCount(1);
            for (int roll = 0; roll < input.getCount(); roll++) {
                for (ItemStack result : activeRecipe.roll(world.rand)) queueOutput(result);
                if (single.getItem().hasContainerItem(single)) queueOutput(single.getItem().getContainerItem(single));
            }
            cuttingInventory.setStackInSlot(0, ItemStack.EMPTY);
            activeRecipe = null; cuttingTicks = 0; recipeIndex++; exportDelay = 10;
            fillOutput();
        } finally { changingInventory = false; }
        markDirty(); sync();
    }
    private int processingSpeed() { return Math.max(1, Math.min(128, (int) (Math.abs(getSpeed()) / 24F))); }
    private void queueOutput(ItemStack result) {
        if (result.isEmpty()) return;
        ItemStack rest = result.copy();
        for (ItemStack queued : pendingOutputs) {
            if (!ItemStack.areItemsEqual(queued, rest) || !ItemStack.areItemStackTagsEqual(queued, rest)) continue;
            int moved = Math.min(rest.getCount(), Math.max(0, queued.getMaxStackSize() - queued.getCount()));
            queued.grow(moved); rest.shrink(moved);
            if (rest.isEmpty()) return;
        }
        while (!rest.isEmpty()) pendingOutputs.add(rest.splitStack(Math.min(rest.getCount(), rest.getMaxStackSize())));
    }
    private void fillOutput() {
        if (cuttingInventory.getStackInSlot(1).isEmpty() && !pendingOutputs.isEmpty())
            cuttingInventory.setStackInSlot(1, pendingOutputs.remove(0));
    }
    public ItemStack getFilter() { return filter.copy(); }
    public void setFilter(ItemStack stack) {
        filter = stack.copy(); if (!filter.isEmpty()) filter.setCount(1);
        recipeIndex = 0; markDirty(); if (world != null && !world.isRemote) sync();
    }
    private void exportCutting() {
        net.minecraft.item.ItemStack out = cuttingInventory.getStackInSlot(1);
        if (out.isEmpty()) return;
        EnumFacing.Axis axis = world.getBlockState(pos).getValue(MechanicalSawBlock.SHAFT_AXIS);
        EnumFacing direction = axis == EnumFacing.Axis.X ? EnumFacing.SOUTH : EnumFacing.EAST;
        if (getSpeed() < 0) direction = direction.getOpposite();
        net.minecraft.tileentity.TileEntity target = world.getTileEntity(pos.offset(direction));
        if (target instanceof com.cogwork.cogworkbridge.content.logistics.belt.BeltTileEntity belt) {
            cuttingInventory.setStackInSlot(1, belt.insertFrom(direction, out.copy(), false));
            return;
        }
        if (target != null) {
            net.minecraftforge.items.IItemHandler handler = target.getCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite());
            if (handler != null) {
                net.minecraft.item.ItemStack remainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(handler, out.copy(), false);
                cuttingInventory.setStackInSlot(1, remainder); return;
            }
        }
        EntityItem drop = new EntityItem(world, pos.getX() + .5 + direction.getXOffset() * .55,
                pos.getY() + .9, pos.getZ() + .5 + direction.getZOffset() * .55, out.copy());
        drop.motionX = direction.getXOffset() * .06; drop.motionY = .1; drop.motionZ = direction.getZOffset() * .06;
        drop.setDefaultPickupDelay(); if (world.spawnEntity(drop)) cuttingInventory.setStackInSlot(1, net.minecraft.item.ItemStack.EMPTY);
    }
    public net.minecraft.item.ItemStack displayItem() {
        net.minecraft.item.ItemStack output = cuttingInventory.getStackInSlot(1);
        return (output.isEmpty() ? cuttingInventory.getStackInSlot(0) : output).copy();
    }
    public float displayProgress(float partial) {
        if (!cuttingInventory.getStackInSlot(1).isEmpty()) return .5F + (10F - exportDelay) / 20F;
        if (getSpeed() == 0) return .5F;
        return Math.min(.5F, (cuttingTicks + partial * processingSpeed()) / (2F * Math.max(1, recipeDuration)));
    }
    public EnumFacing itemDirection() {
        EnumFacing.Axis axis = world.getBlockState(pos).getValue(MechanicalSawBlock.SHAFT_AXIS);
        EnumFacing direction = axis == EnumFacing.Axis.X ? EnumFacing.SOUTH : EnumFacing.EAST;
        return getSpeed() < 0 ? direction.getOpposite() : direction;
    }
    public void dropCuttingInventory() {
        if (world == null || world.isRemote) return;
        for (int i = 0; i < 2; i++) {
            net.minecraft.item.ItemStack stack = cuttingInventory.getStackInSlot(i);
            if (!stack.isEmpty()) net.minecraft.block.Block.spawnAsEntity(world, pos, stack.copy());
            cuttingInventory.setStackInSlot(i, net.minecraft.item.ItemStack.EMPTY);
        }
        for (ItemStack output : pendingOutputs) Block.spawnAsEntity(world, pos, output.copy());
        pendingOutputs.clear();
        if (ListFilterItem.isFilterItem(filter)) Block.spawnAsEntity(world, pos, filter.copy());
        filter = ItemStack.EMPTY; activeRecipe = null;
    }
    private void writeCutting(NBTTagCompound tag) {
        tag.setTag("CuttingItems", cuttingInventory.serializeNBT());
        tag.setInteger("CuttingTicks", cuttingTicks);
        tag.setInteger("SawExportDelay", exportDelay);
        tag.setInteger("SawRecipeIndex", recipeIndex);
        tag.setInteger("SawRecipeDuration", recipeDuration);
        tag.setTag("SawFilter", filter.writeToNBT(new NBTTagCompound()));
        NBTTagList pending = new NBTTagList();
        for (ItemStack stack : pendingOutputs) pending.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("SawPendingOutputs", pending);
        NBTTagList recipe = new NBTTagList();
        if (activeRecipe != null) for (Output output : activeRecipe.outputs()) {
            NBTTagCompound entry = output.stack().writeToNBT(new NBTTagCompound());
            entry.setFloat("Chance", output.chance()); recipe.appendTag(entry);
        }
        tag.setTag("SawActiveRecipe", recipe);
    }
    private void readCutting(NBTTagCompound tag) {
        changingInventory = true;
        try {
            if (tag.hasKey("CuttingItems", 10)) cuttingInventory.deserializeNBT(tag.getCompoundTag("CuttingItems"));
        } finally { changingInventory = false; }
        cuttingTicks = Math.max(0, tag.getInteger("CuttingTicks"));
        exportDelay = Math.max(0, tag.getInteger("SawExportDelay"));
        recipeIndex = tag.getInteger("SawRecipeIndex");
        recipeDuration = Math.max(1, tag.getInteger("SawRecipeDuration"));
        filter = new ItemStack(tag.getCompoundTag("SawFilter"));
        pendingOutputs.clear();
        NBTTagList pending = tag.getTagList("SawPendingOutputs", 10);
        for (int i = 0; i < pending.tagCount(); i++) queueOutput(new ItemStack(pending.getCompoundTagAt(i)));
        java.util.List<Output> outputs = new java.util.ArrayList<>();
        NBTTagList recipe = tag.getTagList("SawActiveRecipe", 10);
        for (int i = 0; i < recipe.tagCount(); i++) {
            NBTTagCompound entry = recipe.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(entry);
            if (!stack.isEmpty()) outputs.add(new Output(stack, entry.getFloat("Chance")));
        }
        activeRecipe = outputs.isEmpty() ? null : new Recipe("cutting", java.util.List.of(),
                java.util.List.of(), outputs, recipeDuration, false, null);
    }
    private long lastDamageTick = Long.MIN_VALUE;
    private boolean worldChanged;
    private boolean flipped;

    public boolean isFlipped() { return flipped; }

    public void setFlipped(boolean flipped) {
        if (world == null || world.isRemote || this.flipped == flipped) return;
        this.flipped = flipped;
        markDirty();
        sync();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("SawFlipped", flipped);
        writeCutting(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        flipped = tag.getBoolean("SawFlipped");
        readCutting(tag);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setBoolean("SawFlipped", flipped);
        writeCutting(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        boolean previous = flipped;
        super.handleUpdateTag(tag);
        flipped = tag.getBoolean("SawFlipped");
        readCutting(tag);
        if (previous == flipped || world == null || !world.isRemote) return;
        if (world instanceof FakeWorld fake) fake.getMobileChunk().setChunkModified();
        else world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Override public float getStressImpact() { return 4.0F; }

    @Override
    public void update() {
        super.update();
        if (world == null || world.isRemote || world instanceof FakeWorld) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalSawBlock) || getSpeed() == 0.0F) {
            breaker.clear();
            return;
        }
        EnumFacing facing = state.getValue(MechanicalSawBlock.FACING);
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            if (facing == EnumFacing.UP) tickCutting();
            breaker.clear();
            return;
        }
        BlockPos target = pos.offset(facing);
        if (!world.isBlockLoaded(target) || !isSawable(world, target, world.getBlockState(target))) {
            breaker.clear();
            return;
        }
        breaker.tick(world, target, Math.abs(getSpeed()) / 100.0F, false,
                targetPos -> cutTree(world, targetPos, null));
    }

    public void wakeBreaker() { breaker.wake(); }
    public void stopMovingBreaker() { breaker.clear(); }

    public boolean tickMovingStep(BearingContraptionEntity contraption,
                                  float fromAngle, float toAngle, float bearingSpeed) {
        if (world == null || contraption.world.isRemote) {
            breaker.clear();
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalSawBlock)) {
            breaker.clear();
            return false;
        }
        EnumFacing facing = state.getValue(MechanicalSawBlock.FACING);
        BlockPos anchor = contraption.getAnchor();
        Vec3d tip = new Vec3d(anchor.getX() + pos.getX() + 0.5D + facing.getXOffset() * 0.65D,
                anchor.getY() + pos.getY() + 0.5D + facing.getYOffset() * 0.65D,
                anchor.getZ() + pos.getZ() + 0.5D + facing.getZOffset() * 0.65D);
        Vec3d before = contraption.actorStepStart(tip, fromAngle);
        Vec3d after = contraption.localToWorld(tip, toAngle);
        Vec3d direction = contraption.localToWorld(tip.add(new Vec3d(facing.getDirectionVec())), toAngle)
                .subtract(after);
        Vec3d motion = after.subtract(before);
        if (motion.lengthSquared() < 1.0E-12D || motion.dotProduct(direction) < -1.0E-8D
                || Math.abs(direction.y) > Math.max(Math.abs(direction.x), Math.abs(direction.z))) {
            breaker.clear();
            return false;
        }
        World realWorld = contraption.world;
        BlockPos tipPos = new BlockPos(after);
        if (realWorld.isBlockLoaded(tipPos)) {
            damageMovingEntities(contraption, tipPos, motion.length());
        }
        BlockPos target = contraption.findToolTarget(pos, facing, toAngle, breaker.currentTarget(),
                (candidate, blockState) -> blockState.getBlockHardness(realWorld, candidate) >= 0.0F
                        && isSawable(realWorld, candidate, blockState));
        if (target == null) {
            breaker.clear();
            return false;
        }
        IBlockState beforeState = realWorld.getBlockState(target);
        double distance = contraption.actorTickMovement(tip, fromAngle, bearingSpeed).length();
        float animationSpeed = distance < 1.0D / 512.0D ? 0.0F
                : (float) (Math.floor((distance * 1000.0D + 100.0D) / 100.0D) * 100.0D);
        float breakSpeed = Math.max(1.0F / 128.0F, Math.min(16.0F, animationSpeed / 500.0F));
        boolean breaking = breaker.tick(realWorld, target, breakSpeed, true, true,
                targetPos -> cutTree(realWorld, targetPos, contraption));
        if (beforeState != realWorld.getBlockState(target)) {
            worldChanged = true;
        }
        return breaking;
    }

    public boolean consumeWorldChanged() {
        boolean changed = worldChanged;
        worldChanged = false;
        return changed;
    }

    private void cutTree(World realWorld, BlockPos target, BearingContraptionEntity contraption) {
        IBlockState initial = realWorld.getBlockState(target);
        java.util.List<BlockPos> connected = SawTreeCutter.collect(realWorld, target, initial);
        for (BlockPos extra : connected) {
            if (contraption == null) {
                realWorld.destroyBlock(extra, true);
            } else {
                ContraptionItemCollector.destroyAndCollect(contraption, extra);
            }
        }
        if (contraption == null) {
            realWorld.destroyBlock(target, true);
        } else {
            ContraptionItemCollector.destroyAndCollect(contraption, target);
        }
    }

    private static boolean isSawable(World world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockSapling) {
            return false;
        }
        return block.isWood(world, pos) || block.isLeaves(state, world, pos)
                || block instanceof BlockReed || block instanceof BlockCactus
                || block == Blocks.MELON_BLOCK || block == Blocks.PUMPKIN
                || block == Blocks.CHORUS_PLANT || block == Blocks.CHORUS_FLOWER
                || block == Blocks.BROWN_MUSHROOM_BLOCK || block == Blocks.RED_MUSHROOM_BLOCK;
    }

    private void damageMovingEntities(BearingContraptionEntity contraption, BlockPos target, double motion) {
        World realWorld = contraption.world;
        long now = realWorld.getTotalWorldTime();
        if (lastDamageTick == now) return;
        lastDamageTick = now;
        float damage = (float) Math.max(2.0D, Math.min(10.0D, 6.0D * Math.pow(motion, 0.4D) + 1.0D));
        for (Entity entity : realWorld.getEntitiesWithinAABBExcludingEntity(contraption, new AxisAlignedBB(target))) {
            if (!(entity instanceof EntityItem) && !(entity instanceof BearingContraptionEntity)) {
                entity.attackEntityFrom(MechanicalSawBlock.SAW_DAMAGE, damage);
            }
        }
    }

    @Override public void invalidate() { breaker.clear(); super.invalidate(); }
    @Override public void onChunkUnload() { breaker.clear(); super.onChunkUnload(); }
}
