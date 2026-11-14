package com.cogwork.cogworkbridge.registry;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.content.equipment.BacktankItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class AllEnchantments {
    public static final Enchantment CAPACITY = new Enchantment(Enchantment.Rarity.COMMON,
            EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST}) {
        @Override public int getMaxLevel() { return 3; }
        @Override public int getMinEnchantability(int level) { return 15 + 15 * (level - 1); }
        @Override public int getMaxEnchantability(int level) { return 45 + 15 * (level - 1); }
        @Override public boolean canApply(ItemStack stack) { return stack.getItem() instanceof BacktankItem; }
        @Override public boolean canApplyAtEnchantingTable(ItemStack stack) { return canApply(stack); }
    }.setName("cogworkbridge.capacity").setRegistryName(Reference.MOD_ID, "capacity");
    public static final Enchantment POTATO_RECOVERY = new Enchantment(Enchantment.Rarity.COMMON,
            EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}) {
        @Override public int getMaxLevel() { return 3; }
        @Override public int getMinEnchantability(int level) { return 15 + 15 * (level - 1); }
        @Override public int getMaxEnchantability(int level) { return 45 + 15 * (level - 1); }
        @Override public boolean canApply(ItemStack stack) { return stack.getItem() instanceof com.cogwork.cogworkbridge.content.equipment.PotatoCannonItem; }
        @Override public boolean canApplyAtEnchantingTable(ItemStack stack) { return canApply(stack); }
    }.setName("cogworkbridge.potato_recovery").setRegistryName(Reference.MOD_ID, "potato_recovery");

    private AllEnchantments() { }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<Enchantment> event) { event.getRegistry().registerAll(CAPACITY, POTATO_RECOVERY); }
}
