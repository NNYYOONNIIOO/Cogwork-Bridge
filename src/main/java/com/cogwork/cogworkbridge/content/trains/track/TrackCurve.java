package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** A bidirectional cubic track connection. All coordinates are world coordinates. */
public final class TrackCurve {
    public final BlockPos first, second;
    public final Vec3d start, finish, control1, control2;
    public final int cost;
    private double[] arcLengths;
    public TrackCurve(BlockPos first, BlockPos second, Vec3d start, Vec3d finish, Vec3d control1, Vec3d control2, int cost) {
        this.first=first.toImmutable(); this.second=second.toImmutable(); this.start=start; this.finish=finish;
        this.control1=control1; this.control2=control2; this.cost=Math.max(1,cost);
    }
    public BlockPos other(BlockPos pos) { return first.equals(pos) ? second : first; }
    public boolean primary(BlockPos pos) { return first.equals(pos); }
    public Vec3d point(double t) {
        double u=1-t;
        return start.scale(u*u*u).add(control1.scale(3*u*u*t))
                .add(control2.scale(3*u*t*t)).add(finish.scale(t*t*t));
    }
    public Vec3d tangent(double t) {
        double u=1-t;
        return control1.subtract(start).scale(3*u*u).add(control2.subtract(control1).scale(6*u*t))
                .add(finish.subtract(control2).scale(3*t*t));
    }
    public double length() {
        ensureArcLengths();return arcLengths[arcLengths.length-1];
    }
    /** Invert sampled arc length so vehicle speed does not change along a Bezier. */
    public double parameterAtFraction(double fraction) {
        ensureArcLengths();double wanted=Math.max(0D,Math.min(1D,fraction))*length();
        int high=java.util.Arrays.binarySearch(arcLengths,wanted);
        if(high>=0)return high/(double)(arcLengths.length-1);
        high=Math.min(arcLengths.length-1,Math.max(1,-high-1));
        double lowLength=arcLengths[high-1],span=arcLengths[high]-lowLength;
        double local=span<1E-9D?0D:(wanted-lowLength)/span;
        return (high-1+local)/(arcLengths.length-1);
    }
    private void ensureArcLengths() {
        if(arcLengths!=null)return;
        arcLengths=new double[65];Vec3d previous=start;
        for(int i=1;i<arcLengths.length;i++) {
            Vec3d p=point(i/(double)(arcLengths.length-1));
            arcLengths[i]=arcLengths[i-1]+p.distanceTo(previous);previous=p;
        }
    }
    public NBTTagCompound write() {
        NBTTagCompound tag=new NBTTagCompound();
        tag.setLong("A",first.toLong()); tag.setLong("B",second.toLong()); tag.setInteger("Cost",cost);
        writeVec(tag,"S",start); writeVec(tag,"E",finish); writeVec(tag,"C1",control1); writeVec(tag,"C2",control2);
        return tag;
    }
    public static TrackCurve read(NBTTagCompound tag) {
        return new TrackCurve(BlockPos.fromLong(tag.getLong("A")), BlockPos.fromLong(tag.getLong("B")),
                readVec(tag,"S"), readVec(tag,"E"), readVec(tag,"C1"), readVec(tag,"C2"),tag.getInteger("Cost"));
    }
    private static void writeVec(NBTTagCompound tag,String key,Vec3d v) {
        tag.setDouble(key+"X",v.x);tag.setDouble(key+"Y",v.y);tag.setDouble(key+"Z",v.z);
    }
    private static Vec3d readVec(NBTTagCompound tag,String key) {
        return new Vec3d(tag.getDouble(key+"X"),tag.getDouble(key+"Y"),tag.getDouble(key+"Z"));
    }
}
