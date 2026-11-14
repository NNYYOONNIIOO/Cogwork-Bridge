package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class CardboardArmorItem extends ItemArmor {
    private static final java.util.Map<EntityPlayer, Float> ORIGINAL_EYE_HEIGHT = new java.util.WeakHashMap<>();
    private static final java.util.Set<EntityLiving> STOP_PENDING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final ArmorMaterial MATERIAL = EnumHelper.addArmorMaterial("COGWORKBRIDGE_CARDBOARD",
            "create:cardboard", 5, new int[]{1, 1, 1, 1}, 4, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0F);

    public CardboardArmorItem(EntityEquipmentSlot slot) {
        super(MATERIAL, 0, slot);
        setCreativeTab(ModCreativeTab.INSTANCE);
    }

    @Override public int getItemBurnTime(ItemStack stack) { return 1000; }
    @Override public boolean getIsRepairable(ItemStack armor, ItemStack material) {
        return !material.isEmpty() && material.getItem() == com.cogwork.cogworkbridge.registry.AllItems.CARDBOARD
                || super.getIsRepairable(armor, material);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "create:textures/models/armor/cardboard_layer_" + (slot == EntityEquipmentSlot.LEGS ? "2" : "1") + ".png";
    }

    public static boolean isHiding(EntityLivingBase entity) {
        if (entity == null || !entity.isSneaking()) return false;
        if (entity instanceof EntityPlayer player && player.capabilities.isFlying) return false;
        for (EntityEquipmentSlot slot : new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET})
            if (!(entity.getItemStackFromSlot(slot).getItem() instanceof CardboardArmorItem)) return false;
        return true;
    }

    @SubscribeEvent
    public static void clearTarget(LivingSetAttackTargetEvent event) {
        if (!event.getEntityLiving().world.isRemote && event.getEntityLiving() instanceof EntityLiving mob
                && isHiding(event.getTarget())) {
            STOP_PENDING.add(mob);
            mob.setAttackTarget(null);
        }
    }

    @SubscribeEvent
    public static void hideFromDetection(net.minecraftforge.event.entity.player.PlayerEvent.Visibility event) {
        if (isHiding(event.getEntityPlayer())) event.modifyVisibility(0);
    }

    private static void stopTargetTasks(EntityLiving mob) {
        var running = mob.targetTasks.executingTaskEntries.iterator();
        while (running.hasNext()) {
            var entry = running.next();
            if (!(entry.action instanceof net.minecraft.entity.ai.EntityAITarget)) continue;
            entry.action.resetTask();
            entry.using = false;
            running.remove();
        }
    }

    @SubscribeEvent
    public static void stopPursuit(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityLiving mob) || mob.world.isRemote || mob.ticksExisted % 16 != 0) return;
        if (STOP_PENDING.remove(mob)) stopTargetTasks(mob);
        if (isHiding(mob.getAttackTarget())) { mob.setAttackTarget(null); stopTargetTasks(mob); }
        if (isHiding(mob.getRevengeTarget())) { mob.setRevengeTarget(null); mob.attackingPlayer = null; }
        if (isHiding(mob.attackingPlayer)) mob.attackingPlayer = null;
        if (mob instanceof net.minecraft.entity.monster.EntityPigZombie pig && pig.angerTargetUUID != null
                && isHiding(pig.world.getPlayerEntityByUUID(pig.angerTargetUUID))) {
            pig.angerLevel = 0;
            pig.randomSoundDelay = 0;
            pig.angerTargetUUID = null;
            pig.setAttackTarget(null);
            pig.setRevengeTarget(null);
            stopTargetTasks(pig);
        }
    }
    @SubscribeEvent
    public static void resizePlayer(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        if (isHiding(player) && !player.isPlayerSleeping() && !player.isElytraFlying()) {
            ORIGINAL_EYE_HEIGHT.putIfAbsent(player, player.eyeHeight);
            player.height = .8F; player.eyeHeight = .6F;
            net.minecraft.util.math.AxisAlignedBB box = player.getEntityBoundingBox();
            player.setEntityBoundingBox(new net.minecraft.util.math.AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.minY + .8D, box.maxZ));
            return;
        }
        Float oldEye = ORIGINAL_EYE_HEIGHT.get(player);
        if (oldEye == null) return;
        float restoredHeight = player.isPlayerSleeping() ? .2F : player.isElytraFlying() ? .6F : player.isSneaking() ? 1.65F : 1.8F;
        net.minecraft.util.math.AxisAlignedBB box = player.getEntityBoundingBox();
        net.minecraft.util.math.AxisAlignedBB restored = new net.minecraft.util.math.AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.minY + restoredHeight, box.maxZ);
        if (!player.world.getCollisionBoxes(player, restored).isEmpty()) {
            player.height = .8F; player.eyeHeight = .6F;
            player.setEntityBoundingBox(new net.minecraft.util.math.AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.minY + .8D, box.maxZ));
            return;
        }
        player.height = restoredHeight; player.eyeHeight = oldEye; player.setEntityBoundingBox(restored);
        ORIGINAL_EYE_HEIGHT.remove(player);
    }
}
