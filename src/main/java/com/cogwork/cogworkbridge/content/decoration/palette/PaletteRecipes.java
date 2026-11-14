package com.cogwork.cogworkbridge.content.decoration.palette;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.BlockFamily;
import com.cogwork.cogworkbridge.registry.BuildingPalette;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreIngredient;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import xy177.farmersfuturedelight.api.WorkstationRecipes;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class PaletteRecipes {
    private PaletteRecipes() { }

    /** Called from the vanilla furnace hook; other items keep their original cooking time. */
    public static int glassCookTime(int original, Object value) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return original;
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null || !Reference.MOD_ID.equals(id.getNamespace())) return original;
        for (String glass : BuildingPalette.GLASS)
            if (id.getPath().equals(glass) || id.getPath().equals(glass + "_pane")) return 50;
        return original;
    }

    @SubscribeEvent public static void crafting(RegistryEvent.Register<IRecipe> event) {
        for (String stone : BuildingPalette.STONES) {
            String[] full = {"cut_" + stone, "polished_cut_" + stone, "cut_" + stone + "_bricks", "small_" + stone + "_bricks"};
            String[] part = {"cut_" + stone, "polished_cut_" + stone, "cut_" + stone + "_brick", "small_" + stone + "_brick"};
            for (int i = 0; i < full.length; i++) addPartRecipes(event, full[i], part[i], true);
        }
        for (String stage : BuildingPalette.COPPER_STAGES) for (String roof : new String[]{"tile", "shingle"}) {
            String stem = stage + "copper_" + roof;
            addPartRecipes(event, stem + "s", stem, false);
            if (stage.startsWith("waxed_")) for (String suffix : new String[]{"s", "_stairs", "_slab"}) {
                String unwaxed = stem.substring("waxed_".length()) + suffix;
                shapeless(event, stem + suffix + "_waxing", new ItemStack(block(stem + suffix)), block(unwaxed), xy177.farmersfuturedelight.common.registry.FFDItems.HONEYCOMB);
            }
        }
        shaped(event, "andesite_alloy_block", new ItemStack(block("andesite_alloy_block")), "XXX", "XXX", "XXX", 'X', AllItems.ANDESITE_ALLOY);
        shaped(event, "experience_block", new ItemStack(block("experience_block")), "XXX", "XXX", "XXX", 'X', AllItems.EXPERIENCE_NUGGET);
        shapeless(event, "andesite_alloy_from_block", new ItemStack(AllItems.ANDESITE_ALLOY, 9), block("andesite_alloy_block"));
        shapeless(event, "experience_nugget_from_block", new ItemStack(AllItems.EXPERIENCE_NUGGET, 9), block("experience_block"));
        shapeless(event, "rose_quartz_tiles_from_small", new ItemStack(block("rose_quartz_tiles")), block("small_rose_quartz_tiles"));
        shapeless(event, "small_rose_quartz_tiles_from_regular", new ItemStack(block("small_rose_quartz_tiles")), block("rose_quartz_tiles"));
        shapeless(event, "bound_cardboard_block", new ItemStack(block("bound_cardboard_block")), com.cogwork.cogworkbridge.registry.AllBlocks.CARDBOARD_BLOCK, net.minecraft.init.Items.STRING);
        shapeless(event, "cardboard_from_bound_block", new ItemStack(AllItems.CARDBOARD, 4), block("bound_cardboard_block"));
        shaped(event, "wooden_bracket", new ItemStack(block("wooden_bracket"), 4), "SSS", "PCP", 'S', "stickWood", 'P', "plankWood", 'C', AllItems.ANDESITE_ALLOY);
        shaped(event, "metal_bracket", new ItemStack(block("metal_bracket"), 4), "SSS", "PCP", 'S', "nuggetIron", 'P', "ingotIron", 'C', AllItems.ANDESITE_ALLOY);
        shaped(event, "metal_girder", new ItemStack(block("metal_girder"), 8), "PPP", "CCC", 'P', AllItems.IRON_SHEET, 'C', AllItems.ANDESITE_ALLOY);
        Ingredient woodenDoors = Ingredient.fromItems(net.minecraft.init.Items.OAK_DOOR, net.minecraft.init.Items.SPRUCE_DOOR,
                net.minecraft.init.Items.BIRCH_DOOR, net.minecraft.init.Items.JUNGLE_DOOR, net.minecraft.init.Items.ACACIA_DOOR, net.minecraft.init.Items.DARK_OAK_DOOR);
        for (String material : new String[]{"andesite", "copper", "brass"})
            shapeless(event, material + "_door", new ItemStack(block(material + "_door")), woodenDoors, BlockFamily.get(material + "_casing"));
        shapeless(event, "framed_glass_door", new ItemStack(block("framed_glass_door")), woodenDoors, block("framed_glass"));
        shapeless(event, "framed_glass_trapdoor", new ItemStack(block("framed_glass_trapdoor")), Blocks.TRAPDOOR, block("framed_glass"));
        shapeless(event, "train_door", new ItemStack(block("train_door")), woodenDoors, block("railway_casing"));
        shapeless(event, "train_trapdoor", new ItemStack(block("train_trapdoor")), Blocks.TRAPDOOR, block("railway_casing"));
        for (String name : BuildingPalette.GLASS)
            shaped(event, name + "_pane", new ItemStack(block(name + "_pane"), 16), "XXX", "XXX", 'X', block(name));
        for (int i = 0; i < BuildingPalette.WINDOWS.length; i++) {
            String name = BuildingPalette.WINDOWS[i], window = name + "_window";
            shaped(event, window + "_pane", new ItemStack(block(window + "_pane"), 16), "XXX", "XXX", 'X', block(window));
            Object frame = null;
            if (i < 6) frame = new ItemStack(Blocks.PLANKS, 1, i);
            else if (name.equals("ornate_iron")) frame = net.minecraft.init.Items.IRON_NUGGET;
            else if (name.equals("industrial_iron") || name.equals("weathered_iron")) frame = block(name + "_block");
            else {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmers_future_delight", name + "_planks"));
                if (item != null && item != net.minecraft.init.Items.AIR) frame = item;
            }
            if (frame != null) shaped(event, window, new ItemStack(block(window), 2), " X ", "XGX", 'X', frame, 'G', "blockGlassColorless");
        }
    }

    private static void addPartRecipes(RegistryEvent.Register<IRecipe> event, String fullName, String partName, boolean wall) {
        Block full = block(fullName), slab = block(partName + "_slab");
        shaped(event, partName + "_stairs", new ItemStack(block(partName + "_stairs"), 4), "X  ", "XX ", "XXX", 'X', full);
        shaped(event, partName + "_slab", new ItemStack(slab, 6), "XXX", 'X', full);
        shapeless(event, partName + "_slab_recycling", new ItemStack(full), slab, slab);
        if (wall) shaped(event, partName + "_wall", new ItemStack(block(partName + "_wall"), 6), "XXX", "XXX", 'X', full);
    }

    private static void shaped(RegistryEvent.Register<IRecipe> event, String name, ItemStack output, Object... input) {
        IRecipe recipe = new ShapedOreRecipe(null, output, input).setRegistryName(Reference.MOD_ID, "palette/" + name);
        event.getRegistry().register(recipe);
    }
    private static void shapeless(RegistryEvent.Register<IRecipe> event, String name, ItemStack output, Object... input) {
        IRecipe recipe = new ShapelessOreRecipe(null, output, input).setRegistryName(Reference.MOD_ID, "palette/" + name);
        event.getRegistry().register(recipe);
    }

    public static void registerStonecutting() {
        for (String stone : BuildingPalette.STONES) {
            List<ItemStack> sources = new ArrayList<>();
            ItemStack natural = naturalStone(stone);
            if (!natural.isEmpty()) sources.add(natural);
            String[] full = {"cut_" + stone, "polished_cut_" + stone, "cut_" + stone + "_bricks", "small_" + stone + "_bricks", "layered_" + stone, stone + "_pillar"};
            String[] part = {"cut_" + stone, "polished_cut_" + stone, "cut_" + stone + "_brick", "small_" + stone + "_brick"};
            for (String name : full) sources.add(new ItemStack(block(name)));
            for (String name : part) {
                sources.add(new ItemStack(block(name + "_stairs")));
                sources.add(new ItemStack(block(name + "_wall")));
            }
            Ingredient input = Ingredient.fromStacks(sources.toArray(new ItemStack[0]));
            for (String name : full) stonecut(name, input, new ItemStack(block(name)));
            for (String name : part) {
                stonecut(name + "_stairs", input, new ItemStack(block(name + "_stairs")));
                stonecut(name + "_slab", input, new ItemStack(block(name + "_slab"), 2));
                stonecut(name + "_wall", input, new ItemStack(block(name + "_wall")));
            }
        }
        stonecut("industrial_iron_block", new OreIngredient("ingotIron"), new ItemStack(block("industrial_iron_block"), 2));
        stonecut("weathered_iron_block", new OreIngredient("ingotIron"), new ItemStack(block("weathered_iron_block"), 2));
        stonecut("rose_quartz_block", Ingredient.fromItem(AllItems.ROSE_QUARTZ), new ItemStack(block("rose_quartz_block"), 2));
        stonecut("rose_quartz_tiles", Ingredient.fromItem(AllItems.POLISHED_ROSE_QUARTZ), new ItemStack(block("rose_quartz_tiles"), 2));
        stonecut("small_rose_quartz_tiles", Ingredient.fromItem(AllItems.POLISHED_ROSE_QUARTZ), new ItemStack(block("small_rose_quartz_tiles"), 2));
        stonecut("copycat_panel", new OreIngredient("ingotZinc"), new ItemStack(block("copycat_panel"), 4));
        stonecut("copycat_step", new OreIngredient("ingotZinc"), new ItemStack(block("copycat_step"), 4));
        for (String name : BuildingPalette.GLASS) {
            stonecut(name, new OreIngredient("blockGlassColorless"), new ItemStack(block(name)));
            stonecut(name + "_pane", new OreIngredient("paneGlassColorless"), new ItemStack(block(name + "_pane")));
        }
        for (String metal : new String[]{"andesite", "brass", "copper"}) {
            Ingredient source = metal.equals("andesite") ? Ingredient.fromItem(AllItems.ANDESITE_ALLOY) : new OreIngredient("ingot" + Character.toUpperCase(metal.charAt(0)) + metal.substring(1));
            stonecut(metal + "_ladder", source, new ItemStack(block(metal + "_ladder"), 2));
            stonecut(metal + "_bars", source, new ItemStack(block(metal + "_bars"), 4));
        }
        for (String stage : BuildingPalette.COPPER_STAGES) for (String roof : new String[]{"tile", "shingle"}) {
            String stem = stage + "copper_" + roof;
            if (stage.isEmpty()) stonecut(stem + "s_from_ingot", new OreIngredient("ingotCopper"), new ItemStack(block(stem + "s"), 2));
            Ingredient full = Ingredient.fromItem(Item.getItemFromBlock(block(stem + "s")));
            stonecut(stem + "_stairs", full, new ItemStack(block(stem + "_stairs")));
            stonecut(stem + "_slab", full, new ItemStack(block(stem + "_slab"), 2));
        }
    }

    private static void stonecut(String name, Ingredient input, ItemStack output) {
        WorkstationRecipes.register(WorkstationRecipes.Type.STONECUTTING, new ResourceLocation(Reference.MOD_ID, "palette/" + name),
                Arrays.asList(input), output, 1, 0F);
    }
    private static Block block(String name) { return BlockFamily.get(name); }
    private static ItemStack naturalStone(String name) {
        switch (name) {
            case "granite": return new ItemStack(Blocks.STONE, 1, 1);
            case "diorite": return new ItemStack(Blocks.STONE, 1, 3);
            case "andesite": return new ItemStack(Blocks.STONE, 1, 5);
            default:
                Block local = block(name);
                if (local != null) return new ItemStack(local);
                Item external = ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmers_future_delight", "dripstone".equals(name) ? "dripstone_block" : name));
                return external == null || external == net.minecraft.init.Items.AIR ? ItemStack.EMPTY : new ItemStack(external);
        }
    }
}
