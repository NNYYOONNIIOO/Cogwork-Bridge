package com.cogwork.cogworkbridge.kinetic;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticGeometryTest {

    private static final KineticGeometry.Position ORIGIN =
            new KineticGeometry.Position(0, 0, 0);

    @Test
    void shaftTransfersAlongItsFacesAndNotAcrossItsSide() {
        KineticGeometry.Block shaft = KineticGeometry.Block.directional(
                KineticGeometry.Axis.Z, KineticGeometry.Kind.SHAFT,
                KineticGeometry.Face.POSITIVE_Z, KineticGeometry.Face.NEGATIVE_Z);
        KineticGeometry.Block cog = KineticGeometry.Block.directional(
                KineticGeometry.Axis.Z, KineticGeometry.Kind.SHAFT,
                KineticGeometry.Face.POSITIVE_Z, KineticGeometry.Face.NEGATIVE_Z);

        assertEquals(1.0D, modifier(shaft, cog, new KineticGeometry.Position(0, 0, 1)));
        assertEquals(1.0D, modifier(shaft, cog, new KineticGeometry.Position(0, 0, -1)));
        assertEquals(0.0D, modifier(shaft, cog, new KineticGeometry.Position(1, 0, 0)));
    }

    @Test
    void shaftTransfersInBothSignsOfEveryAxis() {
        for (KineticGeometry.Axis axis : KineticGeometry.Axis.values()) {
            KineticGeometry.Block source = KineticGeometry.Block.shaft(axis);
            KineticGeometry.Block target = KineticGeometry.Block.shaft(axis);
            assertEquals(1.0D, modifier(source, target, direction(axis, 1)));
            assertEquals(1.0D, modifier(source, target, direction(axis, -1)));
        }
    }

    @Test
    void smallSmallDirectRadialContactReversesRotation() {
        KineticGeometry.Block first = KineticGeometry.Block.smallCog(KineticGeometry.Axis.Y);
        KineticGeometry.Block second = KineticGeometry.Block.smallCog(KineticGeometry.Axis.Y);

        assertEquals(-1.0D, modifier(first, second, new KineticGeometry.Position(1, 0, 0)));
        assertEquals(-1.0D, modifier(first, second, new KineticGeometry.Position(0, 0, -1)));
        assertEquals(1.0D, modifier(first, second, new KineticGeometry.Position(0, 1, 0)));
        assertEquals(1.0D, modifier(first, second, new KineticGeometry.Position(0, -1, 0)));
    }

    @Test
    void smallSmallDiagonalContactDoesNotMesh() {
        KineticGeometry.Block first = KineticGeometry.Block.smallCog(KineticGeometry.Axis.Y);
        KineticGeometry.Block second = KineticGeometry.Block.smallCog(KineticGeometry.Axis.Y);

        assertEquals(0.0D, modifier(first, second, new KineticGeometry.Position(1, 0, 1)));
    }

    @Test
    void aShaftCanDriveACogAlongTheirSharedAxis() {
        KineticGeometry.Block shaft = KineticGeometry.Block.directional(
                KineticGeometry.Axis.Z, KineticGeometry.Kind.SHAFT,
                KineticGeometry.Face.NEGATIVE_Z, KineticGeometry.Face.POSITIVE_Z);
        KineticGeometry.Block cog = KineticGeometry.Block.smallCog(KineticGeometry.Axis.Z);

        assertEquals(1.0D, modifier(shaft, cog, new KineticGeometry.Position(0, 0, 1)));
        assertEquals(1.0D, modifier(cog, shaft, new KineticGeometry.Position(0, 0, -1)));
        assertEquals(1.0D, modifier(shaft, cog, new KineticGeometry.Position(0, 0, -1)));
        assertEquals(1.0D, modifier(cog, shaft, new KineticGeometry.Position(0, 0, 1)));
        assertEquals(0.0D, modifier(shaft, cog, new KineticGeometry.Position(1, 0, 0)));
    }

    @Test
    void smallLargeDiagonalContactUsesCreateGearRatio() {
        KineticGeometry.Block small = KineticGeometry.Block.smallCog(KineticGeometry.Axis.Y);
        KineticGeometry.Block large = KineticGeometry.Block.largeCog(KineticGeometry.Axis.Y);
        KineticGeometry.Position diagonal = new KineticGeometry.Position(1, 0, 1);

        assertEquals(-0.5D, modifier(small, large, diagonal));
        assertEquals(-2.0D, modifier(large, small, diagonal));
        assertEquals(0.0D, modifier(small, large, new KineticGeometry.Position(1, 0, 0)));
    }

    @Test
    void largeLargePerpendicularAxesConnectWithAxialSigns() {
        KineticGeometry.Block x = KineticGeometry.Block.largeCog(KineticGeometry.Axis.X);
        KineticGeometry.Block y = KineticGeometry.Block.largeCog(KineticGeometry.Axis.Y);

        assertEquals(1.0D, modifier(x, y, new KineticGeometry.Position(1, 1, 0)));
        assertEquals(-1.0D, modifier(x, y, new KineticGeometry.Position(1, -1, 0)));
        assertEquals(-1.0D, modifier(x, y, new KineticGeometry.Position(-1, 1, 0)));
        assertEquals(1.0D, modifier(x, y, new KineticGeometry.Position(-1, -1, 0)));
        assertEquals(0.0D, modifier(x, y, new KineticGeometry.Position(1, 0, 0)));
    }

    @Test
    void sameAxisLargeGearsDoNotUseLargeLargeRule() {
        KineticGeometry.Block first = KineticGeometry.Block.largeCog(KineticGeometry.Axis.Y);
        KineticGeometry.Block second = KineticGeometry.Block.largeCog(KineticGeometry.Axis.Y);

        assertEquals(0.0D, modifier(first, second, new KineticGeometry.Position(1, 0, 0)));
        assertEquals(0.0D, modifier(first, second, new KineticGeometry.Position(1, 0, 1)));
    }

    @Test
    void candidateSetsMatchTheTwoUpstreamAddPropagationRules() {
        Set<KineticGeometry.Position> small = new HashSet<>(
                KineticGeometry.candidateOffsets(KineticGeometry.Block.smallCog(KineticGeometry.Axis.Y)));
        Set<KineticGeometry.Position> large = new HashSet<>(
                KineticGeometry.candidateOffsets(KineticGeometry.Block.largeCog(KineticGeometry.Axis.Y)));

        assertEquals(10, small.size());
        assertEquals(18, large.size());
        assertTrue(small.contains(new KineticGeometry.Position(1, 0, 1)));
        assertTrue(large.contains(new KineticGeometry.Position(1, 0, 1)));
        assertTrue(large.contains(new KineticGeometry.Position(1, 1, 0)));
    }

    @Test
    void largeGearCandidatesCoverEveryLargeLargeOffset() {
        for (KineticGeometry.Axis firstAxis : KineticGeometry.Axis.values()) {
            KineticGeometry.Block first =
                    KineticGeometry.Block.largeCog(firstAxis);
            Set<KineticGeometry.Position> candidates = new HashSet<>(
                    KineticGeometry.candidateOffsets(first));
            for (KineticGeometry.Axis secondAxis : KineticGeometry.Axis.values()) {
                if (secondAxis == firstAxis) {
                    continue;
                }
                KineticGeometry.Block second =
                        KineticGeometry.Block.largeCog(secondAxis);
                for (int firstSign : new int[]{-1, 1}) {
                    for (int secondSign : new int[]{-1, 1}) {
                        KineticGeometry.Position offset = mixed(
                                firstAxis, firstSign, secondAxis, secondSign);
                        assertTrue(candidates.contains(offset),
                                firstAxis + " large gear missing " + offset);
                        assertTrue(KineticGeometry.rotationModifier(
                                        first, ORIGIN, second, offset) != 0.0D,
                                firstAxis + "->" + secondAxis + " " + offset);
                    }
                }
            }
        }
    }

    @Test
    void largeSmallCandidatesCoverEveryDiagonalOffset() {
        for (KineticGeometry.Axis axis : KineticGeometry.Axis.values()) {
            KineticGeometry.Block large = KineticGeometry.Block.largeCog(axis);
            KineticGeometry.Block small = KineticGeometry.Block.smallCog(axis);
            Set<KineticGeometry.Position> candidates = new HashSet<>(
                    KineticGeometry.candidateOffsets(large));
            for (KineticGeometry.Axis firstRadial : KineticGeometry.Axis.values()) {
                if (firstRadial == axis) {
                    continue;
                }
                for (KineticGeometry.Axis secondRadial : KineticGeometry.Axis.values()) {
                    if (secondRadial == axis || secondRadial == firstRadial) {
                        continue;
                    }
                    for (int firstSign : new int[]{-1, 1}) {
                        for (int secondSign : new int[]{-1, 1}) {
                            KineticGeometry.Position offset = mixed(
                                    firstRadial, firstSign, secondRadial, secondSign);
                            assertTrue(candidates.contains(offset),
                                    axis + " large/small missing " + offset);
                            assertTrue(KineticGeometry.rotationModifier(
                                            large, ORIGIN, small, offset) != 0.0D);
                        }
                    }
                }
            }
        }
    }

    private static KineticGeometry.Position mixed(
            KineticGeometry.Axis firstAxis, int firstAmount,
            KineticGeometry.Axis secondAxis, int secondAmount) {
        int[] values = new int[3];
        values[firstAxis.ordinal()] = firstAmount;
        values[secondAxis.ordinal()] = secondAmount;
        return new KineticGeometry.Position(values[0], values[1], values[2]);
    }

    private static double modifier(
            KineticGeometry.Block from, KineticGeometry.Block to, KineticGeometry.Position offset) {
        return KineticGeometry.rotationModifier(from, ORIGIN, to, ORIGIN.add(offset));
    }

    private static KineticGeometry.Position direction(KineticGeometry.Axis axis, int amount) {
        return switch (axis) {
            case X -> new KineticGeometry.Position(amount, 0, 0);
            case Y -> new KineticGeometry.Position(0, amount, 0);
            case Z -> new KineticGeometry.Position(0, 0, amount);
        };
    }
}
