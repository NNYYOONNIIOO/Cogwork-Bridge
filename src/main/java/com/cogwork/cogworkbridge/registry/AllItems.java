package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.contraptions.glue.SuperGlueItem;
import com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartContraptionItem;
import com.cogwork.cogworkbridge.content.kinetics.encased.WrenchItem;
import com.cogwork.cogworkbridge.content.fluids.ListFilterItem;
import com.cogwork.cogworkbridge.content.fluids.AttributeFilterItem;
import com.cogwork.cogworkbridge.content.logistics.belt.BeltConnectorItem;
import com.cogwork.cogworkbridge.content.equipment.ExtendoGripItem;
import com.cogwork.cogworkbridge.content.equipment.SandPaperItem;
import com.cogwork.cogworkbridge.content.equipment.GogglesItem;
import com.cogwork.cogworkbridge.content.redstone.LinkedControllerItem;
import com.cogwork.cogworkbridge.content.equipment.SymmetryWandItem;
import net.minecraft.item.Item;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class AllItems {

    public static final com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterItem PACKAGE_FILTER = register("package_filter", new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageFilterItem());
    public static final com.cogwork.cogworkbridge.content.logistics.table.ShoppingListItem SHOPPING_LIST = register("shopping_list", new com.cogwork.cogworkbridge.content.logistics.table.ShoppingListItem());
    public static final com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem[] CARDBOARD_PACKAGES = {
            register("cardboard_package_12x12", new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem(12, 12, false)),
            register("cardboard_package_10x12", new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem(10, 12, false)),
            register("cardboard_package_10x8", new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem(10, 8, false)),
            register("cardboard_package_12x10", new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem(12, 10, false))};
    public static final com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem[] RARE_PACKAGES = registerRarePackages();
    private static com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem[] registerRarePackages() {
        String[] names = {"creeper", "darcy", "evan", "jinx", "kryppers", "simi", "starlotte", "thunder", "up", "vector"};
        var items = new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem[names.length];
        for (int i = 0; i < names.length; i++) items[i] = register("rare_" + names[i] + "_package", new com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem(12, 10, true));
        return items;
    }
    public static final Item EMPTY_SCHEMATIC = register("empty_schematic", new Item().setMaxStackSize(1).setCreativeTab(ModCreativeTab.INSTANCE));
    public static final com.cogwork.cogworkbridge.content.schematics.SchematicQuillItem SCHEMATIC_AND_QUILL = register("schematic_and_quill", new com.cogwork.cogworkbridge.content.schematics.SchematicQuillItem());
    public static final com.cogwork.cogworkbridge.content.schematics.SchematicItem SCHEMATIC = register("schematic", new com.cogwork.cogworkbridge.content.schematics.SchematicItem());
    public static final SuperGlueItem SUPER_GLUE = register("super_glue", new SuperGlueItem());
    public static final com.cogwork.cogworkbridge.content.equipment.PotatoCannonItem POTATO_CANNON = register("potato_cannon", new com.cogwork.cogworkbridge.content.equipment.PotatoCannonItem());
    public static final com.cogwork.cogworkbridge.content.equipment.WorldshaperItem HANDHELD_WORLDSHAPER = register("handheld_worldshaper", new com.cogwork.cogworkbridge.content.equipment.WorldshaperItem());
    public static final com.cogwork.cogworkbridge.content.equipment.BlueprintItem CRAFTING_BLUEPRINT = register("crafting_blueprint", new com.cogwork.cogworkbridge.content.equipment.BlueprintItem());
    public static final com.cogwork.cogworkbridge.content.equipment.CardboardSwordItem CARDBOARD_SWORD = register("cardboard_sword", new com.cogwork.cogworkbridge.content.equipment.CardboardSwordItem());
    public static final Item CARDBOARD_HELMET = register("cardboard_helmet", new com.cogwork.cogworkbridge.content.equipment.CardboardArmorItem(net.minecraft.inventory.EntityEquipmentSlot.HEAD));
    public static final Item CARDBOARD_CHESTPLATE = register("cardboard_chestplate", new com.cogwork.cogworkbridge.content.equipment.CardboardArmorItem(net.minecraft.inventory.EntityEquipmentSlot.CHEST));
    public static final Item CARDBOARD_LEGGINGS = register("cardboard_leggings", new com.cogwork.cogworkbridge.content.equipment.CardboardArmorItem(net.minecraft.inventory.EntityEquipmentSlot.LEGS));
    public static final Item CARDBOARD_BOOTS = register("cardboard_boots", new com.cogwork.cogworkbridge.content.equipment.CardboardArmorItem(net.minecraft.inventory.EntityEquipmentSlot.FEET));
    public static final Item COPPER_BACKTANK = register("copper_backtank", new com.cogwork.cogworkbridge.content.equipment.BacktankItem(false));
    public static final Item NETHERITE_BACKTANK = register("netherite_backtank", new com.cogwork.cogworkbridge.content.equipment.BacktankItem.Netherite());
    public static final Item COPPER_DIVING_HELMET = register("copper_diving_helmet", new com.cogwork.cogworkbridge.content.equipment.DivingGearItem(net.minecraft.inventory.EntityEquipmentSlot.HEAD, false));
    public static final Item NETHERITE_DIVING_HELMET = register("netherite_diving_helmet", new com.cogwork.cogworkbridge.content.equipment.DivingGearItem.Netherite(net.minecraft.inventory.EntityEquipmentSlot.HEAD));
    public static final Item COPPER_DIVING_BOOTS = register("copper_diving_boots", new com.cogwork.cogworkbridge.content.equipment.DivingGearItem(net.minecraft.inventory.EntityEquipmentSlot.FEET, false));
    public static final Item NETHERITE_DIVING_BOOTS = register("netherite_diving_boots", new com.cogwork.cogworkbridge.content.equipment.DivingGearItem.Netherite(net.minecraft.inventory.EntityEquipmentSlot.FEET));
    public static final WrenchItem WRENCH = register("wrench", new WrenchItem());
    public static final com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartCouplingItem MINECART_COUPLING =
            register("minecart_coupling", new com.cogwork.cogworkbridge.content.contraptions.mounted.MinecartCouplingItem());
    public static final com.cogwork.cogworkbridge.content.trains.track.TrainScheduleItem SCHEDULE =
            register("schedule", new com.cogwork.cogworkbridge.content.trains.track.TrainScheduleItem());
    public static final MinecartContraptionItem MINECART_CONTRAPTION = register("minecart_contraption", new MinecartContraptionItem(EntityMinecart.Type.RIDEABLE));
    public static final MinecartContraptionItem CHEST_MINECART_CONTRAPTION = register("chest_minecart_contraption", new MinecartContraptionItem(EntityMinecart.Type.CHEST));
    public static final MinecartContraptionItem FURNACE_MINECART_CONTRAPTION = register("furnace_minecart_contraption", new MinecartContraptionItem(EntityMinecart.Type.FURNACE));
    public static final ListFilterItem FILTER = register("filter", new ListFilterItem());
    public static final BeltConnectorItem BELT_CONNECTOR = register("belt_connector", new BeltConnectorItem());
    public static final AttributeFilterItem ATTRIBUTE_FILTER = register("attribute_filter", new AttributeFilterItem());
    public static final ExtendoGripItem EXTENDO_GRIP = register("extendo_grip", new ExtendoGripItem());
    public static final GogglesItem GOGGLES = register("goggles", new GogglesItem());
    public static final SandPaperItem SAND_PAPER = register("sand_paper", new SandPaperItem());
    public static final SandPaperItem RED_SAND_PAPER = register("red_sand_paper", new SandPaperItem());
    public static final Item ROSE_QUARTZ = register("rose_quartz", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item POLISHED_ROSE_QUARTZ = register("polished_rose_quartz", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final LinkedControllerItem LINKED_CONTROLLER = register("linked_controller", new LinkedControllerItem());
    public static final SymmetryWandItem SYMMETRY_WAND = register("wand_of_symmetry", new SymmetryWandItem());
    public static final Item ANDESITE_ALLOY = register("andesite_alloy", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item IRON_SHEET = register("iron_sheet", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item COPPER_SHEET = register("copper_sheet", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item BRASS_SHEET = register("brass_sheet", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item BRASS_INGOT = register("brass_ingot", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item BRASS_NUGGET = register("brass_nugget", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item ZINC_NUGGET = register("zinc_nugget", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item ZINC_INGOT = register("zinc_ingot", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item ELECTRON_TUBE = register("electron_tube", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item PROPELLER = register("propeller", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item BRASS_HAND = register("brass_hand", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item TRANSMITTER = register("transmitter", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item PRECISION_MECHANISM = register("precision_mechanism", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item INCOMPLETE_TRACK = register("incomplete_track", new com.cogwork.cogworkbridge.content.processing.AssemblyProgressItem());
    public static final Item INCOMPLETE_PRECISION_MECHANISM = register("incomplete_precision_mechanism", new com.cogwork.cogworkbridge.content.processing.AssemblyProgressItem());
    public static final Item TREE_FERTILIZER = register("tree_fertilizer", new com.cogwork.cogworkbridge.content.equipment.TreeFertilizerItem().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item[] COMPAT_CRUSHED_ORES = com.cogwork.cogworkbridge.content.processing.CompatCrushedOres.createItems();
    public static final Item CRUSHED_RAW_GOLD = register("crushed_raw_gold", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item CRUSHED_RAW_IRON = register("crushed_raw_iron", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item CRUSHED_RAW_COPPER = register("crushed_raw_copper", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item CRUSHED_RAW_ZINC = register("crushed_raw_zinc", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item COPPER_NUGGET = register("copper_nugget", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item WHEAT_FLOUR = register("wheat_flour", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item DOUGH = register("dough", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item CINDER_FLOUR = register("cinder_flour", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item POWDERED_OBSIDIAN = register("powdered_obsidian", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item UNPROCESSED_OBSIDIAN_SHEET = register("unprocessed_obsidian_sheet", new com.cogwork.cogworkbridge.content.processing.AssemblyProgressItem());
    public static final Item STURDY_SHEET = register("sturdy_sheet", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item EXPERIENCE_NUGGET = register("experience_nugget", new com.cogwork.cogworkbridge.content.processing.ExperienceNuggetItem());
    public static final Item BAR_OF_CHOCOLATE = register("bar_of_chocolate", new net.minecraft.item.ItemFood(6, .3F, false).setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item SWEET_ROLL = register("sweet_roll", new net.minecraft.item.ItemFood(6, .8F, false).setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item CHOCOLATE_BERRIES = register("chocolate_glazed_berries", new net.minecraft.item.ItemFood(7, .8F, false).setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item HONEYED_APPLE = register("honeyed_apple", new net.minecraft.item.ItemFood(8, .8F, false).setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item BUILDERS_TEA = register("builders_tea", new com.cogwork.cogworkbridge.content.processing.BuildersTeaItem());
    public static final Item PULP = register("pulp", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item CARDBOARD = register("cardboard", new com.cogwork.cogworkbridge.content.processing.FuelItem(1000).setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item WHISK = register("whisk", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item GOLDEN_SHEET = register("golden_sheet", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item BLAZE_CAKE = register("blaze_cake", new com.cogwork.cogworkbridge.content.processing.FuelItem(6400).setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item BLAZE_CAKE_BASE = register("blaze_cake_base", new Item().setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item CREATIVE_BLAZE_CAKE = register("creative_blaze_cake", new com.cogwork.cogworkbridge.content.processing.FuelItem(Integer.MAX_VALUE).setCreativeTab(ModCreativeTab.INSTANCE));
    public static final Item CRAFTER_SLOT_COVER = register("crafter_slot_cover", new Item().setCreativeTab(ModCreativeTab.INSTANCE));

    private static boolean registered;

    private AllItems() {
    }

    private static <T extends Item> T register(String name, T item) {
        item.setRegistryName(Reference.MOD_ID, name);
        item.setTranslationKey(Reference.MOD_ID + "." + name);
        return item;
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        if (registered) {
            return;
        }
        event.getRegistry().register(PACKAGE_FILTER);
        event.getRegistry().register(SHOPPING_LIST);
        for (var box : CARDBOARD_PACKAGES) event.getRegistry().register(box);
        for (var box : RARE_PACKAGES) event.getRegistry().register(box);
        event.getRegistry().register(EMPTY_SCHEMATIC);
        event.getRegistry().register(SCHEMATIC_AND_QUILL);
        event.getRegistry().register(SCHEMATIC);
        event.getRegistry().register(SUPER_GLUE);
        event.getRegistry().register(POTATO_CANNON);
        event.getRegistry().register(HANDHELD_WORLDSHAPER);
        event.getRegistry().register(CRAFTING_BLUEPRINT);
        event.getRegistry().register(CARDBOARD_SWORD);
        for (Item item : new Item[]{CARDBOARD_HELMET, CARDBOARD_CHESTPLATE, CARDBOARD_LEGGINGS, CARDBOARD_BOOTS}) event.getRegistry().register(item);
        for (Item item : new Item[]{COPPER_BACKTANK, NETHERITE_BACKTANK, COPPER_DIVING_HELMET, NETHERITE_DIVING_HELMET, COPPER_DIVING_BOOTS, NETHERITE_DIVING_BOOTS}) event.getRegistry().register(item);
        event.getRegistry().register(WRENCH);
        event.getRegistry().register(MINECART_COUPLING);
        event.getRegistry().register(SCHEDULE);
        event.getRegistry().register(MINECART_CONTRAPTION);
        event.getRegistry().register(CHEST_MINECART_CONTRAPTION);
        event.getRegistry().register(FURNACE_MINECART_CONTRAPTION);
        event.getRegistry().register(FILTER);
        event.getRegistry().register(BELT_CONNECTOR);
        event.getRegistry().register(ATTRIBUTE_FILTER);
        event.getRegistry().register(EXTENDO_GRIP);
        event.getRegistry().register(GOGGLES);
        event.getRegistry().register(SAND_PAPER);
        event.getRegistry().register(RED_SAND_PAPER);
        event.getRegistry().register(ROSE_QUARTZ);
        event.getRegistry().register(POLISHED_ROSE_QUARTZ);
        event.getRegistry().register(LINKED_CONTROLLER);
        event.getRegistry().register(SYMMETRY_WAND);
        event.getRegistry().register(TREE_FERTILIZER);
        event.getRegistry().register(ANDESITE_ALLOY);
        event.getRegistry().register(IRON_SHEET);
        event.getRegistry().register(COPPER_SHEET);
        event.getRegistry().register(BRASS_SHEET);
        event.getRegistry().register(BRASS_INGOT);
        event.getRegistry().register(BRASS_NUGGET);
        event.getRegistry().register(ZINC_NUGGET);
        event.getRegistry().register(ZINC_INGOT);
        event.getRegistry().register(ELECTRON_TUBE);
        event.getRegistry().register(PROPELLER);
        event.getRegistry().register(BRASS_HAND);
        event.getRegistry().register(TRANSMITTER);
        event.getRegistry().register(PRECISION_MECHANISM);
        event.getRegistry().register(INCOMPLETE_PRECISION_MECHANISM);
        event.getRegistry().register(INCOMPLETE_TRACK);
        event.getRegistry().register(CRUSHED_RAW_GOLD);
        for (Item item : COMPAT_CRUSHED_ORES) event.getRegistry().register(item);
        for (Item item : new Item[]{CRUSHED_RAW_IRON, CRUSHED_RAW_COPPER, CRUSHED_RAW_ZINC, COPPER_NUGGET,
                WHEAT_FLOUR, DOUGH, CINDER_FLOUR, POWDERED_OBSIDIAN, UNPROCESSED_OBSIDIAN_SHEET, STURDY_SHEET, EXPERIENCE_NUGGET,
                BAR_OF_CHOCOLATE, SWEET_ROLL, CHOCOLATE_BERRIES, HONEYED_APPLE, BUILDERS_TEA}) event.getRegistry().register(item);
        event.getRegistry().register(PULP);
        event.getRegistry().register(CARDBOARD);
        event.getRegistry().register(WHISK);
        event.getRegistry().register(GOLDEN_SHEET);
        event.getRegistry().register(BLAZE_CAKE);
        event.getRegistry().register(BLAZE_CAKE_BASE);
        event.getRegistry().register(CREATIVE_BLAZE_CAKE);
        event.getRegistry().register(CRAFTER_SLOT_COVER);
        OreDictionary.registerOre("plateGold", GOLDEN_SHEET);
        OreDictionary.registerOre("plateIron", IRON_SHEET);
        OreDictionary.registerOre("plateCopper", COPPER_SHEET);
        OreDictionary.registerOre("plateBrass", BRASS_SHEET);
        OreDictionary.registerOre("plateObsidian", STURDY_SHEET);
        OreDictionary.registerOre("dustObsidian", POWDERED_OBSIDIAN);
        OreDictionary.registerOre("ingotBrass", BRASS_INGOT);
        OreDictionary.registerOre("ingotZinc", ZINC_INGOT);
        OreDictionary.registerOre("nuggetZinc", ZINC_NUGGET);
        OreDictionary.registerOre("nuggetBrass", BRASS_NUGGET);
        OreDictionary.registerOre("nuggetCopper", COPPER_NUGGET);
        OreDictionary.registerOre("flourWheat", WHEAT_FLOUR);
        registered = true;
    }

    public static void init() {
        Item[] ignored = {SUPER_GLUE, WRENCH, FILTER, EXTENDO_GRIP, GOGGLES, SAND_PAPER, RED_SAND_PAPER, ROSE_QUARTZ, POLISHED_ROSE_QUARTZ, LINKED_CONTROLLER, SYMMETRY_WAND, ANDESITE_ALLOY, GOLDEN_SHEET, BLAZE_CAKE, CREATIVE_BLAZE_CAKE,
                CRAFTER_SLOT_COVER};
    }
}
