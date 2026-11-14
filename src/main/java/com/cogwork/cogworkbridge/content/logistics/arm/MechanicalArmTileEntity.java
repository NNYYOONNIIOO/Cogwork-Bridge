package com.cogwork.cogworkbridge.content.logistics.arm;

import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import java.util.ArrayList;
import java.util.List;

/** Input and output positions are stored relative to the arm, so capture and relocation retain them. */
public final class MechanicalArmTileEntity extends KineticTileEntity {
    private static final int RANGE_SQUARED = 25;
    private static final int SEARCH_INPUT = 0, MOVING_INPUT = 1, SEARCH_OUTPUT = 2, MOVING_OUTPUT = 3;
    private final List<Point> points = new ArrayList<>();
    private ItemStack held = ItemStack.EMPTY;
    private int phase, mode = 1, target = -1, previousTarget = -1, lastInput = -1, lastOutput = -1;
    private float progress, previousProgress;
    private boolean startingAtRest;
    private boolean goggles;
    private boolean dancing;
    private record Point(BlockPos offset, EnumFacing side, boolean output) { }

    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(pos).grow(5); }
    public ItemStack getHeldItem() { return held.copy(); }
    public boolean hasGoggles() { return goggles; }
    public boolean isDancing() { return dancing; }
    public void equipGoggles() { if (!goggles) { goggles = true; changed(); } }
    public ItemStack takeHeld() { ItemStack result = held; held = ItemStack.EMPTY; phase = SEARCH_INPUT; changed(); return result; }
    public BlockPos targetOffset() { return offsetFor(target); }
    public BlockPos previousTargetOffset() { return offsetFor(previousTarget); }
    public EnumFacing targetSide() { return sideFor(target); }
    public EnumFacing previousTargetSide() { return sideFor(previousTarget); }
    public boolean hasTarget() { return target >= 0 && target < points.size(); }
    public boolean startsAtRest() { return startingAtRest; }
    private BlockPos offsetFor(int index) { return index >= 0 && index < points.size() ? points.get(index).offset : BlockPos.ORIGIN; }
    private EnumFacing sideFor(int index) { return index >= 0 && index < points.size() ? points.get(index).side : EnumFacing.DOWN; }
    public float animationProgress(float partial) { return previousProgress + (progress - previousProgress) * partial; }
    public int getSelectionMode() { return mode; }
    public void setSelectionMode(int value) {
        int next = Math.max(0, Math.min(2, value));
        if (next != mode) { mode = next; changed(); }
    }
    public void cycleMode(EntityPlayer player) {
        changeMode(player, 1);
    }
    public void changeMode(EntityPlayer player, int delta) {
        if (Math.abs(delta) != 1) return;
        setSelectionMode(Math.floorMod(mode + delta, 3));
        player.sendStatusMessage(new TextComponentTranslation("cogworkbridge.arm.mode." + mode), true);
    }
    public void setPoints(NBTTagList list, int dimension) {
        points.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.getInteger("Dimension") != dimension) continue;
            BlockPos offset = BlockPos.fromLong(entry.getLong("Pos")).subtract(pos);
            if (offset.distanceSq(BlockPos.ORIGIN) >= RANGE_SQUARED || offset.equals(BlockPos.ORIGIN)) continue;
            points.add(new Point(offset, EnumFacing.byIndex(entry.getInteger("Side")), entry.getInteger("Mode") == 1));
        }
        phase = held.isEmpty() ? SEARCH_INPUT : SEARCH_OUTPUT; target = previousTarget = -1; changed();
    }
    @Override public float getStressImpact() { return 1; }
    @Override public void update() {
        if (world == null) return;
        super.update(); previousProgress = progress;
        float increment = Math.min(0.25F, Math.abs(getSpeed()) / 1024F);
        if (increment <= 0) return;
        if (target < 0 && previousTarget >= 0 && progress < 1) {
            progress = Math.min(1, progress + increment);
            if (progress < 1 || world.isRemote) return;
        }
        if (phase == MOVING_INPUT || phase == MOVING_OUTPUT) {
            progress = Math.min(1, progress + increment);
            if (world.isRemote || progress < 1) return;
            if (phase == MOVING_INPUT) collect(); else deposit();
            return;
        }
        if (world.isRemote || phase == SEARCH_INPUT && world.isBlockPowered(pos)) return;
        if (phase == SEARCH_INPUT && !held.isEmpty()) phase = SEARCH_OUTPUT;
        if (phase == SEARCH_OUTPUT && held.isEmpty()) phase = SEARCH_INPUT;
        int found = findPoint(phase == SEARCH_OUTPUT);
        if (found >= 0) {
            dancing = false;
            startingAtRest = target < 0;
            if (target >= 0) previousTarget = target;
            target = found;
            phase = phase == SEARCH_OUTPUT ? MOVING_OUTPUT : MOVING_INPUT;
            progress = previousProgress = 0;
            changed();
        } else if (phase == SEARCH_INPUT && world.getTotalWorldTime() % 10 == 0) {
            boolean music = false;
            for (Point point : points) if (ArmInteractionHandlers.hasMusic(world, pos.add(point.offset))) { music = true; break; }
            if (music != dancing) { dancing = music; changed(); }
        }
    }
    private IItemHandler handler(Point p) { return ArmInteractionHandlers.at(world, pos.add(p.offset), p.side); }
    private int findPoint(boolean output) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) if (points.get(i).output == output) candidates.add(i);
        if (candidates.isEmpty()) return -1;
        int last = output ? lastOutput : lastInput;
        int lastIndex = candidates.indexOf(last);
        int start = mode == 0 ? 0 : (lastIndex + 1) % candidates.size();
        int scan = mode == 2 ? 1 : candidates.size();
        for (int n = 0; n < scan; n++) {
            int index = candidates.get((start + n) % candidates.size());
            IItemHandler inventory = handler(points.get(index));
            if (inventory == null) continue;
            if (output) {
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, held.copy(), true);
                if (remainder.getCount() < held.getCount()) return index;
            } else for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack candidate = inventory.extractItem(slot, 64, true);
                if (!candidate.isEmpty() && distributable(candidate) > 0) return index;
            }
        }
        return -1;
    }
    private int distributable(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (Point p : points) if (p.output) {
            IItemHandler inventory = handler(p);
            if (inventory != null) remaining = ItemHandlerHelper.insertItemStacked(inventory, remaining, true);
            if (remaining.isEmpty()) break;
        }
        return stack.getCount() - remaining.getCount();
    }
    private void collect() {
        if (target >= 0 && target < points.size()) {
            IItemHandler inventory = handler(points.get(target));
            if (inventory != null) for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack candidate = inventory.extractItem(slot, 64, true);
                if (candidate.isEmpty()) continue;
                int amount = distributable(candidate); if (amount <= 0) continue;
                held = inventory.extractItem(slot, amount, false); break;
            }
            lastInput = target;
        }
        previousTarget = target;
        target = -1;
        startingAtRest = false;
        phase = held.isEmpty() ? SEARCH_INPUT : SEARCH_OUTPUT;
        progress = previousProgress = 0;
        changed();
    }
    private void deposit() {
        if (target >= 0 && target < points.size()) {
            IItemHandler inventory = handler(points.get(target));
            if (inventory != null) held = ItemHandlerHelper.insertItemStacked(inventory, held, false);
            lastOutput = target;
        }
        previousTarget = target;
        target = -1;
        startingAtRest = false;
        phase = held.isEmpty() ? SEARCH_INPUT : SEARCH_OUTPUT;
        progress = previousProgress = 0;
        changed();
    }
    private void changed() { markDirty(); if (world != null && !world.isRemote) sync(); }
    private void save(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Point p : points) { NBTTagCompound e = new NBTTagCompound(); e.setLong("Offset", p.offset.toLong());
            e.setInteger("Side", p.side.getIndex()); e.setBoolean("Output", p.output); list.appendTag(e); }
        tag.setTag("Points", list); if (!held.isEmpty()) tag.setTag("Held", held.writeToNBT(new NBTTagCompound()));
        tag.setInteger("Phase", phase); tag.setInteger("Mode", mode); tag.setInteger("Target", target); tag.setInteger("PreviousTarget", previousTarget);
        tag.setInteger("LastInput", lastInput); tag.setInteger("LastOutput", lastOutput); tag.setFloat("Progress", progress);
        tag.setBoolean("StartingAtRest", startingAtRest);
        tag.setBoolean("Goggles", goggles);
        tag.setBoolean("Dancing", dancing);
    }
    private void load(NBTTagCompound tag) {
        points.clear(); NBTTagList list = tag.getTagList("Points", 10);
        for (int i = 0; i < list.tagCount(); i++) { NBTTagCompound e = list.getCompoundTagAt(i);
            points.add(new Point(BlockPos.fromLong(e.getLong("Offset")), EnumFacing.byIndex(e.getInteger("Side")), e.getBoolean("Output"))); }
        held = tag.hasKey("Held", 10) ? new ItemStack(tag.getCompoundTag("Held")) : ItemStack.EMPTY;
        phase = tag.getInteger("Phase"); mode = tag.hasKey("Mode") ? Math.max(0, Math.min(2, tag.getInteger("Mode"))) : 1; target = tag.hasKey("Target") ? tag.getInteger("Target") : -1;
        previousTarget = tag.hasKey("PreviousTarget") ? tag.getInteger("PreviousTarget") : -1;
        lastInput = tag.hasKey("LastInput") ? tag.getInteger("LastInput") : -1;
        lastOutput = tag.hasKey("LastOutput") ? tag.getInteger("LastOutput") : -1;
        progress = previousProgress = tag.getFloat("Progress");
        startingAtRest = tag.getBoolean("StartingAtRest");
        goggles = tag.getBoolean("Goggles");
        dancing = tag.getBoolean("Dancing");
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); save(tag); return tag; }
    @Override public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); load(tag); }
    @Override public NBTTagCompound getUpdateTag() { NBTTagCompound tag = super.getUpdateTag(); save(tag); return tag; }
    @Override public void handleUpdateTag(NBTTagCompound tag) { super.handleUpdateTag(tag); load(tag); }
}
