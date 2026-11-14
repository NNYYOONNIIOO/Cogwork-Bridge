package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.content.decoration.palette.PaletteBlocks;
import com.cogwork.cogworkbridge.content.decoration.palette.WeatheringPaletteBlocks;
import xy177.farmersfuturedelight.common.block.CopperWeathering;
import xy177.farmersfuturedelight.common.block.CopperWeathering.WeatherState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/** Separate registry names keep material variants out of 1.12 block metadata. */
public final class BuildingPalette {
    public static final String[] STONES = {"granite", "diorite", "andesite", "calcite", "dripstone", "deepslate", "tuff",
            "asurine", "crimsite", "limestone", "ochrum", "scoria", "scorchia", "veridium"};
    public static final String[] NATURAL_STONES = {"asurine", "crimsite", "limestone", "ochrum", "scoria", "scorchia", "veridium"};
    public static final String[] WINDOWS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "crimson", "warped", "cherry", "bamboo", "ornate_iron", "industrial_iron", "weathered_iron"};
    public static final String[] GLASS = {"tiled_glass", "framed_glass", "horizontal_framed_glass", "vertical_framed_glass"};
    public static final String[] COPPER_STAGES = {"", "exposed_", "weathered_", "oxidized_", "waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_"};
    private static final List<Block> ITEMS = new ArrayList<>();
    private static boolean initialized;

    private BuildingPalette() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        cube("andesite_alloy_block", Material.ROCK, 1.5F);
        cube("industrial_iron_block", Material.IRON, 5F);
        cube("weathered_iron_block", Material.IRON, 5F);
        add("railway_casing", new com.cogwork.cogworkbridge.content.decoration.CasingBlock());
        add("experience_block", new PaletteBlocks.Experience());
        add("rose_quartz_block", new PaletteBlocks.Pillar(Material.ROCK));
        cube("rose_quartz_tiles", Material.ROCK, 1.5F);
        cube("small_rose_quartz_tiles", Material.ROCK, 1.5F);
        ITEMS.add(BlockFamily.register("bound_cardboard_block", new com.cogwork.cogworkbridge.content.decoration.palette.CardboardBlock(true),
                block -> new com.cogwork.cogworkbridge.content.processing.FuelItem.BlockItem(block, 4000)));
        add("metal_girder", new com.cogwork.cogworkbridge.content.decoration.palette.GirderBlock());
        BlockFamily.register("metal_girder_encased_shaft", new com.cogwork.cogworkbridge.content.decoration.palette.GirderShaftBlock(), null);
        add("copycat_panel", new com.cogwork.cogworkbridge.content.decoration.palette.CopycatBlock.Panel());
        add("copycat_step", new com.cogwork.cogworkbridge.content.decoration.palette.CopycatBlock.Step());
        BlockFamily.register("render_copycat_bars", new RenderPartBlock(), null);
        for (String material : new String[]{"wooden", "metal"}) {
            Block bracket = new Block(material.equals("wooden") ? Material.WOOD : Material.IRON).setHardness(2F);
            BlockFamily.register(material + "_bracket", bracket, com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment.BracketItem::new);
            ITEMS.add(bracket);
            for (String type : new String[]{"shaft", "cog", "pipe"})
                BlockFamily.register("render_" + material + "_bracket_" + type, new com.cogwork.cogworkbridge.content.decoration.palette.BracketAttachment.RenderBlock(), null);
        }
        for (String door : new String[]{"andesite_door", "brass_door", "copper_door", "train_door", "framed_glass_door"}) {
            Block block = new com.cogwork.cogworkbridge.content.decoration.palette.PaletteDoor();
            BlockFamily.register(door, block, com.cogwork.cogworkbridge.content.decoration.palette.PaletteDoor.DoorItem::new);
            ITEMS.add(block);
        }
        for (String door : new String[]{"andesite", "copper"}) for (String half : new String[]{"left", "right"})
            BlockFamily.register("render_" + door + "_door_fold_" + half, new RenderPartBlock(), null);
        for (String trapdoor : new String[]{"train_trapdoor", "framed_glass_trapdoor"})
            add(trapdoor, new com.cogwork.cogworkbridge.content.decoration.palette.PaletteTrapdoor());

        for (String stone : NATURAL_STONES) cube(stone, Material.ROCK, 1.5F);
        for (String stone : STONES) {
            String[] names = {"cut_" + stone, "polished_cut_" + stone, "cut_" + stone + "_bricks", "small_" + stone + "_bricks"};
            String[] partials = {"cut_" + stone, "polished_cut_" + stone, "cut_" + stone + "_brick", "small_" + stone + "_brick"};
            for (int i = 0; i < names.length; i++) {
                Block base = cube(names[i], Material.ROCK, 1.5F);
                add(partials[i] + "_stairs", new PaletteBlocks.Stairs(base));
                slab(partials[i] + "_slab", Material.ROCK);
                add(partials[i] + "_wall", new PaletteBlocks.Wall(base));
            }
            add("layered_" + stone, new PaletteBlocks.Layered());
            add(stone + "_pillar", new PaletteBlocks.Pillar(Material.ROCK));
        }
        for (int stageIndex = 0; stageIndex < COPPER_STAGES.length; stageIndex++) for (String roof : new String[]{"tile", "shingle"}) {
            String stage = COPPER_STAGES[stageIndex];
            WeatherState weather = WeatherState.values()[stageIndex % 4];
            boolean waxed = stageIndex >= 4;
            String stem = stage + "copper_" + roof;
            Block base = add(stem + "s", new WeatheringPaletteBlocks.Cube(weather, waxed));
            add(stem + "_stairs", new WeatheringPaletteBlocks.Stairs(base, weather, waxed));
            slab(stem + "_slab", new WeatheringPaletteBlocks.Slab(weather, waxed), weather, waxed);
        }
        for (String name : GLASS) {
            add(name, new PaletteBlocks.Glass());
            add(name + "_pane", new PaletteBlocks.Pane(false));
        }
        for (String type : WINDOWS) {
            add(type + "_window", new PaletteBlocks.Glass());
            add(type + "_window_pane", new PaletteBlocks.Pane(false));
        }
        for (String metal : new String[]{"andesite", "brass", "copper"}) {
            add(metal + "_ladder", new PaletteBlocks.Ladder());
            add(metal + "_bars", new PaletteBlocks.Pane(true));
        }
    }

    private static Block cube(String name, Material material, float hardness) {
        Block block = new Block(material).setHardness(hardness).setResistance(6F);
        block.setHarvestLevel("pickaxe", 0);
        return add(name, block);
    }

    private static void slab(String name, Material material) {
        slab(name, new PaletteBlocks.Slab(material), null, false);
    }

    private static void slab(String name, PaletteBlocks.Slab single, WeatherState weather, boolean waxed) {
        BlockFamily.register(name, single, PaletteBlocks.SlabItem::new);
        ITEMS.add(single);
        PaletteBlocks.DoubleSlab full = weather == null ? new PaletteBlocks.DoubleSlab(single) : new WeatheringPaletteBlocks.DoubleSlab(single, weather, waxed);
        BlockFamily.register(name + "_double", full, null);
        single.setDoubleSlab(full);
    }

    private static <T extends Block> T add(String name, T block) {
        T registered = BlockFamily.register(name, block);
        ITEMS.add(registered);
        return registered;
    }

    public static List<Block> items() { return Collections.unmodifiableList(ITEMS); }

    public static void registerCopperWeathering() {
        for (String roof : new String[]{"tile", "shingle"}) for (String suffix : new String[]{"s", "_stairs", "_slab", "_slab_double"}) {
            Block[] sequence = new Block[4];
            for (int stage = 0; stage < 4; stage++) {
                String name = COPPER_STAGES[stage] + "copper_" + roof + suffix;
                sequence[stage] = BlockFamily.get(name);
                CopperWeathering.registerWaxedPair(sequence[stage], BlockFamily.get("waxed_" + name));
            }
            CopperWeathering.registerWeatheringSequence(sequence);
        }
    }
}
