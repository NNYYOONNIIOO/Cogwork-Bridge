package com.cogwork.cogworkbridge.content.contraptions.transform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContraptionCollisionShapeTest {

    @Test
    void localAndWorldBoxesShareTheSameRotationPivot() {
        ContraptionTransform.Point pivot = new ContraptionTransform.Point(
                4.5D, 64.5D, -2.5D);
        ContraptionCollisionShape shape = ContraptionCollisionShape.of(
                new ContraptionTransform.Box(5D, 64D, -2D, 6D, 65D, -1D),
                pivot, ContraptionTransform.Axis.Y, 90D);
        assertEquals(5D, shape.local().minX(), 1e-9D);
        assertEquals(3D, shape.world().minX(), 1e-9D);
        assertEquals(-2D, shape.world().minZ(), 1e-9D);
    }
}
