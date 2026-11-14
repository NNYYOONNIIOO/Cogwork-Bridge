package com.cogwork.cogworkbridge.kinetic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KineticPropagatorMappingTest {

    @Test
    void mapsEveryMinecraftFaceToTheGeometryFace() {
        assertEquals(KineticGeometry.Face.NEGATIVE_Y,
                KineticGeometry.faceFor(KineticGeometry.Direction.DOWN));
        assertEquals(KineticGeometry.Face.POSITIVE_Y,
                KineticGeometry.faceFor(KineticGeometry.Direction.UP));
        assertEquals(KineticGeometry.Face.NEGATIVE_Z,
                KineticGeometry.faceFor(KineticGeometry.Direction.NORTH));
        assertEquals(KineticGeometry.Face.POSITIVE_Z,
                KineticGeometry.faceFor(KineticGeometry.Direction.SOUTH));
        assertEquals(KineticGeometry.Face.NEGATIVE_X,
                KineticGeometry.faceFor(KineticGeometry.Direction.WEST));
        assertEquals(KineticGeometry.Face.POSITIVE_X,
                KineticGeometry.faceFor(KineticGeometry.Direction.EAST));
    }
}
