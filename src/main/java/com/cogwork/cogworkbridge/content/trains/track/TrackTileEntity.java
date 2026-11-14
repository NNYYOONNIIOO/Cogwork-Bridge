package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Stores track curves independently from the orientation metadata. */
public final class TrackTileEntity extends TileEntity implements ITickable {
    private final Map<BlockPos, TrackCurve> connections = new LinkedHashMap<>();
    private List<AxisAlignedBB> collisionBoxes;
    private boolean suppressDrops;
    private boolean portalLinked;
    public boolean portalLinked(){return portalLinked;}
    void setPortalLinked(boolean linked){if(portalLinked!=linked){portalLinked=linked;changed();}}
    public void suppressDrops() { suppressDrops=true; }
    public boolean dropsSuppressed() { return suppressDrops; }
    public Collection<TrackCurve> connections() { return Collections.unmodifiableCollection(connections.values()); }
    public boolean has(BlockPos other) { return connections.containsKey(other); }
    public void add(TrackCurve curve) {
        if (!curve.first.equals(pos) && !curve.second.equals(pos)) return;
        connections.put(curve.other(pos), curve); collisionBoxes=null; changed();
    }
    public TrackCurve remove(BlockPos other) {
        TrackCurve removed=connections.remove(other);
        if (removed!=null) { collisionBoxes=null; changed(); }
        return removed;
    }
    @javax.annotation.Nullable public TrackCurve curve(BlockPos other){return connections.get(other);}
    List<AxisAlignedBB> curveCollision() {
        if(collisionBoxes==null) {
            List<AxisAlignedBB> boxes=new ArrayList<>();
            for(TrackCurve curve:connections.values())boxes.addAll(TrackCollisionGeometry.curve(curve,pos));
            collisionBoxes=boxes;
        }
        return collisionBoxes;
    }
    private void changed() {
        markDirty();
        if (world!=null && !world.isRemote) {
            RailwayNetworkData.get(world).update(world,pos);
            world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if(world!=null && !world.isRemote) {
            RailwayNetworkData graph=RailwayNetworkData.get(world);
            if(graph.hasTrack(pos)&&connections.entrySet().removeIf(entry->graph.findCurve(pos,entry.getKey())==null))markDirty();
            graph.update(world,pos);
        }
    }
    @Override public void update() {
        if(world!=null&&!world.isRemote&&(world.getTotalWorldTime()+pos.hashCode())%20==0)RailwayPortals.discover(world,pos);
        if(world==null || world.isRemote || (world.getTotalWorldTime()+pos.hashCode())%200!=0) return;
        for(BlockPos other:new ArrayList<>(connections.keySet())) {
            if(!world.isBlockLoaded(other)) continue;
            if(!(world.getTileEntity(other) instanceof TrackTileEntity peer) || !peer.has(pos)) {
                TrackCurve curve=connections.get(other);
                if(curve!=null)TrackCurveSupport.remove(world,curve,false,null);
            }
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); NBTTagList list=new NBTTagList();
        for(TrackCurve c:connections.values()) list.appendTag(c.write());
        tag.setTag("Curves",list);tag.setBoolean("PortalLinked",portalLinked); return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); connections.clear();collisionBoxes=null;
        portalLinked=tag.getBoolean("PortalLinked");
        NBTTagList list=tag.getTagList("Curves",10);
        for(int i=0;i<list.tagCount() && i<64;i++) {
            TrackCurve curve=TrackCurve.read(list.getCompoundTagAt(i));
            if((curve.first.equals(pos)||curve.second.equals(pos)) && !curve.first.equals(curve.second)
                    && curve.first.distanceSq(curve.second)<=128D*128D) connections.put(curve.other(pos),curve);
        }
    }
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager net,SPacketUpdateTileEntity packet){
        readFromNBT(packet.getNbtCompound());
        if(world!=null&&world.isRemote)world.markBlockRangeForRenderUpdate(pos,pos);
    }
    @Override public AxisAlignedBB getRenderBoundingBox() {
        AxisAlignedBB result=new AxisAlignedBB(pos).grow(2);
        for(TrackCurve c:connections.values()) {
            AxisAlignedBB curveBounds=new AxisAlignedBB(c.start.x,c.start.y,c.start.z,c.finish.x,c.finish.y,c.finish.z);
            for(net.minecraft.util.math.Vec3d point:new net.minecraft.util.math.Vec3d[]{c.control1,c.control2})
                curveBounds=curveBounds.union(new AxisAlignedBB(point.x,point.y,point.z,point.x,point.y,point.z));
            result=result.union(curveBounds.grow(2));
        }
        return result;
    }
    @Override public double getMaxRenderDistanceSquared() { return 65536D; }
}
