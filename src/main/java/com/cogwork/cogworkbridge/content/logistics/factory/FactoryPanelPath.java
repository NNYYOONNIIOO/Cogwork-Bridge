package com.cogwork.cogworkbridge.content.logistics.factory;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/** Half-block arrow routes, adapted from Create 1.21.1 FactoryPanelConnection (MIT). */
public final class FactoryPanelPath {
    private FactoryPanelPath() { }
    public static Vec3d worldPoint(BlockPos pos, EnumFacing face, double x, double y, double z) {
        Vec3d local = switch (face) {
            case DOWN -> new Vec3d(x, 1-y, 1-z);
            case NORTH -> new Vec3d(x, z, 1-y);
            case SOUTH -> new Vec3d(x, 1-z, y);
            case EAST -> new Vec3d(y, 1-x, z);
            case WEST -> new Vec3d(1-y, x, z);
            default -> new Vec3d(x,y,z);
        };
        return local.add(pos.getX(), pos.getY(), pos.getZ());
    }
    public static List<EnumFacing> route(FactoryPanelTileEntity tile, int slot, FactoryPanelTileEntity.Connection link) {
        var state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof FactoryPanelBlock)) return List.of();
        EnumFacing face = state.getValue(FactoryPanelBlock.FACING);
        BlockPos d = link.pos().subtract(tile.getPos());
        int px = switch (face) { case EAST -> -d.getY(); case WEST -> d.getY(); default -> d.getX(); };
        int pz = switch (face) { case DOWN -> -d.getZ(); case NORTH -> d.getY(); case SOUTH -> -d.getY(); default -> d.getZ(); };
        int dx = px * 2 + (link.slot() & 1) - (slot & 1);
        int dz = pz * 2 + (link.slot() >> 1) - (slot >> 1);
        if (Math.abs(dx) + Math.abs(dz) > 96) return List.of();
        List<EnumFacing> path = List.of();
        for (int mode = 0; mode <= 4; mode++) {
            if (link.bendMode() >= 0 && mode != link.bendMode()) continue;
            List<EnumFacing> candidate = new ArrayList<>();
            int firstX = 0, firstZ = 0;
            if (mode > 1) {
                boolean flipX = dx > 0 ^ mode % 2 == 1, flipZ = dz > 0 ^ mode % 2 == 0;
                firstX = flipX ? Math.floorDiv(dx, 2) : -Math.floorDiv(-dx, 2);
                firstZ = flipZ ? Math.floorDiv(dz, 2) : -Math.floorDiv(-dz, 2);
            }
            segment(candidate, firstX, firstZ, mode);
            segment(candidate, dx-firstX, dz-firstZ, mode);
            path = candidate;
            if (link.bendMode() >= 0 || mode == 4 || clear(tile, slot, link, face, candidate)) break;
        }
        return path;
    }
    private static void segment(List<EnumFacing> path, int dx, int dz, int mode) {
        boolean zFirst = mode == 4 ? Math.abs(dz) > Math.abs(dx) : mode % 2 == 1;
        if (zFirst) { for (int i=0;i<Math.abs(dz);i++) path.add(dz>0?EnumFacing.SOUTH:EnumFacing.NORTH); }
        for (int i=0;i<Math.abs(dx);i++) path.add(dx>0?EnumFacing.EAST:EnumFacing.WEST);
        if (!zFirst) { for (int i=0;i<Math.abs(dz);i++) path.add(dz>0?EnumFacing.SOUTH:EnumFacing.NORTH); }
    }
    private static boolean clear(FactoryPanelTileEntity tile, int slot, FactoryPanelTileEntity.Connection link,
            EnumFacing face, List<EnumFacing> path) {
        double x=.25+(slot&1)*.5, z=.25+(slot>>1)*.5;
        for (int i=0;i<path.size()-1;i++) {
            EnumFacing step=path.get(i); x+=step.getXOffset()*.5; z+=step.getZOffset()*.5;
            Vec3d p=worldPoint(tile.getPos(),face,x,.126,z); BlockPos cell=new BlockPos(p);
            if (!tile.getWorld().isBlockLoaded(cell)) return false;
            if (tile.getWorld().getTileEntity(cell) instanceof FactoryPanelTileEntity other) {
                int at=FactoryPanelBlock.slot(face,(float)(p.x-cell.getX()),(float)(p.y-cell.getY()),(float)(p.z-cell.getZ()));
                if (other.active(at) && !(cell.equals(tile.getPos()) && at==slot)
                        && !(cell.equals(link.pos()) && at==link.slot())) return false;
            } else if (!tile.getWorld().getCollisionBoxes(null,new AxisAlignedBB(p.x-.007,p.y-.007,p.z-.007,p.x+.007,p.y+.007,p.z+.007)).isEmpty()) return false;
        }
        return true;
    }
}
