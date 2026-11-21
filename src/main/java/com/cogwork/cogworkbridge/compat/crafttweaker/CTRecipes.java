package com.cogwork.cogworkbridge.compat.crafttweaker;

import com.cogwork.cogworkbridge.content.processing.*;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ModOnly;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;
import java.util.*;
import java.util.function.Predicate;

@ZenRegister
@ModOnly("crafttweaker")
@ZenClass("mods.cogworkbridge.Recipes")
public final class CTRecipes {
    private static final Set<String> SINGLE = Set.of("pressing", "milling", "crushing", "cutting", "sandpaper_polishing",
            "splashing", "haunting", "smoking", "blasting");
    private CTRecipes() { }
    static void change(String description, Runnable action) {
        CraftTweakerAPI.apply(new IAction() {
            @Override public void apply() { action.run(); }
            @Override public String describe() { return "Cogwork Bridge: " + description; }
        });
    }
    static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException("Cogwork Bridge: " + message);
    }
    static String type(String type) {
        require(type != null && RecipeControl.TYPES.contains(type), "Unknown recipe type: " + type); return type;
    }
    static ItemStack output(IIngredient stack) {
        require(stack != null, "Output must not be null");
        require(stack.getAmount() > 0, "Output amount must be positive");
        require(!stack.hasTransformers() && !stack.hasNewTransformers(), "Output transformers are unsupported");
        if (stack instanceof IItemStack item) {
            ItemStack result = CraftTweakerMC.getItemStack(item).copy();
            require(!result.isEmpty() && result.getMetadata() != net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                    && result.getCount() <= result.getMaxStackSize(), "Output must fit one concrete legal item stack");
            return result;
        }
        List<IItemStack> examples = stack.getItems();
        if (examples != null) for (IItemStack example : examples) {
            ItemStack result = CraftTweakerMC.getItemStack(example).copy();
            if (result.isEmpty() || result.getMetadata() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                    || stack.getAmount() > result.getMaxStackSize()) continue;
            result.setCount(stack.getAmount());
            if (stack.matches(CraftTweakerMC.getIItemStack(result))) return result;
        }
        throw new IllegalArgumentException("Cogwork Bridge: output ingredient has no concrete matching item with a legal stack size");
    }
    static FluidStack fluid(ILiquidStack stack) {
        require(stack != null, "Fluid must not be null");
        FluidStack result = CraftTweakerMC.getLiquidStack(stack);
        require(result != null && result.amount > 0, "Fluid amount must be positive"); return result.copy();
    }
    static List<ItemStack> ingredient(IIngredient ingredient, boolean unit) {
        require(ingredient != null, "Ingredient must not be null");
        require(ingredient.getAmount() > 0 && (!unit || ingredient.getAmount() == 1), "This slot consumes exactly one item; use amount 1");
        require(!ingredient.hasTransformers() && !ingredient.hasNewTransformers(), "Ingredient transformers are unsupported; use an explicit output or keepHeld");
        List<IItemStack> examples = ingredient.getItems();
        require(examples != null && !examples.isEmpty(), "Ingredient needs at least one registered item for JEI");
        List<ItemStack> items = new ArrayList<>();
        for (IItemStack example : examples) {
            ItemStack stack = CraftTweakerMC.getItemStack(example).copy();
            if (!stack.isEmpty()) { stack.setCount(1); items.add(stack); }
        }
        require(!items.isEmpty(), "Ingredient resolved to no items");
        return new RecipeControl.Choices(items, stack -> {
            ItemStack candidate = stack.copy(); candidate.setCount(Math.max(stack.getCount(), ingredient.getAmount()));
            return ingredient.matches(CraftTweakerMC.getIItemStack(candidate));
        });
    }
    static Predicate<ItemStack> selector(IIngredient ingredient) {
        require(ingredient != null, "Removal ingredient must not be null");
        return stack -> {
            if (stack.isEmpty()) return false;
            ItemStack copy = stack.copy(); copy.setCount(Math.max(copy.getCount(), ingredient.getAmount()));
            return ingredient.matches(CraftTweakerMC.getIItemStack(copy));
        };
    }
    static List<UpstreamProcessingRecipes.Output> outputs(IIngredient[] outputs, double[] chances) {
        require(outputs != null && outputs.length > 0 && outputs.length <= 9, "Supply 1..9 outputs");
        require(chances != null && chances.length == outputs.length, "One chance is required per output");
        List<UpstreamProcessingRecipes.Output> result = new ArrayList<>();
        for (int i = 0; i < outputs.length; i++) {
            require(Double.isFinite(chances[i]) && chances[i] > 0 && chances[i] <= 1, "Chance must be in (0, 1]");
            result.add(new UpstreamProcessingRecipes.Output(output(outputs[i]), (float) chances[i]));
        }
        return result;
    }
    @ZenMethod public static void add(String type, String name, IIngredient output, IIngredient input) {
        addRecipe(type, name, input, new IIngredient[]{output}, new double[]{1}, 100);
    }
    @ZenMethod public static void addRecipe(String type, String name, IIngredient input, IIngredient[] outputs, double[] chances, int duration) {
        type(type); require(SINGLE.contains(type), "Use the specialized method for " + type);
        require(duration > 0, "Duration must be positive");
        List<ItemStack> choices = ingredient(input, true); var result = outputs(outputs, chances);
        if (type.equals("pressing") || type.equals("sandpaper_polishing"))
            require(result.size() == 1 && result.get(0).chance() == 1, type + " requires one guaranteed output");
        var recipe = new UpstreamProcessingRecipes.Recipe(type, choices, List.of(), result, duration, false, null, RecipeControl.id(name), null);
        change("add " + type + " " + recipe.id(), () -> UpstreamProcessingRecipes.add(recipe));
    }
    @ZenMethod public static void addDeploying(String name, IIngredient output, IIngredient input, IIngredient held, boolean keepHeld) {
        addApplication("deploying", name, output, input, held, keepHeld);
    }
    @ZenMethod public static void addApplication(String type, String name, IIngredient output, IIngredient input, IIngredient held, boolean keepHeld) {
        require(type.equals("deploying") || type.equals("item_application"), "Expected deploying or item_application");
        var recipe = new UpstreamProcessingRecipes.Recipe(type, ingredient(input, true), ingredient(held, true),
                List.of(new UpstreamProcessingRecipes.Output(output(output), 1)), 100, keepHeld, null, RecipeControl.id(name), null);
        if (type.equals("item_application")) require(recipe.outputs().get(0).stack().getItem() instanceof net.minecraft.item.ItemBlock
                && recipe.outputs().get(0).stack().getCount() == 1, "World item application must output one block");
        change("add " + type + " " + recipe.id(), () -> UpstreamProcessingRecipes.add(recipe));
    }
    @ZenMethod public static void addFilling(String name, IIngredient output, IIngredient input, ILiquidStack fluid) {
        var recipe = new UpstreamProcessingRecipes.Recipe("filling", ingredient(input, true), List.of(),
                List.of(new UpstreamProcessingRecipes.Output(output(output), 1)), 100, false, fluid(fluid), RecipeControl.id(name), null);
        require(recipe.fluid().amount <= 1000, "Spout capacity is 1000 mB");
        change("add filling " + recipe.id(), () -> UpstreamProcessingRecipes.add(recipe));
    }
    @ZenMethod public static void addEmptying(String name, IIngredient container, ILiquidStack fluid, IIngredient input) {
        var recipe = new UpstreamProcessingRecipes.Recipe("emptying", ingredient(input, true), List.of(),
                List.of(new UpstreamProcessingRecipes.Output(output(container), 1)), 100, false, null, RecipeControl.id(name), fluid(fluid));
        require(recipe.fluidOutput().amount <= 1500, "Drain capacity is 1500 mB");
        change("add emptying " + recipe.id(), () -> UpstreamProcessingRecipes.add(recipe));
    }
    @ZenMethod public static void addBasin(String type, String name, IIngredient[] inputs, ILiquidStack[] fluids,
                                          IIngredient output, ILiquidStack fluidOutput, int heat) {
        require(type.equals("mixing") || type.equals("compacting"), "Expected mixing or compacting");
        require(inputs != null && fluids != null && heat >= 0 && heat <= 2, "Supply input/fluid arrays and heat 0, 1 or 2");
        List<List<ItemStack>> groups = new ArrayList<>();
        for (IIngredient input : inputs) {
            List<ItemStack> choices = ingredient(input, false);
            require(input.getAmount() <= 64 && groups.size() + input.getAmount() <= 64, "At most 64 item units per basin recipe");
            for (int i = 0; i < input.getAmount(); i++) groups.add(choices);
        }
        List<FluidStack> liquids = new ArrayList<>();
        for (ILiquidStack input : fluids) {
            FluidStack next = fluid(input); boolean merged = false;
            for (FluidStack previous : liquids) if (previous.isFluidEqual(next)) { previous.amount = Math.addExact(previous.amount, next.amount); merged = true; break; }
            if (!merged) liquids.add(next);
        }
        require(!groups.isEmpty() || !liquids.isEmpty(), "A basin recipe needs inputs");
        require(liquids.size() <= 2 && liquids.stream().allMatch(f -> f.amount <= 1000), "Basin has two 1000 mB input tanks");
        require(output != null || fluidOutput != null, "A basin recipe needs an output");
        var recipe = new UpstreamProcessingRecipes.MixingRecipe(RecipeControl.id(name), groups, liquids,
                output == null ? ItemStack.EMPTY : output(output), fluidOutput == null ? null : fluid(fluidOutput), heat);
        require(recipe.fluidOutput() == null || recipe.fluidOutput().amount <= 1000, "Basin fluid output must fit a 1000 mB tank");
        change("add " + type + " " + recipe.id(), () -> UpstreamProcessingRecipes.add(type, recipe));
    }
    @ZenMethod public static void addMechanical(String name, IIngredient output, IIngredient[][] pattern, boolean mirrored) {
        require(pattern != null && pattern.length > 0 && pattern.length <= 32 && pattern[0] != null, "Pattern height must be 1..32");
        int width = pattern[0].length; require(width > 0 && width <= 32, "Pattern width must be 1..32");
        List<List<ItemStack>> slots = new ArrayList<>(); boolean nonempty = false;
        for (IIngredient[] row : pattern) {
            require(row != null && row.length == width, "Pattern must be rectangular; use null for spaces");
            for (IIngredient input : row) { slots.add(input == null ? List.of() : ingredient(input, true)); if (input != null) nonempty = true; }
        }
        require(nonempty, "Pattern must contain an ingredient");
        var recipe = new UpstreamProcessingRecipes.MechanicalRecipe(RecipeControl.id(name), width, pattern.length, slots, output(output), mirrored);
        change("add mechanical crafting " + recipe.id(), () -> UpstreamProcessingRecipes.add(recipe));
    }
    @ZenMethod public static void remove(String type, IIngredient output) {
        type(type); Predicate<ItemStack> match = selector(output);
        change("remove " + type + " by output", () -> RecipeControl.remove(type, recipe -> recipe.outputs().stream().anyMatch(match)));
    }
    @ZenMethod public static void removeInput(String type, IIngredient input) {
        type(type); Predicate<ItemStack> match = selector(input);
        change("remove " + type + " by input", () -> RecipeControl.remove(type, recipe -> recipe.items().stream().flatMap(Collection::stream).anyMatch(match)));
    }
    @ZenMethod public static void removeFluidOutput(String type, ILiquidStack fluid) {
        type(type); FluidStack match = fluid(fluid);
        change("remove " + type + " by fluid output", () -> RecipeControl.remove(type,
                recipe -> recipe.fluidOutput() != null && match.isFluidEqual(recipe.fluidOutput())));
    }
    @ZenMethod public static void removeByName(String type, String name) {
        type(type); String id = RecipeControl.id(name);
        change("remove " + type + " " + id, () -> RecipeControl.remove(type, recipe -> recipe.id().equals(id)));
    }
    @ZenMethod public static void removeFluidInput(String type, ILiquidStack fluid) {
        type(type); FluidStack match = fluid(fluid);
        change("remove " + type + " by fluid input", () -> RecipeControl.remove(type,
                recipe -> recipe.fluids().stream().anyMatch(match::isFluidEqual)));
    }
    @ZenMethod public static void removeWithHeld(String type, IIngredient input, IIngredient held) {
        require(type.equals("deploying") || type.equals("item_application"), "Expected deploying or item_application");
        var first = selector(input); var second = selector(held);
        change("remove " + type + " by input and held item", () -> RecipeControl.remove(type,
                recipe -> recipe.items().stream().flatMap(Collection::stream).anyMatch(first) && recipe.held().stream().anyMatch(second)));
    }
    @ZenMethod public static void removeAll(String type) {
        type(type); change("remove all " + type, () -> RecipeControl.remove(type, recipe -> true));
    }
    @ZenMethod public static void dump() {
        change("dump recipe names", () -> {
            for (String type : new TreeSet<>(RecipeControl.TYPES)) {
                if (type.equals("mixing") || type.equals("compacting")) {
                    for (var recipe : type.equals("mixing") ? UpstreamProcessingRecipes.mixing() : UpstreamProcessingRecipes.compacting())
                        CraftTweakerAPI.logInfo(type + " " + recipe.id());
                } else if (type.equals("mechanical_crafting")) {
                    for (var recipe : UpstreamProcessingRecipes.mechanicalCrafting()) CraftTweakerAPI.logInfo(type + " " + recipe.id());
                } else if (type.equals("assembly")) {
                    for (var kind : AssemblyLineRecipes.Kind.values()) if (ScriptedAssemblyRecipes.allowBuiltin(kind))
                        CraftTweakerAPI.logInfo("assembly cogworkbridge:" + kind.name().toLowerCase(Locale.ROOT));
                    for (var recipe : ScriptedAssemblyRecipes.all()) CraftTweakerAPI.logInfo("assembly " + recipe.id());
                } else for (var recipe : UpstreamProcessingRecipes.all(type)) CraftTweakerAPI.logInfo(type + " " + recipe.id());
            }
        });
    }
}
