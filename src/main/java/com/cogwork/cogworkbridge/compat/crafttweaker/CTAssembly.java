package com.cogwork.cogworkbridge.compat.crafttweaker;

import com.cogwork.cogworkbridge.content.processing.*;
import crafttweaker.annotations.ModOnly;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.item.IItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;
import java.util.*;

@ZenRegister
@ModOnly("crafttweaker")
@ZenClass("mods.cogworkbridge.Assembly")
public final class CTAssembly {
    private CTAssembly() { }
    @ZenMethod public static void add(String name, IIngredient input, IItemStack transitional,
                                      CTAssemblyStep[] steps, int loops, IIngredient[] outputs, double[] weights) {
        CTRecipes.require(steps != null && steps.length > 0 && steps.length <= 6 && loops > 0 && loops <= 64,
                "Assembly requires 1..6 steps and 1..64 loops");
        CTRecipes.require(outputs != null && outputs.length > 0 && outputs.length <= 9 && weights != null && weights.length == outputs.length,
                "Supply 1..9 assembly outputs with one weight each");
        List<ScriptedAssemblyRecipes.Step> operations = new ArrayList<>();
        for (CTAssemblyStep step : steps) { CTRecipes.require(step != null, "Assembly step must not be null"); operations.add(step.step); }
        List<AssemblyLineRecipes.Output> results = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < outputs.length; i++) {
            CTRecipes.require(Double.isFinite(weights[i]) && weights[i] > 0, "Weights must be finite and positive"); sum += weights[i];
        }
        CTRecipes.require(Double.isFinite(sum), "Total weight is too large");
        for (int i = 0; i < outputs.length; i++) results.add(new AssemblyLineRecipes.Output(CTRecipes.output(outputs[i]), (float) (weights[i] / sum)));
        var item = CTRecipes.output(transitional); CTRecipes.require(item.getCount() == 1, "Transitional item count must be 1");
        var line = new ScriptedAssemblyRecipes.Line(RecipeControl.id(name), CTRecipes.ingredient(input, true), item, operations, loops, results);
        CTRecipes.change("add assembly " + line.id(), () -> ScriptedAssemblyRecipes.add(line));
    }
}
