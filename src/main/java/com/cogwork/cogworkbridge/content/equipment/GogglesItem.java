package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/** A head-slot item without armour points, matching Create's goggles. */
public final class GogglesItem extends Item {
    public GogglesItem() { setMaxStackSize(1); setCreativeTab(ModCreativeTab.INSTANCE); }
    @Override public EntityEquipmentSlot getEquipmentSlot(ItemStack stack) { return EntityEquipmentSlot.HEAD; }
    @Override public boolean isValidArmor(ItemStack stack, EntityEquipmentSlot slot, Entity entity) {
        return slot == EntityEquipmentSlot.HEAD;
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        if (!player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).isEmpty())
            return new ActionResult<>(EnumActionResult.FAIL, held);
        player.setItemStackToSlot(EntityEquipmentSlot.HEAD, held.copy());
        held.setCount(0);
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }
}
