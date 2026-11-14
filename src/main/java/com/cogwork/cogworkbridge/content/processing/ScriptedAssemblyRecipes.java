package com.cogwork.cogworkbridge.content.processing;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.*;
import java.util.function.Predicate;

/** Script-defined assembly lines share their progress between all three processing machines. */
public final class ScriptedAssemblyRecipes {
    public static final String TAG = "CogworkBridgeAssembly";
    public record Step(AssemblyLineRecipes.Operation operation, List<ItemStack> held, FluidStack fluid, boolean keepHeld) { }
    public record Line(String id, List<ItemStack> input, ItemStack transitional, List<Step> steps,
                       int loops, List<AssemblyLineRecipes.Output> outputs) {
        public RecipeControl.View view() {
            return new RecipeControl.View("assembly", id, List.of(input), List.of(),
                    outputs.stream().map(AssemblyLineRecipes.Output::stack).toList(), List.of(), null);
        }
        public AssemblyLineRecipes.Recipe display() {
            return new AssemblyLineRecipes.Recipe(null, input, transitional,
                    steps.stream().map(s -> new AssemblyLineRecipes.Step(s.operation(), s.held(), s.fluid())).toList(), loops, outputs);
        }
    }
    public record Match(Line line, int completed) {
        public Step step() { return line.steps().get(completed % line.steps().size()); }
        public ItemStack result(Random random) {
            if (completed + 1 >= line.steps().size() * line.loops()) {
                if (random == null) return line.outputs().get(0).stack().copy();
                float total = 0; for (var out : line.outputs()) total += out.chance();
                float selected = random.nextFloat() * total;
                for (var out : line.outputs()) { selected -= out.chance(); if (selected < 0) return out.stack().copy(); }
                return line.outputs().get(line.outputs().size() - 1).stack().copy();
            }
            ItemStack result = line.transitional().copy(); result.setCount(1);
            NBTTagCompound root = result.hasTagCompound() ? result.getTagCompound().copy() : new NBTTagCompound();
            NBTTagCompound progress = new NBTTagCompound(); progress.setString("Recipe", line.id());
            progress.setInteger("Step", completed + 1); root.setTag(TAG, progress); result.setTagCompound(root);
            return result;
        }
    }
    private static final List<Line> lines = new ArrayList<>();
    private ScriptedAssemblyRecipes() { }
    public static void add(Line line) { lines.removeIf(old -> old.id().equals(line.id())); lines.add(0, line); }
    public static List<Line> all() { return Collections.unmodifiableList(lines); }
    public static void remove(String type, Predicate<RecipeControl.View> filter) {
        if (type.equals("assembly")) lines.removeIf(line -> filter.test(line.view()));
    }
    public static boolean inProgress(ItemStack input) { return input.hasTagCompound() && input.getTagCompound().hasKey(TAG, 10); }
    public static Match find(AssemblyLineRecipes.Operation operation, ItemStack input, ItemStack held, IFluidHandler fluid) {
        if (input.isEmpty()) return null;
        NBTTagCompound progress = inProgress(input) ? input.getTagCompound().getCompoundTag(TAG) : null;
        for (Line line : lines) {
            int step = progress == null ? 0 : progress.getInteger("Step");
            if (progress == null ? !RecipeControl.matches(line.input(), input)
                    : !progress.getString("Recipe").equals(line.id()) || !net.minecraftforge.oredict.OreDictionary.itemMatches(line.transitional(), input, false)) continue;
            if (step < 0 || step >= line.steps().size() * line.loops()) continue;
            Match match = new Match(line, step);
            if (match.step().operation() != operation) continue;
            if (operation == AssemblyLineRecipes.Operation.DEPLOY && !RecipeControl.matches(match.step().held(), held)) continue;
            if (operation == AssemblyLineRecipes.Operation.FILL && fluid != null) {
                FluidStack available = fluid.drain(match.step().fluid(), false);
                if (available == null || available.amount < match.step().fluid().amount) continue;
            }
            return match;
        }
        return null;
    }
    public static boolean allowBuiltin(AssemblyLineRecipes.Kind kind) {
        if (!RecipeControl.hasRemovals("assembly")) return true;
        for (var recipe : AssemblyLineRecipes.builtins()) if (recipe.kind() == kind)
            return RecipeControl.allowed(new RecipeControl.View("assembly", "cogworkbridge:" + kind.name().toLowerCase(Locale.ROOT),
                    List.of(recipe.input()), List.of(), recipe.outputs().stream().map(AssemblyLineRecipes.Output::stack).toList(), List.of(), null));
        return false;
    }
}
