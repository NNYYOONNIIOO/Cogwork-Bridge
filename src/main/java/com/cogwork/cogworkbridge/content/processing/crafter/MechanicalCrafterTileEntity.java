package com.cogwork.cogworkbridge.content.processing.crafter;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MechanicalCrafterTileEntity extends KineticTileEntity {
    public static final String POINTING_NBT = "CrafterPointing";
    private MechanicalCrafterDirection pointing = MechanicalCrafterDirection.DOWN;
    private ItemStack input = ItemStack.EMPTY;
    private final List<PausedIngredient> pausedIngredients = new ArrayList<>();
    private float craftProgress;
    private float previousCraftProgress;
    private float craftDuration;
    private int craftCellX;
    private int craftCellY;
    private int craftGridWidth = 1;
    private int craftGridHeight = 1;
    private int craftStartStage;
    private int craftExportStages;
    private int craftNetworkSize = 1;
    private boolean craftAnimationPrimary;
    private ItemStack craftPreview = ItemStack.EMPTY;
    private boolean redstonePowered;
    private boolean redstoneStartRequested;
    private boolean forcedCraft;
    private boolean packageCraftPending;
    private ItemStack tutorialResult = ItemStack.EMPTY;
    private final java.util.Set<BlockPos> tutorialInputGroup = new java.util.LinkedHashSet<>();

    /** Scene-only input grouping. Stored with the virtual tile so keyframe restores remain deterministic. */
    public net.minecraftforge.items.IItemHandler tutorialInventory() {
        java.util.List<MechanicalCrafterTileEntity> inputs = new java.util.ArrayList<>();
        java.util.Set<BlockPos> group = new java.util.LinkedHashSet<>(tutorialInputGroup); group.add(pos);
        for (BlockPos at : group) if (world.getTileEntity(at) instanceof MechanicalCrafterTileEntity crafter) inputs.add(crafter);
        return new net.minecraftforge.items.IItemHandlerModifiable() {
            @Override public int getSlots() { return inputs.size(); }
            @Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < inputs.size() ? inputs.get(slot).input.copy() : ItemStack.EMPTY; }
            @Override public void setStackInSlot(int slot, ItemStack stack) { if (slot >= 0 && slot < inputs.size()) inputs.get(slot).input = stack.copy(); }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                ItemStack remaining = stack.copy();
                for (MechanicalCrafterTileEntity crafter : inputs) if (crafter.input.isEmpty() && crafter.craftDuration <= 0 && !remaining.isEmpty()) {
                    if (!simulate) { crafter.input = remaining.copy(); crafter.input.setCount(1); }
                    remaining.shrink(1);
                }
                return remaining;
            }
            @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
                ItemStack result = getStackInSlot(slot);
                if (!simulate && !result.isEmpty()) inputs.get(slot).input = ItemStack.EMPTY;
                return result;
            }
        };
    }
    public void linkTutorialInputs(MechanicalCrafterTileEntity other) {
        if (world == null || !world.isRemote || other.world != world) return;
        java.util.Set<BlockPos> group = new java.util.LinkedHashSet<>(tutorialInputGroup);
        group.add(pos); group.add(other.pos); group.addAll(other.tutorialInputGroup);
        for (BlockPos at : group) if (world.getTileEntity(at) instanceof MechanicalCrafterTileEntity crafter) {
            crafter.tutorialInputGroup.clear(); crafter.tutorialInputGroup.addAll(group);
        }
    }
    public void setTutorialResult(ItemStack result) { if (world != null && world.isRemote) tutorialResult = result.copy(); }
    public void startTutorialRecipe() {
        if (world != null && world.isRemote && getTileData().getBoolean("TutorialCrafter")) redstoneStartRequested = true;
    }
    public void clearTutorialRecipe() {
        if (world == null || !world.isRemote || !getTileData().getBoolean("TutorialCrafter")) return;
        List<MechanicalCrafterTileEntity> crafters = connectedCrafters();
        for (MechanicalCrafterTileEntity crafter : crafters) {
            if (crafter.input.getItem() != AllItems.CRAFTER_SLOT_COVER) crafter.input = ItemStack.EMPTY;
            crafter.redstoneStartRequested = false;
        }
        clearCraftAnimation(crafters);
    }
    private void beginTutorialRecipe(List<MechanicalCrafterTileEntity> crafters, boolean allowEmpty) {
        MechanicalCrafterTileEntity outlet = findOutlet(crafters);
        if (outlet == null) return;
        Grid grid = makeGrid(crafters, allowEmpty);
        AnimationLayout layout = animationLayout(crafters, outlet);
        if (grid == null || layout == null) return;
        ItemStack result = tutorialResult;
        if (result.isEmpty()) for (MechanicalCrafterTileEntity crafter : crafters) if (!crafter.tutorialResult.isEmpty()) { result = crafter.tutorialResult; break; }
        if (result.isEmpty()) { CraftPlan plan = findPlan(grid); if (plan != null && !plan.results.isEmpty()) result = plan.results.get(0); }
        if (result.isEmpty()) return;
        craftProgress = previousCraftProgress = 0;
        updateCraftAnimation(crafters, outlet, grid, layout, (layout.exportStages + 2) * (1000F / 64F),
                java.util.List.of(result));
    }
    /** Ponder has a client world, so its crafters must perform the virtual recipe themselves. */
    private void tickTutorialRecipe() {
        boolean powered = world.isBlockPowered(pos);
        if (powered && !redstonePowered) redstoneStartRequested = true;
        redstonePowered = powered;
        List<MechanicalCrafterTileEntity> crafters = connectedCrafters();
        if (crafters.isEmpty() || crafters.get(0) != this) return;
        boolean requested = false;
        for (MechanicalCrafterTileEntity crafter : crafters) {
            requested |= crafter.redstoneStartRequested;
            crafter.redstoneStartRequested = false;
            crafter.previousCraftProgress = crafter.craftProgress;
        }
        float speed = Math.abs(workingSpeed());
        if (speed == 0) return;
        if (craftDuration <= 0) {
            beginTutorialRecipe(crafters, requested);
            return;
        }
        craftProgress = Math.min(craftDuration, craftProgress + animationSpeed(speed) / 64F);
        for (MechanicalCrafterTileEntity crafter : crafters) crafter.craftProgress = craftProgress;
        if (craftProgress < craftDuration) return;
        MechanicalCrafterTileEntity outlet = findOutlet(crafters);
        if (outlet == null) return;
        ItemStack result = outlet.craftPreview;
        TileEntity target = world.getTileEntity(outlet.pos.offset(outlet.outputFacing()));
        IItemHandler receiver = target == null ? null : target.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, outlet.outputFacing().getOpposite());
        if (!result.isEmpty() && receiver != null) {
            if (!ItemHandlerHelper.insertItemStacked(receiver, result.copy(), true).isEmpty()) return;
            ItemHandlerHelper.insertItemStacked(receiver, result.copy(), false);
        } else if (!result.isEmpty() && !ejectOutput(outlet, result)) return;
        for (MechanicalCrafterTileEntity crafter : crafters)
            if (crafter.input.getItem() != AllItems.CRAFTER_SLOT_COVER) crafter.input.shrink(1);
        clearCraftAnimation(crafters);
    }
    private final ItemStackHandler output = new ItemStackHandler(9) {
        @Override protected void onContentsChanged(int slot) { contentsChanged(); }
    };
    private final IItemHandler inputPort = new IItemHandler() {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? input.copy() : ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !input.isEmpty()
                    || !pausedIngredients.isEmpty() || craftDuration > 0.0F) return stack;
            ItemStack remainder = stack.copy();
            remainder.shrink(1);
            if (!simulate) insertOne(stack);
            return remainder;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : 0; }
    };

    @Override public float getStressImpact() { return 4.0F; }
    @Override protected boolean isGear() { return true; }

    public ItemStack getInput() { return input; }
    public MechanicalCrafterDirection getPointing() { return pointing; }
    public void setPointing(MechanicalCrafterDirection direction) {
        if (direction == null || direction == pointing) return;
        pointing = direction;
        contentsChanged();
    }
    public boolean hasInput() { return !input.isEmpty() || !pausedIngredients.isEmpty(); }
    public List<PausedIngredient> getPausedIngredients() { return java.util.Collections.unmodifiableList(pausedIngredients); }
    /** Recipe position relative to the crafter currently holding the stack. */
    public record PausedIngredient(BlockPos originOffset, ItemStack stack, Vec3d renderOffset) { }
    public boolean hasCraftAnimation() { return craftDuration > 0.0F && craftProgress > 0.0F; }
    public boolean isCraftAnimationPrimary() { return craftAnimationPrimary; }
    public ItemStack getCraftPreview() { return craftPreview; }
    public int getCraftExportStages() { return craftExportStages; }

    public float getCraftAnimationProgress(float partialTicks) {
        if (!hasCraftAnimation()) return 0.0F;
        float progress = previousCraftProgress + (craftProgress - previousCraftProgress) * partialTicks;
        return MathHelper.clamp(progress / craftDuration, 0.0F, 1.0F);
    }

    public float getCraftAnimationStage(float partialTicks) {
        return getCraftAnimationProgress(partialTicks) * (craftExportStages + 2);
    }

    public Vec3d getCraftItemOffset(float stage) {
        if (world == null) return Vec3d.ZERO;
        Vec3d origin = new Vec3d(pos);
        MechanicalCrafterTileEntity current = this;
        for (int hop = 0; hop < craftNetworkSize && !current.craftAnimationPrimary; hop++) {
            EnumFacing direction = current.outputFacing();
            TileEntity next = world.getTileEntity(current.pos.offset(direction));
            if (!(next instanceof MechanicalCrafterTileEntity target)) break;
            float fraction = MathHelper.clamp(stage - current.craftStartStage, 0.0F, 1.0F);
            Vec3d currentPos = new Vec3d(current.pos);
            Vec3d grouped = currentPos.add(origin.subtract(currentPos).scale(0.25D));
            if (fraction < 1.0F) {
                return grouped.add(new Vec3d(direction.getDirectionVec()).scale(fraction * 0.75D))
                        .subtract(origin);
            }
            current = target;
        }

        Vec3d outlet = new Vec3d(current.pos);
        Vec3d relative = origin.subtract(outlet);
        if (stage <= craftExportStages) {
            return outlet.add(relative.scale(0.25D)).subtract(origin);
        }
        float progress = MathHelper.clamp(stage - craftExportStages, 0.0F, 1.0F);
        float early = MathHelper.clamp(progress * 2.0F, 0.0F, 1.0F);
        float late = MathHelper.clamp(progress * 2.0F - 1.0F, 0.0F, 1.0F);
        float spacing = 0.25F + (1.0F - 4.0F * (progress - 0.5F) * (progress - 0.5F)) * 0.125F;
        EnumFacing right = MechanicalCrafterDirection.LEFT.relativeTo(facing());
        EnumFacing up = MechanicalCrafterDirection.UP.relativeTo(facing());
        float centerRight = (craftGridWidth - 1) * 0.5F - craftCellX;
        float centerUp = craftCellY - (craftGridHeight - 1) * 0.5F;
        Vec3d center = origin.add(right.getXOffset() * centerRight + up.getXOffset() * centerUp,
                right.getYOffset() * centerRight + up.getYOffset() * centerUp,
                right.getZOffset() * centerRight + up.getZOffset() * centerUp).subtract(outlet);
        return outlet.add(relative.scale(spacing).subtract(center.scale(0.25D * early))
                .scale(1.0F - late)).subtract(origin);
    }

    public boolean insertOne(ItemStack stack) {
        if (stack.isEmpty() || !input.isEmpty() || !pausedIngredients.isEmpty() || craftDuration > 0.0F) return false;
        input = stack.copy();
        input.setCount(1);
        contentsChanged();
        return true;
    }

    /** Insert a package recipe into the connected crafters as one operation. */
    public boolean acceptRecipePackage(List<ItemStack> pattern, net.minecraftforge.items.ItemStackHandler contents, boolean simulate) {
        if (world == null || pattern.size() != 9 || contents.getSlots() != 9) return false;
        List<MechanicalCrafterTileEntity> connected = connectedCrafters();
        if (connected.isEmpty()) return false;
        EnumFacing right = MechanicalCrafterDirection.LEFT.relativeTo(facing());
        EnumFacing up = MechanicalCrafterDirection.UP.relativeTo(facing());
        int minX = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (MechanicalCrafterTileEntity crafter : connected) {
            BlockPos delta = crafter.pos.subtract(pos);
            minX = Math.min(minX, dot(delta, right)); maxY = Math.max(maxY, dot(delta, up));
        }
        Map<Integer, MechanicalCrafterTileEntity> slots = new HashMap<>();
        for (MechanicalCrafterTileEntity crafter : connected) {
            BlockPos delta = crafter.pos.subtract(pos);
            int x = dot(delta, right) - minX, y = maxY - dot(delta, up);
            if (x >= 0 && x < 3 && y >= 0 && y < 3) slots.put(y * 3 + x, crafter);
        }
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < contents.getSlots(); i++) available.add(contents.getStackInSlot(i).copy());
        Map<MechanicalCrafterTileEntity, ItemStack> planned = new LinkedHashMap<>();
        for (int i = 0; i < pattern.size(); i++) {
            ItemStack required = pattern.get(i);
            if (required.isEmpty()) continue;
            MechanicalCrafterTileEntity target = slots.get(i);
            if (target == null || target.hasInput() || target.craftDuration > 0.0F) return false;
            ItemStack found = ItemStack.EMPTY;
            for (ItemStack stack : available)
                if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, required) && ItemStack.areItemStackTagsEqual(stack, required)) { found = stack; break; }
            if (found.isEmpty()) return false;
            ItemStack one = found.copy(); one.setCount(1); found.shrink(1); planned.put(target, one);
        }
        if (planned.isEmpty()) return false;
        for (ItemStack remaining : available) if (!remaining.isEmpty()) return false;
        if (!simulate) {
            for (Map.Entry<MechanicalCrafterTileEntity, ItemStack> entry : planned.entrySet()) entry.getKey().insertOne(entry.getValue());
            MechanicalCrafterTileEntity controller = connected.get(0);
            controller.packageCraftPending = true;
            controller.contentsChanged();
        }
        return true;
    }

    public ItemStack takeForPlayer() {
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack result = output.extractItem(slot, 64, false);
            if (!result.isEmpty()) return result;
        }
        if (input.isEmpty() && !pausedIngredients.isEmpty()) {
            clearPackageCraftPending();
            ItemStack result = pausedIngredients.remove(0).stack();
            contentsChanged();
            return result;
        }
        if (input.isEmpty()) return ItemStack.EMPTY;
        clearPackageCraftPending();
        ItemStack result = input;
        input = ItemStack.EMPTY;
        contentsChanged();
        return result;
    }

    private void clearPackageCraftPending() {
        if (world == null || world.isRemote) return;
        for (MechanicalCrafterTileEntity crafter : connectedCrafters()) {
            if (!crafter.packageCraftPending) continue;
            crafter.packageCraftPending = false; crafter.contentsChanged();
        }
    }

    public void dropItems(World dropWorld, BlockPos dropPos) {
        if (!input.isEmpty()) Block.spawnAsEntity(dropWorld, dropPos, input);
        input = ItemStack.EMPTY;
        for (PausedIngredient ingredient : pausedIngredients) Block.spawnAsEntity(dropWorld, dropPos, ingredient.stack());
        pausedIngredients.clear();
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack stack = output.extractItem(slot, 64, false);
            if (!stack.isEmpty()) Block.spawnAsEntity(dropWorld, dropPos, stack);
        }
    }

    private EnumFacing facing() {
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof MechanicalCrafterBlock)) {
            return EnumFacing.NORTH;
        }
        return world.getBlockState(pos).getValue(MechanicalCrafterBlock.FACING);
    }

    private EnumFacing outputFacing() {
        if (world == null || !(world.getBlockState(pos).getBlock() instanceof MechanicalCrafterBlock)) {
            return EnumFacing.DOWN;
        }
        var state = world.getBlockState(pos);
        return pointing.relativeTo(state.getValue(MechanicalCrafterBlock.FACING));
    }

    private float workingSpeed() {
        float speed = getSpeed();
        if (speed == 0.0F && world instanceof FakeWorld fake
                && fake.getMobileChunk() != null
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
            speed = contraption.getSpeed();
        }
        return speed;
    }

    @Override
    public void update() {
        super.update();
        if (world == null) return;
        if (world.isRemote) {
            if (getTileData().getBoolean("TutorialCrafter")) { tickTutorialRecipe(); return; }
            previousCraftProgress = craftProgress;
            float speed = Math.abs(workingSpeed());
            if (craftDuration > 0.0F && speed != 0.0F) {
                craftProgress = Math.min(craftDuration, craftProgress + animationSpeed(speed) / 64.0F);
            }
            return;
        }
        boolean powered = world.isBlockPowered(pos);
        if (powered && !redstonePowered) redstoneStartRequested = true;
        redstonePowered = powered;
        if (world.getTotalWorldTime() % 2L != 0L) return;
        List<MechanicalCrafterTileEntity> connected = connectedCrafters();
        if (connected.isEmpty() || connected.get(0) != this) return;
        boolean requested = false;
        for (MechanicalCrafterTileEntity crafter : connected) {
            requested |= crafter.redstoneStartRequested;
            crafter.redstoneStartRequested = false;
        }
        float speed = Math.abs(workingSpeed());
        if (speed == 0.0F) {
            pauseAtNearestCrafter(connected);
            return;
        }
        if (!restorePausedIngredients(connected)) {
            resetProgress(connected);
            return;
        }
        MechanicalCrafterTileEntity outlet = findOutlet(connected);
        if (outlet == null || !deliverOutputs(connected, outlet)) {
            resetProgress(connected);
            return;
        }
        Grid grid = makeGrid(connected, forcedCraft || requested || packageCraftPending);
        if (grid == null) {
            resetProgress(connected);
            return;
        }
        CraftPlan plan = findPlan(grid);
        if (plan == null || !hasOutputCapacity(connected, plan.results)) {
            resetProgress(connected);
            return;
        }

        AnimationLayout layout = animationLayout(connected, outlet);
        if (layout == null) {
            resetProgress(connected);
            return;
        }

        if (requested) forcedCraft = true;
        boolean animationStarted = connected.stream().anyMatch(crafter -> crafter.craftDuration <= 0.0F);
        craftProgress += animationSpeed(speed) / 32.0F;
        float duration = (layout.exportStages + 2) * (1000.0F / 64.0F);
        updateCraftAnimation(connected, outlet, grid, layout, duration, plan.results);
        if (craftProgress >= duration) {
            if (applyPlan(connected, plan, forcedCraft || packageCraftPending)) {
                craftProgress = 0.0F;
                forcedCraft = false;
                packageCraftPending = false;
                clearCraftAnimation(connected);
                deliverOutputs(connected, outlet);
            } else {
                craftProgress = duration - 0.01F;
                updateCraftAnimation(connected, outlet, grid, layout, duration, plan.results);
            }
            syncCrafters(connected);
        } else if (animationStarted || world.getTotalWorldTime() % 10L == 0L) {
            syncCrafters(connected);
        }
    }

    private void pauseAtNearestCrafter(List<MechanicalCrafterTileEntity> connected) {
        if (craftDuration <= 0.0F || craftProgress <= 0.0F) return;
        float stages = craftExportStages + 2.0F;
        float stage = craftProgress / craftDuration * stages;
        boolean changed = false;
        for (MechanicalCrafterTileEntity crafter : connected) {
            crafter.previousCraftProgress = crafter.craftProgress;
        }
        for (MechanicalCrafterTileEntity source : connected) {
            if (source.input.isEmpty() || source.input.getItem() == AllItems.CRAFTER_SLOT_COVER) continue;
            Vec3d itemPosition = new Vec3d(source.pos).add(source.getCraftItemOffset(stage));
            MechanicalCrafterTileEntity nearest = source;
            double distance = itemPosition.squareDistanceTo(new Vec3d(source.pos));
            for (MechanicalCrafterTileEntity candidate : connected) {
                double nextDistance = itemPosition.squareDistanceTo(new Vec3d(candidate.pos));
                if (nextDistance < distance) { nearest = candidate; distance = nextDistance; }
            }
            nearest.pausedIngredients.add(new PausedIngredient(source.pos.subtract(nearest.pos), source.input,
                    itemPosition.subtract(new Vec3d(nearest.pos))));
            source.input = ItemStack.EMPTY;
            changed = true;
        }
        if (changed) syncCrafters(connected);
    }

    /** Restore recipe coordinates once motion resumes; the physical owner while paused is never the source slot. */
    private boolean restorePausedIngredients(List<MechanicalCrafterTileEntity> connected) {
        boolean changed = false;
        Set<BlockPos> claimed = new HashSet<>();
        for (MechanicalCrafterTileEntity holder : connected) {
            for (PausedIngredient ingredient : holder.pausedIngredients) {
                BlockPos originPos = holder.pos.add(ingredient.originOffset());
                TileEntity tile = world.getTileEntity(originPos);
                if (!(tile instanceof MechanicalCrafterTileEntity origin)
                        || !connected.contains(origin) || !origin.input.isEmpty()
                        || !claimed.add(originPos)) return false;
            }
        }
        for (MechanicalCrafterTileEntity holder : connected) {
            for (java.util.Iterator<PausedIngredient> it = holder.pausedIngredients.iterator(); it.hasNext();) {
                PausedIngredient ingredient = it.next();
                MechanicalCrafterTileEntity origin = (MechanicalCrafterTileEntity)
                        world.getTileEntity(holder.pos.add(ingredient.originOffset()));
                origin.input = ingredient.stack();
                it.remove();
                changed = true;
            }
        }
        if (changed) syncCrafters(connected);
        return true;
    }

    private void resetProgress(List<MechanicalCrafterTileEntity> connected) {
        boolean changed = craftProgress != 0.0F || forcedCraft;
        for (MechanicalCrafterTileEntity crafter : connected) {
            changed |= crafter.craftDuration != 0.0F || crafter.craftProgress != 0.0F;
        }
        if (!changed) return;
        craftProgress = 0.0F;
        forcedCraft = false;
        clearCraftAnimation(connected);
        syncCrafters(connected);
    }

    private static int animationSpeed(float speed) {
        return MathHelper.clamp((int) speed, 4, 250);
    }

    @Nullable
    private AnimationLayout animationLayout(List<MechanicalCrafterTileEntity> connected,
                                            MechanicalCrafterTileEntity outlet) {
        Map<BlockPos, MechanicalCrafterTileEntity> positions = new HashMap<>();
        Map<MechanicalCrafterTileEntity, Integer> incoming = new HashMap<>();
        Map<MechanicalCrafterTileEntity, Integer> stages = new HashMap<>();
        Map<MechanicalCrafterTileEntity, Boolean> hasItems = new HashMap<>();
        for (MechanicalCrafterTileEntity crafter : connected) {
            positions.put(crafter.pos, crafter);
            incoming.put(crafter, 0);
            stages.put(crafter, 0);
            hasItems.put(crafter, !crafter.input.isEmpty()
                    && crafter.input.getItem() != AllItems.CRAFTER_SLOT_COVER);
        }
        for (MechanicalCrafterTileEntity crafter : connected) {
            MechanicalCrafterTileEntity target = positions.get(crafter.pos.offset(crafter.outputFacing()));
            if (target != null) incoming.put(target, incoming.get(target) + 1);
        }
        ArrayDeque<MechanicalCrafterTileEntity> ready = new ArrayDeque<>();
        for (MechanicalCrafterTileEntity crafter : connected) {
            if (incoming.get(crafter) == 0) ready.addLast(crafter);
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            MechanicalCrafterTileEntity crafter = ready.removeFirst();
            visited++;
            MechanicalCrafterTileEntity target = positions.get(crafter.pos.offset(crafter.outputFacing()));
            if (target == null) continue;
            boolean occupied = hasItems.get(crafter);
            int finish = stages.get(crafter) + (occupied ? 1 : 0);
            stages.put(target, Math.max(stages.get(target), finish));
            hasItems.put(target, hasItems.get(target) || occupied);
            int remaining = incoming.get(target) - 1;
            incoming.put(target, remaining);
            if (remaining == 0) ready.addLast(target);
        }
        return visited == connected.size() ? new AnimationLayout(stages, stages.get(outlet)) : null;
    }

    private void updateCraftAnimation(List<MechanicalCrafterTileEntity> connected,
                                      MechanicalCrafterTileEntity outlet, Grid grid, AnimationLayout layout,
                                      float duration, List<ItemStack> results) {
        int width = Math.max(1, grid.inventory.getWidth());
        int height = Math.max(1, grid.inventory.getHeight());
        Map<MechanicalCrafterTileEntity, Integer> slots = new HashMap<>();
        for (Map.Entry<Integer, MechanicalCrafterTileEntity> entry : grid.cells.entrySet()) {
            slots.put(entry.getValue(), entry.getKey());
        }
        for (MechanicalCrafterTileEntity crafter : connected) {
            int slot = slots.getOrDefault(crafter, 0);
            crafter.craftProgress = craftProgress;
            crafter.craftDuration = duration;
            crafter.craftCellX = slot % width;
            crafter.craftCellY = slot / width;
            crafter.craftGridWidth = width;
            crafter.craftGridHeight = height;
            crafter.craftStartStage = layout.stages.get(crafter);
            crafter.craftExportStages = layout.exportStages;
            crafter.craftNetworkSize = connected.size();
            crafter.craftAnimationPrimary = crafter == outlet;
            crafter.craftPreview = crafter.craftAnimationPrimary && !results.isEmpty()
                    ? results.get(0).copy() : ItemStack.EMPTY;
        }
    }

    private void clearCraftAnimation(List<MechanicalCrafterTileEntity> connected) {
        for (MechanicalCrafterTileEntity crafter : connected) {
            crafter.clearCraftAnimation();
        }
    }

    private void clearCraftAnimation() {
        craftProgress = 0.0F;
        previousCraftProgress = 0.0F;
        clearCraftAnimationDetails();
    }

    private void clearCraftAnimationDetails() {
        craftDuration = 0.0F;
        craftCellX = 0;
        craftCellY = 0;
        craftGridWidth = 1;
        craftGridHeight = 1;
        craftStartStage = 0;
        craftExportStages = 0;
        craftNetworkSize = 1;
        craftAnimationPrimary = false;
        craftPreview = ItemStack.EMPTY;
    }

    private void syncCrafters(List<MechanicalCrafterTileEntity> connected) {
        for (MechanicalCrafterTileEntity crafter : connected) {
            crafter.markDirty();
            crafter.sync();
        }
    }

    private List<MechanicalCrafterTileEntity> connectedCrafters() {
        Map<BlockPos, MechanicalCrafterTileEntity> found = new LinkedHashMap<>();
        ArrayDeque<MechanicalCrafterTileEntity> queue = new ArrayDeque<>();
        found.put(pos.toImmutable(), this);
        queue.add(this);
        EnumFacing rootFacing = facing();
        while (!queue.isEmpty()) {
            MechanicalCrafterTileEntity current = queue.removeFirst();
            for (EnumFacing side : EnumFacing.values()) {
                if (side.getAxis() == rootFacing.getAxis()) continue;
                BlockPos neighborPos = current.pos.offset(side);
                if (!world.isBlockLoaded(neighborPos) || found.containsKey(neighborPos)) continue;
                TileEntity tile = world.getTileEntity(neighborPos);
                if (!(tile instanceof MechanicalCrafterTileEntity other) || other.facing() != rootFacing) continue;
                if (current.outputFacing() != side && other.outputFacing() != side.getOpposite()) continue;
                found.put(neighborPos.toImmutable(), other);
                queue.addLast(other);
            }
        }
        List<MechanicalCrafterTileEntity> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparing(MechanicalCrafterTileEntity::getPos));
        return result;
    }

    @Nullable
    private MechanicalCrafterTileEntity findOutlet(List<MechanicalCrafterTileEntity> crafters) {
        Set<BlockPos> positions = new HashSet<>();
        for (MechanicalCrafterTileEntity crafter : crafters) positions.add(crafter.pos);
        MechanicalCrafterTileEntity outlet = null;
        for (MechanicalCrafterTileEntity crafter : crafters) {
            if (positions.contains(crafter.pos.offset(crafter.outputFacing()))) continue;
            if (outlet != null) return null;
            outlet = crafter;
        }
        return outlet;
    }

    private boolean deliverOutputs(List<MechanicalCrafterTileEntity> crafters,
                                   MechanicalCrafterTileEntity outlet) {
        boolean pending = false;
        for (MechanicalCrafterTileEntity crafter : crafters) {
            for (int slot = 0; slot < crafter.output.getSlots(); slot++) {
                if (!crafter.output.getStackInSlot(slot).isEmpty()) pending = true;
            }
        }
        if (!pending) return true;

        EnumFacing direction = outlet.outputFacing();
        BlockPos targetPos = outlet.pos.offset(direction);
        if (!world.isBlockLoaded(targetPos)) return false;
        TileEntity target = world.getTileEntity(targetPos);
        IItemHandler receiver = target == null ? null
                : target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite());
        boolean emptied = true;
        for (MechanicalCrafterTileEntity crafter : crafters) {
            for (int slot = 0; slot < crafter.output.getSlots(); slot++) {
                ItemStack stack = crafter.output.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                if (receiver == null) {
                    if (ejectOutput(outlet, stack)) crafter.output.extractItem(slot, stack.getCount(), false);
                } else {
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(receiver, stack.copy(), false);
                    int inserted = stack.getCount() - remainder.getCount();
                    if (inserted > 0) crafter.output.extractItem(slot, inserted, false);
                }
                if (!crafter.output.getStackInSlot(slot).isEmpty()) emptied = false;
            }
        }
        return emptied;
    }

    private boolean ejectOutput(MechanicalCrafterTileEntity outlet, ItemStack stack) {
        EnumFacing front = outlet.facing();
        Vec3d origin = new Vec3d(outlet.pos).add(0.5D, 0.5D, 0.5D)
                .add(front.getXOffset() * 0.75D, front.getYOffset() * 0.75D, front.getZOffset() * 0.75D);
        World dropWorld = world;
        if (world instanceof FakeWorld fake) {
            dropWorld = fake.getParentWorld();
            if (fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
                BlockPos anchor = contraption.getAnchor();
                origin = contraption.localToWorld(origin.add(anchor.getX(), anchor.getY(), anchor.getZ()));
            } else {
                return false;
            }
        }
        if (!dropWorld.isBlockLoaded(new BlockPos(origin))) return false;
        EntityItem item = new EntityItem(dropWorld, origin.x, origin.y, origin.z, stack.copy());
        item.setDefaultPickupDelay();
        return dropWorld.spawnEntity(item);
    }

    @Nullable
    private Grid makeGrid(List<MechanicalCrafterTileEntity> crafters, boolean allowEmpty) {
        EnumFacing layoutRight = MechanicalCrafterDirection.LEFT.relativeTo(facing());
        EnumFacing layoutUp = MechanicalCrafterDirection.UP.relativeTo(facing());
        Map<Integer, MechanicalCrafterTileEntity> cells = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (MechanicalCrafterTileEntity crafter : crafters) {
            if (crafter.input.isEmpty()) {
                if (!allowEmpty) return null;
                continue;
            }
            if (crafter.input.getItem() == AllItems.CRAFTER_SLOT_COVER) continue;
            BlockPos delta = crafter.pos.subtract(pos);
            int x = dot(delta, layoutRight);
            int y = dot(delta, layoutUp);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (minX == Integer.MAX_VALUE) return null;
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE) return null;

        Container container = new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        };
        InventoryCrafting inventory = new InventoryCrafting(container, width, height);
        for (MechanicalCrafterTileEntity crafter : crafters) {
            if (crafter.input.isEmpty() || crafter.input.getItem() == AllItems.CRAFTER_SLOT_COVER) continue;
            BlockPos delta = crafter.pos.subtract(pos);
            int x = dot(delta, layoutRight) - minX;
            int y = maxY - dot(delta, layoutUp);
            int slot = y * width + x;
            if (slot < 0 || slot >= width * height || cells.put(slot, crafter) != null) return null;
            inventory.setInventorySlotContents(slot, crafter.input.copy());
        }
        return new Grid(inventory, cells);
    }

    private static int dot(BlockPos position, EnumFacing direction) {
        return position.getX() * direction.getXOffset() + position.getY() * direction.getYOffset()
                + position.getZ() * direction.getZOffset();
    }

    @Nullable
    private CraftPlan findPlan(Grid grid) {
        for (var mechanical : com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.mechanicalCrafting()) {
            if (mechanical.matches(grid.inventory)) return new CraftPlan(java.util.Collections.singletonList(mechanical.result().copy()));
        }
        IRecipe recipe = CraftingManager.findMatchingRecipe(grid.inventory, world);
        if (recipe == null) return null;
        ItemStack result = recipe.getCraftingResult(grid.inventory);
        if (result.isEmpty()) return null;
        List<ItemStack> results = new ArrayList<>();
        results.add(result.copy());
        for (ItemStack remainder : recipe.getRemainingItems(grid.inventory)) {
            if (!remainder.isEmpty()) results.add(remainder.copy());
        }
        return new CraftPlan(results);
    }

    private boolean hasOutputCapacity(List<MechanicalCrafterTileEntity> crafters, List<ItemStack> results) {
        List<ItemStackHandler> simulated = new ArrayList<>();
        for (MechanicalCrafterTileEntity crafter : crafters) {
            ItemStackHandler copy = new ItemStackHandler(9);
            copy.deserializeNBT(crafter.output.serializeNBT());
            simulated.add(copy);
        }
        for (ItemStack result : results) {
            ItemStack remainder = result.copy();
            for (ItemStackHandler inventory : simulated) {
                remainder = ItemHandlerHelper.insertItemStacked(inventory, remainder, false);
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private boolean applyPlan(List<MechanicalCrafterTileEntity> crafters, CraftPlan plan, boolean allowEmpty) {
        Grid current = makeGrid(crafters, allowEmpty);
        if (current == null) return false;
        CraftPlan currentPlan = findPlan(current);
        if (currentPlan == null || !sameOutputs(plan.results, currentPlan.results)
                || !hasOutputCapacity(crafters, currentPlan.results)) return false;

        for (MechanicalCrafterTileEntity crafter : crafters) {
            if (!crafter.input.isEmpty() && crafter.input.getItem() != AllItems.CRAFTER_SLOT_COVER) {
                crafter.input = ItemStack.EMPTY;
                crafter.markDirty();
            }
        }
        for (ItemStack result : currentPlan.results) {
            ItemStack remainder = result.copy();
            for (MechanicalCrafterTileEntity crafter : crafters) {
                remainder = ItemHandlerHelper.insertItemStacked(crafter.output, remainder, false);
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private static boolean sameOutputs(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            if (!ItemStack.areItemStacksEqual(first.get(i), second.get(i))) return false;
        }
        return true;
    }

    private record AnimationLayout(Map<MechanicalCrafterTileEntity, Integer> stages, int exportStages) { }
    private record Grid(InventoryCrafting inventory, Map<Integer, MechanicalCrafterTileEntity> cells) { }
    private record CraftPlan(List<ItemStack> results) { }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing side) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inputPort);
        }
        return super.getCapability(capability, side);
    }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(hasCraftAnimation() ? craftNetworkSize : 0.25D);
    }

    private void contentsChanged() {
        markDirty();
        if (world != null && !world.isRemote) sync();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!tutorialResult.isEmpty()) tag.setTag("TutorialResult", tutorialResult.writeToNBT(new NBTTagCompound()));
        if (getTileData().getBoolean("TutorialCrafter")) {
            tag.setBoolean("TutorialPowered", redstonePowered);
            tag.setBoolean("TutorialStartRequested", redstoneStartRequested);
        }
        if (!tutorialInputGroup.isEmpty()) {
            NBTTagList group = new NBTTagList();
            for (BlockPos member : tutorialInputGroup) group.appendTag(new net.minecraft.nbt.NBTTagLong(member.toLong()));
            tag.setTag("TutorialInputGroup", group);
        }
        tag.setByte(POINTING_NBT, (byte) pointing.ordinal());
        if (!input.isEmpty()) tag.setTag("CrafterInput", input.writeToNBT(new NBTTagCompound()));
        writePausedIngredients(tag);
        tag.setTag("CrafterOutput", output.serializeNBT());
        tag.setFloat("CrafterProgress", craftProgress);
        tag.setBoolean("CrafterForced", forcedCraft);
        tag.setBoolean("PackageCraftPending", packageCraftPending);
        writeCraftAnimation(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        tutorialResult = tag.hasKey("TutorialResult", 10) ? new ItemStack(tag.getCompoundTag("TutorialResult")) : ItemStack.EMPTY;
        if (getTileData().getBoolean("TutorialCrafter")) {
            redstonePowered = tag.getBoolean("TutorialPowered");
            redstoneStartRequested = tag.getBoolean("TutorialStartRequested");
        }
        tutorialInputGroup.clear();
        NBTTagList group = tag.getTagList("TutorialInputGroup", 4);
        for (int i = 0; i < group.tagCount(); i++) tutorialInputGroup.add(BlockPos.fromLong(((net.minecraft.nbt.NBTTagLong) group.get(i)).getLong()));
        pointing = readPointing(tag);
        input = tag.hasKey("CrafterInput", 10) ? new ItemStack(tag.getCompoundTag("CrafterInput")) : ItemStack.EMPTY;
        readPausedIngredients(tag);
        if (tag.hasKey("CrafterOutput", 10)) output.deserializeNBT(tag.getCompoundTag("CrafterOutput"));
        float progress = tag.getFloat("CrafterProgress");
        craftProgress = Float.isFinite(progress) ? Math.max(0.0F, progress) : 0.0F;
        previousCraftProgress = craftProgress;
        forcedCraft = tag.getBoolean("CrafterForced");
        packageCraftPending = tag.getBoolean("PackageCraftPending");
        readCraftAnimation(tag);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setByte(POINTING_NBT, (byte) pointing.ordinal());
        if (!input.isEmpty()) tag.setTag("CrafterInput", input.writeToNBT(new NBTTagCompound()));
        writePausedIngredients(tag);
        tag.setTag("CrafterOutput", output.serializeNBT());
        tag.setFloat("CrafterProgress", craftProgress);
        tag.setBoolean("CrafterForced", forcedCraft);
        tag.setBoolean("PackageCraftPending", packageCraftPending);
        writeCraftAnimation(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        float oldProgress = craftProgress;
        float oldDuration = craftDuration;
        super.handleUpdateTag(tag);
        pointing = readPointing(tag);
        input = tag.hasKey("CrafterInput", 10) ? new ItemStack(tag.getCompoundTag("CrafterInput")) : ItemStack.EMPTY;
        readPausedIngredients(tag);
        if (tag.hasKey("CrafterOutput", 10)) output.deserializeNBT(tag.getCompoundTag("CrafterOutput"));
        float progress = tag.getFloat("CrafterProgress");
        craftProgress = Float.isFinite(progress) ? Math.max(0.0F, progress) : 0.0F;
        forcedCraft = tag.getBoolean("CrafterForced");
        packageCraftPending = tag.getBoolean("PackageCraftPending");
        readCraftAnimation(tag);
        if (oldDuration > 0.0F && oldDuration == craftDuration && craftProgress > 0.0F
                && getSpeed() != 0.0F && pausedIngredients.isEmpty()) {
            craftProgress = Math.max(oldProgress, craftProgress);
            previousCraftProgress = oldProgress;
        } else {
            previousCraftProgress = craftProgress;
        }
    }

    public static MechanicalCrafterDirection readPointing(NBTTagCompound tag) {
        int ordinal = tag.hasKey(POINTING_NBT, 99) ? tag.getByte(POINTING_NBT) : -1;
        MechanicalCrafterDirection[] directions = MechanicalCrafterDirection.values();
        return ordinal >= 0 && ordinal < directions.length ? directions[ordinal] : MechanicalCrafterDirection.DOWN;
    }

    private void writePausedIngredients(NBTTagCompound tag) {
        NBTTagList entries = new NBTTagList();
        for (PausedIngredient ingredient : pausedIngredients) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("OriginOffset", ingredient.originOffset().toLong());
            entry.setTag("Stack", ingredient.stack().writeToNBT(new NBTTagCompound()));
            entry.setDouble("RenderX", ingredient.renderOffset().x);
            entry.setDouble("RenderY", ingredient.renderOffset().y);
            entry.setDouble("RenderZ", ingredient.renderOffset().z);
            entries.appendTag(entry);
        }
        tag.setTag("CrafterPausedIngredients", entries);
    }

    private void readPausedIngredients(NBTTagCompound tag) {
        pausedIngredients.clear();
        NBTTagList entries = tag.getTagList("CrafterPausedIngredients", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(entry.getCompoundTag("Stack"));
            if (!stack.isEmpty()) {
                BlockPos offset = entry.hasKey("OriginOffset", 4)
                        ? BlockPos.fromLong(entry.getLong("OriginOffset"))
                        : BlockPos.fromLong(entry.getLong("Origin")).subtract(pos);
                Vec3d renderOffset = entry.hasKey("RenderX")
                        ? new Vec3d(entry.getDouble("RenderX"), entry.getDouble("RenderY"), entry.getDouble("RenderZ"))
                        : new Vec3d(offset).scale(0.25D);
                pausedIngredients.add(new PausedIngredient(offset, stack, renderOffset));
            }
        }
    }

    private void writeCraftAnimation(NBTTagCompound tag) {
        tag.setFloat("CrafterDuration", craftDuration);
        tag.setInteger("CrafterCellX", craftCellX);
        tag.setInteger("CrafterCellY", craftCellY);
        tag.setInteger("CrafterGridWidth", craftGridWidth);
        tag.setInteger("CrafterGridHeight", craftGridHeight);
        tag.setInteger("CrafterStartStage", craftStartStage);
        tag.setInteger("CrafterExportStages", craftExportStages);
        tag.setInteger("CrafterNetworkSize", craftNetworkSize);
        tag.setBoolean("CrafterPrimary", craftAnimationPrimary);
        tag.removeTag("CrafterPreview");
        if (!craftPreview.isEmpty()) tag.setTag("CrafterPreview", craftPreview.writeToNBT(new NBTTagCompound()));
    }

    private void readCraftAnimation(NBTTagCompound tag) {
        float duration = tag.getFloat("CrafterDuration");
        craftDuration = Float.isFinite(duration) ? Math.max(0.0F, duration) : 0.0F;
        craftCellX = Math.max(0, tag.getInteger("CrafterCellX"));
        craftCellY = Math.max(0, tag.getInteger("CrafterCellY"));
        craftGridWidth = Math.max(1, tag.getInteger("CrafterGridWidth"));
        craftGridHeight = Math.max(1, tag.getInteger("CrafterGridHeight"));
        craftStartStage = Math.max(0, tag.getInteger("CrafterStartStage"));
        craftExportStages = Math.max(0, tag.getInteger("CrafterExportStages"));
        craftNetworkSize = Math.max(1, tag.getInteger("CrafterNetworkSize"));
        craftAnimationPrimary = tag.getBoolean("CrafterPrimary");
        craftPreview = tag.hasKey("CrafterPreview", 10)
                ? new ItemStack(tag.getCompoundTag("CrafterPreview")) : ItemStack.EMPTY;
    }
}
