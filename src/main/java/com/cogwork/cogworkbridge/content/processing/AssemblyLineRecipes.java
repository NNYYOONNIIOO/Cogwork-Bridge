package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The assembly lines currently implemented by the spout, press and deployer. */
public final class AssemblyLineRecipes {
    public static final String PRECISION_STEP = "AssemblyStep";
    private AssemblyLineRecipes() { }

    public enum Kind { PRECISION, STURDY_SHEET, TRACK }
    public enum Operation { DEPLOY, FILL, PRESS }
    public record Step(Operation operation, List<ItemStack> items, @Nullable FluidStack fluid) { }
    public record Output(ItemStack stack, float chance) { }
    public record Recipe(Kind kind, List<ItemStack> input, ItemStack transitional,
                         List<Step> steps, int loops, List<Output> outputs) { }
    public record Progress(Kind kind, int completed, int total) { }

    public static List<Recipe> all() {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : builtins()) if (ScriptedAssemblyRecipes.allowBuiltin(recipe.kind())) result.add(recipe);
        for (var line : ScriptedAssemblyRecipes.all()) result.add(line.display());
        return result;
    }
    public static List<Recipe> builtins() {
        List<Output> precisionOutputs = Arrays.asList(
                new Output(new ItemStack(AllItems.PRECISION_MECHANISM), 120F / 150F),
                new Output(new ItemStack(AllItems.GOLDEN_SHEET), 8F / 150F),
                new Output(new ItemStack(AllItems.ANDESITE_ALLOY), 8F / 150F),
                new Output(new ItemStack(AllBlocks.COGWHEEL), 5F / 150F),
                new Output(new ItemStack(Items.GOLD_NUGGET), 3F / 150F),
                new Output(new ItemStack(AllBlocks.SHAFT), 2F / 150F),
                new Output(new ItemStack(AllItems.CRUSHED_RAW_GOLD), 2F / 150F),
                new Output(new ItemStack(Items.IRON_INGOT), 1F / 150F),
                new Output(new ItemStack(Items.CLOCK), 1F / 150F));
        return Arrays.asList(
                new Recipe(Kind.PRECISION, ore("plateGold", new ItemStack(AllItems.GOLDEN_SHEET)),
                        new ItemStack(AllItems.INCOMPLETE_PRECISION_MECHANISM), steps(Kind.PRECISION), 5, precisionOutputs),
                new Recipe(Kind.STURDY_SHEET, ore("dustObsidian", new ItemStack(AllItems.POWDERED_OBSIDIAN)),
                        new ItemStack(AllItems.UNPROCESSED_OBSIDIAN_SHEET), steps(Kind.STURDY_SHEET), 1,
                        Collections.singletonList(new Output(new ItemStack(AllItems.STURDY_SHEET), 1F))),
                new Recipe(Kind.TRACK, com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.sleepers(),
                        new ItemStack(AllItems.INCOMPLETE_TRACK), steps(Kind.TRACK), 1,
                        Collections.singletonList(new Output(new ItemStack(AllBlocks.TRACK), 1F))));
    }

    public static List<Step> steps(Kind kind) {
        if (kind == Kind.TRACK) return Arrays.asList(
                new Step(Operation.DEPLOY, com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.nuggets(), null),
                new Step(Operation.DEPLOY, com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.nuggets(), null),
                new Step(Operation.PRESS, Collections.emptyList(), null));
        if (kind == Kind.PRECISION) return Arrays.asList(
                new Step(Operation.DEPLOY, Collections.singletonList(new ItemStack(AllBlocks.COGWHEEL)), null),
                new Step(Operation.DEPLOY, Collections.singletonList(new ItemStack(AllBlocks.LARGE_COGWHEEL)), null),
                new Step(Operation.DEPLOY, ore("nuggetIron", new ItemStack(Items.IRON_NUGGET)), null));
        return Arrays.asList(new Step(Operation.FILL, Collections.emptyList(), new FluidStack(FluidRegistry.LAVA, 500)),
                new Step(Operation.PRESS, Collections.emptyList(), null),
                new Step(Operation.PRESS, Collections.emptyList(), null));
    }

    private static List<ItemStack> ore(String name, ItemStack fallback) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : OreDictionary.getOres(name)) if (!stack.isEmpty()) result.add(stack.copy());
        if (result.isEmpty()) result.add(fallback);
        return result;
    }

    @Nullable public static Progress progress(ItemStack stack) {
        if (stack.isEmpty()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (stack.getItem() == AllItems.INCOMPLETE_TRACK) {
            int completed = com.cogwork.cogworkbridge.content.trains.track.TrackAssemblySequence.stage(stack);
            return new Progress(Kind.TRACK, completed, 3);
        }
        if (stack.getItem() == AllItems.INCOMPLETE_PRECISION_MECHANISM) {
            int completed = tag == null ? 0 : tag.getInteger(PRECISION_STEP);
            return new Progress(Kind.PRECISION, Math.max(0, Math.min(14, completed)), 15);
        }
        if (stack.getItem() == AllItems.UNPROCESSED_OBSIDIAN_SHEET) {
            int presses = tag == null ? 0 : tag.getInteger(ObsidianSheetSequence.STEP);
            return new Progress(Kind.STURDY_SHEET, 1 + Math.max(0, Math.min(1, presses)), 3);
        }
        return null;
    }
}
