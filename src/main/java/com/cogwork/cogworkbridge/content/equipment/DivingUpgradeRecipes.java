package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllItems;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreIngredient;
import xy177.farmersfuturedelight.api.WorkstationRecipes;
import xy177.farmersfuturedelight.common.registry.FFDItems;

/** The two upstream smithing upgrades for each piece of diving equipment. */
public final class DivingUpgradeRecipes {
    private DivingUpgradeRecipes() { }

    public static void registerSmithing() {
        ItemStack template = FFDItems.effectiveStack(FFDItems.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        if (template.isEmpty()) return;
        registerPair("netherite_backtank", template, AllItems.COPPER_BACKTANK,
                AllItems.NETHERITE_BACKTANK, FFDItems.NETHERITE_CHESTPLATE);
        registerPair("netherite_diving_helmet", template, AllItems.COPPER_DIVING_HELMET,
                AllItems.NETHERITE_DIVING_HELMET, FFDItems.NETHERITE_HELMET);
        registerPair("netherite_diving_boots", template, AllItems.COPPER_DIVING_BOOTS,
                AllItems.NETHERITE_DIVING_BOOTS, FFDItems.NETHERITE_BOOTS);
    }

    private static void registerPair(String name, ItemStack template, Item copper, Item result, Item netheriteArmor) {
        register(name, template, copper, new OreIngredient("ingotNetherite"), result);
        ItemStack armor = FFDItems.effectiveStack(netheriteArmor);
        if (!armor.isEmpty()) register(name + "_from_netherite", template, armor.getItem(), item(copper), result);
    }

    private static void register(String name, ItemStack template, Item base, Ingredient addition, Item result) {
        WorkstationRecipes.register(WorkstationRecipes.Type.SMITHING, new ResourceLocation(Reference.MOD_ID, name),
                Arrays.asList(Ingredient.fromStacks(template), item(base), addition),
                inputs -> WorkstationRecipes.copySmithingData(inputs.get(1), new ItemStack(result)), 1, 0F);
    }

    // Match used equipment regardless of damage while retaining an example for JEI.
    private static Ingredient item(Item expected) {
        return new Ingredient(new ItemStack(expected)) {
            @Override public boolean apply(@Nullable ItemStack stack) {
                return stack != null && !stack.isEmpty() && stack.getItem() == expected;
            }
            @Override public boolean isSimple() { return false; }
        };
    }
}
