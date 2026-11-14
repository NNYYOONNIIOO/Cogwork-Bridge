package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.registry.AllItems;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class PotatoCannonItem extends net.minecraft.item.ItemBow {
    public record Ammo(float damage, int reload, float velocity, float knockback, int split) { }

    public PotatoCannonItem() {
        setCreativeTab(ModCreativeTab.INSTANCE);
        setMaxStackSize(1);
        setMaxDamage(100);
    }

    public static Ammo ammo(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        String id = item.getRegistryName() == null ? "" : item.getRegistryName().toString();
        if (id.equals("farmers_future_delight:sweet_berries")) return new Ammo(3, 10, 1.05F, .1F, 3);
        if (id.equals("farmers_future_delight:glow_berries")) return new Ammo(2, 10, 1.05F, .05F, 2);
        if (item instanceof xy177.farmersfuturedelight.common.item.ItemSuspiciousStew)
            return new Ammo(3, 40, .8F, .2F, 1);
        if (item == Items.POTATO || item == Items.POISONOUS_POTATO) return new Ammo(5, 15, 1.25F, item == Items.POTATO ? 1.5F : .05F, 1);
        if (item == Items.BAKED_POTATO) return new Ammo(5, 15, 1.25F, .5F, 1);
        if (item == Items.CARROT) return new Ammo(4, 12, 1.45F, .3F, 1);
        if (item == Items.GOLDEN_CARROT) return new Ammo(12, 15, 1.45F, .5F, 1);
        if (item == Items.APPLE) return new Ammo(5, 10, 1.45F, .5F, 1);
        if (item == Items.GOLDEN_APPLE) return new Ammo(1, 100, 1.45F, .05F, 1);
        if (item == Items.BEETROOT) return new Ammo(2, 5, 1.6F, .1F, 1);
        if (item == Items.MELON || item == Items.SPECKLED_MELON) return new Ammo(item == Items.MELON ? 3 : 5, 8, 1.45F, .1F, 1);
        if (item == Item.getItemFromBlock(Blocks.MELON_BLOCK)) return new Ammo(8, 20, .95F, 2F, 1);
        if (item == Item.getItemFromBlock(Blocks.PUMPKIN)) return new Ammo(6, 15, .95F, 2F, 1);
        if (item == Items.PUMPKIN_PIE) return new Ammo(7, 15, 1.1F, .05F, 1);
        if (item == Items.CAKE) return new Ammo(8, 15, 1.1F, .1F, 1);
        if (item == Items.FISH && stack.getMetadata() == 3) return new Ammo(4, 10, 1.1F, .4F, 1);
        if (item == Items.FISH || item == Items.COOKED_FISH) return new Ammo(4, 10, 1.3F, .6F, 1);
        if (item == Items.CHORUS_FRUIT) return new Ammo(3, 15, 1.2F, .05F, 1);
        if (item == AllItems.CHOCOLATE_BERRIES) return new Ammo(4, 10, 1.05F, .2F, 3);
        if (item == AllItems.HONEYED_APPLE) return new Ammo(6, 15, 1.35F, .1F, 1);
        if (item == AllItems.BLAZE_CAKE) return new Ammo(15, 20, 1.1F, .3F, 1);
        return null;
    }
    @Override public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.enchantment.Enchantment enchantment) {
        return enchantment == com.cogwork.cogworkbridge.registry.AllEnchantments.POTATO_RECOVERY
                || enchantment == net.minecraft.init.Enchantments.POWER
                || enchantment == net.minecraft.init.Enchantments.PUNCH
                || enchantment == net.minecraft.init.Enchantments.LOOTING
                || enchantment == net.minecraft.init.Enchantments.UNBREAKING
                || enchantment == net.minecraft.init.Enchantments.MENDING;
    }
    @Override public int getItemEnchantability() { return 1; }

    private ItemStack findAmmo(EntityPlayer player, EnumHand hand) {
        ItemStack other = player.getHeldItem(hand == EnumHand.MAIN_HAND ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND);
        if (ammo(other) != null) return other;
        for (ItemStack candidate : player.inventory.mainInventory) if (ammo(candidate) != null) return candidate;
        return ItemStack.EMPTY;
    }

    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                                EnumFacing face, float x, float y, float z) {
        return onItemRightClick(world, player, hand).getType();
    }

    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack cannon = player.getHeldItem(hand);
        ItemStack ammunition = findAmmo(player, hand);
        Ammo type = ammo(ammunition);
        if (type == null || player.getCooldownTracker().hasCooldown(this)) return new ActionResult<>(EnumActionResult.PASS, cannon);
        if (world.isRemote) com.cogwork.cogworkbridge.CogworkBridge.proxy.potatoCannonFired(hand);
        if (!world.isRemote) {
            ItemStack projectileItem = ammunition.copy();
            projectileItem.setCount(1);
            for (int i = 0; i < type.split(); i++) {
                PotatoProjectileEntity projectile = new PotatoProjectileEntity(world, player, projectileItem, cannon);
                if (i > 0) projectile.disableRecovery();
                projectile.shoot(player, player.rotationPitch, player.rotationYaw, 0F, 2F * type.velocity(), type.split() > 1 ? 5F : 1F);
                world.spawnEntity(projectile);
            }
            if (!player.capabilities.isCreativeMode) {
                ammunition.shrink(1);
                ItemStack tank = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
                if (tank.getItem() instanceof BacktankItem && BacktankItem.getAir(tank) > 0) BacktankItem.consumeAir(player, tank, 4);
                else cannon.damageItem(1, player);
            }
            world.playSound(null, player.posX, player.posY, player.posZ, com.cogwork.cogworkbridge.registry.AllSounds.POTATO_FIRE,
                    SoundCategory.PLAYERS, .8F, 1.1F + world.rand.nextFloat() * .2F);
            player.getCooldownTracker().setCooldown(this, type.reload());
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, cannon);
    }
}
