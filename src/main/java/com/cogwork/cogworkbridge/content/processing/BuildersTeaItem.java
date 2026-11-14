package com.cogwork.cogworkbridge.content.processing;

import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public final class BuildersTeaItem extends ItemFood {
    public BuildersTeaItem() { super(1, .6F, false); setMaxStackSize(16); setAlwaysEdible(); setCreativeTab(ModCreativeTab.INSTANCE); }
    @Override public EnumAction getItemUseAction(ItemStack stack) { return EnumAction.DRINK; }
    @Override public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase user) {
        ItemStack remaining = super.onItemUseFinish(stack, world, user);
        if (world.isRemote || !(user instanceof EntityPlayer player)) return remaining;
        player.addPotionEffect(new PotionEffect(MobEffects.HASTE, 3600, 0, false, false));
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (remaining.isEmpty()) return bottle;
        if (!player.inventory.addItemStackToInventory(bottle)) player.dropItem(bottle, false);
        return remaining;
    }
}
