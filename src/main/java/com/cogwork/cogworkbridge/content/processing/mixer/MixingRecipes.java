package com.cogwork.cogworkbridge.content.processing.mixer;

import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerTileEntity;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import java.util.Collections;

/** Upstream alloy mixing recipes using the 1.12 ore dictionary. */
public final class MixingRecipes {
    private MixingRecipes() { }
    public record Plan(String id, int[] counts, ItemStack result, int water, net.minecraftforge.fluids.FluidStack fluidOutput,
                       java.util.List<net.minecraftforge.fluids.FluidStack> fluidInputs) {
        public Plan(String id, int[] counts, ItemStack result, int water) { this(id, counts, result, water, null, Collections.emptyList()); }
        public Plan(String id, int[] counts, ItemStack result, int water, net.minecraftforge.fluids.FluidStack fluidOutput) {
            this(id, counts, result, water, fluidOutput, Collections.emptyList());
        }
    }
    public static boolean ore(ItemStack stack, String name) {
        for (int id : OreDictionary.getOreIDs(stack)) if (name.equals(OreDictionary.getOreName(id))) return true;
        return false;
    }
    public static Plan find(World world, BlockPos basinPos, BasinTileEntity basin) {
        int andesite = -1, iron = -1, zincNugget = -1, copper = -1, zinc = -1, cobblestone = -1;
        for (int i = 0; i < basin.getInputItems().getSlots(); i++) {
            ItemStack stack = basin.getInputItems().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == Item.getItemFromBlock(Blocks.STONE) && stack.getMetadata() == 5) andesite = i;
            if (ore(stack, "nuggetIron")) iron = i;
            if (ore(stack, "nuggetZinc")) zincNugget = i;
            if (ore(stack, "ingotCopper")) copper = i;
            if (ore(stack, "ingotZinc")) zinc = i;
            if (stack.getItem() == Item.getItemFromBlock(Blocks.COBBLESTONE) || ore(stack, "cobblestone")) cobblestone = i;
        }
        int[] pulp = new int[basin.getInputItems().getSlots()];
        int needed = 4;
        for (int i = 0; i < pulp.length && needed > 0; i++) {
            ItemStack stack = basin.getInputItems().getStackInSlot(i);
            if (!pulpifiable(stack)) continue;
            pulp[i] = Math.min(needed, stack.getCount()); needed -= pulp[i];
        }
        net.minecraftforge.fluids.FluidStack water = new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 250);
        if (needed == 0 && basin.drainInputFluid(water, false) == 250
                && basin.processBatch(pulp, Collections.singletonList(new ItemStack(AllItems.PULP)), true))
            return new Plan("pulp", pulp, new ItemStack(AllItems.PULP), 250);
        if (andesite >= 0 && (iron >= 0 || zincNugget >= 0))
            return plan(basin, "andesite_alloy", andesite, iron >= 0 ? iron : zincNugget);
        if (copper >= 0 && zinc >= 0 && world.getTileEntity(basinPos.down()) instanceof BlazeBurnerTileEntity burner && burner.getProcessingHeat() >= 1)
            return plan(basin, "brass_ingot", copper, zinc);
        if (cobblestone >= 0 && world.getTileEntity(basinPos.down()) instanceof BlazeBurnerTileEntity burner && burner.getProcessingHeat() >= 2) {
            int[] counts = new int[basin.getInputItems().getSlots()]; counts[cobblestone] = 1;
            net.minecraftforge.fluids.FluidStack lava = new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.LAVA, 50);
            if (basin.fillOutput(lava, false) == lava.amount && basin.processBatch(counts, Collections.emptyList(), true))
                return new Plan("lava_from_cobble", counts, ItemStack.EMPTY, 0, lava);
        }
        int heat = world.getTileEntity(basinPos.down()) instanceof BlazeBurnerTileEntity burner ? burner.getProcessingHeat() : 0;
        if (heat >= 1) {
            Plan brewing = com.cogwork.cogworkbridge.content.fluids.PotionMixingRecipes.find(basin);
            if (brewing != null) return brewing;
        }
        for (var recipe : com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.mixing()) {
            if (heat < recipe.heat()) continue;
            int[] counts = matchItems(basin, recipe.items());
            if (counts == null) continue;
            boolean matches = true;
            for (net.minecraftforge.fluids.FluidStack fluid : recipe.fluids())
                if (basin.drainInputFluid(fluid, false) != fluid.amount) { matches = false; break; }
            if (!matches || recipe.fluidOutput() != null && basin.fillOutput(recipe.fluidOutput(), false) != recipe.fluidOutput().amount
                    || !basin.processBatch(counts, Collections.singletonList(recipe.itemOutput()), true)) continue;
            return new Plan(recipe.id(), counts, recipe.itemOutput().copy(), 0,
                    recipe.fluidOutput() == null ? null : recipe.fluidOutput().copy(), recipe.fluids());
        }
        return null;
    }
    public static Plan afterBatch(World world, BlockPos basinPos, BasinTileEntity basin, Plan current) {
        BasinTileEntity projected = new BasinTileEntity();
        projected.readFromNBT(basin.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
        if (!projected.processBatch(current.counts(), Collections.singletonList(current.result()), false)) return null;
        if (current.water() > 0 && projected.drainInputFluid(
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, current.water()), true) != current.water()) return null;
        for (net.minecraftforge.fluids.FluidStack fluid : current.fluidInputs())
            if (projected.drainInputFluid(fluid, true) != fluid.amount) return null;
        if (current.fluidOutput() != null && projected.fillOutput(current.fluidOutput(), true) != current.fluidOutput().amount) return null;
        return find(world, basinPos, projected);
    }
    public static int[] matchItems(BasinTileEntity basin, java.util.List<java.util.List<ItemStack>> ingredients) {
        int[] counts = new int[basin.getInputItems().getSlots()];
        return assign(basin, ingredients, 0, counts, new java.util.HashSet<>()) ? counts : null;
    }
    private static boolean assign(BasinTileEntity basin, java.util.List<java.util.List<ItemStack>> ingredients, int index, int[] counts, java.util.Set<String> visited) {
        if (index == ingredients.size()) return true;
        if (!visited.add(index + ":" + java.util.Arrays.toString(counts))) return false;
        for (int slot = 0; slot < counts.length; slot++) {
            ItemStack stack = basin.getInputItems().getStackInSlot(slot);
            if (stack.getCount() <= counts[slot]) continue;
            boolean matches = false;
            for (ItemStack option : ingredients.get(index)) if (OreDictionary.itemMatches(option, stack, false)) { matches = true; break; }
            if (!matches) continue;
            counts[slot]++;
            if (assign(basin, ingredients, index + 1, counts, visited)) return true;
            counts[slot]--;
        }
        return false;
    }
    public static boolean pulpifiable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == net.minecraft.init.Items.REEDS || stack.getItem() == Item.getItemFromBlock(Blocks.SAPLING)) return true;
        return ore(stack, "treeSapling") || stack.getItem().getRegistryName() != null
                && "farmers_future_delight:bamboo".equals(stack.getItem().getRegistryName().toString());
    }
    public static ItemStack result(ItemStack first, ItemStack second, boolean heated) {
        boolean andesiteFirst = first.getItem() == Item.getItemFromBlock(Blocks.STONE) && first.getMetadata() == 5;
        boolean andesiteSecond = second.getItem() == Item.getItemFromBlock(Blocks.STONE) && second.getMetadata() == 5;
        if (andesiteFirst && (ore(second, "nuggetIron") || ore(second, "nuggetZinc"))
                || andesiteSecond && (ore(first, "nuggetIron") || ore(first, "nuggetZinc"))) return new ItemStack(AllItems.ANDESITE_ALLOY);
        if (heated && (ore(first, "ingotCopper") && ore(second, "ingotZinc") || ore(second, "ingotCopper") && ore(first, "ingotZinc")))
            return new ItemStack(AllItems.BRASS_INGOT, 2);
        return ItemStack.EMPTY;
    }
    private static Plan plan(BasinTileEntity basin, String id, int first, int second) {
        ItemStack output = result(basin.getInputItems().getStackInSlot(first), basin.getInputItems().getStackInSlot(second), id.equals("brass_ingot"));
        int[] counts = new int[basin.getInputItems().getSlots()]; counts[first]++; counts[second]++;
        return basin.processBatch(counts, Collections.singletonList(output), true) ? new Plan(id, counts, output, 0) : null;
    }
}
