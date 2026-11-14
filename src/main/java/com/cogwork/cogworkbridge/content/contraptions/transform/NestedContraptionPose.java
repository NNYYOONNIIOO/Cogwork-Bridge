package com.cogwork.cogworkbridge.content.contraptions.transform;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * A serializable local pose for a stabilized child contraption. The local
 * bearing position is expressed in the parent's unrotated structure grid.
 */
public record NestedContraptionPose(
        BlockPos bearingLocal,
        EnumFacing facing,
        float angle,
        float speed,
        boolean stalled) {

    public NestedContraptionPose {
        bearingLocal = bearingLocal.toImmutable();
    }

    public BlockPos anchorLocal() {
        return bearingLocal.offset(facing);
    }

    public ContraptionTransform.Axis axis() {
        return ContraptionTransform.from(facing.getAxis());
    }

    public ContraptionTransform.Point localPivot() {
        BlockPos anchor = anchorLocal();
        return new ContraptionTransform.Point(
                anchor.getX() + 0.5D,
                anchor.getY() + 0.5D,
                anchor.getZ() + 0.5D);
    }

    public ContraptionTransform.Point localToParent(ContraptionTransform.Point point) {
        return point.subtract(localPivot())
                .rotate(axis(), angle)
                .add(localPivot().x(), localPivot().y(), localPivot().z());
    }

    public ContraptionTransform.Point parentToLocal(ContraptionTransform.Point point) {
        return point.subtract(localPivot())
                .rotate(axis(), -angle)
                .add(localPivot().x(), localPivot().y(), localPivot().z());
    }

    public AxisAlignedBB transformBox(AxisAlignedBB box) {
        ContraptionTransform.Box source = new ContraptionTransform.Box(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        ContraptionTransform.Box result =
                ContraptionTransform.rotateAroundBlockCenter(
                        source, localPivot(), axis(), angle);
        return new AxisAlignedBB(
                result.minX(), result.minY(), result.minZ(),
                result.maxX(), result.maxY(), result.maxZ());
    }
}
