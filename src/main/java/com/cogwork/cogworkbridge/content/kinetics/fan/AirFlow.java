package com.cogwork.cogworkbridge.content.kinetics.fan;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntitySkeletonHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.List;

/** World-space air-volume and collision helpers for fans. */
public final class AirFlow {
    private static final DamageSource FAN_LAVA_DAMAGE = new DamageSource("cogworkbridge.fan_lava");
    private static final DamageSource FAN_FIRE_DAMAGE = new DamageSource("cogworkbridge.fan_fire");

    private static final double[][] PROBES = {
            {0.25D, 0.25D}, {0.25D, 0.75D}, {0.5D, 0.5D}, {0.75D, 0.25D}, {0.75D, 0.75D}
    };

    private AirFlow() {
    }

    static float range(float speed) {
        return 3.0F + 17.0F * Math.min(Math.abs(speed) / 256.0F, 1.0F);
    }

    static boolean pushing(float speed, EnumFacing facing) {
        return facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? speed > 0 : speed < 0;
    }

    public static float unobstructedRange(World world, BlockPos source, EnumFacing facing, float maximum) {
        for (int step = 0; step < maximum; step++) {
            BlockPos pos = source.offset(facing, step + 1);
            if (!world.isBlockLoaded(pos)) {
                return step;
            }
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof BlazeBurnerBlock || EncasedFanTileEntity.isFFDCampfire(state) || state.getBlock() instanceof BlockFence
                    || state.getBlock() instanceof BlockLeaves || state.getBlock() == Blocks.IRON_BARS) {
                continue;
            }
            List<AxisAlignedBB> boxes = new ArrayList<>();
            state.addCollisionBoxToList(world, pos, new AxisAlignedBB(pos), boxes, null, false);
            if (boxes.isEmpty()) {
                continue;
            }
            double depth = 0;
            for (double[] probe : PROBES) {
                double nearest = Double.POSITIVE_INFINITY;
                for (AxisAlignedBB box : boxes) {
                    double candidate = depthOnProbe(box.offset(-pos.getX(), -pos.getY(), -pos.getZ()),
                            facing, probe[0], probe[1]);
                    nearest = Math.min(nearest, candidate);
                }
                if (Double.isInfinite(nearest)) {
                    depth = Double.POSITIVE_INFINITY;
                    break;
                }
                depth = Math.max(depth, nearest);
            }
            if (!Double.isInfinite(depth)) {
                return Math.min(maximum, (float) (step + depth + 1.0D / 32.0D));
            }
        }
        return maximum;
    }

    private static double depthOnProbe(AxisAlignedBB box, EnumFacing facing, double first, double second) {
        EnumFacing.Axis axis = facing.getAxis();
        boolean intersects;
        double min;
        double max;
        if (axis == EnumFacing.Axis.X) {
            intersects = first >= box.minY && first <= box.maxY && second >= box.minZ && second <= box.maxZ;
            min = box.minX;
            max = box.maxX;
        } else if (axis == EnumFacing.Axis.Y) {
            intersects = first >= box.minX && first <= box.maxX && second >= box.minZ && second <= box.maxZ;
            min = box.minY;
            max = box.maxY;
        } else {
            intersects = first >= box.minX && first <= box.maxX && second >= box.minY && second <= box.maxY;
            min = box.minZ;
            max = box.maxZ;
        }
        if (!intersects) {
            return Double.POSITIVE_INFINITY;
        }
        return facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? min : 1.0D - max;
    }

    static void applyLine(World world, BlockPos source, EnumFacing facing, float speed, float distance,
                          EncasedFanTileEntity.Medium[] media) {
        if (distance < 0.25F) {
            return;
        }
        BlockPos first = source.offset(facing);
        double tail = Math.max(0.0D, distance - 1.0D);
        AxisAlignedBB bounds = new AxisAlignedBB(first).expand(
                facing.getXOffset() * tail, facing.getYOffset() * tail, facing.getZOffset() * tail);
        Vec3d origin = new Vec3d(source).add(0.5D, 0.5D, 0.5D);
        double direction = pushing(speed, facing) ? 1.0D : -1.0D;
        for (Entity entity : world.getEntitiesWithinAABBExcludingEntity(null, bounds)) {
            if (!affected(entity)) {
                continue;
            }
            double fromFan = Math.max(0.125D, entity.getPositionVector().distanceTo(origin));
            double resistance = entity.isSneaking() ? 4096.0D : 512.0D;
            double target = Math.abs(speed) * distance / (resistance * fromFan) * direction;
            double dx = MathHelper.clamp(facing.getXOffset() * target - entity.motionX, -5.0D, 5.0D) / 8.0D;
            double dy = MathHelper.clamp(facing.getYOffset() * target - entity.motionY, -5.0D, 5.0D) / 8.0D;
            double dz = MathHelper.clamp(facing.getZOffset() * target - entity.motionZ, -5.0D, 5.0D) / 8.0D;
            applyVelocity(entity, dx, dy, dz);
            entity.fallDistance = 0;

            float flowDistance = (float) ((entity.posX - (source.getX() + 0.5D)) * facing.getXOffset()
                    + (entity.posY - (source.getY() + 0.5D)) * facing.getYOffset()
                    + (entity.posZ - (source.getZ() + 0.5D)) * facing.getZOffset());
            if (media.length == 0) {
                continue;
            }
            int segment = MathHelper.clamp((int) flowDistance, 0, media.length - 1);
            EncasedFanTileEntity.Medium medium = media[segment];
            if (entity instanceof EntityItem item) {
                if (FanItemProcessing.applyToEntity(item, medium))
                    com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, source, "fan_processing");
            } else affectWithMedium(entity, medium);
        }
        for (int i = 0; i < media.length && i < Math.ceil(distance); i++) {
            if (media[i] == EncasedFanTileEntity.Medium.NONE) continue;
            BlockPos flow = source.offset(facing, i + 1);
            for (int below = 0; below <= 1; below++) {
                BlockPos target = flow.down(below);
                if (!world.isBlockLoaded(target)) continue;
                TileEntity tile = world.getTileEntity(target);
                if (tile instanceof FanProcessingHolder holder) holder.applyFanProcessing(media[i], source);
            }
        }
    }

    private static void affectWithMedium(Entity entity, EncasedFanTileEntity.Medium medium) {
        if (medium == EncasedFanTileEntity.Medium.HAUNTING) {
            if (entity instanceof EntityLivingBase living) {
                living.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 30, 0, false, false));
                living.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 20, 1, false, false));
            }
            if (entity instanceof EntityHorse horse) hauntHorse(horse);
            return;
        }
        if (medium == EncasedFanTileEntity.Medium.NONE || entity.isImmuneToFire()) {
            return;
        }
        if (medium == EncasedFanTileEntity.Medium.BLASTING) {
            entity.setFire(10);
            entity.attackEntityFrom(FAN_LAVA_DAMAGE, 4.0F);
        } else if (medium == EncasedFanTileEntity.Medium.SMOKING) {
            entity.setFire(2);
            entity.attackEntityFrom(FAN_FIRE_DAMAGE, 2.0F);
        }
    }

    private static void hauntHorse(EntityHorse horse) {
        int progress = horse.getEntityData().getInteger("CogworkBridgeHaunting");
        if (progress < 100) {
            if (progress % 10 == 0) horse.world.playSound(null, horse.getPosition(),
                    xy177.farmersfuturedelight.common.registry.FFDSounds.SOUL_ESCAPE,
                    SoundCategory.NEUTRAL, 1F, 1.5F * progress / 100F);
            horse.getEntityData().setInteger("CogworkBridgeHaunting", progress + 1);
            return;
        }
        NBTTagCompound data = horse.writeToNBT(new NBTTagCompound());
        data.removeTag("UUIDMost");
        data.removeTag("UUIDLeast");
        EntitySkeletonHorse skeleton = new EntitySkeletonHorse(horse.world);
        skeleton.readFromNBT(data);
        if (!horse.world.spawnEntity(skeleton)) return;
        ItemStack armor = new ItemStack(data.getCompoundTag("ArmorItem"));
        if (!armor.isEmpty()) horse.entityDropItem(armor, 0F);
        horse.world.playSound(null, horse.getPosition(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.NEUTRAL, 1.25F, .65F);
        horse.setDead();
    }

    static void applyVelocity(Entity entity, double x, double y, double z) {
        entity.addVelocity(x, y, z);
        if (entity instanceof EntityPlayerMP) {
            entity.velocityChanged = true;
        }
    }

    static boolean affected(Entity entity) {
        return entity.isEntityAlive() && !(entity instanceof BearingContraptionEntity)
                && !(entity instanceof EntityPlayer player && player.isCreative() && player.capabilities.isFlying);
    }
}
