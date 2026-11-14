package com.cogwork.cogworkbridge.content.processing.casing;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllBlocks;
import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Applies casing materials directly to stripped logs and wood. */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class CasingApplication {
    private CasingApplication() { }
    public static Block casing(ItemStack held) {
        if (held.isEmpty()) return null;
        if (held.getItem() == AllItems.ANDESITE_ALLOY) return AllBlocks.ANDESITE_CASING;
        for (int id : OreDictionary.getOreIDs(held)) {
            String name = OreDictionary.getOreName(id);
            if (name.equals("ingotCopper")) return AllBlocks.COPPER_CASING;
            if (name.equals("ingotBrass")) return AllBlocks.BRASS_CASING;
        }
        if (held.getItem() == AllItems.BRASS_INGOT) return AllBlocks.BRASS_CASING;
        return null;
    }
    public static boolean isStripped(Block block) {
        if (block.getRegistryName() == null) return false;
        String path = block.getRegistryName().getPath();
        boolean stripped = path.startsWith("stripped_") || path.contains("_stripped_");
        return stripped && (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae"));
    }

    public static boolean isStripped(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (int id : OreDictionary.getOreIDs(stack))
            if ("stripped_logs".equals(OreDictionary.getOreName(id))) return true;
        return stack.getItem() instanceof net.minecraft.item.ItemBlock && isStripped(Block.getBlockFromItem(stack.getItem()));
    }

    public static void registerStrippedLogs() {
        // Preserve entries registered by other mods under common equivalent names.
        for (String alias : new String[]{"logWoodStripped", "logStripped", "strippedLog", "strippedWood"}) {
            for (ItemStack existing : OreDictionary.getOres(alias, false)) registerStripped(existing);
        }
        for (Block block : ForgeRegistries.BLOCKS) {
            if (!isStripped(block)) continue;
            Item item = Item.getItemFromBlock(block);
            if (item == null || item == net.minecraft.init.Items.AIR) continue;
            NonNullList<ItemStack> variants = NonNullList.create();
            item.getSubItems(CreativeTabs.SEARCH, variants);
            if (variants.isEmpty()) variants.add(new ItemStack(item));
            for (ItemStack variant : variants) {
                if (variant.getItem() == item) registerStripped(variant);
            }
        }
    }

    private static void registerStripped(ItemStack variant) {
        if (variant.isEmpty()) return;
        for (ItemStack existing : OreDictionary.getOres("stripped_logs")) {
            if (OreDictionary.itemMatches(existing, variant, false)) return;
        }
        ItemStack one = variant.copy();
        one.setCount(1);
        OreDictionary.registerOre("stripped_logs", one);
    }

    private static boolean isStripped(IBlockState state) {
        ItemStack stack = new ItemStack(state.getBlock(), 1, state.getBlock().damageDropped(state));
        return isStripped(stack);
    }
    @SubscribeEvent public static void apply(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().getBlockState(event.getPos()).getBlock() == AllBlocks.BRASS_CASING && hasObsidianPlate(event.getItemStack())) {
            if (!event.getEntityPlayer().canPlayerEdit(event.getPos(), event.getFace(), event.getItemStack())
                    || !event.getWorld().isBlockModifiable(event.getEntityPlayer(), event.getPos())) return;
            event.setCancellationResult(EnumActionResult.SUCCESS); event.setCanceled(true);
            if (!event.getWorld().isRemote && event.getWorld().setBlockState(event.getPos(),
                    com.cogwork.cogworkbridge.registry.BlockFamily.get("railway_casing").getDefaultState(), 3)
                    && !event.getEntityPlayer().capabilities.isCreativeMode) event.getItemStack().shrink(1);
            return;
        }
        Block casing = casing(event.getItemStack());
        if (casing == null || !isStripped(event.getWorld().getBlockState(event.getPos()))) return;
        if (!event.getEntityPlayer().canPlayerEdit(event.getPos(), event.getFace(), event.getItemStack())
                || !event.getWorld().isBlockModifiable(event.getEntityPlayer(), event.getPos())) return;
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
        if (!event.getWorld().isRemote && event.getWorld().setBlockState(event.getPos(), casing.getDefaultState(), 3)
                && !event.getEntityPlayer().capabilities.isCreativeMode) event.getItemStack().shrink(1);
    }
    private static boolean hasObsidianPlate(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (int id : OreDictionary.getOreIDs(stack)) if ("plateObsidian".equals(OreDictionary.getOreName(id))) return true;
        return false;
    }
}
