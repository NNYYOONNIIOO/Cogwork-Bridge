package com.cogwork.cogworkbridge.content.contraptions.mounted;

import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Server-side distance constraint for the two carts carrying a structure. */
public final class MountedCartCoupling {
    private MountedCartCoupling() { }

    public static void tick(EntityMinecart first, EntityMinecart second, double length) {
        if (length <= 0D) return;
        Vec3d firstMotion = motion(first);
        Vec3d secondMotion = motion(second);
        Vec3d firstNext = first.getPositionVector().add(clamp(firstMotion, 1D));
        Vec3d secondNext = second.getPositionVector().add(clamp(secondMotion, 1D));
        Vec3d link = secondNext.subtract(firstNext);
        double distance = link.length();
        if (distance > 1.0E-6D) {
            boolean canFirst = canAddMotion(first);
            boolean canSecond = canAddMotion(second);
            double error = distance - length;
            if (canFirst) {
                double share = canSecond ? .5D : 1D;
                firstMotion = firstMotion.add(correction(first, firstNext, link, error * share));
                setMotion(first, clamp(firstMotion, maxSpeed(first, firstNext)));
            }
            if (canSecond) {
                double share = canFirst ? .5D : 1D;
                secondMotion = secondMotion.add(correction(second, secondNext, link.scale(-1D), error * share));
                setMotion(second, clamp(secondMotion, maxSpeed(second, secondNext)));
            }
        }

        EntityMinecart movableFirst = canAddMotion(first) || !canAddMotion(second) ? first : second;
        EntityMinecart movableSecond = movableFirst == first ? second : first;
        boolean half = true;
        for (EntityMinecart cart : new EntityMinecart[]{movableFirst, movableSecond, movableFirst}) {
            EntityMinecart other = cart == first ? second : first;
            Vec3d position = cart.getPositionVector();
            Vec3d currentLink = other.getPositionVector().subtract(position);
            double error = currentLink.length() - length;
            if (Math.abs(error) < .125D) continue;
            double amount = error * (half ? .5D : 1D) * (canAddMotion(cart) ? 1D : .5D);
            Vec3d movement = clamp(correction(cart, position, currentLink, amount),
                    Math.min(1.75D, maxSpeed(cart, position)));
            if (movement.lengthSquared() > 1.0E-10D) {
                cart.move(MoverType.SELF, movement.x, movement.y, movement.z);
                cart.velocityChanged = true;
                half = false;
            }
        }
    }

    private static Vec3d correction(EntityMinecart cart, Vec3d position, Vec3d link, double amount) {
        double distance = link.length();
        if (distance < 1.0E-6D || Math.abs(amount) < 1.0E-7D) return Vec3d.ZERO;
        Vec3d axis = railAxis(cart, position);
        if (axis == null) return link.scale(amount / distance);
        double along = link.dotProduct(axis);
        if (Math.abs(along) < 1.0E-7D) return Vec3d.ZERO;
        double radius = Math.max(0D, distance - amount);
        double remainder = radius * radius - (link.lengthSquared() - along * along);
        double step = remainder < 0D ? along : along - Math.copySign(Math.sqrt(remainder), along);
        return axis.scale(step);
    }

    private static Vec3d railAxis(EntityMinecart cart, Vec3d position) {
        World world = cart.world;
        BlockPos railPos = new BlockPos(position);
        if (BlockRailBase.isRailBlock(world, railPos.down())) railPos = railPos.down();
        if (!world.isBlockLoaded(railPos)) return null;
        IBlockState state = world.getBlockState(railPos);
        if (!(state.getBlock() instanceof BlockRailBase rail)) return null;
        return switch (rail.getRailDirection(world, railPos, state, cart)) {
            case NORTH_SOUTH -> new Vec3d(0D, 0D, 1D);
            case EAST_WEST -> new Vec3d(1D, 0D, 0D);
            case ASCENDING_EAST -> new Vec3d(1D, 1D, 0D).normalize();
            case ASCENDING_WEST -> new Vec3d(-1D, 1D, 0D).normalize();
            case ASCENDING_NORTH -> new Vec3d(0D, 1D, -1D).normalize();
            case ASCENDING_SOUTH -> new Vec3d(0D, 1D, 1D).normalize();
            case NORTH_EAST, SOUTH_WEST -> new Vec3d(1D, 0D, 1D).normalize();
            case NORTH_WEST, SOUTH_EAST -> new Vec3d(1D, 0D, -1D).normalize();
        };
    }

    private static double maxSpeed(EntityMinecart cart, Vec3d position) {
        World world = cart.world;
        BlockPos railPos = new BlockPos(position);
        if (BlockRailBase.isRailBlock(world, railPos.down())) railPos = railPos.down();
        if (world.isBlockLoaded(railPos) && world.getBlockState(railPos).getBlock() instanceof BlockRailBase rail)
            return Math.max(0D, Math.min(cart.getCurrentCartSpeedCapOnRail(), rail.getRailMaxSpeed(world, cart, railPos)));
        return Math.max(0D, cart.getMaxCartSpeedOnRail());
    }

    private static boolean canAddMotion(EntityMinecart cart) {
        return !(cart instanceof EntityMinecartFurnace furnace)
                || Math.abs(furnace.pushX) < 1.0E-7D && Math.abs(furnace.pushZ) < 1.0E-7D;
    }

    private static Vec3d motion(EntityMinecart cart) { return new Vec3d(cart.motionX, cart.motionY, cart.motionZ); }

    private static void setMotion(EntityMinecart cart, Vec3d motion) {
        cart.motionX = motion.x;
        cart.motionY = motion.y;
        cart.motionZ = motion.z;
        cart.velocityChanged = true;
    }

    private static Vec3d clamp(Vec3d vector, double max) {
        double length = vector.length();
        return length > max && length > 1.0E-9D ? vector.scale(max / length) : vector;
    }
}
