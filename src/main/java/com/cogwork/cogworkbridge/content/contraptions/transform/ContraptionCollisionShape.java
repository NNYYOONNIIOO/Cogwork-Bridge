package com.cogwork.cogworkbridge.content.contraptions.transform;

/**
 * A block collision shape in both contraption-local and world coordinates.
 *
 * <p>The local box is used for swept entity movement; the world box is exposed
 * to the vanilla movement query. Both are produced from the same pivot and angle.</p>
 */
public record ContraptionCollisionShape(ContraptionTransform.Box local,
                                        ContraptionTransform.Box world) {

    public static ContraptionCollisionShape of(
            ContraptionTransform.Box local, ContraptionTransform.Point pivot,
            ContraptionTransform.Axis axis, double angle) {
        ContraptionTransform.Box centered = new ContraptionTransform.Box(
                local.minX() - pivot.x(), local.minY() - pivot.y(),
                local.minZ() - pivot.z(), local.maxX() - pivot.x(),
                local.maxY() - pivot.y(), local.maxZ() - pivot.z());
        ContraptionTransform.Box rotated = centered.rotate(axis, angle);
        ContraptionTransform.Box world = new ContraptionTransform.Box(
                pivot.x() + rotated.minX(), pivot.y() + rotated.minY(),
                pivot.z() + rotated.minZ(), pivot.x() + rotated.maxX(),
                pivot.y() + rotated.maxY(), pivot.z() + rotated.maxZ());
        return new ContraptionCollisionShape(local, world);
    }
}
