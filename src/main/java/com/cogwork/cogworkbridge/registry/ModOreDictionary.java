package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.processing.casing.CasingApplication;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

/** Ore registrations which require block items and optional mod items to exist. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ModOreDictionary {
    private static boolean registered;
    private ModOreDictionary() { }

    // Forge 1.12 fires the sound registry after both block and item registries,
    // and before JSON recipes are loaded. Pre-init is earlier than either registry.
    @SubscribeEvent public static void afterItemRegistry(RegistryEvent.Register<SoundEvent> event) {
        if (registered) return;
        registered = true;
        CasingApplication.registerStrippedLogs();
        OreDictionary.registerOre("blockBrass", AllBlocks.BRASS_BLOCK);
        OreDictionary.registerOre("blockZinc", AllBlocks.ZINC_BLOCK);
        OreDictionary.registerOre("oreZinc", AllBlocks.ZINC_ORE);
        OreDictionary.registerOre("oreZinc", AllBlocks.DEEPSLATE_ZINC_ORE);
        OreDictionary.registerOre("track", AllBlocks.TRACK);
        OreDictionary.registerOre("cogworkBridgeSmallFlower", new net.minecraft.item.ItemStack(Blocks.YELLOW_FLOWER, 1, OreDictionary.WILDCARD_VALUE));
        OreDictionary.registerOre("cogworkBridgeSmallFlower", new net.minecraft.item.ItemStack(Blocks.RED_FLOWER, 1, OreDictionary.WILDCARD_VALUE));
        for (String color : new String[]{"horn", "brain", "tube", "bubble", "fire"})
            registerOptional("cogworkBridgeCoral", "farmers_future_delight:" + color + "_coral");
        OreDictionary.registerOre("blockGlassColorless", Blocks.GLASS);
        OreDictionary.registerOre("paneGlassColorless", Blocks.GLASS_PANE);
        for (String name : BuildingPalette.GLASS) {
            OreDictionary.registerOre("blockGlassColorless", BlockFamily.get(name));
            OreDictionary.registerOre("paneGlassColorless", BlockFamily.get(name + "_pane"));
        }
        registerOptional("foodDriedKelp", "farmers_future_delight:dried_kelp");
        registerOptional("blockDriedKelp", "farmers_future_delight:dried_kelp_block");
        registerOptional("rawZinc", "farmers_future_delight:raw_zinc");
        registerOptional("blockBarrelWood", "farmers_future_delight:barrel");
        registerOptional("rodLightning", "farmers_future_delight:lightning_rod");
        for (Block block : AllBlocks.POSTBOXES) OreDictionary.registerOre("cogworkBridgePostbox", block);
        for (Block block : AllBlocks.TABLE_CLOTHS) OreDictionary.registerOre("cogworkBridgeTableCloth", block);
        for (Block block : AllBlocks.SEATS) OreDictionary.registerOre("cogworkBridgeSeat", block);
        for (Block block : AllBlocks.COLOR_VALVE_HANDLES) OreDictionary.registerOre("cogworkBridgeValveHandle", block);
        OreDictionary.registerOre("cogworkBridgeValveHandle", AllBlocks.COPPER_VALVE_HANDLE);
    }

    private static void registerOptional(String ore, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item != null && item != Items.AIR) OreDictionary.registerOre(ore, item);
    }
}
