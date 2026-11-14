package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import java.util.*;

/** Invisible curve support, shared by all curves passing through the block. */
public final class FakeTrackTile extends TileEntity implements ITickable {
    public record Link(BlockPos first,BlockPos second) { }
    private final Map<Link,TrackCurve> links=new LinkedHashMap<>();
    private List<AxisAlignedBB> collisionBoxes;
    private boolean suppressBreak,suppressDrops;
    public Set<Link> links(){return new LinkedHashSet<>(links.keySet());}
    public boolean suppressBreak(){return suppressBreak;}
    public boolean suppressDrops(){return suppressDrops;}
    public void suppressDropsForCreative(){suppressDrops=true;}
    public void add(TrackCurve curve){links.put(new Link(curve.first,curve.second),curve);changed();}
    public void remove(TrackCurve curve) {
        Link link=new Link(curve.first,curve.second);
        if(!links.containsKey(link))return;
        links.remove(link);changed();
        if(links.isEmpty()&&world!=null&&!world.isRemote) {
            suppressBreak=true;world.setBlockToAir(pos);
        }
    }
    private void changed() {
        collisionBoxes=null;markDirty();
        if(world!=null&&!world.isRemote)
            world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);
    }
    List<AxisAlignedBB> curveCollision() {
        if(collisionBoxes!=null)return collisionBoxes;
        List<AxisAlignedBB> result=new ArrayList<>();
        boolean unresolved=false;
        for(Map.Entry<Link,TrackCurve> entry:links.entrySet()) {
            TrackCurve curve=entry.getValue();
            if(curve==null) {
                Link link=entry.getKey();
                if(world!=null&&world.isBlockLoaded(link.first())
                        &&world.getTileEntity(link.first()) instanceof TrackTileEntity track)curve=track.curve(link.second());
                if(curve==null&&world!=null&&world.isBlockLoaded(link.second())
                        &&world.getTileEntity(link.second()) instanceof TrackTileEntity track)curve=track.curve(link.first());
                if(curve==null&&world!=null&&!world.isRemote)
                    curve=RailwayNetworkData.get(world).findCurve(link.first(),link.second());
                if(curve!=null)entry.setValue(curve);
            }
            if(curve!=null)result.addAll(TrackCollisionGeometry.curve(curve,pos));
            else unresolved=true;
        }
        if(unresolved||links.isEmpty())result.add(new AxisAlignedBB(0,0,0,1,.125D,1));
        // Retry unresolved legacy links when their endpoint or its update arrives.
        if(!unresolved)collisionBoxes=result;
        return result;
    }
    @Override public void update() {
        if(world==null||world.isRemote||(world.getTotalWorldTime()+pos.hashCode())%100!=0)return;
        RailwayNetworkData graph=RailwayNetworkData.get(world);
        boolean dirty=false;
        for(Iterator<Map.Entry<Link,TrackCurve>> it=links.entrySet().iterator();it.hasNext();) {
            Map.Entry<Link,TrackCurve> entry=it.next();Link link=entry.getKey();
            TrackCurve curve=graph.findCurve(link.first(),link.second());
            if(curve==null){it.remove();dirty=true;}
            else if(entry.getValue()==null){entry.setValue(curve);dirty=true;}
        }
        if(dirty)changed();
        if(links.isEmpty()){suppressBreak=true;world.setBlockToAir(pos);}
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);NBTTagList list=new NBTTagList();
        for(Map.Entry<Link,TrackCurve> link:links.entrySet()) {
            NBTTagCompound entry=link.getValue()==null?new NBTTagCompound():link.getValue().write();
            entry.setLong("A",link.getKey().first().toLong());entry.setLong("B",link.getKey().second().toLong());
            list.appendTag(entry);
        }
        tag.setTag("Curves",list);return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);links.clear();collisionBoxes=null;NBTTagList list=tag.getTagList("Curves",10);
        for(int i=0;i<list.tagCount()&&i<64;i++) {
            NBTTagCompound entry=list.getCompoundTagAt(i);
            BlockPos a=BlockPos.fromLong(entry.getLong("A")),b=BlockPos.fromLong(entry.getLong("B"));
            if(!a.equals(b)&&a.distanceSq(b)<=128D*128D)
                links.put(new Link(a,b),entry.hasKey("SX")?TrackCurve.read(entry):null);
        }
    }
    @Override public NBTTagCompound getUpdateTag(){return writeToNBT(new NBTTagCompound());}
    @Override public SPacketUpdateTileEntity getUpdatePacket(){return new SPacketUpdateTileEntity(pos,0,getUpdateTag());}
    @Override public void onDataPacket(NetworkManager network,SPacketUpdateTileEntity packet){readFromNBT(packet.getNbtCompound());}
}
