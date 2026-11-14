package com.cogwork.cogworkbridge.content.processing;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.*;
import java.util.function.Predicate;

/** Recipe editing shared by scripts, execution and JEI. Has no optional-mod references. */
public final class RecipeControl {
    public record View(String type, String id, List<List<ItemStack>> items, List<ItemStack> held,
                       List<ItemStack> outputs, List<FluidStack> fluids, FluidStack fluidOutput) { }
    private static final Map<String, List<Predicate<View>>> removals = new HashMap<>();
    public static final Set<String> TYPES = Set.of("pressing", "milling", "crushing", "cutting", "deploying",
            "item_application", "sandpaper_polishing", "splashing", "haunting", "smoking", "blasting",
            "filling", "emptying", "mixing", "compacting", "mechanical_crafting", "assembly");
    private RecipeControl() { }

    /** Keeps example stacks for JEI while preserving live ore/NBT/condition matching. */
    public static final class Choices extends AbstractList<ItemStack> {
        private final List<ItemStack> examples;
        private final Predicate<ItemStack> predicate;
        public Choices(List<ItemStack> examples, Predicate<ItemStack> predicate) {
            this.examples = List.copyOf(examples); this.predicate = predicate;
        }
        @Override public ItemStack get(int index) { return examples.get(index); }
        @Override public int size() { return examples.size(); }
        public boolean matches(ItemStack stack) { return !stack.isEmpty() && predicate.test(stack); }
    }
    public static boolean matches(List<ItemStack> choices, ItemStack stack) {
        if (choices instanceof Choices custom) return custom.matches(stack);
        if (stack.isEmpty()) return false;
        for (ItemStack choice : choices) if (OreDictionary.itemMatches(choice, stack, false)) return true;
        return false;
    }
    public static View view(UpstreamProcessingRecipes.Recipe recipe) {
        return new View(recipe.type(), recipe.id(), List.of(recipe.inputs()), recipe.heldItems(),
                recipe.outputs().stream().map(UpstreamProcessingRecipes.Output::stack).toList(),
                recipe.fluid() == null ? List.of() : List.of(recipe.fluid()), recipe.fluidOutput());
    }
    public static View view(String type, UpstreamProcessingRecipes.MixingRecipe recipe) {
        return new View(type, recipe.id(), recipe.items(), List.of(),
                recipe.itemOutput().isEmpty() ? List.of() : List.of(recipe.itemOutput()), recipe.fluids(), recipe.fluidOutput());
    }
    public static View view(UpstreamProcessingRecipes.MechanicalRecipe recipe) {
        return new View("mechanical_crafting", recipe.id(), recipe.ingredients(), List.of(), List.of(recipe.result()), List.of(), null);
    }
    public static boolean allowed(View candidate) {
        for (Predicate<View> filter : removals.getOrDefault(candidate.type(), List.of()))
            if (filter.test(candidate)) return false;
        return true;
    }
    public static boolean hasRemovals(String type) { return removals.containsKey(type); }
    public static boolean allow(String type, ItemStack input, ItemStack held, ItemStack output) {
        return allowed(new View(type, "", List.of(List.of(input)), held.isEmpty() ? List.of() : List.of(held),
                output.isEmpty() ? List.of() : List.of(output), List.of(), null));
    }
    public static ItemStack fallback(String type, ItemStack input, ItemStack held, ItemStack output) {
        return allow(type, input, held, output) ? output : ItemStack.EMPTY;
    }
    public static void remove(String type, Predicate<View> filter) {
        List<View> removed = snapshots(type).stream().filter(filter).toList();
        UpstreamProcessingRecipes.remove(type, filter);
        ScriptedAssemblyRecipes.remove(type, filter);
        removals.computeIfAbsent(type, unused -> new ArrayList<>()).add(candidate -> filter.test(candidate)
                || removed.stream().anyMatch(old -> sameFallback(old, candidate)));
    }
    private static List<View> snapshots(String type) {
        if (type.equals("mixing")) return UpstreamProcessingRecipes.mixing().stream().map(r -> view(type, r)).toList();
        if (type.equals("compacting")) return UpstreamProcessingRecipes.compacting().stream().map(r -> view(type, r)).toList();
        if (type.equals("mechanical_crafting")) return UpstreamProcessingRecipes.mechanicalCrafting().stream().map(RecipeControl::view).toList();
        if (type.equals("assembly")) return ScriptedAssemblyRecipes.all().stream().map(ScriptedAssemblyRecipes.Line::view).toList();
        return UpstreamProcessingRecipes.all(type).stream().map(RecipeControl::view).toList();
    }
    private static boolean sameFallback(View old, View candidate) {
        if (old.outputs().isEmpty() ? candidate.fluidOutput() == null || old.fluidOutput() == null
                || !old.fluidOutput().isFluidEqual(candidate.fluidOutput())
                : candidate.outputs().stream().noneMatch(actual -> old.outputs().stream().anyMatch(expected ->
                        ItemStack.areItemsEqual(expected, actual) && ItemStack.areItemStackTagsEqual(expected, actual)))) return false;
        if (!old.held().isEmpty() && candidate.held().stream().noneMatch(stack -> matches(old.held(), stack))) return false;
        if (old.items().isEmpty()) return candidate.items().isEmpty();
        return candidate.items().stream().flatMap(Collection::stream).anyMatch(stack -> old.items().stream().anyMatch(group -> matches(group, stack)));
    }
    public static String id(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Cogwork Bridge: recipe name must not be empty");
        net.minecraft.util.ResourceLocation location = new net.minecraft.util.ResourceLocation(name.contains(":") ? name : "cogworkbridge:" + name);
        return location.toString();
    }
    public static String upstreamId(String path) { return id("create:" + path.replaceFirst("\\.json$", "")); }
}
