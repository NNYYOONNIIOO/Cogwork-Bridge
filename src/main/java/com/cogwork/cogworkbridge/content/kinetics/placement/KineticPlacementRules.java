package com.cogwork.cogworkbridge.content.kinetics.placement;

import com.cogwork.cogworkbridge.kinetic.KineticBlock;
import com.cogwork.cogworkbridge.content.kinetics.shaft.ShaftBlock;
import com.cogwork.cogworkbridge.content.kinetics.cogwheel.CogwheelBlock;
import com.cogwork.cogworkbridge.content.kinetics.speedController.SpeedControllerBlock;
import com.cogwork.cogworkbridge.content.processing.mixer.MechanicalMixerBlock;
import com.cogwork.cogworkbridge.content.processing.millstone.MillstoneBlock;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class KineticPlacementRules {
    private static final int MAX_PLACEMENT_RANGE = 16;

    private KineticPlacementRules() {
    }

    public static KineticPlacementPlan plan(World world, BlockPos clickedPos,
                                            EnumFacing face, float hitX,
                                            float hitY, float hitZ,
                                            EntityPlayer player, EnumHand hand, Block placed) {
        ItemStack stack = player.getHeldItem(hand);
        IBlockState clickedState = world.getBlockState(clickedPos);
        boolean replaceClicked = clickedState.getBlock().isReplaceable(world, clickedPos);
        BlockPos target = replaceClicked ? clickedPos : clickedPos.offset(face);
        EnumFacing.Axis axis = placementAxis(world, target, face, player, placed);
        boolean assisted = false;
        if (!player.isSneaking()
                && (placed instanceof ShaftBlock || placed instanceof CogwheelBlock)
                && clickedState.getBlock() instanceof ShaftBlock sourceShaft) {
            axis = sourceShaft.getAxis(clickedState);
            EnumFacing direction = face.getAxis() == axis ? face
                    : directionTowardHit(axis, hitX, hitY, hitZ);
            BlockPos extension = findShaftExtension(world, clickedPos, direction, axis,
                    face, player, stack, placed);
            if (extension == null) {
                extension = findShaftExtension(world, clickedPos,
                        direction.getOpposite(), axis, face, player, stack, placed);
            }
            assisted = true;
            target = extension == null ? clickedPos.offset(direction) : extension;
            if (extension == null) {
                return new KineticPlacementPlan(target, axis, false, true, null);
            }
        } else if (!player.isSneaking() && placed instanceof CogwheelBlock cog
                && clickedState.getBlock() instanceof CogwheelBlock sourceCog) {
            return planCogwheel(world, clickedPos, face, hitX, hitY, hitZ,
                    player, hand, cog, sourceCog);
        } else if (!player.isSneaking() && placed instanceof CogwheelBlock cog
                && clickedState.getBlock() instanceof MechanicalMixerBlock mixer
                && face.getAxis() != mixer.getAxis(clickedState)) {
            return planIntegratedCogwheel(world, clickedPos, face, hitX, hitY,
                    hitZ, player, hand, cog, mixer.getAxis(clickedState));
        } else if (!player.isSneaking() && placed instanceof CogwheelBlock cog
                && clickedState.getBlock() instanceof MillstoneBlock millstone
                && face.getAxis() != millstone.getAxis(clickedState)) {
            return planIntegratedCogwheel(world, clickedPos, face, hitX, hitY,
                    hitZ, player, hand, cog, millstone.getAxis(clickedState));
        }

        boolean valid = canPlace(world, target, face, player, stack, placed, axis);
        IBlockState previewState = null;
        if (valid && placed instanceof KineticBlock kinetic) {
            if (assisted) {
                KineticPlacementContext.setForcedAxis(axis);
            }
            try {
                previewState = kinetic.getStateForPlacement(world, target,
                        face, hitX, hitY, hitZ,
                        stack.getItem().getMetadata(stack.getMetadata()), player, hand);
                if (previewState != null) {
                    axis = kinetic.getAxis(previewState);
                }
                valid = previewState != null
                        && (!(placed instanceof CogwheelBlock cog)
                            || validCogwheelPosition(world, target, axis, cog.isLarge()))
                        && canPlace(world, target, face, player, stack, placed, axis);
            } finally {
                if (assisted) {
                    KineticPlacementContext.clear();
                }
            }
        }
        return new KineticPlacementPlan(target.toImmutable(), axis, valid,
                assisted, previewState);
    }

    private static BlockPos findShaftExtension(World world, BlockPos clickedPos,
                                                EnumFacing direction,
                                                EnumFacing.Axis axis, EnumFacing face,
                                                EntityPlayer player, ItemStack stack,
                                                Block placed) {
        BlockPos cursor = clickedPos;
        for (int distance = 0; distance < MAX_PLACEMENT_RANGE; distance++) {
            BlockPos nextPos = cursor.offset(direction);
            if (!world.isBlockLoaded(nextPos)) {
                return null;
            }
            IBlockState next = world.getBlockState(nextPos);
            if (next.getBlock().isReplaceable(world, nextPos)) {
                return canPlace(world, nextPos, face, player, stack, placed, axis)
                        ? nextPos : null;
            }
            if (!(next.getBlock() instanceof KineticBlock kinetic)
                    || !(next.getBlock() instanceof ShaftBlock
                    || next.getBlock() instanceof CogwheelBlock)
                    || kinetic.getAxis(next) != axis) {
                return null;
            }
            cursor = nextPos;
        }
        return null;
    }

    private static KineticPlacementPlan planCogwheel(
            World world, BlockPos clickedPos, EnumFacing hitFace,
            float hitX, float hitY, float hitZ, EntityPlayer player, EnumHand hand,
            CogwheelBlock placed, CogwheelBlock source) {
        EnumFacing.Axis sourceAxis = source.getAxis(world.getBlockState(clickedPos));
        java.util.List<EnumFacing> around = directionsByHit(sourceAxis,
                hitX, hitY, hitZ);
        java.util.List<PlacementCandidate> candidates = new java.util.ArrayList<>();
        if (hitFace.getAxis() == sourceAxis
                && hitOnShaft(sourceAxis, hitX, hitY, hitZ)) {
            candidates.add(new PlacementCandidate(clickedPos.offset(hitFace), sourceAxis));
        } else if (!placed.isLarge() && !source.isLarge()) {
            for (EnumFacing direction : around) {
                candidates.add(new PlacementCandidate(
                        clickedPos.offset(direction), sourceAxis));
            }
        } else if (placed.isLarge() && source.isLarge()) {
            EnumFacing alongAxis = directionTowardHit(sourceAxis, hitX, hitY, hitZ);
            for (EnumFacing direction : around) {
                candidates.add(new PlacementCandidate(
                        clickedPos.offset(direction).offset(alongAxis),
                        direction.getAxis()));
            }
        } else {
            EnumFacing closest = around.getFirst();
            for (EnumFacing direction : around) {
                if (direction.getAxis() != closest.getAxis()) {
                    candidates.add(new PlacementCandidate(
                            clickedPos.offset(closest).offset(direction), sourceAxis));
                }
            }
        }

        return finishCogwheelPlan(world, hitFace, hitX, hitY, hitZ, player, hand, placed, candidates);
    }

    private static KineticPlacementPlan planIntegratedCogwheel(
            World world, BlockPos clickedPos, EnumFacing hitFace,
            float hitX, float hitY, float hitZ, EntityPlayer player, EnumHand hand,
            CogwheelBlock placed, EnumFacing.Axis sourceAxis) {
        java.util.List<PlacementCandidate> candidates = new java.util.ArrayList<>();
        for (EnumFacing direction : directionsByHit(sourceAxis, hitX, hitY, hitZ)) {
            if (placed.isLarge()) {
                if (direction.getAxis() != hitFace.getAxis()) {
                    candidates.add(new PlacementCandidate(
                            clickedPos.offset(hitFace).offset(direction), sourceAxis));
                }
            } else {
                candidates.add(new PlacementCandidate(clickedPos.offset(direction), sourceAxis));
            }
        }
        return finishCogwheelPlan(world, hitFace, hitX, hitY, hitZ, player, hand, placed, candidates);
    }

    private static KineticPlacementPlan finishCogwheelPlan(
            World world, EnumFacing hitFace, float hitX, float hitY, float hitZ,
            EntityPlayer player, EnumHand hand,
            CogwheelBlock placed, java.util.List<PlacementCandidate> candidates) {
        ItemStack stack = player.getHeldItem(hand);
        PlacementCandidate selected = null;
        for (PlacementCandidate candidate : candidates) {
            BlockPos pos = candidate.pos();
            if (!canPlace(world, pos, hitFace, player, stack, placed, candidate.axis())) {
                continue;
            }
            selected = candidate;
            break;
        }

        if (selected == null) {
            PlacementCandidate fallback = candidates.getFirst();
            return new KineticPlacementPlan(fallback.pos(), fallback.axis(),
                    false, true, null);
        }

        KineticPlacementContext.setForcedAxis(selected.axis());
        try {
            IBlockState preview = placed.getStateForPlacement(world, selected.pos(),
                    hitFace, hitX, hitY, hitZ,
                    stack.getItem().getMetadata(stack.getMetadata()), player, hand);
            boolean valid = preview != null;
            return new KineticPlacementPlan(selected.pos().toImmutable(),
                    selected.axis(), valid, true, preview);
        } finally {
            KineticPlacementContext.clear();
        }
    }

    private static boolean canPlace(World world, BlockPos pos, EnumFacing face,
                                    EntityPlayer player, ItemStack stack, Block placed,
                                    EnumFacing.Axis axis) {
        return !stack.isEmpty() && world.isBlockLoaded(pos)
                && world.getBlockState(pos).getBlock().isReplaceable(world, pos)
                && player.canPlayerEdit(pos, face, stack)
                && placed.canPlaceBlockAt(world, pos)
                && (!(placed instanceof CogwheelBlock cog)
                    || validCogwheelPosition(world, pos, axis, cog.isLarge()))
                && world.mayPlace(placed, pos, false, face, player);
    }

    public static EnumFacing.Axis placementAxis(World world, BlockPos pos,
                                                 EnumFacing face, EntityLivingBase placer,
                                                 Block placed) {
        boolean sneaking = placer != null && placer.isSneaking();
        EnumFacing.Axis viewAxis = placer == null ? face.getAxis() : KineticBlock.getPlacementAxis(placer);
        if (sneaking) {
            return viewAxis;
        }
        EnumFacing preferredFacing = KineticBlock.getPreferredShaftFacing(world, pos);
        EnumFacing.Axis preferred = preferredFacing == null ? null : preferredFacing.getAxis();
        if (placed instanceof CogwheelBlock cog) {
            IBlockState below = world.getBlockState(pos.down());
            if (cog.isLarge() && below.getBlock() instanceof SpeedControllerBlock controller) {
                return controller.getAxis(below) == EnumFacing.Axis.X
                        ? EnumFacing.Axis.Z : EnumFacing.Axis.X;
            }
            IBlockState against = world.getBlockState(pos.offset(face.getOpposite()));
            if (against.getBlock() instanceof CogwheelBlock neighbor && !neighbor.isLarge()) {
                return neighbor.getAxis(against);
            }
            if (against.getBlock() instanceof MechanicalMixerBlock mixer) {
                return mixer.getAxis(against);
            }
            if (against.getBlock() instanceof MillstoneBlock millstone) {
                return millstone.getAxis(against);
            }
            return preferred != null ? preferred : viewAxis;
        }
        return preferred != null ? preferred : viewAxis;
    }

    public static boolean validCogwheelPosition(World world, BlockPos pos,
                                                 EnumFacing.Axis axis,
                                                 boolean large) {
        for (EnumFacing direction : EnumFacing.values()) {
            if (direction.getAxis() == axis) {
                continue;
            }
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isBlockLoaded(neighborPos)) {
                continue;
            }
            IBlockState neighbor = world.getBlockState(neighborPos);
            boolean neighborIsLarge;
            EnumFacing.Axis neighborAxis;
            if (neighbor.getBlock() instanceof CogwheelBlock cog) {
                neighborIsLarge = cog.isLarge();
                neighborAxis = cog.getAxis(neighbor);
            } else if (neighbor.getBlock() instanceof MechanicalMixerBlock mixer) {
                neighborIsLarge = false;
                neighborAxis = mixer.getAxis(neighbor);
            } else if (neighbor.getBlock() instanceof MillstoneBlock millstone) {
                neighborIsLarge = false;
                neighborAxis = millstone.getAxis(neighbor);
            } else {
                continue;
            }
            if (direction.getAxis() == neighborAxis) {
                continue;
            }
            if (neighborIsLarge || large) {
                return false;
            }
        }
        return true;
    }

    private static java.util.List<EnumFacing> directionsByHit(
            EnumFacing.Axis excluded, float hitX, float hitY, float hitZ) {
        return java.util.Arrays.stream(EnumFacing.values())
                .filter(direction -> direction.getAxis() != excluded)
                .sorted(java.util.Comparator.comparingDouble(direction ->
                        hitDistance(direction, hitX, hitY, hitZ)))
                .toList();
    }

    private static double hitDistance(EnumFacing direction,
                                      float hitX, float hitY, float hitZ) {
        double coordinate = switch (direction.getAxis()) {
            case X -> hitX;
            case Y -> hitY;
            case Z -> hitZ;
        };
        return direction.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE
                ? coordinate : 1.0D - coordinate;
    }

    private static boolean hitOnShaft(EnumFacing.Axis axis,
                                      float hitX, float hitY, float hitZ) {
        double radius = 3.0D / 16.0D + 0.001D;
        return (axis == EnumFacing.Axis.X || Math.abs(hitX - 0.5D) <= radius)
                && (axis == EnumFacing.Axis.Y || Math.abs(hitY - 0.5D) <= radius)
                && (axis == EnumFacing.Axis.Z || Math.abs(hitZ - 0.5D) <= radius);
    }

    private static EnumFacing directionTowardHit(EnumFacing.Axis axis,
                                                  float hitX, float hitY, float hitZ) {
        double coordinate = switch (axis) {
            case X -> hitX;
            case Y -> hitY;
            case Z -> hitZ;
        };
        EnumFacing negative = EnumFacing.getFacingFromAxis(
                EnumFacing.AxisDirection.NEGATIVE, axis);
        EnumFacing positive = negative.getOpposite();
        return coordinate <= 0.5D ? negative : positive;
    }

    private record PlacementCandidate(BlockPos pos, EnumFacing.Axis axis) {
    }
}
