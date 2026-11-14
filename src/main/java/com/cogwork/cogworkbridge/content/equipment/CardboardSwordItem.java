package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.AllSounds;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class CardboardSwordItem extends ItemSword {
    public CardboardSwordItem() {
        super(ToolMaterial.WOOD);
        setMaxDamage(0);
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.INSTANCE);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment == Enchantments.KNOCKBACK;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return EnchantmentHelper.getEnchantments(book).keySet().stream()
                .allMatch(enchantment -> enchantment == Enchantments.KNOCKBACK);
    }

    @Override
    public int getItemBurnTime(ItemStack stack) {
        return 1000;
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof CardboardSwordItem) || event.getWorld().isRemote) return;
        event.getWorld().playSound(null, event.getPos(), AllSounds.CARDBOARD_BONK,
                SoundCategory.PLAYERS, .5F, 1.85F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = player.getHeldItemMainhand();
        if (!(held.getItem() instanceof CardboardSwordItem)
                || !(event.getTarget() instanceof EntityLivingBase target)) return;
        if (target.getCreatureAttribute() == EnumCreatureAttribute.ARTHROPOD) return;

        event.setCanceled(true);
        if (player.world.isRemote) return;
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                AllSounds.CARDBOARD_BONK, SoundCategory.PLAYERS, .75F, 1.85F);
        int strength = 2 + EnchantmentHelper.getEnchantmentLevel(Enchantments.KNOCKBACK, held);
        if (player.isSprinting() && player.getCooledAttackStrength(.5F) > .9F) strength++;
        float yaw = player.rotationYaw * (float) Math.PI / 180F;
        target.dismountRidingEntity();
        target.knockBack(player, strength * .5F, MathHelper.sin(yaw), -MathHelper.cos(yaw));
        target.velocityChanged = true;
        if (!(target instanceof EntityPlayer) && !(target instanceof IMob))
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 60, 9, true, false));
        player.motionX *= .6D;
        player.motionZ *= .6D;
        player.setSprinting(false);
        player.resetCooldown();
    }
}
