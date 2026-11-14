package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.Reference;
import com.cogwork.cogworkbridge.registry.ModCreativeTab;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class DivingGearItem extends ItemArmor {
    private static final StackWalker SWIMMING_CALLERS = StackWalker.getInstance();
    private static final ArmorMaterial COPPER = EnumHelper.addArmorMaterial("COGWORKBRIDGE_COPPER",
            "create:copper_diving", 15, new int[]{1, 3, 4, 2}, 7, SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0F);
    private static final ArmorMaterial NETHERITE = EnumHelper.addArmorMaterial("COGWORKBRIDGE_NETHERITE",
            "create:netherite_diving", 37, new int[]{3, 6, 8, 3}, 15, SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 3F);
    protected final boolean netherite;

    public DivingGearItem(EntityEquipmentSlot slot, boolean netherite) {
        super(netherite ? NETHERITE : COPPER, 0, slot);
        this.netherite = netherite;
        setCreativeTab(ModCreativeTab.INSTANCE);
    }

    public static final class Netherite extends DivingGearItem implements xy177.farmersfuturedelight.common.item.INetheriteItem {
        public Netherite(EntityEquipmentSlot slot) { super(slot, true); }
    }
    public final boolean isNetherite() { return netherite; }

    @Override public boolean getIsRepairable(ItemStack armor, ItemStack material) {
        if (!material.isEmpty()) {
            String expected = netherite ? "ingotNetherite" : "ingotCopper";
            for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(material))
                if (expected.equals(net.minecraftforge.oredict.OreDictionary.getOreName(id))) return true;
            var id = material.getItem().getRegistryName();
            if (id != null && id.getNamespace().equals("farmers_future_delight")
                    && id.getPath().equals(netherite ? "netherite_ingot" : "copper_ingot")) return true;
        }
        return super.getIsRepairable(armor, material);
    }

    @Override public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "create:textures/models/armor/" + (netherite ? "netherite" : "copper")
                + "_diving_layer_" + (slot == EntityEquipmentSlot.LEGS ? "2" : "1") + ".png";
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        ItemStack helmet = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        ItemStack tank = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        boolean lava = entity.isInLava();
        if (helmet.getItem() instanceof DivingGearItem diving && diving.armorType == EntityEquipmentSlot.HEAD
                && tank.getItem() instanceof BacktankItem backtank && BacktankItem.getAir(tank) > 0
                && (entity.isInsideOfMaterial(Material.WATER) || lava && diving.netherite && backtank.netherite)) {
            if (!entity.world.isRemote && entity instanceof EntityPlayer player)
                com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(player, lava ? "diving_suit_lava" : "diving_suit");
            if (!lava) entity.setAir(300);
            if (!entity.world.isRemote && entity.ticksExisted % 20 == 0) BacktankItem.consumeAir(entity, tank, 1);
        }
        if (hasNetheriteSuit(entity)) {
            entity.extinguish();
            // FFD normally samples a water block above the swimmer before following
            // an upward look. Lava cannot satisfy that sample; seed upward motion
            // so its existing swim travel can apply the same look response.
            if (lava && entity instanceof EntityPlayer player && player.isSprinting()
                    && !player.isSneaking() && !player.isRiding() && !player.capabilities.isFlying
                    && player.getLookVec().y > 0 && player.motionY <= 0) player.motionY = 1.0E-6D;
        }
        ItemStack boots = entity.getItemStackFromSlot(EntityEquipmentSlot.FEET);
        if (!(boots.getItem() instanceof DivingGearItem diving) || diving.armorType != EntityEquipmentSlot.FEET) {
            entity.getEntityData().removeTag("HeavyBoots");
            entity.getEntityData().removeTag("CogworkBridgeLavaGrounded");
            return;
        }
        entity.getEntityData().setBoolean("HeavyBoots", true);
        if (!entity.isInLava()) entity.getEntityData().removeTag("CogworkBridgeLavaGrounded");
        if (!entity.isInWater() || entity instanceof EntityPlayer p && p.capabilities.isFlying) return;
        entity.onGround = entity.onGround || entity.collidedVertically;
        if (entity.isJumping && entity.onGround) { entity.motionY += .5D; entity.onGround = false; }
        else entity.motionY -= .05D;
        double horizontal = Math.sqrt(entity.motionX * entity.motionX + entity.motionZ * entity.motionZ);
        if (horizontal < .145D && (entity.moveForward > 0 || entity.moveStrafing != 0) && !entity.isSneaking()) {
            entity.motionX *= 1.3D;
            entity.motionZ *= 1.3D;
        }
    }

    /** Called immediately after vanilla lava movement, before its drag and gravity. */
    public static void afterFluidTravel(Object value) {
        if (!(value instanceof EntityLivingBase entity) || !entity.isInLava() || entity.isInWater()
                || entity instanceof EntityPlayer player && player.capabilities.isFlying) return;
        ItemStack boots = entity.getItemStackFromSlot(EntityEquipmentSlot.FEET);
        if (!(boots.getItem() instanceof DivingGearItem gear) || !gear.netherite || gear.armorType != EntityEquipmentSlot.FEET) return;
        double y = entity.motionY;
        double vertical = y < 0 ? Math.max(0, 2.5D - Math.abs(y) * 2) : 1;
        double horizontal = 1.75D;
        if (!entity.onGround) {
            if (entity.isJumping && entity.getEntityData().hasKey("CogworkBridgeLavaGrounded"))
                vertical = y == 0 ? 0 : (entity.isInsideOfMaterial(Material.LAVA) ? 1 : .5D) / y;
            else if (y > 0) vertical = 1.3D;
            entity.getEntityData().removeTag("CogworkBridgeLavaGrounded");
        } else {
            entity.getEntityData().setBoolean("CogworkBridgeLavaGrounded", true);
            horizontal = entity.isSprinting() ? 1.85D : 1.75D;
        }
        entity.motionX *= horizontal;
        entity.motionY *= vertical;
        entity.motionZ *= horizontal;
    }

    public static boolean hasNetheriteSuit(EntityLivingBase entity) {
        ItemStack helmet = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        ItemStack tank = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        ItemStack legs = entity.getItemStackFromSlot(EntityEquipmentSlot.LEGS);
        ItemStack boots = entity.getItemStackFromSlot(EntityEquipmentSlot.FEET);
        return helmet.getItem() instanceof DivingGearItem head && head.netherite
                && tank.getItem() instanceof BacktankItem backtank && backtank.netherite && BacktankItem.getAir(tank) > 0
                && isNetheriteArmor(legs, EntityEquipmentSlot.LEGS) && isNetheriteArmor(boots, EntityEquipmentSlot.FEET);
    }
    private static boolean isNetheriteArmor(ItemStack stack, EntityEquipmentSlot slot) {
        if (!(stack.getItem() instanceof ItemArmor armor) || armor.armorType != slot) return false;
        return armor instanceof xy177.farmersfuturedelight.common.item.INetheriteItem
                || armor instanceof DivingGearItem diving && diving.netherite
                || armor.getArmorMaterial().name().toLowerCase(java.util.Locale.ROOT).contains("netherite");
    }

    /** Native fire checks also read the field directly, without a damage event. */
    public static boolean equipmentFireImmunity(Object value, boolean original) {
        return original || value instanceof EntityLivingBase entity && hasNetheriteSuit(entity);
    }

    private static boolean canSwimInLava(Object value) {
        return value instanceof EntityPlayer player && !player.capabilities.isFlying && !player.isSpectator()
                && !player.isRiding() && player.isInLava() && hasNetheriteSuit(player);
    }

    /** Vanilla excludes water from sprint dust, but lava swimming also needs the sprint flag. */
    public static boolean runningParticleSprintQuery(boolean original, Object value) {
        return original && !canSwimInLava(value);
    }

    /**
     * FFD excludes its core package from LaunchWrapper transformers. Extend only
     * the vanilla queries made by its actual swimming entry points, keeping lava
     * distinct from water for drowning, fluids, machines and every other caller.
     */
    private static boolean ffdSwimmingQuery() {
        return SWIMMING_CALLERS.walk(frames -> frames.limit(12).anyMatch(frame -> {
            String type = frame.getClassName(), method = frame.getMethodName();
            if (type.equals("xy177.farmersfuturedelight.core.FFDGameplayHooks"))
                return method.equals("updateSwimmingState") || method.equals("handleSwimmingTravel");
            if (type.equals("xy177.farmersfuturedelight.client.FFDClientSwimmingHooks"))
                return method.equals("updateLocalPlayer") || method.equals("updateSwimmingInput")
                        || method.equals("applyPlayerRotations");
            return false;
        }));
    }

    public static boolean swimmingFluidQuery(boolean original, Object value) {
        return original || canSwimInLava(value) && ffdSwimmingQuery();
    }

    public static boolean swimmingEyeQuery(boolean original, Object value, Object material) {
        if (original || material != Material.WATER || !canSwimInLava(value)) return original;
        return ffdSwimmingQuery() && ((EntityPlayer) value).isInsideOfMaterial(Material.LAVA);
    }

    @SubscribeEvent
    public static void protectFromFire(LivingAttackEvent event) {
        if (event.getSource().isFireDamage() && hasNetheriteSuit(event.getEntityLiving())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void underwaterMining(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack helmet = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        if (helmet.getItem() instanceof DivingGearItem gear && gear.armorType == EntityEquipmentSlot.HEAD
                && player.isInsideOfMaterial(Material.WATER)
                && !net.minecraft.enchantment.EnchantmentHelper.getAquaAffinityModifier(player))
            event.setNewSpeed(event.getNewSpeed() * 5F);
    }
}
