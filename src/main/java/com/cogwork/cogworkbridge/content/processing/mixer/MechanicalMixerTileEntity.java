package com.cogwork.cogworkbridge.content.processing.mixer;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.processing.basin.BasinTileEntity;
import com.cogwork.cogworkbridge.kinetic.KineticTileEntity;
import com.cogwork.cogworkbridge.vendor.movingworld.common.chunk.mobilechunk.world.FakeWorld;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

public final class MechanicalMixerTileEntity extends KineticTileEntity {
    public static final float STRESS_IMPACT = 4.0F;
    public static final float MINIMUM_PROCESSING_SPEED = 30.0F;

    private int processingTicks;
    private String processingRecipe = "";
    private boolean running;
    private int runningTicks;

    @Override public void update() {
        super.update();
        if (world == null) return;
        if (world.isRemote) {
            if (running && runningTicks != 20 && ++runningTicks >= 40) {
                running = false;
                runningTicks = 0;
            }
            return;
        }
        BasinTileEntity basin = getBasin();
        MixingRecipes.Plan plan = basin == null || !isSpeedRequirementFulfilled() ? null : MixingRecipes.find(world, pos.down(2), basin);
        if (!running) {
            if (plan == null) return;
            running = true;
            runningTicks = 0;
            processingTicks = 0;
            processingRecipe = plan.id();
            markDirty(); sync();
            return;
        }
        if (runningTicks > 20) {
            if (plan != null) {
                runningTicks = 40 - runningTicks;
                processingRecipe = plan.id();
                processingTicks = 0;
                markDirty(); sync();
                return;
            }
            if (++runningTicks >= 40) {
                running = false;
                runningTicks = 0;
                processingRecipe = "";
                markDirty(); sync();
            }
            return;
        }
        if (plan == null) {
            runningTicks = runningTicks < 20 ? 40 - runningTicks : 21;
            processingTicks = 0;
            markDirty(); sync();
            return;
        }
        if (!plan.id().equals(processingRecipe)) {
            processingRecipe = plan.id();
            processingTicks = 0;
            markDirty();
        }
        if (runningTicks < 20) {
            runningTicks++;
            return;
        }
        if (world.getTotalWorldTime() % 8L == 0L)
            world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_STONE_HIT,
                    net.minecraft.util.SoundCategory.BLOCKS, .2F, Math.abs(workingSpeed()) < 65F ? .75F : 1.25F);
        processingTicks += Math.max(1, (int) (Math.abs(workingSpeed()) / 16F));
        if (processingTicks >= 100) {
            MixingRecipes.Plan next = MixingRecipes.afterBatch(world, pos.down(2), basin, plan);
            boolean fluidsAvailable = true;
            for (net.minecraftforge.fluids.FluidStack fluid : plan.fluidInputs())
                if (basin.drainInputFluid(fluid, false) != fluid.amount) { fluidsAvailable = false; break; }
            if ((plan.water() == 0 || basin.drainInputFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, plan.water()), false) == plan.water())
                    && fluidsAvailable && (plan.fluidOutput() == null || basin.fillOutput(plan.fluidOutput(), false) == plan.fluidOutput().amount)) {
                if (basin.processBatch(plan.counts(), java.util.Collections.singletonList(plan.result()), false)) {
                    if (plan.water() > 0) basin.drainInputFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, plan.water()), true);
                    for (net.minecraftforge.fluids.FluidStack fluid : plan.fluidInputs()) basin.drainInputFluid(fluid, true);
                    if (plan.fluidOutput() != null) basin.fillOutput(plan.fluidOutput(), true);
                    com.cogwork.cogworkbridge.content.advancement.AllAdvancements.awardAt(world, pos, "mechanical_mixer");
                }
            }
            processingTicks = 0;
            if (next == null) {
                runningTicks = 21;
                markDirty(); sync();
            } else {
                processingRecipe = next.id();
                markDirty();
            }
        }
    }

    @Override
    public float getStressImpact() {
        return STRESS_IMPACT;
    }

    @Override
    protected boolean isGear() {
        return true;
    }

    @Override
    protected boolean acceptsConnection(EnumFacing facing) {
        return facing.getAxis() != EnumFacing.Axis.Y;
    }

    public float workingSpeed() {
        float speed = getSpeed();
        if (speed == 0.0F && world instanceof FakeWorld fake
                && fake.getMobileChunk().getEntityMovingWorld() instanceof BearingContraptionEntity contraption) {
            speed = contraption.getSpeed();
        }
        return speed;
    }

    public boolean isSpeedRequirementFulfilled() {
        return Math.abs(workingSpeed()) >= MINIMUM_PROCESSING_SPEED;
    }

    public BasinTileEntity getBasin() {
        if (world == null) return null;
        TileEntity basin = world.getTileEntity(pos.down(2));
        return basin instanceof BasinTileEntity ? (BasinTileEntity) basin : null;
    }

    public float rotationAngle(float partialTicks, float speedScale) {
        float phase = getContraptionRenderPos() == null
                ? (float) (((world == null ? 0L : world.getTotalWorldTime()) + partialTicks)
                    * workingSpeed() * 0.3D)
                : getPhaseDegrees() + partialTicks * workingSpeed() * 0.3F;
        return (phase * speedScale) % 360.0F;
    }

    public float headAngle(float partialTicks) {
        return rotationAngle(partialTicks, 0.5F);
    }

    public float headOffset(float partialTicks) {
        if (!running) return 7.0F / 16.0F;
        float progress = runningTicks < 20 ? (runningTicks + partialTicks) / 20F
                : runningTicks == 20 ? 1F : (40F - runningTicks - partialTicks) / 20F;
        progress = net.minecraft.util.math.MathHelper.clamp(progress, 0F, 1F);
        return 7.0F / 16.0F + (1F - net.minecraft.util.math.MathHelper.cos(progress * (float) Math.PI)) / 2F;
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setBoolean("MixerRunning", running);
        tag.setInteger("MixerRunningTicks", runningTicks);
        tag.setInteger("MixerProcessingTicks", processingTicks);
        tag.setString("MixerRecipe", processingRecipe);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readAnimation(tag);
        processingTicks = tag.getInteger("MixerProcessingTicks");
        processingRecipe = tag.getString("MixerRecipe");
    }

    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setBoolean("MixerRunning", running);
        tag.setInteger("MixerRunningTicks", runningTicks);
        return tag;
    }

    @Override public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readAnimation(tag);
    }

    private void readAnimation(NBTTagCompound tag) {
        running = tag.getBoolean("MixerRunning");
        runningTicks = net.minecraft.util.math.MathHelper.clamp(tag.getInteger("MixerRunningTicks"), 0, 40);
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(0.5D, 2.0D, 0.5D);
    }
}
