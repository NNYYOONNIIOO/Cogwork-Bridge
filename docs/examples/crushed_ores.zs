// Requires the tungsten definition from crushed_ores.cfg and populated ore/nugget dictionaries.
// Copy to scripts/crushed_ores.zs and restart. This example is not installed automatically.
import mods.cogworkbridge.Recipes;

Recipes.removeInput("crushing", <ore:oreTungsten>);
Recipes.addRecipe("crushing", "compat/tungsten", <ore:oreTungsten>,
    [<cogworkbridge:crushed_raw_tungsten> * 2], [1.0], 400);

Recipes.removeInput("splashing", <cogworkbridge:crushed_raw_tungsten>);
Recipes.addRecipe("splashing", "compat/tungsten_washing",
    <cogworkbridge:crushed_raw_tungsten>, [<ore:nuggetTungsten> * 9], [1.0], 100);
