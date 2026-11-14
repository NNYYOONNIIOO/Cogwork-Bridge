package com.cogwork.cogworkbridge.content.equipment;

import com.cogwork.cogworkbridge.registry.AllItems;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;

public final class PotatoProjectileEntity extends EntityThrowable {
    private static final DataParameter<ItemStack> ITEM = EntityDataManager.createKey(PotatoProjectileEntity.class, DataSerializers.ITEM_STACK);
    private static final DataParameter<Integer> STUCK = EntityDataManager.createKey(PotatoProjectileEntity.class, DataSerializers.VARINT);
    private float damageMultiplier = 1F;
    private float additionalKnockback;
    private float recoveryChance;
    private Vec3d stuckOffset;
    private double stuckFallSpeed;
    private int missingTargetTicks;

    public PotatoProjectileEntity(World world) { super(world); setSize(.25F, .25F); }
    public PotatoProjectileEntity(World world, EntityLivingBase shooter, ItemStack item, ItemStack cannon) {
        super(world, shooter);
        setSize(.25F, .25F);
        dataManager.set(ITEM, item.copy());
        damageMultiplier += EnchantmentHelper.getEnchantmentLevel(Enchantments.POWER, cannon) * .2F;
        additionalKnockback = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH, cannon) * .5F;
        int recovery = EnchantmentHelper.getEnchantmentLevel(com.cogwork.cogworkbridge.registry.AllEnchantments.POTATO_RECOVERY, cannon);
        if (recovery > 0) recoveryChance = .125F + recovery * .125F;
    }
    @Override protected void entityInit() { super.entityInit(); dataManager.register(ITEM, ItemStack.EMPTY); dataManager.register(STUCK, -1); }
    public ItemStack getItem() { return dataManager.get(ITEM); }
    public void disableRecovery() { recoveryChance = 0F; }
    public boolean isStuck() { return dataManager.get(STUCK) >= 0; }
    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (!getItem().isEmpty()) tag.setTag("Item", getItem().writeToNBT(new NBTTagCompound()));
        tag.setFloat("DamageMultiplier", damageMultiplier);
        tag.setFloat("Knockback", additionalKnockback);
        tag.setFloat("Recovery", recoveryChance);
    }
    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey("Item", 10)) dataManager.set(ITEM, new ItemStack(tag.getCompoundTag("Item")));
        damageMultiplier = tag.hasKey("DamageMultiplier") ? tag.getFloat("DamageMultiplier") : 1F;
        additionalKnockback = tag.getFloat("Knockback");
        recoveryChance = tag.getFloat("Recovery");
    }
    private boolean sticky(ItemStack stack) {
        return stack.getItem() == Items.PUMPKIN_PIE || stack.getItem() == Items.CAKE || stack.getItem() == AllItems.BLAZE_CAKE
                || stack.getItem() == Items.FISH || stack.getItem() == Items.COOKED_FISH;
    }
    @Override public void onUpdate() {
        int targetId = dataManager.get(STUCK);
        if (targetId < 0) { super.onUpdate(); return; }
        Entity target = world.getEntityByID(targetId);
        if (target == null || target.isDead) {
            if (!world.isRemote || ++missingTargetTicks > 40) setDead();
            return;
        }
        if (stuckOffset == null) stuckOffset = getPositionVector().subtract(target.getPositionVector());
        stuckFallSpeed += .007D;
        stuckOffset = stuckOffset.add(0, -stuckFallSpeed, 0);
        Vec3d location = target.getPositionVector().add(stuckOffset);
        lastTickPosX = posX; lastTickPosY = posY; lastTickPosZ = posZ;
        setPosition(location.x, location.y, location.z); ticksExisted++;
        if (posY < target.posY - .1D) { if (!world.isRemote) world.setEntityState(this, (byte) 3); setDead(); }
    }
    private void recover(ItemStack item) {
        if (rand.nextFloat() < recoveryChance) entityDropItem(item.copy(), 0);
        else if (item.getItem() instanceof xy177.farmersfuturedelight.common.item.ItemSuspiciousStew)
            entityDropItem(new ItemStack(Items.BOWL), 0);
    }
    @Override protected void onImpact(RayTraceResult hit) {
        if (world.isRemote) return;
        ItemStack item = getItem();
        PotatoCannonItem.Ammo ammo = PotatoCannonItem.ammo(item);
        if (ammo == null) { setDead(); return; }
        world.playSound(null, posX, posY, posZ, com.cogwork.cogworkbridge.registry.AllSounds.POTATO_HIT, net.minecraft.util.SoundCategory.PLAYERS, .7F, 1F);
        boolean recoverable = true;
        if (hit.entityHit != null) {
            if (item.getItem() == Items.BAKED_POTATO || item.getItem() == AllItems.BLAZE_CAKE)
                hit.entityHit.setFire(item.getItem() == AllItems.BLAZE_CAKE ? 12 : 3);
            if (!hit.entityHit.attackEntityFrom(DamageSource.causeThrownDamage(this, getThrower()), ammo.damage() * damageMultiplier)) { setDead(); return; }
            if (hit.entityHit instanceof EntityLivingBase living) {
                living.knockBack(this, ammo.knockback() + additionalKnockback, -motionX, -motionZ);
                if (item.getItem() instanceof xy177.farmersfuturedelight.common.item.ItemSuspiciousStew)
                    for (PotionEffect effect : xy177.farmersfuturedelight.common.item.ItemSuspiciousStew.getEffects(item))
                        living.addPotionEffect(new PotionEffect(effect));
                if (item.getItem() == Items.POISONOUS_POTATO) living.addPotionEffect(new PotionEffect(MobEffects.POISON, 160, 0));
                if (item.getItem() == AllItems.HONEYED_APPLE) living.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 160, 1));
                if (item.getItem() == Items.SPECKLED_MELON) living.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 100, 0));
                if (item.getItem().getRegistryName() != null && item.getItem().getRegistryName().toString().equals("farmers_future_delight:glow_berries")) {
                    living.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 200, 0)); recoverable = false;
                }
                if (item.getItem() == Items.FISH && item.getMetadata() == 3) {
                    living.addPotionEffect(new PotionEffect(MobEffects.POISON, 1200, 1));
                    living.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 300, 2));
                    living.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 300, 1)); recoverable = false;
                }
                if (item.getItem() == Items.CHORUS_FRUIT) {
                    for (int attempt = 0; attempt < 16; attempt++) {
                        double x = living.posX + (rand.nextDouble() - .5D) * 20D;
                        double y = Math.max(0, Math.min(world.getHeight() - 1, living.posY + rand.nextInt(20) - 10));
                        double z = living.posZ + (rand.nextDouble() - .5D) * 20D;
                        if (living.attemptTeleport(x, y, z)) { recoverable = false; break; }
                    }
                }
                if (item.getItem() == Items.GOLDEN_APPLE) {
                    if (item.getMetadata() == 0 && living instanceof net.minecraft.entity.monster.EntityZombieVillager zombie && living.isPotionActive(MobEffects.WEAKNESS)
                            && getThrower() instanceof net.minecraft.entity.player.EntityPlayer player) {
                        net.minecraft.util.NonNullList<ItemStack> offhand = player.inventory.offHandInventory;
                        ItemStack old = offhand.get(0); offhand.set(0, new ItemStack(Items.GOLDEN_APPLE));
                        try { player.interactOn(zombie, EnumHand.OFF_HAND); } finally { offhand.set(0, old); }
                    } else {
                        living.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, item.getMetadata() > 0 ? 400 : 100, 1));
                        living.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 2400, item.getMetadata() > 0 ? 3 : 0));
                        if (item.getMetadata() > 0) {
                            living.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 6000, 0));
                            living.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 6000, 0));
                        }
                    }
                    recoverable = false;
                }
            }
            if (recoverable) recover(item);
            if (sticky(item) && hit.entityHit.isEntityAlive()) {
                stuckOffset = getPositionVector().subtract(hit.entityHit.getPositionVector());
                dataManager.set(STUCK, hit.entityHit.getEntityId()); motionX = motionY = motionZ = 0D; return;
            }
        } else if (hit.typeOfHit == RayTraceResult.Type.BLOCK && (item.getItem() == Items.POTATO || item.getItem() == Items.CARROT)) {
            BlockPos ground = hit.getBlockPos();
            BlockPos target = ground.up();
            Block crop = item.getItem() == Items.POTATO ? Blocks.POTATOES : Blocks.CARROTS;
            if (hit.sideHit == EnumFacing.UP && world.getBlockState(ground).getBlock() == Blocks.FARMLAND && world.isAirBlock(target)
                    && getThrower() instanceof net.minecraft.entity.player.EntityPlayer player && world.isBlockModifiable(player, target)) {
                world.setBlockState(target, crop.getDefaultState(), 3); recoverable = false;
            }
            if (recoverable) recover(item);
        } else if (hit.typeOfHit == RayTraceResult.Type.BLOCK) {
            if (item.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.MELON_BLOCK)
                    || item.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.PUMPKIN)) {
                BlockPos target = hit.getBlockPos().offset(hit.sideHit);
                Block block = Block.getBlockFromItem(item.getItem());
                if (world.isAirBlock(target) && getThrower() instanceof net.minecraft.entity.player.EntityPlayer player && world.isBlockModifiable(player, target)) {
                    if (hit.sideHit == EnumFacing.UP) world.setBlockState(target, block.getDefaultState(), 3);
                    else { net.minecraft.entity.item.EntityFallingBlock falling = new net.minecraft.entity.item.EntityFallingBlock(world, target.getX() + .5D, target.getY(), target.getZ() + .5D, block.getDefaultState()); falling.fallTime = 1; world.spawnEntity(falling); }
                    recoverable = false;
                }
            }
            if (recoverable) recover(item);
        }
        world.setEntityState(this, (byte) 3);
        setDead();
    }
    @Override public void handleStatusUpdate(byte id) {
        if (id != 3 || getItem().isEmpty()) { super.handleStatusUpdate(id); return; }
        for (int i = 0; i < 8; i++) world.spawnParticle(EnumParticleTypes.ITEM_CRACK, posX, posY, posZ,
                (rand.nextDouble() - .5D) * .15D, rand.nextDouble() * .15D, (rand.nextDouble() - .5D) * .15D,
                net.minecraft.item.Item.getIdFromItem(getItem().getItem()), getItem().getMetadata());
    }
}
