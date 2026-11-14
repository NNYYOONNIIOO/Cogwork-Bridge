package com.cogwork.cogworkbridge.content.logistics.packagebox;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

/** A persistent, hand-pickable parcel with the dimensions of its package style. */
public final class PackageEntity extends EntityItem {
    public PackageEntity(World world) { super(world); lifespan = Integer.MAX_VALUE; }
    public PackageEntity(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack); lifespan = Integer.MAX_VALUE; updateSize();
    }
    private void updateSize() {
        ItemStack stack = getItem();
        if (!stack.isEmpty() && stack.getItem() instanceof PackageItem item)
            setSize(item.packageWidth() / 16F, item.packageHeight() / 16F);
    }
    @Override public void setItem(ItemStack stack) { super.setItem(stack); updateSize(); }
    @Override public void notifyDataManagerChange(net.minecraft.network.datasync.DataParameter<?> key) {
        super.notifyDataManagerChange(key); updateSize();
    }
    @Override public void onUpdate() {
        super.onUpdate();
        if (!world.isRemote && !isDead && isInWater()) spill();
    }
    @Override public void onCollideWithPlayer(EntityPlayer player) { }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBeAttackedWithItem() { return !isDead; }
    @Override public boolean canBePushed() { return true; }
    @Override public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (!player.getHeldItem(hand).isEmpty() || isDead) return false;
        if (!world.isRemote) { player.setHeldItem(hand, getItem().copy()); setDead(); }
        return true;
    }
    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        if (world.isRemote || isDead || isEntityInvulnerable(source) || source == DamageSource.FALL) return false;
        if (source == DamageSource.OUT_OF_WORLD) { setDead(); return false; }
        if (source == DamageSource.IN_WALL && ticksExisted < 20) return false;
        if (source.getTrueSource() instanceof EntityPlayer player && !player.capabilities.allowEdit) return false;
        if (source.isFireDamage()) {
            if (!isBurning()) { setFire(5); return false; }
            var data = getEntityData();
            float health = data.hasKey("PackageFireHealth") ? data.getFloat("PackageFireHealth") : 5F;
            health -= .15F; data.setFloat("PackageFireHealth", health);
            if (health <= .5F) spill();
            return false;
        }
        spill(); return true;
    }
    private void spill() {
        ItemStack parcel = getItem();
        if (!PackageItem.isPackage(parcel)) { setDead(); return; }
        ItemStackHandler contents = PackageItem.contents(parcel);
        boolean remaining = false;
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack stack = contents.getStackInSlot(i).copy();
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ItemMonsterPlacer && spawnEgg(stack)) {
                stack.shrink(1); contents.setStackInSlot(i, stack.copy());
            }
            if (stack.isEmpty()) continue;
            EntityItem drop = new EntityItem(world, posX, posY + height * .5, posZ, stack);
            drop.setDefaultPickupDelay();
            if (world.spawnEntity(drop)) contents.setStackInSlot(i, ItemStack.EMPTY);
            else remaining = true;
        }
        if (!remaining) setDead();
        else { ItemStack partial = parcel.copy(); PackageItem.setContents(partial, contents); setItem(partial); }
    }
    private boolean spawnEgg(ItemStack stack) {
        net.minecraft.util.ResourceLocation id = ItemMonsterPlacer.getNamedIdFrom(stack);
        if (id == null || !EntityList.ENTITY_EGGS.containsKey(id)) return false;
        Entity created = EntityList.createEntityByIDFromName(id, world);
        if (!(created instanceof EntityLiving living)) return false;
        living.setLocationAndAngles(posX, posY, posZ, world.rand.nextFloat() * 360F, 0F);
        living.rotationYawHead = living.rotationYaw; living.renderYawOffset = living.rotationYaw;
        if (net.minecraftforge.event.ForgeEventFactory.doSpecialSpawn(living, world, (float) posX, (float) posY, (float) posZ, null)) return false;
        living.onInitialSpawn(world.getDifficultyForLocation(new net.minecraft.util.math.BlockPos(living)), null);
        if (stack.hasDisplayName()) living.setCustomNameTag(stack.getDisplayName());
        if (!world.spawnEntity(living)) return false;
        living.playLivingSound(); return true;
    }
}
