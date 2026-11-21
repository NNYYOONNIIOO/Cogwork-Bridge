package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;

/** Upstream compatible crushed metals, using the installed mods' ore dictionary. */
public final class CompatCrushedOres {
    public static final String[] NAMES = {"osmium", "platinum", "silver", "tin", "lead", "quicksilver", "aluminum", "uranium", "nickel"};
    private static final String[][] ALIASES = {{"Osmium"}, {"Platinum"}, {"Silver"}, {"Tin"}, {"Lead"},
            {"Quicksilver", "Mercury"}, {"Aluminum", "Aluminium"}, {"Uranium"}, {"Nickel"}};
    private static final List<ConfiguredItem> configuredItems = new ArrayList<>();
    private static boolean autoRecipes;
    private CompatCrushedOres() { }

    /** Read definitions during the item registry event, before the registry is frozen or CRT runs. */
    public static void registerConfiguredItems(IForgeRegistry<Item> registry) {
        Configuration config = new Configuration(new File(Loader.instance().getConfigDir(), Reference.MOD_ID + "/crushed_ores.cfg"));
        config.load();
        String[] materials = config.getStringList("materials", "general", new String[0],
                "One material per line: id|OreSuffix[,Alias...]|RRGGBB|Display name. "
                + "Example: tungsten|Tungsten,Wolfram|A8A8B0|Crushed Raw Tungsten. "
                + "Creates cogworkbridge:crushed_raw_<id>. Built-in ids cannot be replaced. "
                + "Restart required; use the same definitions on the client and server.");
        autoRecipes = config.getBoolean("autoRecipes", "general", true,
                "Automatically add crushing, washing, furnace and FFD blasting recipes for configured materials using ore/raw/blockRaw/nugget/ingot + each suffix. "
                + "Existing processing recipes take priority. False creates only items and ore dictionary entries; use CRT for recipes. Restart required.");
        if (config.hasChanged()) config.save();
        for (String definition : materials) {
            String[] fields = definition.split("\\|", -1);
            if (fields.length != 4) {
                CogworkBridge.LOGGER.warn("Invalid crushed ore definition (expected id|aliases|RRGGBB|display name): {}", definition);
                continue;
            }
            String name = fields[0].trim();
            String[] aliases = Arrays.stream(fields[1].split(",", -1)).map(String::trim).toArray(String[]::new);
            String color = fields[2].trim();
            if (!name.matches("[a-z0-9_]+") || Arrays.stream(aliases).anyMatch(alias -> !alias.matches("[A-Za-z][A-Za-z0-9_]*"))
                    || !color.matches("[0-9A-Fa-f]{6}")) {
                CogworkBridge.LOGGER.warn("Invalid crushed ore id, ore suffix or RGB color: {}", definition);
                continue;
            }
            ResourceLocation id = new ResourceLocation(Reference.MOD_ID, "crushed_raw_" + name);
            if (registry.containsKey(id)) {
                CogworkBridge.LOGGER.warn("Duplicate or built-in crushed ore id: {}", id);
                continue;
            }
            ConfiguredItem item = new ConfiguredItem(aliases, Integer.parseInt(color, 16), fields[3].trim());
            item.setRegistryName(id);
            item.setTranslationKey(Reference.MOD_ID + ".crushed_raw_" + name);
            item.setCreativeTab(ModCreativeTab.INSTANCE);
            registry.register(item);
            configuredItems.add(item);
            for (String alias : aliases) {
                OreDictionary.registerOre("crushed" + alias, item);
                OreDictionary.registerOre("crushedRaw" + alias, item);
            }
        }
    }

    public static List<ConfiguredItem> configuredItems() {
        return Collections.unmodifiableList(configuredItems);
    }

    public static final class ConfiguredItem extends Item {
        private final String[] aliases;
        private final int color;
        private final String displayName;

        private ConfiguredItem(String[] aliases, int color, String displayName) {
            this.aliases = aliases;
            this.color = color;
            this.displayName = displayName.isEmpty() ? "Crushed Raw " + aliases[0] : displayName;
        }

        public int color() { return color; }

        @Override public String getItemStackDisplayName(ItemStack stack) {
            return net.minecraft.util.text.translation.I18n.canTranslate(getTranslationKey(stack) + ".name")
                    ? super.getItemStackDisplayName(stack) : displayName;
        }
    }

    public static Item[] createItems() {
        Item[] items = new Item[NAMES.length];
        for (int i = 0; i < items.length; i++) {
            Item item = new ConditionalItem(i);
            item.setRegistryName(Reference.MOD_ID, "crushed_raw_" + NAMES[i]);
            item.setTranslationKey(Reference.MOD_ID + ".crushed_raw_" + NAMES[i]);
            item.setCreativeTab(ModCreativeTab.INSTANCE);
            items[i] = item;
        }
        return items;
    }

    private static final class ConditionalItem extends Item {
        private final int metal;
        ConditionalItem(int metal) { this.metal = metal; }
        @Override public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
            if (isInCreativeTab(tab) && !collect(metal, "ore").isEmpty()) items.add(new ItemStack(this));
        }
    }

    private static List<ItemStack> collect(int metal, String prefix) {
        return collect(ALIASES[metal], prefix);
    }

    private static List<ItemStack> collect(String[] aliases, String prefix) {
        List<ItemStack> result = new ArrayList<>();
        for (String alias : aliases) for (ItemStack stack : OreDictionary.getOres(prefix + alias, false)) {
            if (stack.isEmpty()) continue;
            boolean present = false;
            for (ItemStack previous : result) if (OreDictionary.itemMatches(previous, stack, false)) { present = true; break; }
            if (!present) result.add(stack.copy());
        }
        return result;
    }

    /** Called once after ordinary upstream data has been parsed. */
    public static void addRecipes(List<UpstreamProcessingRecipes.Recipe> recipes) {
        for (int i = 0; i < NAMES.length; i++) addRecipes(recipes, AllItems.COMPAT_CRUSHED_ORES[i], ALIASES[i]);
        if (autoRecipes) for (ConfiguredItem item : configuredItems) addRecipes(recipes, item, item.aliases);
    }

    private static void addRecipes(List<UpstreamProcessingRecipes.Recipe> recipes, Item crushed, String[] aliases) {
        addCrushing(recipes, collect(aliases, "ore"), crushed, 0);
        addCrushing(recipes, collect(aliases, "raw"), crushed, 1);
        addCrushing(recipes, collect(aliases, "blockRaw"), crushed, 2);
        List<ItemStack> nuggets = collect(aliases, "nugget");
        nuggets.removeIf(stack -> stack.getMetadata() == OreDictionary.WILDCARD_VALUE);
        if (!nuggets.isEmpty() && !hasRecipe(recipes, "splashing", new ItemStack(crushed))) {
            ItemStack output = nuggets.get(0).copy(); output.setCount(9);
            recipes.add(new UpstreamProcessingRecipes.Recipe("splashing", Collections.singletonList(new ItemStack(crushed)),
                    Collections.emptyList(), Collections.singletonList(new UpstreamProcessingRecipes.Output(output, 1F)), 100, false, null));
        }
    }

    private static void addCrushing(List<UpstreamProcessingRecipes.Recipe> recipes, List<ItemStack> choices, Item crushed, int kind) {
        choices.removeIf(stack -> hasRecipe(recipes, "crushing", stack));
        if (choices.isEmpty()) return;
        List<UpstreamProcessingRecipes.Output> out = new ArrayList<>();
        out.add(new UpstreamProcessingRecipes.Output(new ItemStack(crushed, kind == 2 ? 9 : 1), 1F));
        if (kind == 0) out.add(new UpstreamProcessingRecipes.Output(new ItemStack(crushed), .75F));
        out.add(new UpstreamProcessingRecipes.Output(new ItemStack(AllItems.EXPERIENCE_NUGGET, kind == 2 ? 9 : 1), .75F));
        recipes.add(new UpstreamProcessingRecipes.Recipe("crushing", choices, Collections.emptyList(), out, 400, false, null));
    }

    private static boolean hasRecipe(List<UpstreamProcessingRecipes.Recipe> recipes, String type, ItemStack input) {
        for (UpstreamProcessingRecipes.Recipe recipe : recipes) if (recipe.type().equals(type) && recipe.matches(input)) return true;
        return false;
    }

    /** Resolve output from any installed mod, including alternative aluminum spellings. */
    public static void registerSmelting() {
        for (int i = 0; i < NAMES.length; i++) registerSmelting(AllItems.COMPAT_CRUSHED_ORES[i], ALIASES[i]);
        if (autoRecipes) for (ConfiguredItem item : configuredItems) registerSmelting(item, item.aliases);
    }

    private static void registerSmelting(Item crushed, String[] aliases) {
        List<ItemStack> ingots = collect(aliases, "ingot");
        ingots.removeIf(stack -> stack.getMetadata() == OreDictionary.WILDCARD_VALUE);
        if (ingots.isEmpty()) return;
        ItemStack output = ingots.get(0).copy(); output.setCount(1);
        GameRegistry.addSmelting(crushed, output.copy(), .1F);
        xy177.farmersfuturedelight.api.WorkstationRecipes.register(
                xy177.farmersfuturedelight.api.WorkstationRecipes.Type.BLASTING,
                new ResourceLocation(Reference.MOD_ID, "upstream/" + crushed.getRegistryName().getPath().replace("crushed_raw_", "crushed_")),
                Collections.singletonList(net.minecraft.item.crafting.Ingredient.fromItem(crushed)),
                output, 100, .1F);
    }
}
