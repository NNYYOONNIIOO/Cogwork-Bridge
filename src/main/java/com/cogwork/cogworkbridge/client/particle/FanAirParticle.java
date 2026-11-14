package com.cogwork.cogworkbridge.client.particle;

import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanBlock;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity;
import com.cogwork.cogworkbridge.content.kinetics.fan.EncasedFanTileEntity.Medium;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class FanAirParticle extends Particle {
    private final EncasedFanTileEntity fan;
    private final EnumFacing facing;
    private final boolean pushing;

    public FanAirParticle(EncasedFanTileEntity fan, EnumFacing facing, boolean pushing) {
        super(fan.getWorld(), fan.getPos().getX() + 0.5D,
                fan.getPos().getY() + 0.5D, fan.getPos().getZ() + 0.5D);
        this.fan = fan;
        this.facing = facing;
        this.pushing = pushing;
        double offset = pushing ? 0.5D : fan.getAirRange() + 0.5D;
        setPosition(posX + facing.getXOffset() * offset + (rand.nextDouble() - 0.5D) * 0.5D,
                posY + facing.getYOffset() * offset + (rand.nextDouble() - 0.5D) * 0.5D,
                posZ + facing.getZOffset() * offset + (rand.nextDouble() - 0.5D) * 0.5D);
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        particleMaxAge = 40;
        particleScale *= 0.75F;
        canCollide = false;
        setRBGColorF(0.933F, 0.933F, 0.933F);
        setAlphaF(0.25F);
        setParticleTextureIndex(0);
    }

    @Override
    public void onUpdate() {
        BlockPos origin = fan.getPos();
        if (fan.isInvalid() || fan.getWorld() != world || fan.getSpeed() == 0.0F
                || fan.getAirRange() <= 0.0F || !world.isBlockLoaded(origin)) {
            setExpired();
            return;
        }
        IBlockState state = world.getBlockState(origin);
        boolean currentPushing = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE
                ? fan.getSpeed() > 0.0F : fan.getSpeed() < 0.0F;
        if (!(state.getBlock() instanceof EncasedFanBlock)
                || state.getValue(EncasedFanBlock.FACING) != facing || currentPushing != pushing) {
            setExpired();
            return;
        }
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (particleAge++ >= particleMaxAge) {
            setExpired();
            return;
        }
        double distance = Math.abs((posX - origin.getX() - 0.5D) * facing.getXOffset()
                + (posY - origin.getY() - 0.5D) * facing.getYOffset()
                + (posZ - origin.getZ() - 0.5D) * facing.getZOffset()) - 0.5D;
        float range = fan.getAirRange();
        if (distance > range + 1.0D || distance < -0.25D) {
            setExpired();
            return;
        }
        double motion = (range - distance + 1.0D) / 16.0D * (pushing ? 1.0D : -1.0D);
        Medium medium = fan.getMediumAt(distance);
        if (medium == Medium.NONE) {
            setRBGColorF(0.933F, 0.933F, 0.933F);
            setAlphaF(0.25F);
            int frame = MathHelper.clamp((int) (distance / range * 8.0D) + rand.nextInt(4), 0, 7);
            setParticleTextureIndex(7 - frame);
        } else {
            setParticleTextureIndex(7 - rand.nextInt(3));
            switch (medium) {
                case SPLASHING -> {
                    setMixedColor(0x4499FF, 0x2277FF);
                    setAlphaF(1.0F);
                    if (rand.nextInt(32) == 0) extra(EnumParticleTypes.WATER_SPLASH, motion * 0.125D);
                    if (rand.nextInt(32) == 0) extra(EnumParticleTypes.WATER_BUBBLE, motion * 0.125D);
                }
                case BLASTING -> {
                    setMixedColor(0xFF4400, 0xFF8855);
                    setAlphaF(0.5F);
                    if (rand.nextInt(32) == 0) extra(EnumParticleTypes.FLAME, motion * 0.25D);
                    if (rand.nextInt(16) == 0) extra(EnumParticleTypes.LAVA, motion * 0.25D);
                }
                case SMOKING -> {
                    setMixedColor(0x000000, 0x555555);
                    setAlphaF(1.0F);
                    if (rand.nextInt(32) == 0) extra(EnumParticleTypes.SMOKE_NORMAL, motion * 0.125D);
                    if (rand.nextInt(32) == 0) extra(EnumParticleTypes.SMOKE_LARGE, motion * 0.125D);
                }
                case HAUNTING -> {
                    setMixedColor(0x000000, 0x126568);
                    setAlphaF(1.0F);
                    if (rand.nextInt(128) == 0)
                        xy177.farmersfuturedelight.FarmerFutureDelight.proxy.spawnSoulFlameParticle(world, posX, posY, posZ);
                    if (rand.nextInt(32) == 0) extra(EnumParticleTypes.SMOKE_NORMAL, motion * .125D);
                }
                default -> { }
            }
        }
        move(facing.getXOffset() * motion, facing.getYOffset() * motion, facing.getZOffset() * motion);
    }

    private void setMixedColor(int first, int second) {
        float mix = rand.nextFloat();
        float red = ((first >> 16 & 255) * (1.0F - mix) + (second >> 16 & 255) * mix) / 255.0F;
        float green = ((first >> 8 & 255) * (1.0F - mix) + (second >> 8 & 255) * mix) / 255.0F;
        float blue = ((first & 255) * (1.0F - mix) + (second & 255) * mix) / 255.0F;
        setRBGColorF(red, green, blue);
    }

    private void extra(EnumParticleTypes type, double speed) {
        world.spawnParticle(type, posX, posY, posZ,
                facing.getXOffset() * speed, facing.getYOffset() * speed, facing.getZOffset() * speed);
    }
}
