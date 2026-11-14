package com.cogwork.cogworkbridge.content.fluids;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** The effects of fluid flowing out of an open pipe, adapted to the 1.12 world API. */
final class OpenPipeEffects {
    private OpenPipeEffects() { }

    static boolean isVirtualEffectFluid(FluidStack stack) {
        return PotionFluid.isPotionFluid(stack) || isMilk(stack.getFluid());
    }

    private static boolean isMilk(Fluid fluid) {
        String name = fluid.getName();
        return "milk".equals(name) || name.endsWith(":milk");
    }

    static void apply(World world, BlockPos target, EnumFacing side, FluidStack fluid) {
        if (world == null || world.isRemote) return;
        AxisAlignedBB area = new AxisAlignedBB(target).expand(0, side == EnumFacing.DOWN ? -2 : -1, 0);
        if (PotionFluid.isPotionFluid(fluid)) {
            for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
                for (PotionEffect effect : PotionUtils.getEffectsFromStack(PotionFluid.toBottle(fluid))) {
                    if (!entity.isPotionApplicable(effect)) continue;
                    Potion potion = effect.getPotion();
                    if (potion.isInstant()) {
                        potion.affectEntity(null, null, entity, effect.getAmplifier(), 0.5D);
                    } else {
                        entity.addPotionEffect(new PotionEffect(effect));
                    }
                }
            }
            return;
        }
        if (world.getTotalWorldTime() % 5 != 0) return;
        if (fluid.getFluid() == FluidRegistry.WATER) {
            for (Entity entity : world.getEntitiesWithinAABB(Entity.class, area)) {
                if (entity.isBurning()) entity.extinguish();
            }
            int bottom = target.getY() - (side == EnumFacing.DOWN ? 2 : 1);
            for (int y = bottom; y <= target.getY(); y++) {
                BlockPos at = new BlockPos(target.getX(), y, target.getZ());
                if (!world.isBlockLoaded(at)) continue;
                IBlockState state = world.getBlockState(at);
                if (state.getBlock() == Blocks.FIRE) world.setBlockToAir(at);
            }
        } else if (fluid.getFluid() == FluidRegistry.LAVA) {
            for (Entity entity : world.getEntitiesWithinAABB(Entity.class, area)) {
                if (!entity.isImmuneToFire()) {
                    entity.attackEntityFrom(DamageSource.LAVA, 2.0F);
                    entity.setFire(3);
                }
            }
        } else if (isMilk(fluid.getFluid())) {
            for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
                entity.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
            }
        }
    }
}
