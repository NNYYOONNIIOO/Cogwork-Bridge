// Optional examples. Copy selected statements into the instance's scripts/*.zs and restart.
// This file is not automatically loaded or deployed.
import mods.cogworkbridge.Recipes;
import mods.cogworkbridge.Assembly;
import mods.cogworkbridge.AssemblyStep;

Recipes.remove("pressing", <cogworkbridge:iron_sheet>);
Recipes.add("pressing", "example/iron_sheet", <cogworkbridge:iron_sheet>, <ore:ingotIron>);
Recipes.addRecipe("crushing", "example/gravel", <minecraft:gravel>,
    [<minecraft:sand>, <minecraft:flint>], [1.0, 0.25], 200);
Recipes.add("milling", "example/milling", <minecraft:dye:15>, <minecraft:bone>);
Recipes.add("cutting", "example/cutting", <minecraft:stick> * 4, <minecraft:planks>);
Recipes.add("sandpaper_polishing", "example/polishing", <minecraft:diamond>, <minecraft:coal>);
Recipes.add("splashing", "example/washing", <minecraft:flint>, <minecraft:gravel>);
Recipes.add("haunting", "example/haunting", <minecraft:soul_sand>, <minecraft:sand>);
Recipes.add("smoking", "example/smoking", <minecraft:baked_potato>, <minecraft:carrot>);
Recipes.add("blasting", "example/blasting", <minecraft:gold_ingot>, <minecraft:iron_ingot>);
Recipes.addDeploying("example/deployer", <minecraft:gold_ingot>, <minecraft:iron_ingot>, <minecraft:redstone>, false);
Recipes.addApplication("item_application", "example/casing", <cogworkbridge:andesite_casing>, <ore:stripped_logs>, <cogworkbridge:andesite_alloy>, true);
Recipes.addFilling("example/wet_sponge", <minecraft:sponge:1>, <minecraft:sponge>, <liquid:water> * 500);
Recipes.addEmptying("example/sponge_drain", <minecraft:sponge>, <liquid:water> * 500, <minecraft:sponge:1>);
Recipes.addBasin("mixing", "example/mixing", [<ore:ingotIron> * 2, <minecraft:redstone>],
    [<liquid:water> * 250], <minecraft:gold_ingot> * 2, null, 1);
Recipes.addBasin("compacting", "example/compacting", [<minecraft:coal> * 4], [], <minecraft:diamond>, null, 0);
Recipes.addMechanical("example/mechanical", <minecraft:diamond>, [
    [<minecraft:iron_ingot>, null, null, <minecraft:iron_ingot>],
    [null, <minecraft:gold_ingot>, <minecraft:gold_ingot>, null]
], true);
Assembly.add("example/assembly", <minecraft:iron_ingot>, <cogworkbridge:incomplete_precision_mechanism>, [
    AssemblyStep.deploy(<minecraft:redstone>, false),
    AssemblyStep.fill(<liquid:water> * 250),
    AssemblyStep.press()
], 2, [<minecraft:diamond>, <minecraft:flint>], [9.0, 1.0]);

// Other removal methods (uncomment as needed):
// Ore-dictionary outputs select one concrete member once; JEI and processing use that same member.
// Recipes.remove("mixing", <ore:ingotBrass>);
// Recipes.addBasin("mixing", "alloys/brass", [<ore:ingotCopper>, <ore:ingotZinc>], [], <ore:ingotBrass> * 2, null, 1);
// Recipes.removeInput("cutting", <minecraft:log>);
// Recipes.removeWithHeld("deploying", <minecraft:iron_ingot>, <minecraft:redstone>);
// Recipes.removeFluidInput("mixing", <liquid:water>);
// Recipes.removeFluidOutput("emptying", <liquid:water>);
// Recipes.removeByName("assembly", "cogworkbridge:precision");
// Recipes.removeAll("smoking");
// Recipes.dump();
