package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.Vec3d;
import javax.annotation.Nullable;

/** The fourteen non-portal 1.21.1 orientations fit in the 1.12 metadata budget. */
public enum TrackShape implements IStringSerializable {
    ZO(0,0,1), XO(1,0,0), PD(1,0,1), ND(-1,0,1),
    AN(0,1,-1), AS(0,1,1), AE(1,1,0), AW(-1,1,0),
    CR_O(0,0,1), CR_D(1,0,1), CR_PDX(1,0,0), CR_PDZ(0,0,1), CR_NDX(1,0,0), CR_NDZ(0,0,1);
    public final int x, y, z;
    TrackShape(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
    public Vec3d axis() { return new Vec3d(x,y,z); }
    public boolean junction() { return ordinal() >= CR_O.ordinal(); }
    public boolean slope() { return y != 0; }
    /** Combine the two level axes that may occupy a crossing, as in Create's track overlay. */
    @Nullable public static TrackShape overlay(TrackShape existing, TrackShape placed) {
        if (existing == placed || existing.contains(placed)) return existing;
        if (existing.junction() || placed.junction() || existing.slope() || placed.slope()) return null;
        if (pair(existing, placed, XO, ZO)) return CR_O;
        if (pair(existing, placed, PD, ND)) return CR_D;
        if (pair(existing, placed, XO, PD)) return CR_PDX;
        if (pair(existing, placed, ZO, PD)) return CR_PDZ;
        if (pair(existing, placed, XO, ND)) return CR_NDX;
        if (pair(existing, placed, ZO, ND)) return CR_NDZ;
        return null;
    }
    private static boolean pair(TrackShape a, TrackShape b, TrackShape x, TrackShape y) {
        return a == x && b == y || a == y && b == x;
    }
    public boolean contains(TrackShape axis) {
        if (this == axis) return true;
        return switch (this) {
            case CR_O -> axis == XO || axis == ZO;
            case CR_D -> axis == PD || axis == ND;
            case CR_PDX -> axis == PD || axis == XO;
            case CR_PDZ -> axis == PD || axis == ZO;
            case CR_NDX -> axis == ND || axis == XO;
            case CR_NDZ -> axis == ND || axis == ZO;
            default -> false;
        };
    }
    @Override public String getName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public static TrackShape byMeta(int meta) {
        TrackShape[] values = values(); return meta >= 0 && meta < values.length ? values[meta] : ZO;
    }
}
