package com.cogwork.cogworkbridge.client;

import com.cogwork.cogworkbridge.CogworkBridge;
import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Maps Cogwork Bridge block items directly to the upstream Create item models. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class UpstreamItemModels {

    private UpstreamItemModels() {
    }

    @SubscribeEvent
    public static void register(ModelRegistryEvent event) {
        for (Block block : com.cogwork.cogworkbridge.registry.BuildingPalette.items()) {
            net.minecraft.client.renderer.block.statemap.StateMap.Builder mapper = new net.minecraft.client.renderer.block.statemap.StateMap.Builder();
            for (net.minecraft.block.properties.IProperty<?> property : block.getBlockState().getProperties()) {
                if ("waterlogged".equals(property.getName()) || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks.Wall
                        && property == net.minecraft.block.BlockWall.VARIANT
                        || block instanceof com.cogwork.cogworkbridge.content.decoration.palette.PaletteDoor && property == net.minecraft.block.BlockDoor.POWERED) mapper.ignore(property);
            }
            ModelLoader.setCustomStateMapper(block, mapper.build());
            register(block);
        }
        for (Item item : new Item[]{AllItems.MINECART_CONTRAPTION, AllItems.CHEST_MINECART_CONTRAPTION,
                AllItems.FURNACE_MINECART_CONTRAPTION}) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
        for (Item item : new Item[]{AllItems.SCHEDULE, AllItems.CRAFTING_BLUEPRINT, AllItems.HANDHELD_WORLDSHAPER, AllItems.POTATO_CANNON, AllItems.COPPER_BACKTANK, AllItems.NETHERITE_BACKTANK, AllItems.COPPER_DIVING_HELMET,
                AllItems.NETHERITE_DIVING_HELMET, AllItems.COPPER_DIVING_BOOTS, AllItems.NETHERITE_DIVING_BOOTS,
                AllItems.CARDBOARD_SWORD, AllItems.CARDBOARD_HELMET, AllItems.CARDBOARD_CHESTPLATE,
                AllItems.CARDBOARD_LEGGINGS, AllItems.CARDBOARD_BOOTS, AllItems.TREE_FERTILIZER, AllItems.IRON_SHEET, AllItems.COPPER_SHEET, AllItems.BRASS_SHEET,
                AllItems.BRASS_INGOT, AllItems.ZINC_INGOT, AllItems.ZINC_NUGGET, AllItems.BRASS_NUGGET, AllItems.WHISK, AllItems.PULP, AllItems.CARDBOARD,
                AllItems.ELECTRON_TUBE, AllItems.PROPELLER, AllItems.BRASS_HAND, AllItems.TRANSMITTER, AllItems.PRECISION_MECHANISM, AllItems.INCOMPLETE_PRECISION_MECHANISM, AllItems.INCOMPLETE_TRACK, AllItems.CRUSHED_RAW_GOLD,
                AllItems.CRUSHED_RAW_IRON, AllItems.CRUSHED_RAW_COPPER, AllItems.CRUSHED_RAW_ZINC, AllItems.COPPER_NUGGET,
                AllItems.WHEAT_FLOUR, AllItems.DOUGH, AllItems.CINDER_FLOUR, AllItems.POWDERED_OBSIDIAN, AllItems.UNPROCESSED_OBSIDIAN_SHEET, AllItems.STURDY_SHEET, AllItems.EXPERIENCE_NUGGET, AllItems.BLAZE_CAKE_BASE,
                AllItems.BAR_OF_CHOCOLATE, AllItems.SWEET_ROLL, AllItems.CHOCOLATE_BERRIES, AllItems.HONEYED_APPLE, AllItems.BUILDERS_TEA})
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(new ResourceLocation("create", item.getRegistryName().getPath()), "inventory"));
        for (Item item : AllItems.COMPAT_CRUSHED_ORES)
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(new ResourceLocation("create", item.getRegistryName().getPath()), "inventory"));
        for (Item item : com.cogwork.cogworkbridge.content.processing.CompatCrushedOres.configuredItems())
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, "configured_crushed_ore"), "inventory"));
        for (String fluidName : new String[]{"honey", "chocolate"}) {
            net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry.getFluid(fluidName);
            Block block = fluid == null ? null : fluid.getBlock();
            if (block != null && Reference.MOD_ID.equals(block.getRegistryName().getNamespace())) {
                ModelLoader.setCustomStateMapper(block, new net.minecraft.client.renderer.block.statemap.StateMapperBase() {
                    @Override protected ModelResourceLocation getModelResourceLocation(net.minecraft.block.state.IBlockState state) {
                        return new ModelResourceLocation(state.getBlock().getRegistryName(), "fluid");
                    }
                });
            }
        }
        for (Item bucket : new Item[]{com.cogwork.cogworkbridge.content.fluids.ProcessingFluids.HONEY_BUCKET,
                com.cogwork.cogworkbridge.content.fluids.ProcessingFluids.CHOCOLATE_BUCKET})
            ModelLoader.setCustomModelResourceLocation(bucket, 0,
                    new ModelResourceLocation(new ResourceLocation("create", bucket.getRegistryName().getPath()), "inventory"));
        for (Item item : new Item[]{AllItems.EMPTY_SCHEMATIC, AllItems.SCHEMATIC_AND_QUILL, AllItems.SCHEMATIC})
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(new ResourceLocation("create", item.getRegistryName().getPath()), "inventory"));
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(AllBlocks.TRACK), 0,
                new ModelResourceLocation(new ResourceLocation("create", "track"), "inventory"));
        register(AllBlocks.TRACK_STATION);
        register(AllBlocks.TRACK_SIGNAL);
        register(AllBlocks.TRACK_OBSERVER);
        register(AllBlocks.TRAIN_CONTROLS);
        register(AllBlocks.SCHEMATIC_TABLE);
        register(AllBlocks.SCHEMATICANNON);
        register(AllBlocks.FACTORY_GAUGE);
        for (var seat : AllBlocks.SEATS) register(seat);
        for (var toolbox : AllBlocks.TOOLBOXES) register(toolbox);
        register(AllBlocks.PECULIAR_BELL);
        register(AllBlocks.HAUNTED_BELL);
        register(AllBlocks.ANDESITE_SCAFFOLDING);
        register(AllBlocks.BRASS_SCAFFOLDING);
        register(AllBlocks.COPPER_SCAFFOLDING);
        for (var cloth : AllBlocks.TABLE_CLOTHS) register(cloth);
        register(AllBlocks.ANDESITE_TABLE_CLOTH);
        register(AllBlocks.BRASS_TABLE_CLOTH);
        register(AllBlocks.COPPER_TABLE_CLOTH);
        register(AllBlocks.PACKAGE_FROGPORT);
        register(AllBlocks.CHAIN_CONVEYOR);
        for (var postbox : AllBlocks.POSTBOXES) register(postbox);
        register(AllBlocks.REPACKAGER);
        register(AllBlocks.REDSTONE_REQUESTER);
        register(AllBlocks.STOCK_TICKER);
        register(AllBlocks.STOCK_LINK);
        ModelLoader.setCustomModelResourceLocation(AllItems.PACKAGE_FILTER, 0, new ModelResourceLocation(new ResourceLocation("create", "package_filter"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.SHOPPING_LIST, 0, new ModelResourceLocation(new ResourceLocation("create", "shopping_list"), "inventory"));
        for (var box : AllItems.CARDBOARD_PACKAGES) ModelLoader.setCustomModelResourceLocation(box, 0, new ModelResourceLocation(new ResourceLocation("create", box.getRegistryName().getPath()), "inventory"));
        for (var box : AllItems.RARE_PACKAGES) ModelLoader.setCustomModelResourceLocation(box, 0, new ModelResourceLocation(new ResourceLocation("create", box.getRegistryName().getPath()), "inventory"));
        register(AllBlocks.CONTENT_OBSERVER);
        register(AllBlocks.STOCKPILE_SWITCH);
        register(AllBlocks.PLACARD);
        register(AllBlocks.PACKAGER);
        register(AllBlocks.PULSE_EXTENDER);
        register(AllBlocks.PULSE_REPEATER);
        register(AllBlocks.PULSE_TIMER);
        register(AllBlocks.POWERED_LATCH);
        register(AllBlocks.POWERED_TOGGLE_LATCH);
        register(AllBlocks.ANALOG_LEVER);
        register(AllBlocks.DESK_BELL);
        register(AllBlocks.ROSE_QUARTZ_LAMP);
        registerLocal(AllBlocks.CLIPBOARD);
        registerLocal(AllBlocks.REDSTONE_LINK);
        ModelLoader.setCustomModelResourceLocation(AllItems.LINKED_CONTROLLER, 0,
                new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, "linked_controller"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.SYMMETRY_WAND, 0,
                new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, "wand_of_symmetry"), "inventory"));
        registerLocal(AllBlocks.DISPLAY_LINK);
        registerLocal(AllBlocks.DISPLAY_BOARD);
        registerLocal(AllBlocks.NIXIE_TUBE);
        ModelLoader.setCustomModelResourceLocation(AllItems.EXTENDO_GRIP, 0,
                new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, "extendo_grip"), "inventory"));
        for (Item item : new Item[]{AllItems.GOGGLES, AllItems.SAND_PAPER, AllItems.RED_SAND_PAPER, AllItems.ROSE_QUARTZ, AllItems.POLISHED_ROSE_QUARTZ}) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, item.getRegistryName().getPath()), "inventory"));
        }
        registerLocal(AllBlocks.SPEEDOMETER);
        registerLocal(AllBlocks.STRESSOMETER);
        register(AllBlocks.HAND_CRANK);
        register(AllBlocks.COPPER_VALVE_HANDLE);
        for (var valve : AllBlocks.COLOR_VALVE_HANDLES) register(valve);
        register(AllBlocks.LARGE_WATER_WHEEL);
        register(AllBlocks.WATER_WHEEL);
        register(AllBlocks.WINDMILL_BEARING);
        register(AllBlocks.SAIL_FRAME);
        register(AllBlocks.LINEAR_CHASSIS);
        register(AllBlocks.SECONDARY_LINEAR_CHASSIS);
        register(AllBlocks.RADIAL_CHASSIS);
        registerLocal(AllBlocks.STICKER);
        registerLocal(AllBlocks.CONTRAPTION_CONTROLS);
        registerLocal(AllBlocks.REDSTONE_CONTACT);
        registerLocal(AllBlocks.ELEVATOR_CONTACT);
        registerLocal(AllBlocks.ELEVATOR_PULLEY);
        registerLocal(AllBlocks.ROPE_PULLEY);
        register(AllBlocks.MECHANICAL_PISTON);
        register(AllBlocks.STICKY_MECHANICAL_PISTON);
        register(AllBlocks.PISTON_EXTENSION_POLE);
        register(AllBlocks.CLOCKWORK_BEARING);
        register(AllBlocks.GANTRY_SHAFT);
        register(AllBlocks.GANTRY_CARRIAGE);
        register(AllBlocks.CART_ASSEMBLER);
        register(AllBlocks.CONTROLLER_RAIL);
        register(AllBlocks.STEAM_ENGINE);
        for (var sail : AllBlocks.SAILS) register(sail);
        register(AllBlocks.SHAFT);
        register(AllBlocks.SHAFT_ENCASED_ANDESITE, "andesite_encased_shaft");
        register(AllBlocks.SHAFT_ENCASED_BRASS, "brass_encased_shaft");
        register(AllBlocks.COGWHEEL);
        register(AllBlocks.LARGE_COGWHEEL);
        register(AllBlocks.COGWHEEL_ENCASED_ANDESITE, "andesite_encased_cogwheel");
        register(AllBlocks.COGWHEEL_ENCASED_BRASS, "brass_encased_cogwheel");
        register(AllBlocks.LARGE_COGWHEEL_ENCASED_ANDESITE, "andesite_encased_large_cogwheel");
        register(AllBlocks.LARGE_COGWHEEL_ENCASED_BRASS, "brass_encased_large_cogwheel");
        register(AllBlocks.BRASS_BLOCK);
        register(AllBlocks.ZINC_BLOCK);
        register(AllBlocks.CARDBOARD_BLOCK);
        registerLocal(AllBlocks.CUCKOO_CLOCK);
        registerLocal(AllBlocks.MYSTERIOUS_CUCKOO_CLOCK);
        registerLocal(AllBlocks.STEAM_WHISTLE);
        register(AllBlocks.TURNTABLE);
        register(AllBlocks.FLYWHEEL);
        register(AllBlocks.ZINC_ORE);
        register(AllBlocks.DEEPSLATE_ZINC_ORE);
        register(AllBlocks.ANDESITE_CASING);
        register(AllBlocks.BRASS_CASING);
        register(AllBlocks.COPPER_CASING);
        register(AllBlocks.GEARBOX);
        register(AllBlocks.VERTICAL_GEARBOX);
        register(AllBlocks.CLUTCH);
        register(AllBlocks.GEARSHIFT);
        register(AllBlocks.SEQUENCED_GEARSHIFT);
        register(AllBlocks.ENCASED_CHAIN_DRIVE);
        register(AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT);
        register(AllBlocks.ROTATION_SPEED_CONTROLLER);
        register(AllBlocks.CREATIVE_MOTOR);
        register(AllBlocks.MECHANICAL_BEARING);
        register(AllBlocks.MECHANICAL_DRILL);
        register(AllBlocks.DEPLOYER);
        register(AllBlocks.MECHANICAL_SAW);
        register(AllBlocks.MECHANICAL_PLOUGH);
        register(AllBlocks.MECHANICAL_HARVESTER);
        register(AllBlocks.MECHANICAL_ROLLER);
        register(AllBlocks.ENCASED_FAN);
        register(AllBlocks.NOZZLE);
        register(AllBlocks.BASIN);
        register(AllBlocks.FLUID_PIPE);
        register(AllBlocks.MECHANICAL_PUMP);
        register(AllBlocks.SMART_FLUID_PIPE);
        register(AllBlocks.FLUID_VALVE);
        register(AllBlocks.FLUID_TANK);
        register(AllBlocks.CREATIVE_FLUID_TANK);
        register(AllBlocks.HOSE_PULLEY);
        register(AllBlocks.ITEM_DRAIN);
        register(AllBlocks.MECHANICAL_ARM);
        register(AllBlocks.ANDESITE_TUNNEL);
        register(AllBlocks.BRASS_TUNNEL);
        register(AllBlocks.DEPOT);
        register(AllBlocks.CREATIVE_CRATE);
        register(AllBlocks.PORTABLE_STORAGE_INTERFACE);
        register(AllBlocks.CHUTE);
        register(AllBlocks.SMART_CHUTE);
        register(AllBlocks.ANDESITE_FUNNEL);
        register(AllBlocks.BRASS_FUNNEL);
        register(AllBlocks.ITEM_VAULT);
        register(AllBlocks.ITEM_HATCH);
        register(AllBlocks.WEIGHTED_EJECTOR);
        register(AllBlocks.SPOUT);
        register(AllBlocks.PORTABLE_FLUID_INTERFACE);
        register(AllBlocks.BLAZE_BURNER);
        register(AllBlocks.EMPTY_BLAZE_BURNER);
        register(AllBlocks.MECHANICAL_PRESS);
        register(AllBlocks.MECHANICAL_MIXER);
        register(AllBlocks.MILLSTONE);
        register(AllBlocks.MECHANICAL_CRAFTER);
        register(AllBlocks.CRUSHING_WHEEL);
        ModelLoader.setCustomModelResourceLocation(AllItems.SUPER_GLUE, 0,
                new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, "super_glue"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.WRENCH, 0,
                new ModelResourceLocation(new ResourceLocation("create", "wrench"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.FILTER, 0,
                new ModelResourceLocation(new ResourceLocation("create", "filter"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.MINECART_COUPLING, 0,
                new ModelResourceLocation(new ResourceLocation("create", "minecart_coupling"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.BELT_CONNECTOR, 0,
                new ModelResourceLocation(new ResourceLocation("create", "belt_connector"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.ATTRIBUTE_FILTER, 0,
                new ModelResourceLocation(new ResourceLocation("create", "attribute_filter"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.ANDESITE_ALLOY, 0,
                new ModelResourceLocation(new ResourceLocation("create", "andesite_alloy"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.GOLDEN_SHEET, 0,
                new ModelResourceLocation(new ResourceLocation("create", "golden_sheet"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.BLAZE_CAKE, 0,
                new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, "blaze_cake"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.CREATIVE_BLAZE_CAKE, 0,
                new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, "creative_blaze_cake"), "inventory"));
        ModelLoader.setCustomModelResourceLocation(AllItems.CRAFTER_SLOT_COVER, 0,
                new ModelResourceLocation(new ResourceLocation("create", "crafter_slot_cover"), "inventory"));
    }

    @SubscribeEvent
    public static void colors(net.minecraftforge.client.event.ColorHandlerEvent.Item event) {
        for (var item : com.cogwork.cogworkbridge.content.processing.CompatCrushedOres.configuredItems())
            event.getItemColors().registerItemColorHandler((stack, tint) -> tint == 0 ? item.color() : 0xffffff, item);
    }

    private static void registerLocal(Block block) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
                new ModelResourceLocation(new ResourceLocation(Reference.MOD_ID, block.getRegistryName().getPath()), "inventory"));
    }

    private static void register(Block block) {
        register(block, block.getRegistryName().getPath());
    }

    private static void register(Block block, String upstreamName) {
        Item item = Item.getItemFromBlock(block);
        if (item == Items.AIR) {
            CogworkBridge.LOGGER.error("No ItemBlock registered for {}", block.getRegistryName());
            return;
        }

        ModelResourceLocation model = new ModelResourceLocation(
                new ResourceLocation("create", upstreamName), "inventory");
        ModelLoader.setCustomModelResourceLocation(item, 0, model);
    }
}
