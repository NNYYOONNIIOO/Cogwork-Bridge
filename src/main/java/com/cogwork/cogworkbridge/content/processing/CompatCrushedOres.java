package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Upstream compatible crushed metals, using the installed mods' ore dictionary. */
public final class CompatCrushedOres {
    public static final String[] NAMES = {"osmium", "platinum", "silver", "tin", "lead", "quicksilver", "aluminum", "uranium", "nickel"};
    private static final String[][] ALIASES = {{"Osmium"}, {"Platinum"}, {"Silver"}, {"Tin"}, {"Lead"},
            {"Quicksilver", "Mercury"}, {"Aluminum", "Aluminium"}, {"Uranium"}, {"Nickel"}};
    private CompatCrushedOres() { }

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
        List<ItemStack> result = new ArrayList<>();
        for (String alias : ALIASES[metal]) for (ItemStack stack : OreDictionary.getOres(prefix + alias, false)) {
            if (stack.isEmpty()) continue;
            boolean present = false;
            for (ItemStack previous : result) if (OreDictionary.itemMatches(previous, stack, false)) { present = true; break; }
            if (!present) result.add(stack.copy());
        }
        return result;
    }

    /** Called once after ordinary upstream data has been parsed. */
    public static void addRecipes(List<UpstreamProcessingRecipes.Recipe> recipes) {
        for (int i = 0; i < NAMES.length; i++) {
            Item crushed = AllItems.COMPAT_CRUSHED_ORES[i];
            addCrushing(recipes, collect(i, "ore"), crushed, 0);
            addCrushing(recipes, collect(i, "raw"), crushed, 1);
            addCrushing(recipes, collect(i, "blockRaw"), crushed, 2);
            List<ItemStack> nuggets = collect(i, "nugget");
            if (!nuggets.isEmpty() && !hasRecipe(recipes, "splashing", new ItemStack(crushed))) {
                ItemStack output = nuggets.get(0).copy(); output.setCount(9);
                recipes.add(new UpstreamProcessingRecipes.Recipe("splashing", Collections.singletonList(new ItemStack(crushed)),
                        Collections.emptyList(), Collections.singletonList(new UpstreamProcessingRecipes.Output(output, 1F)), 100, false, null));
            }
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
        for (int i = 0; i < NAMES.length; i++) {
            List<ItemStack> ingots = collect(i, "ingot");
            if (ingots.isEmpty()) continue;
            ItemStack output = ingots.get(0).copy(); output.setCount(1);
            GameRegistry.addSmelting(AllItems.COMPAT_CRUSHED_ORES[i], output.copy(), .1F);
            xy177.farmersfuturedelight.api.WorkstationRecipes.register(
                    xy177.farmersfuturedelight.api.WorkstationRecipes.Type.BLASTING,
                    new ResourceLocation(Reference.MOD_ID, "upstream/crushed_" + NAMES[i]),
                    Collections.singletonList(net.minecraft.item.crafting.Ingredient.fromItem(AllItems.COMPAT_CRUSHED_ORES[i])),
                    output, 100, .1F);
        }
    }
}
