package com.cogwork.cogworkbridge.content.contraptions.transform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContraptionTransformTest {

    @Test
    void angleAdvancesExactlyOncePerTick() {
        assertEquals(9.6D, ContraptionTransform.advance(0.0D, 32.0D), 1.0E-9D);
        assertEquals(19.2D, ContraptionTransform.advance(
                ContraptionTransform.advance(0.0D, 32.0D), 32.0D), 1.0E-9D);
        assertEquals(19.2D, ContraptionTransform.advance(19.2D, 0.0D), 1.0E-9D);
    }

    @Test
    void settleAngleRoundsAcrossTheWrapPoint() {
        assertEquals(0.0D, ContraptionTransform.quarterTurn(359.0D), 1.0E-9D);
        assertEquals(90.0D, ContraptionTransform.quarterTurn(46.0D), 1.0E-9D);
        assertEquals(270.0D, ContraptionTransform.quarterTurn(314.0D), 1.0E-9D);
    }

    @Test
    void boxRotatesAroundOriginWithoutChangingSize() {
        ContraptionTransform.Box result = new ContraptionTransform.Box(
                0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)
                .rotate(ContraptionTransform.Axis.Y, 90.0D);
        assertBox(result, -1.0D, 0.0D, 0.0D, 0.0D, 1.0D, 1.0D);
    }

    @Test
    void interpolationTakesShortestPathAcrossWrap() {
        assertEquals(2.0D, ContraptionTransform.interpolate(358.0D, 6.0D, 0.5D),
                1.0E-9D);
    }

    @Test
    void shortestDeltaUsesTheSignedPathAcrossWrap() {
        assertEquals(8.0D, ContraptionTransform.shortestDelta(358.0D, 6.0D),
                1.0E-9D);
        assertEquals(-8.0D, ContraptionTransform.shortestDelta(6.0D, 358.0D),
                1.0E-9D);
    }

    @Test
    void clientClockReconcilesWithoutJumpingToServerAngle() {
        double delta = ContraptionTransform.clientTickDelta(32.0D, 32.0D);
        assertEquals(20.266666666666666D, delta, 1.0E-9D);
        assertEquals(10.133333333333333D,
                ContraptionTransform.interpolateClientTick(0.0D, delta, 0.5D),
                1.0E-9D);
    }

    @Test
    void blockCentresRoundTripThroughTheSharedTransform() {
        ContraptionTransform.Point pivot = new ContraptionTransform.Point(10.5D, 64.5D, -3.5D);
        ContraptionTransform.Point block = new ContraptionTransform.Point(11.5D, 64.5D, -3.5D);
        ContraptionTransform.Point world = ContraptionTransform.rotateAroundBlockCenter(
                        block.x(), block.y(), block.z(), pivot,
                        ContraptionTransform.Axis.Y, 90.0D);
        assertEquals(10.5D, world.x(), 1.0E-9D);
        assertEquals(64.5D, world.y(), 1.0E-9D);
        assertEquals(-2.5D, world.z(), 1.0E-9D);
        ContraptionTransform.Point local = ContraptionTransform.toLocal(
                world, pivot, ContraptionTransform.Axis.Y, 90.0D);
        assertEquals(block.x(), local.x(), 1.0E-9D);
        assertEquals(block.y(), local.y(), 1.0E-9D);
        assertEquals(block.z(), local.z(), 1.0E-9D);
    }

    @Test
    void anchorPivotLeavesTheAnchorCellFixed() {
        ContraptionTransform.Point pivot = new ContraptionTransform.Point(
                4.5D, 64.5D, -2.5D);
        ContraptionTransform.Point result = ContraptionTransform.rotateAroundBlockCenter(
                pivot.x(), pivot.y(), pivot.z(), pivot, ContraptionTransform.Axis.X, 90.0D);
        assertEquals(pivot.x(), result.x(), 1.0E-9D);
        assertEquals(pivot.y(), result.y(), 1.0E-9D);
        assertEquals(pivot.z(), result.z(), 1.0E-9D);
    }

    @Test
    void relativeGlueAreaRotatesWithTheStructure() {
        ContraptionTransform.Box local = new ContraptionTransform.Box(
                0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        ContraptionTransform.Box rotated = local.rotate(
                ContraptionTransform.Axis.Y, 90.0D);
        assertEquals(-1.0D, rotated.minX(), 1.0E-9D);
        assertEquals(0.0D, rotated.minZ(), 1.0E-9D);
        assertEquals(0.0D, rotated.maxX(), 1.0E-9D);
        assertEquals(1.0D, rotated.maxZ(), 1.0E-9D);
    }

    @Test
    void modelCoordinatesKeepTheAnchorCellAtItsWorldOrigin() {
        ContraptionTransform.Point pivot = new ContraptionTransform.Point(
                4.5D, 64.5D, -2.5D);
        ContraptionTransform.Point anchorCorner = new ContraptionTransform.Point(
                4.0D, 64.0D, -3.0D);
        ContraptionTransform.Point rendered = new ContraptionTransform.Point(
                anchorCorner.x() - pivot.x(),
                anchorCorner.y() - pivot.y(),
                anchorCorner.z() - pivot.z());
        ContraptionTransform.Point world = rendered.add(
                pivot.x(), pivot.y(), pivot.z());
        assertEquals(anchorCorner.x(), world.x(), 1.0E-9D);
        assertEquals(anchorCorner.y(), world.y(), 1.0E-9D);
        assertEquals(anchorCorner.z(), world.z(), 1.0E-9D);
    }

    @Test
    void blockAlignedGlueBoxRotatesWithoutDrift() {
        ContraptionTransform.Box box = new ContraptionTransform.Box(
                -1.0D, 0.0D, -2.0D, 2.0D, 1.0D, 1.0D);
        ContraptionTransform.Box rotated = box.rotate(
                ContraptionTransform.Axis.Y, 90.0D);
        assertBox(rotated, -1.0D, 0.0D, -1.0D, 2.0D, 1.0D, 2.0D);
    }

    @Test
    void glueBoxUsesTheSameAnchorRotationAsBlocksForEveryAxis() {
        for (ContraptionTransform.Axis axis : ContraptionTransform.Axis.values()) {
            for (double angle : new double[]{90.0D, 180.0D, 270.0D}) {
                ContraptionTransform.Point pivot = new ContraptionTransform.Point(
                        10.5D, 64.5D, -3.5D);
                ContraptionTransform.Box local = new ContraptionTransform.Box(
                        -1.0D, 0.0D, -3.0D, 2.0D, 1.0D, 0.0D);
                ContraptionTransform.Box rotated = local.rotate(axis, angle);
                ContraptionTransform.Box blockLocal = new ContraptionTransform.Box(
                        -1.0D + 10.5D, 0.0D + 64.5D, -3.0D - 3.5D,
                        2.0D + 10.5D, 1.0D + 64.5D, 0.0D - 3.5D);
                ContraptionTransform.Point center = new ContraptionTransform.Point(
                        (blockLocal.minX() + blockLocal.maxX()) * 0.5D,
                        (blockLocal.minY() + blockLocal.maxY()) * 0.5D,
                        (blockLocal.minZ() + blockLocal.maxZ()) * 0.5D);
                ContraptionTransform.Point blockCenter = rotateAround(center, pivot, axis, angle);
                assertEquals(rotated.centerX() + pivot.x(), blockCenter.x(), 1.0E-9D);
                assertEquals(rotated.centerY() + pivot.y(), blockCenter.y(), 1.0E-9D);
                assertEquals(rotated.centerZ() + pivot.z(), blockCenter.z(), 1.0E-9D);
            }
        }
    }

    @Test
    void worldBoxUsesTheSamePivotAtNonZeroCoordinates() {
        ContraptionTransform.Point pivot = new ContraptionTransform.Point(
                1062.5D, 7.5D, -994.5D);
        ContraptionTransform.Box box = new ContraptionTransform.Box(
                1062.0D, 7.0D, -978.0D,
                1063.0D, 8.0D, -977.0D);
        ContraptionTransform.Box transformed =
                ContraptionTransform.transformBox(box,
                        point -> rotateAround(point, pivot,
                                ContraptionTransform.Axis.X, 19.2D));
        ContraptionTransform.Point expected = rotateAround(
                new ContraptionTransform.Point(
                        1062.5D, 7.5D, -977.5D),
                pivot, ContraptionTransform.Axis.X, 19.2D);
        assertEquals(expected.x(), transformed.centerX(), 1.0E-9D);
        assertEquals(expected.y(), transformed.centerY(), 1.0E-9D);
        assertEquals(expected.z(), transformed.centerZ(), 1.0E-9D);
    }

    @Test
    void localBoxIsShiftedIntoWorldGridBeforeRotation() {
        ContraptionTransform.Point anchor = new ContraptionTransform.Point(
                1050.5D, 10.5D, -1016.5D);
        ContraptionTransform.Box local = new ContraptionTransform.Box(
                0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        java.util.function.Function<ContraptionTransform.Point,
                ContraptionTransform.Point> transform =
                point -> point.add(1050.0D, 10.0D, -1017.0D)
                        .subtract(anchor)
                        .rotate(ContraptionTransform.Axis.Y, 38.4D)
                        .add(anchor.x(), anchor.y(), anchor.z());
        ContraptionTransform.Box transformed =
                ContraptionTransform.transformBox(local, transform);
        assertBox(transformed,
                java.util.stream.DoubleStream.of(
                                transform.apply(new ContraptionTransform.Point(0.0D, 0.0D, 0.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 0.0D, 0.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 1.0D, 0.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 1.0D, 0.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 0.0D, 1.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 0.0D, 1.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 1.0D, 1.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 1.0D, 1.0D)).x())
                        .min().orElseThrow(),
                anchor.y() - 0.5D,
                java.util.stream.DoubleStream.of(
                                transform.apply(new ContraptionTransform.Point(0.0D, 0.0D, 0.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 0.0D, 0.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 1.0D, 0.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 1.0D, 0.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 0.0D, 1.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 0.0D, 1.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 1.0D, 1.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 1.0D, 1.0D)).z())
                        .min().orElseThrow(),
                java.util.stream.DoubleStream.of(
                                transform.apply(new ContraptionTransform.Point(0.0D, 0.0D, 0.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 0.0D, 0.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 1.0D, 0.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 1.0D, 0.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 0.0D, 1.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 0.0D, 1.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 1.0D, 1.0D)).x(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 1.0D, 1.0D)).x())
                        .max().orElseThrow(),
                anchor.y() + 0.5D,
                java.util.stream.DoubleStream.of(
                                transform.apply(new ContraptionTransform.Point(0.0D, 0.0D, 0.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 0.0D, 0.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 1.0D, 0.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 1.0D, 0.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 0.0D, 1.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 0.0D, 1.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(0.0D, 1.0D, 1.0D)).z(),
                                transform.apply(new ContraptionTransform.Point(1.0D, 1.0D, 1.0D)).z())
                        .max().orElseThrow());
    }

    @Test
    void blockKeepsItsAuthoredWorldCoordinatesBeforeRotation() {
        ContraptionTransform.Point pivot = new ContraptionTransform.Point(
                1050.5D, 10.5D, -1016.5D);
        ContraptionTransform.Box block = new ContraptionTransform.Box(
                1062.0D, 1.0D, -983.0D,
                1063.0D, 2.0D, -982.0D);
        ContraptionTransform.Point local = ContraptionTransform.toLocal(
                new ContraptionTransform.Point(
                        block.centerX(), block.centerY(), block.centerZ()),
                pivot, ContraptionTransform.Axis.Z, 28.8D);
        ContraptionTransform.Point restored =
                ContraptionTransform.rotateAroundBlockCenter(
                        local.x(), local.y(), local.z(), pivot,
                        ContraptionTransform.Axis.Z, 28.8D);
        assertEquals(block.centerX(), restored.x(), 1.0E-9D);
        assertEquals(block.centerY(), restored.y(), 1.0E-9D);
        assertEquals(block.centerZ(), restored.z(), 1.0E-9D);
    }

    @Test
    void directionVectorsUseTheSameRotationAsWorldPositions() {
        ContraptionTransform.Point up =
                new ContraptionTransform.Point(0.0D, 1.0D, 0.0D);
        ContraptionTransform.Point quarter =
                up.rotate(ContraptionTransform.Axis.X, 90.0D);
        ContraptionTransform.Point half =
                up.rotate(ContraptionTransform.Axis.X, 180.0D);
        assertEquals(0.0D, quarter.x(), 1.0E-9D);
        assertEquals(0.0D, quarter.y(), 1.0E-9D);
        assertEquals(1.0D, quarter.z(), 1.0E-9D);
        assertEquals(0.0D, half.x(), 1.0E-9D);
        assertEquals(-1.0D, half.y(), 1.0E-9D);
        assertEquals(0.0D, half.z(), 1.0E-9D);
    }

    private static ContraptionTransform.Point rotateAround(
            ContraptionTransform.Point point, ContraptionTransform.Point pivot,
            ContraptionTransform.Axis axis, double angle) {
        return point.subtract(pivot).rotate(axis, angle).add(
                pivot.x(), pivot.y(), pivot.z());
    }

    private static void assertBox(ContraptionTransform.Box box,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        assertEquals(minX, box.minX(), 1.0E-9D);
        assertEquals(minY, box.minY(), 1.0E-9D);
        assertEquals(minZ, box.minZ(), 1.0E-9D);
        assertEquals(maxX, box.maxX(), 1.0E-9D);
        assertEquals(maxY, box.maxY(), 1.0E-9D);
        assertEquals(maxZ, box.maxZ(), 1.0E-9D);
    }
}
