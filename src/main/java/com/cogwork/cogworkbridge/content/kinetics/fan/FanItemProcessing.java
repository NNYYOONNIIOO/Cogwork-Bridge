package com.cogwork.cogworkbridge.content.kinetics.fan;

import com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

/** Fan processing shared by loose items, belts and depots. */
public final class FanItemProcessing {
    private FanItemProcessing() { }

    private static String recipeType(EncasedFanTileEntity.Medium medium) {
        return switch (medium) {
            case SPLASHING -> "splashing";
            case HAUNTING -> "haunting";
            default -> null;
        };
    }

    private static ItemStack result(EncasedFanTileEntity.Medium medium, ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        String type = recipeType(medium);
        if (type != null) {
            var recipe = UpstreamProcessingRecipes.find(type, input);
            return recipe == null ? ItemStack.EMPTY : recipe.outputs().get(0).stack().copy();
        }
        ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input);
        boolean food = input.getItem() instanceof ItemFood || !result.isEmpty() && result.getItem() instanceof ItemFood;
        if (medium != EncasedFanTileEntity.Medium.SMOKING && medium != EncasedFanTileEntity.Medium.BLASTING
                || medium == EncasedFanTileEntity.Medium.SMOKING && !food
                || medium == EncasedFanTileEntity.Medium.BLASTING && food) return ItemStack.EMPTY;
        return result.isEmpty() ? ItemStack.EMPTY : result.copy();
    }

    public static List<ItemStack> results(EncasedFanTileEntity.Medium medium, ItemStack input) {
        return results(medium, input, java.util.concurrent.ThreadLocalRandom.current());
    }
    /** Maximum possible outputs, for checking storage before rolling chance. */
    public static List<ItemStack> possible(EncasedFanTileEntity.Medium medium, ItemStack input) {
        String type = recipeType(medium);
        if (type == null) return results(medium, input);
        var recipe = UpstreamProcessingRecipes.find(type, input);
        List<ItemStack> outputs = new ArrayList<>();
        if (recipe == null) return outputs;
        for (var result : recipe.outputs()) {
            int remaining = result.stack().getCount() * input.getCount();
            while (remaining > 0) {
                ItemStack next = result.stack().copy();
                int count = Math.min(remaining, next.getMaxStackSize());
                next.setCount(count); outputs.add(next); remaining -= count;
            }
        }
        return outputs;
    }
    public static List<ItemStack> results(EncasedFanTileEntity.Medium medium, ItemStack input, java.util.Random random) {
        String type = recipeType(medium);
        if (type != null) {
            var recipe = UpstreamProcessingRecipes.find(type, input);
            List<ItemStack> outputs = new ArrayList<>();
            if (recipe == null) return outputs;
            for (int i = 0; i < input.getCount(); i++) for (ItemStack stack : recipe.roll(random)) {
                for (ItemStack current : outputs) {
                    if (!net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(current, stack)) continue;
                    int amount = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                    current.grow(amount); stack.shrink(amount);
                    if (stack.isEmpty()) break;
                }
                if (!stack.isEmpty()) outputs.add(stack);
            }
            return outputs;
        }
        ItemStack template = result(medium, input);
        List<ItemStack> outputs = new ArrayList<>();
        if (template.isEmpty()) return outputs;
        int remaining = input.getCount() * template.getCount();
        while (remaining > 0) {
            ItemStack next = template.copy();
            int amount = Math.min(remaining, next.getMaxStackSize());
            next.setCount(amount); outputs.add(next); remaining -= amount;
        }
        return outputs;
    }

    /** Process loose items with the same timer as belt and depot items. */
    public static void applyToEntity(EntityItem entity, EncasedFanTileEntity.Medium medium) {
        ItemStack input = entity.getItem();
        ItemStack output = result(medium, input);
        if (output.isEmpty()) return;
        NBTTagCompound data = entity.getEntityData();
        NBTTagCompound progressTag = data.getCompoundTag("CogworkBridgeFanProcessing");
        ItemStack expected = progressTag.hasKey("Input", 10)
                ? new ItemStack(progressTag.getCompoundTag("Input")) : ItemStack.EMPTY;
        if (!ItemStack.areItemsEqual(expected, input) || !ItemStack.areItemStackTagsEqual(expected, input)) {
            progressTag = new NBTTagCompound();
            ItemStack one = input.copy();
            one.setCount(1);
            progressTag.setTag("Input", one.writeToNBT(new NBTTagCompound()));
        }
        long tick = entity.world.getTotalWorldTime();
        if (progressTag.hasKey("LastTick", 4) && progressTag.getLong("LastTick") == tick) return;
        Progress progress = new Progress();
        progress.read(progressTag);
        boolean ready = progress.tick(medium, input, tick);
        progress.write(progressTag);
        progressTag.setLong("LastTick", tick);
        data.setTag("CogworkBridgeFanProcessing", progressTag);
        if (!ready) return;

        List<EntityItem> spawned = new ArrayList<>();
        for (ItemStack stack : results(medium, input, entity.world.rand)) {
            EntityItem produced = new EntityItem(entity.world, entity.posX, entity.posY, entity.posZ, stack);
            produced.motionX = entity.motionX; produced.motionY = entity.motionY; produced.motionZ = entity.motionZ;
            produced.setDefaultPickupDelay();
            if (!entity.world.spawnEntity(produced)) { for (EntityItem rollback : spawned) rollback.setDead(); return; }
            spawned.add(produced);
        }
        entity.setDead();
    }

    public static final class Progress {
        private int medium = -1, remaining;
        private long lastTick = Long.MIN_VALUE;

        public boolean tick(EncasedFanTileEntity.Medium type, ItemStack input, long tick) {
            if (result(type, input).isEmpty()) return false;
            if (medium != type.ordinal()) {
                medium = type.ordinal();
                remaining = 150 * ((input.getCount() - 1) / 16 + 1) + 1;
            }
            if (lastTick == tick) return false;
            lastTick = tick;
            return --remaining <= 0;
        }

        public void reset() { medium = -1; remaining = 0; lastTick = Long.MIN_VALUE; }
        public void copyFrom(Progress other) { medium = other.medium; remaining = other.remaining; lastTick = other.lastTick; }
        public void write(NBTTagCompound tag) { tag.setInteger("FanMedium", medium); tag.setInteger("FanRemaining", remaining); }
        public void read(NBTTagCompound tag) {
            medium = tag.hasKey("FanMedium") ? tag.getInteger("FanMedium") : -1;
            remaining = Math.max(0, tag.getInteger("FanRemaining"));
            lastTick = Long.MIN_VALUE;
        }
    }
}
