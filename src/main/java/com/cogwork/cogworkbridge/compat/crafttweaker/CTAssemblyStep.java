package com.cogwork.cogworkbridge.compat.crafttweaker;

import com.cogwork.cogworkbridge.content.processing.*;
import crafttweaker.annotations.ModOnly;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.liquid.ILiquidStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;
import java.util.List;

@ZenRegister
@ModOnly("crafttweaker")
@ZenClass("mods.cogworkbridge.AssemblyStep")
public final class CTAssemblyStep {
    final ScriptedAssemblyRecipes.Step step;
    private CTAssemblyStep(ScriptedAssemblyRecipes.Step step) { this.step = step; }
    @ZenMethod public static CTAssemblyStep press() {
        return new CTAssemblyStep(new ScriptedAssemblyRecipes.Step(AssemblyLineRecipes.Operation.PRESS, List.of(), null, false));
    }
    @ZenMethod public static CTAssemblyStep deploy(IIngredient held, boolean keepHeld) {
        return new CTAssemblyStep(new ScriptedAssemblyRecipes.Step(AssemblyLineRecipes.Operation.DEPLOY, CTRecipes.ingredient(held, true), null, keepHeld));
    }
    @ZenMethod public static CTAssemblyStep fill(ILiquidStack fluid) {
        var liquid = CTRecipes.fluid(fluid); CTRecipes.require(liquid.amount <= 1000, "Spout capacity is 1000 mB");
        return new CTAssemblyStep(new ScriptedAssemblyRecipes.Step(AssemblyLineRecipes.Operation.FILL, List.of(), liquid, false));
    }
}
