package com.cogwork.cogworkbridge.content.kinetics.crusher;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Available 1.12 inputs from the upstream crushing recipes. */
public final class CrushingRecipes {
    private CrushingRecipes() { }
    private static com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.Recipe find(ItemStack input) {
        var recipe = com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("crushing", input);
        return recipe != null ? recipe : com.cogwork.cogworkbridge.content.processing.UpstreamProcessingRecipes.find("milling", input);
    }
    public static boolean accepts(ItemStack input) { return !input.isEmpty() && (find(input) != null || input.getItem() == Item.getItemFromBlock(Blocks.GRAVEL)); }
    public static int duration(ItemStack input) {
        var recipe = find(input);
        return recipe == null ? 250 : recipe.duration();
    }
    public static List<ItemStack> possible(ItemStack input) {
        var recipe = find(input);
        if (recipe != null) {
            List<ItemStack> outputs = new ArrayList<>();
            for (var output : recipe.outputs()) outputs.add(output.stack().copy());
            return outputs;
        }
        return accepts(input) ? java.util.Arrays.asList(new ItemStack(Blocks.SAND), new ItemStack(Items.FLINT), new ItemStack(Items.CLAY_BALL)) : java.util.Collections.emptyList();
    }
    public static List<ItemStack> results(ItemStack input, Random random) {
        List<ItemStack> results = new ArrayList<>();
        if (!accepts(input)) return results;
        var recipe = find(input);
        if (recipe != null) return recipe.roll(random);
        results.add(new ItemStack(Blocks.SAND));
        if (random.nextFloat() < .10F) results.add(new ItemStack(Items.FLINT));
        if (random.nextFloat() < .05F) results.add(new ItemStack(Items.CLAY_BALL));
        return results;
    }
}
