package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.Reference;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Declares one {@link Block} per registry name and feeds them to the
 * {@code RegistryEvent.Register<Block>} / {@code <Item>} events.
 *
 * <p>Cogwork Bridge never encodes visual or behavioural variants in metadata.
 * Each variant is a distinct block instance with its own registry name, e.g.
 * {@code cogworkbridge:shaft} and {@code cogworkbridge:shaft_encased_andesite}.
 * Metadata is reserved for orientation only; see {@code docs/BLOCK-META-CONTRACT.md}.
 *
 * <p>Everything declared here is recorded so {@link StateGuard} can assert the
 * metadata budget at startup and in tests.
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class BlockFamily {

    private static final Map<String, Block> REGISTERED = new LinkedHashMap<>();
    private static final List<Block> ORDER = new ArrayList<>();
    private static final List<Item> PENDING_ITEMS = new ArrayList<>();
    private static boolean registered;

    private BlockFamily() {
    }

    /** Declares a block under {@code cogworkbridge:<name>} with a matching {@link ItemBlock}. */
    public static <T extends Block> T register(String name, T block) {
        return register(name, block, ItemBlock::new);
    }

    /**
     * Declares a block and, optionally, a custom item form.
     *
     * @param itemFactory {@code null} to declare a block without an item
     */
    public static <T extends Block> T register(String name, T block, @Nullable Function<Block, Item> itemFactory) {
        String fullName = Reference.MOD_ID + ":" + name;
        validateName(name, fullName);
        if (REGISTERED.containsKey(fullName)) {
            throw new IllegalStateException("Duplicate block registry name: " + fullName);
        }

        block.setRegistryName(Reference.MOD_ID, name);
        block.setTranslationKey(Reference.MOD_ID + "." + name);
        block.setCreativeTab(ModCreativeTab.INSTANCE);

        if (itemFactory != null) {
            Item item = itemFactory.apply(block);
            if (item != null) {
                item.setRegistryName(Reference.MOD_ID, name);
                item.setTranslationKey(Reference.MOD_ID + "." + name);
                item.setCreativeTab(ModCreativeTab.INSTANCE);
                PENDING_ITEMS.add(item);
            }
        }

        REGISTERED.put(fullName, block);
        ORDER.add(block);
        return block;
    }

    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        if (registered) {
            return;
        }
        BuildingPalette.init();
        for (Block block : ORDER) {
            event.getRegistry().register(block);
        }
        registered = true;
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        for (Item item : PENDING_ITEMS) {
            event.getRegistry().register(item);
        }
    }

    private static void validateName(String name, String fullName) {
        if (name.isEmpty() || name.contains(":") || !name.equals(name.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Registry names must be lowercase snake_case without a namespace: " + fullName);
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= (char) 97 && c <= (char) 122) || (c >= (char) 48 && c <= (char) 57) || c == (char) 95 || c == (char) 46 || c == (char) 45;
            if (!ok) {
                throw new IllegalArgumentException("Illegal character in registry name " + fullName);
            }
        }
    }

    /** Every block declared through this class, in registration order. */
    public static List<Block> all() {
        return Collections.unmodifiableList(ORDER);
    }

    public static Map<String, Block> byName() {
        return Collections.unmodifiableMap(REGISTERED);
    }

    /** Looks up a previously declared block by its bare registry path. */
    @Nullable
    public static Block get(String name) {
        return REGISTERED.get(Reference.MOD_ID + ":" + name);
    }

    public static int count() {
        return ORDER.size();
    }
}


