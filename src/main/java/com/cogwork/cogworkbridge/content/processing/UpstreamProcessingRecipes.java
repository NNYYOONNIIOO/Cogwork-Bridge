package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.Reference;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockStone;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads recipes from the same user-supplied Create jar that provides the models. */
public final class UpstreamProcessingRecipes {
    public record Output(ItemStack stack, float chance) { }
    public record MixingRecipe(String id, List<List<ItemStack>> items, List<net.minecraftforge.fluids.FluidStack> fluids,
                               ItemStack itemOutput, net.minecraftforge.fluids.FluidStack fluidOutput, int heat) { }
    public record MechanicalRecipe(String id, int width, int height, List<List<ItemStack>> ingredients,
                                   ItemStack result, boolean mirrored) {
        public boolean matches(net.minecraft.inventory.InventoryCrafting inventory) {
            for (int y = 0; y <= inventory.getHeight() - height; y++)
                for (int x = 0; x <= inventory.getWidth() - width; x++)
                    if (matchesAt(inventory, x, y, false) || mirrored && matchesAt(inventory, x, y, true)) return true;
            return false;
        }
        private boolean matchesAt(net.minecraft.inventory.InventoryCrafting inventory, int left, int top, boolean flip) {
            for (int y = 0; y < inventory.getHeight(); y++) for (int x = 0; x < inventory.getWidth(); x++) {
                int localX = x - left, localY = y - top;
                List<ItemStack> options = localX >= 0 && localX < width && localY >= 0 && localY < height
                        ? ingredients.get(localY * width + (flip ? width - 1 - localX : localX)) : Collections.emptyList();
                ItemStack actual = inventory.getStackInRowAndColumn(x, y);
                if (options.isEmpty()) { if (!actual.isEmpty()) return false; }
                else {
                    if (actual.isEmpty()) return false;
                    boolean found = false;
                    for (ItemStack option : options) if (OreDictionary.itemMatches(option, actual, false)) { found = true; break; }
                    if (!found) return false;
                }
            }
            return true;
        }
    }
    public record Recipe(String type, List<ItemStack> inputs, List<ItemStack> heldItems, List<Output> outputs,
                         int duration, boolean keepHeld, net.minecraftforge.fluids.FluidStack fluid) {
        public boolean matches(ItemStack input) {
            for (ItemStack option : inputs) if (OreDictionary.itemMatches(option, input, false)) return true;
            return false;
        }
        public List<ItemStack> roll(Random random) {
            List<ItemStack> result = new ArrayList<>();
            for (Output output : outputs) if (output.chance() >= 1F || random.nextFloat() < output.chance()) result.add(output.stack().copy());
            return result;
        }
        public boolean matchesHeld(ItemStack held) {
            for (ItemStack option : heldItems) if (OreDictionary.itemMatches(option, held, false)) return true;
            return false;
        }
    }
    private static final List<Recipe> recipes = new ArrayList<>();
    private static final List<MixingRecipe> mixingRecipes = new ArrayList<>();
    private static final List<MixingRecipe> compactingRecipes = new ArrayList<>();
    private static final List<MechanicalRecipe> mechanicalRecipes = new ArrayList<>();
    private static final Map<String, List<Recipe>> byType = new HashMap<>();
    private static boolean loaded;
    private UpstreamProcessingRecipes() { }
    public static synchronized List<Recipe> all(String type) {
        load();
        return byType.getOrDefault(type, Collections.emptyList());
    }
    public static synchronized List<MixingRecipe> mixing() {
        load();
        return Collections.unmodifiableList(mixingRecipes);
    }
    public static synchronized List<MixingRecipe> compacting() {
        load();
        return Collections.unmodifiableList(compactingRecipes);
    }
    public static synchronized List<MechanicalRecipe> mechanicalCrafting() {
        load();
        return Collections.unmodifiableList(mechanicalRecipes);
    }
    public static Recipe find(String type, ItemStack input) {
        if (input.isEmpty()) return null;
        for (Recipe recipe : all(type)) if (recipe.matches(input)) return recipe;
        return null;
    }
    public static Recipe find(String type, ItemStack input, ItemStack held) {
        if (input.isEmpty() || held.isEmpty()) return null;
        for (Recipe recipe : all(type)) if (recipe.matches(input) && recipe.matchesHeld(held)) return recipe;
        return null;
    }
    private static void load() {
        if (loaded) return;
        loaded = true;
        var targets = com.cogwork.cogworkbridge.core.CogworkBridgeCore.getTargets();
        if (targets.isEmpty() && com.cogwork.cogworkbridge.core.CogworkBridgeCore.getGameDirectory() != null)
            targets = com.cogwork.cogworkbridge.core.TargetJarLocator.scan(com.cogwork.cogworkbridge.core.CogworkBridgeCore.getGameDirectory(), null);
        for (var source : targets) {
            try (JarFile jar = new JarFile(source.getFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String path = entry.getName();
                    String prefix = "data/create/recipe/";
                    if (!path.startsWith(prefix) || !path.endsWith(".json")) continue;
                    String remainder = path.substring(prefix.length());
                    int slash = remainder.indexOf('/');
                    if (slash < 0 || !supported(remainder.substring(0, slash))) continue;
                    try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                        accept(remainder, new JsonParser().parse(reader).getAsJsonObject());
                    } catch (RuntimeException ignored) { }
                }
            } catch (Exception ignored) { }
            if (!recipes.isEmpty() || !mixingRecipes.isEmpty() || !compactingRecipes.isEmpty()) break;
        }
        if (recipes.isEmpty() && mixingRecipes.isEmpty() && compactingRecipes.isEmpty()) {
            try (var stream = UpstreamProcessingRecipes.class.getResourceAsStream("/data/cogworkbridge/upstream_processing.json")) {
                if (stream != null) try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    for (var entry : new JsonParser().parse(reader).getAsJsonObject().entrySet())
                        try { accept(entry.getKey(), entry.getValue().getAsJsonObject()); } catch (RuntimeException ignored) { }
                }
            } catch (Exception ignored) { }
        }
        if (mechanicalRecipes.isEmpty()) {
            try (var stream = UpstreamProcessingRecipes.class.getResourceAsStream("/data/cogworkbridge/upstream_mechanical_crafting.json")) {
                if (stream != null) try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    for (var entry : new JsonParser().parse(reader).getAsJsonObject().entrySet())
                        try { accept("mechanical_crafting/" + entry.getKey() + ".json", entry.getValue().getAsJsonObject()); } catch (RuntimeException ignored) { }
                }
            } catch (Exception ignored) { }
        }
        CompatCrushedOres.addRecipes(recipes);
        for (Recipe recipe : recipes) byType.computeIfAbsent(recipe.type(), ignored -> new ArrayList<>()).add(recipe);
        for (var entry : byType.entrySet()) entry.setValue(Collections.unmodifiableList(entry.getValue()));
        org.apache.logging.log4j.LogManager.getLogger("CogworkBridge").info("Loaded {} compatible upstream processing recipes", recipes.size());
    }
    private static void accept(String remainder, JsonObject json) {
        int slash = remainder.indexOf('/');
        if (slash < 0 || !remainder.endsWith(".json")) return;
        String type = remainder.substring(0, slash);
        if (!supported(type)) return;
        if (!conditions(json.getAsJsonArray("neoforge:conditions"))) return;
        if (type.equals("mechanical_crafting")) { parseMechanical(remainder, json); return; }
        JsonArray ingredients = json.getAsJsonArray("ingredients"), outputData = json.getAsJsonArray("results");
        if (type.equals("mixing") || type.equals("compacting")) {
            parseBasin(type.equals("mixing") ? mixingRecipes : compactingRecipes, remainder, json, ingredients, outputData); return;
        }
        boolean itemApplication = type.equals("deploying") || type.equals("item_application");
        if (ingredients == null || ingredients.size() != (itemApplication || type.equals("filling") ? 2 : 1) || outputData == null) return;
        List<ItemStack> inputs = ingredient(ingredients.get(0));
        List<ItemStack> held = itemApplication ? ingredient(ingredients.get(1)) : Collections.emptyList();
        net.minecraftforge.fluids.FluidStack fluid = type.equals("filling") ? fluid(ingredients.get(1)) : null;
        List<Output> outputs = new ArrayList<>();
        for (JsonElement item : outputData) {
            JsonObject data = item.getAsJsonObject();
            ItemStack stack = resolve(data.get("id").getAsString());
            if (stack.isEmpty()) return;
            stack.setCount(data.has("count") ? data.get("count").getAsInt() : 1);
            outputs.add(new Output(stack, data.has("chance") ? data.get("chance").getAsFloat() : 1F));
        }
        if (!inputs.isEmpty() && !outputs.isEmpty() && (!itemApplication || !held.isEmpty()) && (!type.equals("filling") || fluid != null))
            recipes.add(new Recipe(type, inputs, held, outputs,
                    json.has("processing_time") ? Math.max(1, json.get("processing_time").getAsInt()) : 100,
                    json.has("keep_held_item") && json.get("keep_held_item").getAsBoolean(), fluid));
    }
    private static boolean supported(String type) {
        return type.equals("pressing") || type.equals("milling") || type.equals("crushing") || type.equals("cutting")
                || type.equals("splashing") || type.equals("haunting") || type.equals("deploying") || type.equals("filling")
                || type.equals("mixing") || type.equals("compacting") || type.equals("mechanical_crafting")
                || type.equals("sandpaper_polishing") || type.equals("item_application");
    }
    private static void parseMechanical(String id, JsonObject json) {
        JsonArray pattern = json.getAsJsonArray("pattern");
        JsonObject key = json.getAsJsonObject("key"), result = json.getAsJsonObject("result");
        if (pattern == null || pattern.size() == 0 || key == null || result == null || !result.has("id")) return;
        int width = pattern.get(0).getAsString().length(), height = pattern.size();
        if (width == 0 || width > 32 || height > 32) return;
        ItemStack output = resolve(result.get("id").getAsString());
        if (output.isEmpty()) return;
        output.setCount(result.has("count") ? result.get("count").getAsInt() : 1);
        if (output.isEmpty() || output.getCount() > output.getMaxStackSize()) return;
        Map<Character, List<ItemStack>> choices = new HashMap<>();
        for (var entry : key.entrySet()) {
            if (entry.getKey().length() != 1 || entry.getKey().charAt(0) == ' ') return;
            List<ItemStack> options = ingredient(entry.getValue());
            if (options.isEmpty()) return;
            choices.put(entry.getKey().charAt(0), options);
        }
        List<List<ItemStack>> slots = new ArrayList<>();
        for (JsonElement rowValue : pattern) {
            String row = rowValue.getAsString();
            if (row.length() != width) return;
            for (int x = 0; x < width; x++) {
                char symbol = row.charAt(x);
                if (symbol == ' ') slots.add(Collections.emptyList());
                else if (choices.containsKey(symbol)) slots.add(choices.get(symbol));
                else return;
            }
        }
        mechanicalRecipes.add(new MechanicalRecipe(id, width, height, slots, output,
                json.has("accept_mirrored") && json.get("accept_mirrored").getAsBoolean()));
    }
    private static void parseBasin(List<MixingRecipe> destination, String id, JsonObject json, JsonArray ingredients, JsonArray outputs) {
        if (ingredients == null || outputs == null || outputs.size() != 1) return;
        List<List<ItemStack>> items = new ArrayList<>();
        List<net.minecraftforge.fluids.FluidStack> fluids = new ArrayList<>();
        for (JsonElement element : ingredients) {
            JsonObject obj = element.isJsonObject() ? element.getAsJsonObject() : null;
            if (obj != null && obj.has("amount") && obj.has("type") && obj.get("type").getAsString().startsWith("neoforge:")) {
                net.minecraftforge.fluids.FluidStack fluid = fluid(element);
                if (fluid == null) return;
                boolean merged = false;
                for (net.minecraftforge.fluids.FluidStack previous : fluids) if (previous.isFluidEqual(fluid)) {
                    previous.amount += fluid.amount; merged = true; break;
                }
                if (!merged) fluids.add(fluid);
            } else {
                List<ItemStack> choices = ingredient(element);
                if (choices.isEmpty()) return;
                items.add(choices);
            }
        }
        JsonObject result = outputs.get(0).getAsJsonObject();
        if (!result.has("id") || result.has("chance")) return;
        ItemStack itemOutput = ItemStack.EMPTY;
        net.minecraftforge.fluids.FluidStack fluidOutput = null;
        if (result.has("amount")) {
            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("fluid", result.get("id").getAsString());
            wrapper.add("amount", result.get("amount"));
            fluidOutput = fluid(wrapper);
            if (fluidOutput == null) return;
        } else {
            itemOutput = resolve(result.get("id").getAsString());
            if (itemOutput.isEmpty()) return;
            itemOutput.setCount(result.has("count") ? result.get("count").getAsInt() : 1);
        }
        int heat = json.has("heat_requirement") ? switch (json.get("heat_requirement").getAsString()) {
            case "heated" -> 1; case "superheated" -> 2; default -> 0;
        } : 0;
        destination.add(new MixingRecipe(id, items, fluids, itemOutput, fluidOutput, heat));
    }
    private static boolean conditions(JsonArray conditions) {
        if (conditions == null) return true;
        for (JsonElement element : conditions) if (!condition(element.getAsJsonObject())) return false;
        return true;
    }
    private static net.minecraftforge.fluids.FluidStack fluid(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject value = element.getAsJsonObject();
        if (!value.has("amount")) return null;
        int amount = value.get("amount").getAsInt();
        if (amount <= 0) return null;
        if (value.has("components")) {
            if (!value.has("fluids") || !value.get("fluids").getAsString().equals("create:potion")) return null;
            JsonObject components = value.getAsJsonObject("components");
            if (!components.has("minecraft:potion_contents")) return null;
            JsonObject contents = components.getAsJsonObject("minecraft:potion_contents");
            if (!contents.has("potion") || contents.size() != 1) return null;
            String bottle = components.has("create:potion_fluid_bottle_type")
                    ? components.get("create:potion_fluid_bottle_type").getAsString() : "regular";
            Item bottleItem = bottle.equals("splash") ? Items.SPLASH_POTION : bottle.equals("lingering") ? Items.LINGERING_POTION
                    : bottle.equals("regular") ? Items.POTIONITEM : null;
            if (bottleItem == null) return null;
            net.minecraft.potion.PotionType potion = net.minecraft.potion.PotionType.getPotionTypeForName(contents.get("potion").getAsString());
            if (potion == null) return null;
            ItemStack stack = net.minecraft.potion.PotionUtils.addPotionToItemStack(new ItemStack(bottleItem), potion);
            return com.cogwork.cogworkbridge.content.fluids.PotionFluid.fromPotion(stack, amount);
        }
        String id = value.has("fluid") ? value.get("fluid").getAsString()
                : value.has("tag") ? value.get("tag").getAsString() : "";
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry.getFluid(name);
        return fluid == null ? null : new net.minecraftforge.fluids.FluidStack(fluid, amount);
    }
    private static boolean condition(JsonObject condition) {
        String type = condition.get("type").getAsString();
        if (type.endsWith(":not")) return !condition(condition.getAsJsonObject("value"));
        if (type.endsWith(":mod_loaded")) return Loader.isModLoaded(condition.get("modid").getAsString());
        if (type.endsWith(":tag_empty")) return ingredient(condition).isEmpty();
        return false;
    }
    private static List<ItemStack> ingredient(JsonElement element) {
        List<ItemStack> result = new ArrayList<>();
        if (element.isJsonArray()) { for (JsonElement child : element.getAsJsonArray()) result.addAll(ingredient(child)); return result; }
        JsonObject json = element.getAsJsonObject();
        if (json.has("item")) {
            ItemStack item = resolve(json.get("item").getAsString());
            if (!item.isEmpty()) result.add(item);
        } else if (json.has("tag")) {
            String tag = json.get("tag").getAsString();
            if (tag.startsWith("create:stone_types/")) {
                addStoneTypes(result, tag.substring("create:stone_types/".length()));
                return result;
            }
            String specialOre = switch (tag) {
                case "c:stones" -> "stone";
                case "c:rods/wooden" -> "stickWood";
                case "c:ender_pearls" -> "enderpearl";
                case "c:glass_blocks" -> "blockGlass";
                case "c:obsidians" -> "obsidian";
                default -> null;
            };
            if (specialOre != null) {
                for (ItemStack stack : OreDictionary.getOres(specialOre)) result.add(stack.copy());
                ItemStack vanilla = switch (tag) {
                    case "c:stones" -> new ItemStack(Blocks.STONE);
                    case "c:rods/wooden" -> new ItemStack(Items.STICK);
                    case "c:ender_pearls" -> new ItemStack(Items.ENDER_PEARL);
                    case "c:glass_blocks" -> new ItemStack(Blocks.GLASS);
                    default -> new ItemStack(Blocks.OBSIDIAN);
                };
                result.add(vanilla);
                return result;
            }
            if (json.get("tag").getAsString().equals("c:eggs")) { result.add(new ItemStack(Items.EGG)); return result; }
            if (tag.equals("c:stripped_logs") || tag.equals("c:stripped_woods")) {
                for (ItemStack stack : OreDictionary.getOres("stripped_logs")) result.add(stack.copy());
                return result;
            }
            if (tag.equals("c:strings")) { result.add(new ItemStack(Items.STRING)); return result; }
            if (json.get("tag").getAsString().equals("create:pulpifiable")) {
                result.add(new ItemStack(Items.REEDS));
                for (ItemStack sapling : OreDictionary.getOres("treeSapling")) result.add(sapling.copy());
                ResourceLocation bamboo = new ResourceLocation("farmers_future_delight", "bamboo");
                if (ForgeRegistries.ITEMS.containsKey(bamboo)) result.add(new ItemStack(ForgeRegistries.ITEMS.getValue(bamboo)));
                return result;
            }
            if (json.get("tag").getAsString().equals("minecraft:axes")) {
                for (Item item : ForgeRegistries.ITEMS) if (item instanceof net.minecraft.item.ItemAxe) result.add(new ItemStack(item));
                return result;
            }
            String ore = oreName(json.get("tag").getAsString());
            if (ore != null) for (ItemStack stack : OreDictionary.getOres(ore)) result.add(stack.copy());
        }
        return result;
    }
    private static void addStoneTypes(List<ItemStack> result, String stone) {
        boolean known = false;
        for (String candidate : com.cogwork.cogworkbridge.registry.BuildingPalette.STONES)
            if (candidate.equals(stone)) { known = true; break; }
        if (!known) return;
        String[] blocks = {"cut_" + stone, "polished_cut_" + stone, "cut_" + stone + "_bricks",
                "small_" + stone + "_bricks", "layered_" + stone, stone + "_pillar"};
        String[] parts = {"cut_" + stone, "polished_cut_" + stone, "cut_" + stone + "_brick", "small_" + stone + "_brick"};
        for (String block : blocks) addAvailable(result, "create:" + block);
        for (String part : parts) {
            addAvailable(result, "create:" + part + "_stairs");
            addAvailable(result, "create:" + part + "_wall");
        }
        ItemStack natural = resolve("create:" + stone);
        if (natural.isEmpty()) natural = resolve("minecraft:" + (stone.equals("dripstone") ? "dripstone_block" : stone));
        if (!natural.isEmpty()) result.add(natural);
    }
    private static void addAvailable(List<ItemStack> result, String id) {
        ItemStack stack = resolve(id);
        if (!stack.isEmpty()) result.add(stack);
    }
    private static String oreName(String tag) {
        if (tag.equals("minecraft:planks")) return "plankWood";
        if (tag.equals("minecraft:logs")) return "logWood";
        if (tag.equals("minecraft:leaves")) return "treeLeaves";
        if (tag.equals("c:cobblestones")) return "cobblestone";
        if (!tag.startsWith("c:")) return null;
        String[] bits = tag.substring(2).split("/");
        if (bits.length != 2) return null;
        String prefix = switch (bits[0]) {
            case "ingots" -> "ingot"; case "nuggets" -> "nugget"; case "ores" -> "ore";
            case "raw_materials" -> "raw"; case "plates" -> "plate"; case "storage_blocks" -> "block";
            case "gems" -> "gem"; case "dusts" -> "dust"; case "flours" -> "flour"; default -> "";
        };
        if (prefix.isEmpty()) return null;
        StringBuilder name = new StringBuilder(prefix);
        for (String part : bits[1].split("_")) if (!part.isEmpty()) name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        return name.toString();
    }
    public static ItemStack resolve(String id) {
        if (id.equals("minecraft:andesite")) return new ItemStack(Blocks.STONE, 1, BlockStone.EnumType.ANDESITE.getMetadata());
        if (id.equals("minecraft:granite")) return new ItemStack(Blocks.STONE, 1, BlockStone.EnumType.GRANITE.getMetadata());
        if (id.equals("minecraft:diorite")) return new ItemStack(Blocks.STONE, 1, BlockStone.EnumType.DIORITE.getMetadata());
        if (id.equals("minecraft:red_sand")) return new ItemStack(Blocks.SAND, 1, 1);
        if (id.equals("minecraft:white_wool")) return new ItemStack(Blocks.WOOL);
        if (id.equals("minecraft:sugar_cane")) return new ItemStack(Items.REEDS);
        if (id.equals("minecraft:grass_block")) return new ItemStack(Blocks.GRASS);
        if (id.equals("minecraft:dirt_path")) return new ItemStack(Blocks.GRASS_PATH);
        if (id.equals("minecraft:coarse_dirt")) return new ItemStack(Blocks.DIRT, 1, 1);
        if (id.equals("minecraft:podzol")) return new ItemStack(Blocks.DIRT, 1, 2);
        if (id.equals("minecraft:terracotta")) return new ItemStack(Blocks.HARDENED_CLAY);
        if (id.equals("minecraft:snow_block")) return new ItemStack(Blocks.SNOW);
        if (id.equals("minecraft:nether_quartz_ore")) return new ItemStack(Blocks.QUARTZ_ORE);
        if (id.equals("minecraft:short_grass")) return new ItemStack(Blocks.TALLGRASS, 1, 1);
        if (id.equals("minecraft:fern")) return new ItemStack(Blocks.TALLGRASS, 1, 2);
        if (id.equals("minecraft:dandelion")) return new ItemStack(Blocks.YELLOW_FLOWER);
        String[] flowers = {"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"};
        for (int i = 0; i < flowers.length; i++) if (id.equals("minecraft:" + flowers[i])) return new ItemStack(Blocks.RED_FLOWER, 1, i);
        String[] tallFlowers = {"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"};
        for (int i = 0; i < tallFlowers.length; i++) if (id.equals("minecraft:" + tallFlowers[i])) return new ItemStack(Blocks.DOUBLE_PLANT, 1, i);
        String[] dyes = {"black", "red", "green", "brown", "blue", "purple", "cyan", "light_gray", "gray", "pink", "lime", "yellow", "light_blue", "magenta", "orange", "white"};
        for (int i = 0; i < dyes.length; i++) if (id.equals("minecraft:" + dyes[i] + "_dye")) return new ItemStack(Items.DYE, 1, i);
        if (id.equals("minecraft:ink_sac")) return new ItemStack(Items.DYE, 1, 0);
        if (id.equals("minecraft:cocoa_beans")) return new ItemStack(Items.DYE, 1, 3);
        if (id.equals("minecraft:lapis_lazuli")) return new ItemStack(Items.DYE, 1, 4);
        if (id.equals("minecraft:bone_meal")) return new ItemStack(Items.DYE, 1, 15);
        if (id.equals("minecraft:charcoal")) return new ItemStack(Items.COAL, 1, 1);
        if (id.startsWith("create:")) id = Reference.MOD_ID + id.substring("create".length());
        ResourceLocation key = new ResourceLocation(id);
        if (!ForgeRegistries.ITEMS.containsKey(key) && key.getNamespace().equals("minecraft")) {
            ResourceLocation ffd = new ResourceLocation("farmers_future_delight", key.getPath());
            if (ForgeRegistries.ITEMS.containsKey(ffd)) key = ffd;
        }
        Item item = ForgeRegistries.ITEMS.containsKey(key) ? ForgeRegistries.ITEMS.getValue(key) : null;
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
