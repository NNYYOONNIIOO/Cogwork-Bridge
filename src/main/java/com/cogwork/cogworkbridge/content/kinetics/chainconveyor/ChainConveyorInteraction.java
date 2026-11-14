package com.cogwork.cogworkbridge.content.kinetics.chainconveyor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;

/** Ray intersection with the suspended chains, including the two separate strands. */
public final class ChainConveyorInteraction {
    private ChainConveyorInteraction() { }
    public record Hit(ChainConveyorTileEntity wheel, net.minecraft.util.math.BlockPos end, float progress, Vec3d point) { }
    public record ParcelHit(ChainConveyorTileEntity wheel, ChainConveyorTileEntity.Parcel parcel) { }
    /** A wheel extends outside its hosting block; vanilla block ray tracing misses the rim. */
    public static ChainConveyorTileEntity traceWheel(EntityPlayer player, double reach) {
        Vec3d eye = player.getPositionEyes(1F), end = eye.add(player.getLook(1F).normalize().scale(reach));
        RayTraceResult obstruction = player.world.rayTraceBlocks(eye, end, false, true, false);
        double best = obstruction != null && obstruction.hitVec != null ? eye.squareDistanceTo(obstruction.hitVec) + 0.01D : reach * reach;
        ChainConveyorTileEntity nearest = null;
        for (TileEntity tile : new ArrayList<>(player.world.loadedTileEntityList)) {
            if (!(tile instanceof ChainConveyorTileEntity wheel) || tile.isInvalid()) continue;
            if (player.getDistanceSq(wheel.getPos()) > (reach + 3D) * (reach + 3D)) continue;
            RayTraceResult hit = ChainConveyorBlock.OUTLINE.offset(wheel.getPos()).calculateIntercept(eye, end);
            if (hit == null || eye.squareDistanceTo(hit.hitVec) > best) continue;
            best = eye.squareDistanceTo(hit.hitVec);
            nearest = wheel;
        }
        return nearest;
    }
    public static ParcelHit traceParcel(EntityPlayer player, double reach) {
        Vec3d eye = player.getPositionEyes(1F), end = eye.add(player.getLook(1F).normalize().scale(reach));
        RayTraceResult obstruction = player.world.rayTraceBlocks(eye, end, false, true, false);
        double best = obstruction != null && obstruction.hitVec != null ? eye.squareDistanceTo(obstruction.hitVec) : reach * reach;
        ParcelHit nearest = null;
        for (TileEntity tile : new ArrayList<>(player.world.loadedTileEntityList)) {
            if (!(tile instanceof ChainConveyorTileEntity wheel) || tile.isInvalid()) continue;
            for (var box : wheel.parcels()) {
                if (!(box.item.getItem() instanceof com.cogwork.cogworkbridge.content.logistics.packagebox.PackageItem item)) continue;
                Vec3d p = wheel.packagePosition(box, 1F);
                double half = item.packageWidth() / 32D, height = item.packageHeight() / 16D;
                var aabb = new net.minecraft.util.math.AxisAlignedBB(p.x-half, p.y-.55D-height/2, p.z-half,
                        p.x+half, p.y-.55D+height/2, p.z+half);
                RayTraceResult hit = aabb.calculateIntercept(eye, end);
                if (hit == null || eye.squareDistanceTo(hit.hitVec) > best) continue;
                best = eye.squareDistanceTo(hit.hitVec); nearest = new ParcelHit(wheel, box);
            }
        }
        return nearest;
    }
    public static Hit trace(EntityPlayer player, double reach) {
        Vec3d eye = player.getPositionEyes(1F), ray = player.getLook(1F).normalize().scale(reach);
        RayTraceResult obstruction = player.world.rayTraceBlocks(eye, eye.add(ray), false, true, false);
        double max = obstruction != null && obstruction.hitVec != null ? eye.distanceTo(obstruction.hitVec) : reach;
        Hit nearest = null; double best = max + .05D;
        for (TileEntity tile : new ArrayList<>(player.world.loadedTileEntityList)) {
            if (!(tile instanceof ChainConveyorTileEntity wheel) || tile.isInvalid()) continue;
            for (var target : wheel.connections()) {
                var g = wheel.geometry(target); Vec3d strand = g.end().subtract(g.start());
                Vec3d w = eye.subtract(g.start()); double a = ray.dotProduct(ray), b = ray.dotProduct(strand), c = strand.dotProduct(strand);
                double d = ray.dotProduct(w), e = strand.dotProduct(w), denominator = a*c-b*b;
                if (c < 1.0e-8 || denominator < 1.0e-8) continue;
                double s = Math.max(0, Math.min(1, (b*e-c*d)/denominator));
                double t = Math.max(0, Math.min(1, (b*s+e)/c));
                s = Math.max(0, Math.min(1, (b*t-d)/a));
                Vec3d p = eye.add(ray.scale(s)), q = g.start().add(strand.scale(t));
                double distance = s * reach;
                if (p.squareDistanceTo(q) > .22D * .22D || distance > best) continue;
                best = distance; nearest = new Hit(wheel, target, (float)(t*g.length()), q);
            }
        }
        return nearest;
    }
}
