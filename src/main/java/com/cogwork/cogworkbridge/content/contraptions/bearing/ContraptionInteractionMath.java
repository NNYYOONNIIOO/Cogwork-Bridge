package com.cogwork.cogworkbridge.content.contraptions.bearing;

import net.minecraft.entity.player.EntityPlayer;

/** Pure distance and explosion rules shared by interaction entry points. */
public final class ContraptionInteractionMath {

    public static final double INTERACTION_REACH = 8.0D;
    public static final double INTERACTION_REACH_SQUARED =
            INTERACTION_REACH * INTERACTION_REACH;
    public static final double SELECTION_EPSILON = 0.002D;
    private ContraptionInteractionMath() {
    }

    public static boolean withinInteractionReach(double dx, double dy,
                                                  double dz) {
        return dx * dx + dy * dy + dz * dz
                <= INTERACTION_REACH_SQUARED;
    }

    public static double interactionReach(EntityPlayer player) {
        if (player == null || player.getEntityAttribute(EntityPlayer.REACH_DISTANCE) == null)
            return INTERACTION_REACH;
        double value = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        return Double.isFinite(value) ? Math.max(INTERACTION_REACH, Math.min(64.0D, value)) : INTERACTION_REACH;
    }

    public static boolean withinInteractionReach(EntityPlayer player, double dx, double dy, double dz) {
        double reach = interactionReach(player);
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    public static boolean intersectsInteractionReach(
            double eyeX, double eyeY, double eyeZ,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        return distanceSquaredToBox(
                eyeX, eyeY, eyeZ, minX, minY, minZ, maxX, maxY, maxZ)
                <= INTERACTION_REACH_SQUARED;
    }

    public static boolean intersectsInteractionReach(EntityPlayer player,
                                                     double eyeX, double eyeY, double eyeZ,
                                                     double minX, double minY, double minZ,
                                                     double maxX, double maxY, double maxZ) {
        double reach = interactionReach(player);
        return distanceSquaredToBox(eyeX, eyeY, eyeZ, minX, minY, minZ, maxX, maxY, maxZ)
                <= reach * reach;
    }

    public static double distanceSquaredToBox(
            double eyeX, double eyeY, double eyeZ,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double closestX = clamp(eyeX, minX, maxX);
        double closestY = clamp(eyeY, minY, maxY);
        double closestZ = clamp(eyeZ, minZ, maxZ);
        double dx = eyeX - closestX;
        double dy = eyeY - closestY;
        double dz = eyeZ - closestZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The contraption snapshot already removes the block before this point.
     * Calling Block#onExplosionDestroy again can issue an unconditional world
     * write, and no current block in the project needs that duplicate hook.
     */
    public static boolean shouldCallBlockExplosionHook() {
        return false;
    }
}
