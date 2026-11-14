package com.cogwork.cogworkbridge.content.contraptions.glue;

import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Runtime feedback for the upstream two-click super-glue workflow. */
public final class GlueFeedback {

    private GlueFeedback() {
    }

    public static void playSelection(World world, BlockPos pos, EnumFacing side,
                                     float pitch) {
        playPlaceSound(world, pos, pitch);
        spawnFaceParticles(world, pos, side, 40.0F);
    }

    public static void playConfirmation(World world, BlockPos pos, EnumFacing side) {
        playPlaceSound(world, pos, 0.95F);
        spawnFaceParticles(world, pos, side, 0.25F);
    }

    /** Applies feedback on the client after the server confirms a new area. */
    public static void playCreation(World world, GlueBox box) {
        if (box == null) {
            return;
        }
        BlockPos center = new BlockPos(
                (box.minX() + box.maxX()) / 2,
                (box.minY() + box.maxY()) / 2,
                (box.minZ() + box.maxZ()) / 2);
        playPlaceSound(world, center, 0.95F);
        spawnEdgeParticles(world, box);
    }

    public static void playRemoval(World world, GlueBox box) {
        if (box == null) {
            return;
        }
        BlockPos center = new BlockPos(
                (box.minX() + box.maxX()) / 2,
                (box.minY() + box.maxY()) / 2,
                (box.minZ() + box.maxZ()) / 2);
        playSlimeAdded(world, center, 0.5F);
        spawnBoxEdgeParticles(world, box);
    }

    private static void playPlaceSound(World world, BlockPos pos, float pitch) {
        playSlimeAdded(world, pos, pitch);
        if (world == null || pos == null) {
            return;
        }
        world.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM,
                SoundCategory.BLOCKS, 0.75F, 1.0F);
    }

    private static void playSlimeAdded(World world, BlockPos pos, float pitch) {
        if (world == null || pos == null) {
            return;
        }
        world.playSound(null, pos, SoundEvents.ENTITY_SLIME_SQUISH,
                SoundCategory.BLOCKS, 0.5F, pitch);
    }

    private static void spawnFaceParticles(World world, BlockPos pos,
                                           EnumFacing side, float distance) {
        if (world == null || pos == null) {
            return;
        }
        EnumFacing face = side == null ? EnumFacing.UP : side;
        Vec3d normal = new Vec3d(face.getXOffset(), face.getYOffset(),
                face.getZOffset());
        Vec3d plane = axisPlane(normal);
        Vec3d facePos = new Vec3d(pos.getX() + 0.5D,
                pos.getY() + 0.5D, pos.getZ() + 0.5D).add(normal.scale(0.5D));
        Vec3d planeOffset = plane.scale(distance);
        int count = distance >= 1.0F ? 40 : 15;
        for (int i = 0; i < count; i++) {
            Vec3d offset = rotate(planeOffset,
                    Math.toRadians(world.rand.nextDouble() * 360.0D),
                    face.getAxis());
            Vec3d motion = offset.normalize().scale(1.0D / 16.0D);
            if (distance >= 1.0F) {
                offset = new Vec3d(clamp(offset.x), clamp(offset.y),
                        clamp(offset.z));
            }
            Vec3d particlePos = facePos.add(offset);
            world.spawnParticle(EnumParticleTypes.ITEM_CRACK,
                    particlePos.x, particlePos.y, particlePos.z,
                    motion.x, motion.y, motion.z,
                    Item.getIdFromItem(Items.SLIME_BALL));
        }
    }

    private static void spawnBoxEdgeParticles(World world, GlueBox box) {
        spawnEdgeParticles(world, box);
    }

    private static void spawnEdgeParticles(World world, GlueBox box) {
        spawnEdge(world, box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.minY(), box.minZ());
        spawnEdge(world, box.maxX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.minZ());
        spawnEdge(world, box.maxX(), box.maxY(), box.minZ(),
                box.minX(), box.maxY(), box.minZ());
        spawnEdge(world, box.minX(), box.maxY(), box.minZ(),
                box.minX(), box.minY(), box.minZ());
        spawnEdge(world, box.minX(), box.minY(), box.maxZ(),
                box.maxX(), box.minY(), box.maxZ());
        spawnEdge(world, box.maxX(), box.minY(), box.maxZ(),
                box.maxX(), box.maxY(), box.maxZ());
        spawnEdge(world, box.maxX(), box.maxY(), box.maxZ(),
                box.minX(), box.maxY(), box.maxZ());
        spawnEdge(world, box.minX(), box.maxY(), box.maxZ(),
                box.minX(), box.minY(), box.maxZ());
    }

    private static void spawnEdge(World world, double x0, double y0, double z0,
                                  double x1, double y1, double z1) {
        int steps = Math.max(1, (int) Math.ceil(
                Math.abs(x1 - x0) + Math.abs(y1 - y0) + Math.abs(z1 - z0)));
        for (int i = 0; i <= steps * 2; i++) {
            double t = i / (double) (steps * 2);
            world.spawnParticle(EnumParticleTypes.ITEM_CRACK,
                    x0 + (x1 - x0) * t, y0 + (y1 - y0) * t,
                    z0 + (z1 - z0) * t, 0.0D, 0.0D, 0.0D,
                    Item.getIdFromItem(Items.SLIME_BALL));
        }
    }

    private static Vec3d axisPlane(Vec3d normal) {
        if (Math.abs(normal.x) > 0.5D) {
            return new Vec3d(0.0D, 1.0D, 1.0D);
        }
        if (Math.abs(normal.y) > 0.5D) {
            return new Vec3d(1.0D, 0.0D, 1.0D);
        }
        return new Vec3d(1.0D, 1.0D, 0.0D);
    }

    private static Vec3d rotate(Vec3d vector, double angle,
                                EnumFacing.Axis axis) {
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        return switch (axis) {
            case X -> new Vec3d(vector.x,
                    vector.y * cosine - vector.z * sine,
                    vector.y * sine + vector.z * cosine);
            case Y -> new Vec3d(vector.x * cosine + vector.z * sine,
                    vector.y, -vector.x * sine + vector.z * cosine);
            case Z -> new Vec3d(vector.x * cosine - vector.y * sine,
                    vector.x * sine + vector.y * cosine, vector.z);
        };
    }

    private static double clamp(double value) {
        return Math.max(-0.5D, Math.min(0.5D, value));
    }
}
