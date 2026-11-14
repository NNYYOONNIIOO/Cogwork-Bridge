package com.cogwork.cogworkbridge.content.contraptions.actors.harvester;

import com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity;
import com.cogwork.cogworkbridge.content.contraptions.bearing.ContraptionItemCollector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockReed;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;

import javax.annotation.Nullable;
import java.util.Collections;

public final class MechanicalHarvesterTileEntity extends TileEntity {
    private BlockPos visitedPos;
    private boolean worldChanged;
    private double lastRenderTime = Double.NaN;
    private float bladeAngle;
    private float tutorialAnimatedSpeed = Float.NaN;
    @Override public net.minecraft.nbt.NBTTagCompound writeToNBT(net.minecraft.nbt.NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (!Float.isNaN(tutorialAnimatedSpeed)) tag.setFloat("TutorialAnimatedSpeed", tutorialAnimatedSpeed);
        return tag;
    }
    @Override public void readFromNBT(net.minecraft.nbt.NBTTagCompound tag) {
        super.readFromNBT(tag);
        tutorialAnimatedSpeed = tag.hasKey("TutorialAnimatedSpeed") ? tag.getFloat("TutorialAnimatedSpeed") : Float.NaN;
    }

    public void tickMovingStep(BearingContraptionEntity contraption, float fromAngle, float toAngle) {
        if (world == null || contraption.world.isRemote) {
            stopMoving();
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof MechanicalHarvesterBlock)) {
            stopMoving();
            return;
        }
        EnumFacing facing = state.getValue(MechanicalHarvesterBlock.FACING);
        Vec3d tip = localTip(contraption, facing);
        Vec3d before = contraption.actorStepStart(tip, fromAngle);
        Vec3d after = contraption.localToWorld(tip, toAngle);
        Vec3d direction = contraption.localToWorld(tip.add(new Vec3d(facing.getDirectionVec())), toAngle)
                .subtract(after);
        Vec3d motion = after.subtract(before);
        if (motion.lengthSquared() < 1.0E-12D || motion.dotProduct(direction) < -1.0E-8D) {
            stopMoving();
            return;
        }
        BlockPos target = new BlockPos(after);
        World realWorld = contraption.world;
        if (!realWorld.isBlockLoaded(target)
                || (!contraption.isNested() && target.equals(contraption.getBearingPos()))) {
            stopMoving();
            return;
        }
        if (target.equals(visitedPos)) {
            return;
        }
        visitedPos = target.toImmutable();
        IBlockState targetState = realWorld.getBlockState(target);
        HarvestTarget harvest = harvestTarget(realWorld, target, targetState);
        if (harvest == null) {
            return;
        }
        ItemStack seed = harvest.replant == null ? ItemStack.EMPTY
                : targetState.getBlock().getItem(realWorld, target, targetState);
        if (!ContraptionItemCollector.harvestAndCollect(contraption, target, seed, harvest.shear)) {
            return;
        }
        if (harvest.replant != null && realWorld.isAirBlock(target)) {
            realWorld.setBlockState(target, harvest.replant, 3);
        }
        worldChanged = true;
    }

    @Nullable
    private static HarvestTarget harvestTarget(World world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        if (block.isAir(state, world, pos)) {
            return null;
        }
        if (block.isLeaves(state, world, pos)) {
            return new HarvestTarget(null, true);
        }
        if (block instanceof BlockReed) {
            return new HarvestTarget(null, false);
        }
        if (block instanceof BlockCrops crop) {
            if (!crop.isMaxAge(state)) {
                return null;
            }
            IBlockState young = stateWithZeroAge(state);
            if (young == null) {
                young = crop.withAge(0);
            }
            return new HarvestTarget(crop.canBlockStay(world, pos, young) ? young : null, false);
        }
        boolean plant = state.getCollisionBoundingBox(world, pos) == Block.NULL_AABB;
        if (!plant && !(block instanceof BlockCocoa)) {
            return null;
        }
        PropertyInteger age = ageProperty(state);
        if (age != null) {
            if (!state.getValue(age).equals(Collections.max(age.getAllowedValues()))) {
                return null;
            }
            IBlockState young = stateWithZeroAge(state);
            if (young != null && block instanceof BlockBush bush && !bush.canBlockStay(world, pos, young)) {
                young = null;
            } else if (young != null && block instanceof BlockCocoa cocoa
                    && !cocoa.canBlockStay(world, pos, young)) {
                young = null;
            }
            return new HarvestTarget(young, false);
        }
        return plant && (block instanceof BlockBush || block instanceof IPlantable)
                ? new HarvestTarget(null, false) : null;
    }

    @Nullable
    private static IBlockState stateWithZeroAge(IBlockState state) {
        PropertyInteger age = ageProperty(state);
        return age != null && age.getAllowedValues().contains(0) ? state.withProperty(age, 0) : null;
    }

    @Nullable
    private static PropertyInteger ageProperty(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property instanceof PropertyInteger age && "age".equals(age.getName())) {
                return age;
            }
        }
        return null;
    }

    public float movingAnimationSpeed(BearingContraptionEntity contraption, EnumFacing facing) {
        if (!contraption.hasMotion()) {
            return 0.0F;
        }
        Vec3d tip = localTip(contraption, facing);
        float angle = contraption.getAngle();
        Vec3d before = contraption.localToWorld(tip, angle);
        Vec3d after = before.add(contraption.actorTickMovement(tip, angle, contraption.getSpeed()));
        Vec3d direction = contraption.localToWorld(tip.add(new Vec3d(facing.getDirectionVec())), angle)
                .subtract(before);
        Vec3d movement = after.subtract(before);
        if (movement.dotProduct(direction) < -1.0E-8D) {
            return 0.0F;
        }
        double distance = movement.length();
        return distance < 1.0D / 512.0D ? 0.0F
                : (float) (Math.floor((distance * 1000.0D + 100.0D) / 100.0D) * 100.0D);
    }

    public float bladeAngle(double time, float speed) {
        if (!Float.isNaN(tutorialAnimatedSpeed)) return (float) ((time * tutorialAnimatedSpeed / 20D) % 360D);
        if (!Double.isNaN(lastRenderTime)) {
            double elapsed = Math.max(0.0D, Math.min(1.0D, time - lastRenderTime));
            bladeAngle = (bladeAngle + (float) elapsed * speed / 20.0F) % 360.0F;
        }
        lastRenderTime = time;
        return bladeAngle;
    }

    private Vec3d localTip(BearingContraptionEntity contraption, EnumFacing facing) {
        BlockPos anchor = contraption.getAnchor();
        return new Vec3d(anchor.getX() + pos.getX() + 0.5D + facing.getXOffset() * 0.45D,
                anchor.getY() + pos.getY() + 0.5D,
                anchor.getZ() + pos.getZ() + 0.5D + facing.getZOffset() * 0.45D);
    }

    public boolean consumeWorldChanged() {
        boolean changed = worldChanged;
        worldChanged = false;
        return changed;
    }

    public void stopMoving() {
        visitedPos = null;
    }

    private record HarvestTarget(@Nullable IBlockState replant, boolean shear) {
    }
}
