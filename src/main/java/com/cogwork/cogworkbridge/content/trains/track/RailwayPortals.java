package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.block.BlockPortal;
import net.minecraft.init.Blocks;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.storage.WorldSavedData;
import java.util.*;

/** Portal pairing and routes between dimension-local track graphs; no portal is generated automatically. */
public final class RailwayPortals extends WorldSavedData {
    private static final String NAME="cogworkbridge_railway_portals";
    private static boolean connecting;
    private record Link(BlockPos track,EnumFacing face,int dimension,BlockPos other,EnumFacing otherFace) { }
    /** Rigid portal frames. Directions point into the source and out of the destination. */
    public record Passage(int sourceDimension, int targetDimension, Vec3d source, Vec3d target,
                          EnumFacing sourceDirection, EnumFacing targetDirection) {
        public Vec3d mapVector(Vec3d vector) {
            Vec3d a=new Vec3d(sourceDirection.getDirectionVec()), b=new Vec3d(targetDirection.getDirectionVec());
            Vec3d rightA=new Vec3d(-a.z,0,a.x), rightB=new Vec3d(-b.z,0,b.x);
            return b.scale(vector.dotProduct(a)).add(rightB.scale(vector.dotProduct(rightA))).add(0,vector.y,0);
        }
        public Vec3d map(Vec3d point) { return target.add(mapVector(point.subtract(source))); }
        public Passage reverse() { return new Passage(targetDimension,sourceDimension,target,source,
                targetDirection.getOpposite(),sourceDirection.getOpposite()); }
        public double sourceDistance(Vec3d point) { return point.subtract(source).dotProduct(new Vec3d(sourceDirection.getDirectionVec())); }
        public NBTTagCompound write() {
            var tag=new NBTTagCompound();tag.setInteger("FromDimension",sourceDimension);tag.setInteger("ToDimension",targetDimension);
            tag.setDouble("FromX",source.x);tag.setDouble("FromY",source.y);tag.setDouble("FromZ",source.z);
            tag.setDouble("ToX",target.x);tag.setDouble("ToY",target.y);tag.setDouble("ToZ",target.z);
            tag.setByte("FromDirection",(byte)sourceDirection.getIndex());tag.setByte("ToDirection",(byte)targetDirection.getIndex());return tag;
        }
        public static Passage read(NBTTagCompound tag) {
            if(!tag.hasKey("FromX")||!tag.hasKey("ToX"))return null;
            EnumFacing a=EnumFacing.byIndex(tag.getByte("FromDirection")), b=EnumFacing.byIndex(tag.getByte("ToDirection"));
            if(a.getAxis()==EnumFacing.Axis.Y||b.getAxis()==EnumFacing.Axis.Y)return null;
            Vec3d from=new Vec3d(tag.getDouble("FromX"),tag.getDouble("FromY"),tag.getDouble("FromZ"));
            Vec3d to=new Vec3d(tag.getDouble("ToX"),tag.getDouble("ToY"),tag.getDouble("ToZ"));
            if(!Double.isFinite(from.lengthSquared())||!Double.isFinite(to.lengthSquared()))return null;
            return new Passage(tag.getInteger("FromDimension"),tag.getInteger("ToDimension"),from,to,a,b);
        }
    }
    private final Map<BlockPos,Link> links=new LinkedHashMap<>();
    public RailwayPortals(){this(NAME);}
    public RailwayPortals(String name){super(name);}
    private static RailwayPortals get(World world) {
        var storage=world.getPerWorldStorage();var data=(RailwayPortals)storage.getOrLoadData(RailwayPortals.class,NAME);
        if(data==null){data=new RailwayPortals();storage.setData(NAME,data);}return data;
    }
    public static void remove(World world,BlockPos pos) {
        Link old=get(world).links.remove(pos);get(world).markDirty();
        setLinked(world,pos,false);
        if(old!=null&&world.getMinecraftServer()!=null) {var other=world.getMinecraftServer().getWorld(old.dimension);if(other!=null){get(other).links.remove(old.other);get(other).markDirty();setLinked(other,old.other,false);}}
    }
    private static void setLinked(World world,BlockPos pos,boolean linked){if(world.isBlockLoaded(pos)&&world.getTileEntity(pos) instanceof TrackTileEntity tile)tile.setPortalLinked(linked);}
    private static boolean valid(World world,Link link) {
        if(!world.isBlockLoaded(link.track))return true;
        var state=world.getBlockState(link.track);var portal=world.getBlockState(link.track.offset(link.face));
        return state.getBlock() instanceof TrackBlock&&(state.getValue(TrackBlock.SHAPE)==TrackShape.XO&&link.face.getAxis()==EnumFacing.Axis.X
                ||state.getValue(TrackBlock.SHAPE)==TrackShape.ZO&&link.face.getAxis()==EnumFacing.Axis.Z)
                &&portal.getBlock()==Blocks.PORTAL&&portal.getValue(BlockPortal.AXIS)!=link.face.getAxis();
    }
    private static RailwayNetworkData.Node boundary(BlockPos pos,EnumFacing face) {
        return RailwayNetworkData.Node.at(new Vec3d(pos.getX()+.5+face.getXOffset()*.5,pos.getY(),pos.getZ()+.5+face.getZOffset()*.5));
    }
    /** A portal can start clipping the body before either bogey reaches its boundary. */
    static Passage nearby(World world,Vec3d position,Vec3d heading,double reach) {
        if(world.getMinecraftServer()==null||heading==null)return null;
        Passage best=null;double distance=Double.MAX_VALUE;
        for(Link link:get(world).links.values()) {
            if(!valid(world,link)||!world.isBlockLoaded(link.track))continue;
            Vec3d normal=new Vec3d(link.face.getDirectionVec()), at=boundary(link.track,link.face).position();
            Vec3d delta=position.subtract(at);
            if(Math.abs(heading.dotProduct(normal))<.95||Math.abs(delta.x*normal.z-delta.z*normal.x)>.25
                    ||Math.abs(delta.y)>1||delta.dotProduct(normal)>1||delta.lengthSquared()>reach*reach)continue;
            WorldServer target=world.getMinecraftServer().getWorld(link.dimension);
            Link peer=target==null?null:get(target).links.get(link.other);
            if(peer==null||!valid(target,peer)||!target.isBlockLoaded(link.other))continue;
            if(delta.lengthSquared()<distance) {
                distance=delta.lengthSquared();best=new Passage(world.provider.getDimension(),link.dimension,at,
                        boundary(link.other,link.otherFace).position(),link.face,link.otherFace.getOpposite());
            }
        }
        return best;
    }
    public static void discover(World world,BlockPos pos) {
        if(world.isRemote||connecting||!(world instanceof WorldServer source))return;
        Link linked=get(world).links.get(pos);
        if(linked!=null) {
            var peer=world.getMinecraftServer().getWorld(linked.dimension);var reverse=peer==null?null:get(peer).links.get(linked.other);
            if(!valid(world,linked)||peer==null||reverse==null||!valid(peer,reverse))remove(world,pos);
            else {setLinked(world,pos,true);setLinked(peer,linked.other,true);return;}
        }
        var state=world.getBlockState(pos);if(!(state.getBlock() instanceof TrackBlock))return;
        TrackShape shape=state.getValue(TrackBlock.SHAPE);if(shape!=TrackShape.XO&&shape!=TrackShape.ZO)return;
        int dim=world.provider.getDimension();if(dim!=0&&dim!=-1)return;
        WorldServer other=world.getMinecraftServer().getWorld(dim==0?-1:0);if(other==null)return;
        for(EnumFacing face:EnumFacing.HORIZONTALS) {
            if(face.getAxis()!=(shape==TrackShape.XO?EnumFacing.Axis.X:EnumFacing.Axis.Z)
                    ||world.getBlockState(pos.offset(face)).getBlock()!=Blocks.PORTAL
                    ||world.getBlockState(pos.offset(face)).getValue(BlockPortal.AXIS)==face.getAxis())continue;
            connecting=true;
            try {
                EntityMinecartEmpty probe=new EntityMinecartEmpty(world);
                probe.setLocationAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()+.5,face.getHorizontalAngle(),0);
                probe.setPortal(pos.offset(face));
                double factor=world.provider.getMovementFactor()/other.provider.getMovementFactor();
                probe.setWorld(other);probe.dimension=other.provider.getDimension();
                probe.setLocationAndAngles(MathHelper.clamp(probe.posX*factor,-29999872,29999872),probe.posY,
                        MathHelper.clamp(probe.posZ*factor,-29999872,29999872),probe.rotationYaw,0);
                if(!other.getDefaultTeleporter().placeInExistingPortal(probe,probe.rotationYaw))continue;
                BlockPos portal=null;double best=Double.MAX_VALUE;
                BlockPos around=new BlockPos(probe);
                for(BlockPos candidate:BlockPos.getAllInBox(around.add(-3,-3,-3),around.add(3,3,3)))
                    if(other.getBlockState(candidate).getBlock()==Blocks.PORTAL&&candidate.distanceSq(around)<best){portal=candidate.toImmutable();best=candidate.distanceSq(around);}
                if(portal==null)continue;
                while(other.getBlockState(portal.down()).getBlock()==Blocks.PORTAL)portal=portal.down();
                EnumFacing outward=EnumFacing.fromAngle(probe.rotationYaw);
                var portalAxis=other.getBlockState(portal).getValue(BlockPortal.AXIS);
                if(outward.getAxis()==portalAxis)outward=portalAxis==EnumFacing.Axis.X?EnumFacing.NORTH:EnumFacing.EAST;
                BlockPos peer=portal.offset(outward);var existing=other.getBlockState(peer);
                TrackShape peerShape=outward.getAxis()==EnumFacing.Axis.X?TrackShape.XO:TrackShape.ZO;
                if(existing.getBlock() instanceof TrackBlock&&existing.getValue(TrackBlock.SHAPE)!=peerShape)continue;
                Link peerLink=get(other).links.get(peer);if(peerLink!=null&&(!peerLink.other.equals(pos)||peerLink.dimension!=dim))continue;
                if(!(existing.getBlock() instanceof TrackBlock)&&!existing.getBlock().isReplaceable(other,peer))continue;
                if(!(existing.getBlock() instanceof TrackBlock))other.setBlockState(peer,state.withProperty(TrackBlock.SHAPE,peerShape),3);
                get(world).links.put(pos.toImmutable(),new Link(pos.toImmutable(),face,other.provider.getDimension(),peer,outward.getOpposite()));
                get(other).links.put(peer,new Link(peer,outward.getOpposite(),dim,pos.toImmutable(),face));
                get(world).markDirty();get(other).markDirty();setLinked(world,pos,true);setLinked(other,peer,true);RailwayNetworkData.get(other).update(other,peer);return;
            } finally {connecting=false;}
        }
    }
    public static TrackCursor next(World world,BlockPos track,RailwayNetworkData.Node junction,Vec3d tangent) {
        Link link=get(world).links.get(track);if(link==null||!boundary(track,link.face).equals(junction)
                ||tangent.dotProduct(new Vec3d(link.face.getDirectionVec()))<.8||world.getMinecraftServer()==null)return null;
        var other=world.getMinecraftServer().getWorld(link.dimension);if(other==null||!valid(world,link))return null;
        Link peer=get(other).links.get(link.other);if(peer==null||!valid(other,peer)||!other.isBlockLoaded(link.other))return null;
        var graph=RailwayNetworkData.get(other);var point=boundary(link.other,link.otherFace);
        for(var edge:graph.edges(point))if(edge.track().equals(link.other)&&TrackCursor.usable(other,edge)
                &&edge.tangent(0).dotProduct(new Vec3d(link.otherFace.getOpposite().getDirectionVec()))>.8)return TrackCursor.onEdge(edge,0);
        return null;
    }
    /** Dijkstra over local routes and paired portal boundaries, retaining each edge's dimension. */
    public static List<RailwayNetworkData.Travel> route(World world,int startDim,RailwayNetworkData.Node start,int endDim,RailwayNetworkData.Node end) {
        record Point(int dimension,RailwayNetworkData.Node node) { }
        record Candidate(Point point,double distance,List<RailwayNetworkData.Travel> path) { }
        var server=world.getMinecraftServer();if(server==null)return List.of();
        var queue=new PriorityQueue<Candidate>(Comparator.comparingDouble(Candidate::distance));var seen=new HashMap<Point,Double>();
        queue.add(new Candidate(new Point(startDim,start),0,List.of()));
        Candidate best=null;int visited=0;
        while(!queue.isEmpty()&&visited++<512) {
            Candidate c=queue.poll();if(c.distance>=seen.getOrDefault(c.point,Double.POSITIVE_INFINITY))continue;seen.put(c.point,c.distance);
            WorldServer level=server.getWorld(c.point.dimension);if(level==null)continue;var graph=RailwayNetworkData.get(level);
            if(c.point.dimension==endDim) {
                var tail=graph.route(c.point.node,end);
                if(!tail.isEmpty()||c.point.node.equals(end)) {var path=new ArrayList<>(c.path);path.addAll(tail);double cost=c.distance+tail.stream().mapToDouble(RailwayNetworkData.Travel::length).sum();if(best==null||cost<best.distance)best=new Candidate(new Point(endDim,end),cost,path);}
            }
            for(Link link:get(level).links.values()) {
                if(!valid(level,link))continue;
                var at=boundary(link.track,link.face);var segment=graph.route(c.point.node,at);
                if(segment.isEmpty()&&!c.point.node.equals(at))continue;
                var path=new ArrayList<>(c.path);path.addAll(segment);double cost=c.distance+segment.stream().mapToDouble(RailwayNetworkData.Travel::length).sum();
                if(best==null||cost<best.distance)queue.add(new Candidate(new Point(link.dimension,boundary(link.other,link.otherFace)),cost+.001,path));
            }
        }return best==null?List.of():best.path;
    }
    @Override public void readFromNBT(NBTTagCompound root) {
        links.clear();var list=root.getTagList("Links",10);for(int i=0;i<Math.min(100000,list.tagCount());i++) {
            var t=list.getCompoundTagAt(i);BlockPos pos=BlockPos.fromLong(t.getLong("Pos"));
            links.put(pos,new Link(pos,EnumFacing.byIndex(t.getInteger("Face")),t.getInteger("Dimension"),BlockPos.fromLong(t.getLong("Other")),EnumFacing.byIndex(t.getInteger("OtherFace"))));
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound root) {
        NBTTagList list=new NBTTagList();for(var link:links.values()){var t=new NBTTagCompound();t.setLong("Pos",link.track.toLong());t.setInteger("Face",link.face.getIndex());t.setInteger("Dimension",link.dimension);t.setLong("Other",link.other.toLong());t.setInteger("OtherFace",link.otherFace.getIndex());list.appendTag(t);}root.setTag("Links",list);return root;
    }
}
