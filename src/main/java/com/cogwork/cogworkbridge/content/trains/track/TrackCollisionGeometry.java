package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Collision surfaces follow the width and height of the visible railway bed. */
final class TrackCollisionGeometry {
    private static final double WIDTH=1.375D;
    private static final EnumMap<TrackShape,List<AxisAlignedBB>> SHAPES=new EnumMap<>(TrackShape.class);
    private record Cell(int x,int y,int z) { }
    private TrackCollisionGeometry() { }

    static List<AxisAlignedBB> straight(TrackShape shape) {
        return SHAPES.computeIfAbsent(shape,TrackCollisionGeometry::buildStraight);
    }
    private static List<AxisAlignedBB> buildStraight(TrackShape shape) {
        if(shape==TrackShape.XO)return Collections.singletonList(new AxisAlignedBB(0,0,-.875D,1,.125D,1.875D));
        if(shape==TrackShape.ZO)return Collections.singletonList(new AxisAlignedBB(-.875D,0,0,1.875D,.125D,1));
        Set<Cell> cells=new LinkedHashSet<>();
        addStraight(cells,shape.axis(),shape.slope());
        Vec3d second=switch(shape) {
            case CR_O -> new Vec3d(1,0,0);
            case CR_D -> new Vec3d(-1,0,1);
            case CR_PDX,CR_PDZ -> new Vec3d(1,0,1);
            case CR_NDX,CR_NDZ -> new Vec3d(-1,0,1);
            default -> null;
        };
        if(second!=null)addStraight(cells,second,false);
        return boxes(cells);
    }
    private static void addStraight(Set<Cell> cells,Vec3d axis,boolean slope) {
        Vec3d normal=new Vec3d(axis.z,0,-axis.x).normalize();
        for(int i=0;i<=12;i++) {
            double step=i/12D-.5D;
            Vec3d point=new Vec3d(.5D,slope?.5D:0D,.5D).add(axis.scale(step));
            addSection(cells,point,normal,BlockPos.ORIGIN);
        }
    }

    static List<AxisAlignedBB> curve(TrackCurve curve,BlockPos owner) {
        Set<Cell> cells=new LinkedHashSet<>();
        int samples=Math.max(8,(int)Math.ceil(curve.length()*8D));
        for(int i=0;i<=samples;i++) {
            double t=curve.parameterAtFraction(i/(double)samples);
            Vec3d point=curve.point(t);
            if(!new BlockPos(point.x,point.y+.125D,point.z).equals(owner))continue;
            Vec3d tangent=curve.tangent(t);
            Vec3d normal=new Vec3d(tangent.z,0,-tangent.x).normalize();
            if(normal.lengthSquared()<1E-10D)normal=new Vec3d(1,0,0);
            addSection(cells,point,normal,owner);
        }
        return boxes(cells);
    }
    private static void addSection(Set<Cell> cells,Vec3d point,Vec3d normal,BlockPos owner) {
        int height=(int)Math.round((point.y-owner.getY()+.125D)*8D);
        for(int across=-11;across<=11;across++) {
            double distance=across*WIDTH/11D;
            double x=point.x+normal.x*distance-owner.getX();
            double z=point.z+normal.z*distance-owner.getZ();
            cells.add(new Cell(MathHelper.floor(x*4D),height,MathHelper.floor(z*4D)));
        }
    }
    private static List<AxisAlignedBB> boxes(Set<Cell> cells) {
        List<AxisAlignedBB> result=new ArrayList<>(cells.size());
        for(Cell cell:cells) {
            double x=cell.x*.25D,z=cell.z*.25D,top=cell.y*.125D;
            result.add(new AxisAlignedBB(x,top-.125D,z,x+.25D,top,z+.25D));
        }
        return Collections.unmodifiableList(result);
    }
}
