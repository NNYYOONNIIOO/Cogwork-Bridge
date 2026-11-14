package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import javax.annotation.Nullable;
import java.util.*;

/** Dimension-local graph of track endpoints, crossing axes and curved connections. */
public final class RailwayNetworkData extends WorldSavedData {
    private static final String NAME="cogworkbridge_railway_graph";
    private static final int MAX_TRACKS=1000000;
    private final Map<BlockPos,TrackRecord> tracks=new HashMap<>();
    private final Map<BlockPos,Target> targets=new HashMap<>();
    private final Map<Node,List<Travel>> graph=new HashMap<>();
    private final Map<SectionKey,Section> sections=new HashMap<>();
    private final Map<Node,List<Target>> signalBoundaries=new HashMap<>();
    private boolean signalIndexDirty=true;
    private final Map<Long,List<Travel>> spatialEdges=new HashMap<>();
    private final Map<UUID,Set<EdgeKey>> occupied=new HashMap<>();
    private final Map<UUID,UUID> occupationTrain=new HashMap<>();
    private boolean rebuild=true;
    private transient World contextWorld;
    public World world() { return contextWorld; }
    public int dimension() { return contextWorld==null?0:contextWorld.provider.getDimension(); }
    public RailwayNetworkData forDimension(int dimension) {
        if(dimension==dimension())return this;
        var server=contextWorld==null?null:contextWorld.getMinecraftServer();var level=server==null?null:server.getWorld(dimension);
        return level==null?null:get(level);
    }
    public RailwayNetworkData(){this(NAME);}
    public RailwayNetworkData(String name){super(name);}
    public static RailwayNetworkData get(World world){
        MapStorage storage=world.getPerWorldStorage();
        RailwayNetworkData data=(RailwayNetworkData)storage.getOrLoadData(RailwayNetworkData.class,NAME);
        if(data==null){data=new RailwayNetworkData();storage.setData(NAME,data);}
        data.contextWorld=world;
        return data;
    }
    private record TrackRecord(TrackShape shape,List<TrackCurve> curves) { }
    /** Devices remain indexed even while their chunk is unloaded. */
    public record Target(BlockPos owner,BlockPos track,String kind,boolean front,String name) { }
    /** Coordinates are in half-block units; this makes adjoining rail endpoints exact. */
    public record Node(int x,int y,int z) {
        public Vec3d position(){return new Vec3d(x*.5D,y*.5D,z*.5D);}
        public static Node at(Vec3d v){
            return new Node((int)Math.round(v.x*2D),(int)Math.round(v.y*2D),(int)Math.round(v.z*2D));
        }
    }
    public record Travel(Node from,Node to,double length,BlockPos track,@Nullable TrackCurve curve,boolean reversed,int dimension) {
        public Vec3d point(double t) {
            double clamped=Math.max(0D,Math.min(1D,t));
            if(curve!=null)return curve.point(curve.parameterAtFraction(reversed?1D-clamped:clamped));
            Vec3d a=from.position(),b=to.position();return a.add(b.subtract(a).scale(clamped));
        }
        public Vec3d tangent(double t) {
            if(curve!=null)return curve.tangent(curve.parameterAtFraction(reversed?1D-t:t)).scale(reversed?-1:1);
            return to.position().subtract(from.position());
        }
    }
    public void update(World world,BlockPos pos) {
        if(world.isRemote)return;
        IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof TrackBlock)){remove(pos);return;}
        List<TrackCurve> curves=world.getTileEntity(pos) instanceof TrackTileEntity te
                ?new ArrayList<>(te.connections()):Collections.emptyList();
        tracks.put(pos.toImmutable(),new TrackRecord(state.getValue(TrackBlock.SHAPE),curves));
        rebuild=true;sections.clear();signalIndexDirty=true;markDirty();
    }
    public void remove(BlockPos pos) {
        if(tracks.remove(pos)!=null){rebuild=true;sections.clear();signalIndexDirty=true;markDirty();}
    }
    public boolean hasTrack(BlockPos pos){return tracks.containsKey(pos);}
    @Nullable public TrackCurve findCurve(BlockPos a,BlockPos b) {
        TrackRecord record=tracks.get(a);
        if(record!=null)for(TrackCurve curve:record.curves)if(curve.other(a).equals(b))return curve;
        record=tracks.get(b);
        if(record!=null)for(TrackCurve curve:record.curves)if(curve.other(b).equals(a))return curve;
        return null;
    }
    public void forgetCurve(TrackCurve curve) {
        boolean changed=false;
        for(BlockPos endpoint:new BlockPos[]{curve.first,curve.second}) {
            TrackRecord record=tracks.get(endpoint);if(record==null)continue;
            List<TrackCurve> remaining=new ArrayList<>(record.curves);
            if(remaining.removeIf(c->c.first.equals(curve.first)&&c.second.equals(curve.second))) {
                tracks.put(endpoint,new TrackRecord(record.shape,remaining));changed=true;
            }
        }
        if(changed){rebuild=true;sections.clear();signalIndexDirty=true;markDirty();}
    }
    public void setTarget(BlockPos owner,BlockPos track,String kind,boolean front,String name) {
        Target next=new Target(owner.toImmutable(),track.toImmutable(),kind,front,name==null?"":name);
        Target old=targets.put(next.owner,next);
        if(!next.equals(old)) {
            if("signal".equals(next.kind)||old!=null&&"signal".equals(old.kind)){sections.clear();signalIndexDirty=true;}
            markDirty();
        }
    }
    public void removeTarget(BlockPos owner) {
        Target old=targets.remove(owner);
        if(old!=null){if("signal".equals(old.kind)){sections.clear();signalIndexDirty=true;}markDirty();}
    }
    public Collection<Target> targets() { return Collections.unmodifiableCollection(targets.values()); }
    public boolean canTarget(BlockPos track,String kind,boolean front) {
        TrackRecord record=tracks.get(track);
        if(record==null||record.shape.junction())return false;
        for(Target target:targets.values()) {
            if(!target.track.equals(track))continue;
            if(!"signal".equals(kind)||!"signal".equals(target.kind)||target.front==front)return false;
        }
        return true;
    }
    public List<Travel> edges(Node node){ensureGraph();return Collections.unmodifiableList(graph.getOrDefault(node,Collections.emptyList()));}
    public Set<Node> nodes(){ensureGraph();return Collections.unmodifiableSet(graph.keySet());}
    /** A crossing shares a location, but its two axes are not a switch. */
    public boolean permitsTransition(BlockPos incoming,Node at,Vec3d tangent,Travel next) {
        TrackRecord record=tracks.get(incoming);
        if(record==null||!record.shape.junction()||!next.track.equals(incoming))return true;
        Node center=Node.at(new Vec3d(incoming.getX()+.5D,incoming.getY(),incoming.getZ()+.5D));
        return !center.equals(at)||tangent.normalize().dotProduct(next.tangent(0D).normalize())>.99D;
    }
    private record EdgeKey(Node first,Node second,BlockPos owner) {
        static EdgeKey of(Travel edge) {
            Node a=edge.from,b=edge.to;
            int order=Comparator.comparingInt(Node::x).thenComparingInt(Node::y).thenComparingInt(Node::z).compare(a,b);
            return order<=0?new EdgeKey(a,b,edge.track):new EdgeKey(b,a,edge.track);
        }
    }
    private record SectionKey(BlockPos track,boolean front) { }
    private record Section(Set<EdgeKey> edges,Set<BlockPos> exits,boolean valid) { }
    private void ensureSignalIndex() {
        if(!signalIndexDirty)return;
        signalBoundaries.clear();
        for(Target target:targets.values())if(target.kind.equals("signal")) {
            TrackRecord record=tracks.get(target.track);if(record==null)continue;
            Node node=Node.at(new Vec3d(target.track.getX()+.5D,
                    target.track.getY()+(record.shape.slope()?.5D:0D),target.track.getZ()+.5D));
            signalBoundaries.computeIfAbsent(node,k->new ArrayList<>()).add(target);
        }
        signalIndexDirty=false;
    }

    /** Cache topology until rails or signal boundaries change; occupancy is read live. */
    private Section section(BlockPos track,boolean front) {
        SectionKey key=new SectionKey(track,front);
        Section cached=sections.get(key);
        if(cached!=null)return cached;
        ensureGraph();TrackRecord record=tracks.get(track);
        if(record==null||record.shape.junction())return new Section(Collections.emptySet(),Collections.emptySet(),false);
        Vec3d center=new Vec3d(track.getX()+.5D,track.getY()+(record.shape.slope()?.5D:0D),track.getZ()+.5D);
        Node start=Node.at(center);Vec3d wanted=record.shape.axis().normalize().scale(front?1D:-1D);
        Travel first=null;
        for(Travel candidate:graph.getOrDefault(start,Collections.emptyList()))
            if(candidate.track.equals(track)&&candidate.tangent(0D).normalize().dotProduct(wanted)>.99D){first=candidate;break;}
        if(first==null)return new Section(Collections.emptySet(),Collections.emptySet(),false);
        ensureSignalIndex();
        Set<Travel> visited=new HashSet<>();Set<EdgeKey> edges=new HashSet<>();Set<BlockPos> exits=new HashSet<>();
        ArrayDeque<Travel> queue=new ArrayDeque<>();queue.add(first);visited.add(first);
        boolean valid=true;
        while(!queue.isEmpty()) {
            Travel incoming=queue.removeFirst();Node node=incoming.to;edges.add(EdgeKey.of(incoming));
            if(visited.size()>65536){valid=false;break;}
            boolean stop=false;
            for(Target boundary:signalBoundaries.getOrDefault(node,Collections.emptyList())) {
                TrackRecord boundaryTrack=tracks.get(boundary.track);
                Vec3d facing=boundaryTrack.shape.axis().normalize().scale(boundary.front?1D:-1D);
                if(incoming.tangent(1D).normalize().dotProduct(facing)>.7D) {
                    exits.add(boundary.owner);stop=true;
                }
            }
            if(stop)continue;
            for(Travel edge:graph.getOrDefault(node,Collections.emptyList())) {
                if(edge.to.equals(incoming.from)&&edge.track.equals(incoming.track))continue;
                TrackRecord crossing=tracks.get(incoming.track);
                if(crossing!=null&&crossing.shape.junction()&&edge.track.equals(incoming.track)
                        &&incoming.tangent(1D).normalize().dotProduct(edge.tangent(0D).normalize())<.99D)continue;
                if(visited.add(edge))queue.addLast(edge);
            }
        }
        Section result=new Section(edges,exits,valid);sections.put(key,result);return result;
    }
    public boolean sectionOccupied(World world,BlockPos track,boolean front) {
        return sectionOccupied(world,track,front,null);
    }
    private boolean sectionOccupied(World world,BlockPos track,boolean front,@Nullable UUID except) {
        Section section=section(track,front);
        if(!section.valid)return true;
        for(Map.Entry<UUID,Set<EdgeKey>> carriage:occupied.entrySet()) {
            if(carriage.getKey().equals(except) || occupationTrain.getOrDefault(carriage.getKey(),carriage.getKey()).equals(except))continue;
            for(EdgeKey edge:carriage.getValue())if(section.edges.contains(edge))return true;
        }
        return false;
    }
    /** Keep the occupied edges when the carriage's chunk is unloaded. */
    public void updateOccupation(RailwayCarriageEntity carriage) {
        ensureGraph();
        Travel current=carriage.currentEdge(this);
        if(current==null)return;
        Set<EdgeKey> edges=new HashSet<>();if(current.dimension()==dimension())edges.add(EdgeKey.of(current));
        for(Travel edge:carriage.occupiedTrainPath(this))if(edge.dimension()==dimension())edges.add(EdgeKey.of(edge));
        com.cogwork.cogworkbridge.content.contraptions.bearing.BearingContraptionEntity body=null;
        for(var copy:carriage.dimensionalCopies())if(copy.world.provider.getDimension()==dimension()){body=copy.body();break;}
        if(body!=null&&body.dimension==dimension()) {
            AxisAlignedBB box=body.getEntityBoundingBox().grow(.15D,1.25D,.15D);
            int minX=(int)Math.floor(box.minX/16D),maxX=(int)Math.floor(box.maxX/16D);
            int minZ=(int)Math.floor(box.minZ/16D),maxZ=(int)Math.floor(box.maxZ/16D);
            Set<EdgeKey> examined=new HashSet<>();
            if(maxX-minX<=128&&maxZ-minZ<=128)
                for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)
                    for(Travel edge:spatialEdges.getOrDefault(ChunkPos.asLong(x,z),Collections.emptyList())) {
                        EdgeKey edgeKey=EdgeKey.of(edge);
                        if(examined.add(edgeKey)&&intersects(edge,box))edges.add(edgeKey);
                    }
        } else if(occupied.containsKey(carriage.getUniqueID()))return;
        UUID id=carriage.getUniqueID();
        boolean changed=!edges.equals(occupied.put(id,edges));
        if(!carriage.trainId().equals(occupationTrain.put(id,carriage.trainId())))changed=true;
        if(changed)markDirty();
    }
    public void removeOccupation(UUID carriage) {
        occupationTrain.remove(carriage);
        if(occupied.remove(carriage)!=null)markDirty();
    }
    public boolean carriageOccupies(UUID carriage,BlockPos track) {
        for(EdgeKey edge:occupied.getOrDefault(carriage,Collections.emptySet()))
            if(edge.owner.equals(track))return true;
        return false;
    }
    private static boolean intersects(Travel edge,AxisAlignedBB box) {
        int steps=edge.curve==null?1:Math.max(4,(int)Math.ceil(edge.length*2D));
        Vec3d previous=edge.point(0D);
        for(int i=1;i<=steps;i++) {
            Vec3d next=edge.point(i/(double)steps);
            if(box.contains(previous)||box.contains(next)||box.calculateIntercept(previous,next)!=null)return true;
            previous=next;
        }
        return false;
    }
    /** A cross signal allows entry only when its section and at least one exit are clear. */
    public boolean signalBlocked(World world,BlockPos track,boolean front,boolean cross) {
        return signalBlocked(world,track,front,cross,null);
    }
    private boolean signalBlocked(World world,BlockPos track,boolean front,boolean cross,@Nullable UUID except) {
        Section section=section(track,front);
        if(!section.valid||sectionOccupied(world,track,front,except))return true;
        if(!cross||section.exits.isEmpty())return false;
        for(BlockPos exit:section.exits) {
            if(!world.isBlockLoaded(exit))continue;
            if(world.getTileEntity(exit) instanceof RailwayTargetTile.Signal signal&&signal.isGreen())return false;
        }
        return true;
    }
    /** Signals apply only to traffic approaching their selected side. */
    public boolean redSignalAt(World world,Node node,Vec3d travelDirection,@Nullable UUID train) {
        ensureSignalIndex();
        for(Target target:signalBoundaries.getOrDefault(node,Collections.emptyList())) {
            TrackRecord record=tracks.get(target.track);if(record==null)continue;
            Vec3d facing=record.shape.axis().normalize().scale(target.front?1D:-1D);
            if(travelDirection.dotProduct(facing)<.7D)continue;
            if(!world.isBlockLoaded(target.owner))return true;
            if(!(world.getTileEntity(target.owner) instanceof RailwayTargetTile.Signal signal)
                    ||world.isBlockPowered(target.owner)
                    ||signalBlocked(world,target.track,target.front,signal.crossSignal(),train))return true;
        }
        return false;
    }
    @Nullable public Node stationArrival(Target station) {
        if(!"station".equals(station.kind))return null;
        TrackRecord record=tracks.get(station.track);
        if(record==null||record.shape!=TrackShape.XO&&record.shape!=TrackShape.ZO)return null;
        int sign=station.front?1:-1;
        BlockPos arrival=station.track.add(record.shape.x*sign,0,record.shape.z*sign);
        TrackRecord rail=tracks.get(arrival);
        if(rail==null||rail.shape!=record.shape)return null;
        return Node.at(new Vec3d(arrival.getX()+.5D,arrival.getY(),arrival.getZ()+.5D));
    }

    private void ensureGraph(){
        if(!rebuild)return;
        graph.clear();spatialEdges.clear();
        for(Map.Entry<BlockPos,TrackRecord> entry:tracks.entrySet()) {
            BlockPos pos=entry.getKey(); TrackRecord record=entry.getValue();
            for(Vec3d axis:axes(record.shape)) {
                Vec3d center=new Vec3d(pos.getX()+.5,pos.getY()+(record.shape.slope()?.5:0),pos.getZ()+.5);
                Node a=Node.at(center.subtract(axis.scale(.5))),b=Node.at(center.add(axis.scale(.5)));
                // Every centre is a graph node so a signal or observer can occupy it.
                Node middle=Node.at(center);
                add(a,middle,a.position().distanceTo(middle.position()),pos,null);
                add(middle,b,middle.position().distanceTo(b.position()),pos,null);
            }
            for(TrackCurve curve:record.curves) {
                if(!curve.primary(pos) || !tracks.containsKey(curve.second))continue;
                Node a=Node.at(curve.start),b=Node.at(curve.finish);
                if(!a.equals(b))add(a,b,curve.length(),pos,curve);
            }
        }
        rebuild=false;
    }
    private void add(Node a,Node b,double length,BlockPos pos,@Nullable TrackCurve curve){
        Travel forward=new Travel(a,b,length,pos,curve,false,dimension());
        graph.computeIfAbsent(a,k->new ArrayList<>()).add(forward);
        graph.computeIfAbsent(b,k->new ArrayList<>()).add(new Travel(b,a,length,pos,curve,true,dimension()));
        Vec3d p=a.position(),q=b.position();
        double minX=Math.min(p.x,q.x),maxX=Math.max(p.x,q.x),minZ=Math.min(p.z,q.z),maxZ=Math.max(p.z,q.z);
        if(curve!=null)for(Vec3d control:new Vec3d[]{curve.control1,curve.control2}) {
            minX=Math.min(minX,control.x);maxX=Math.max(maxX,control.x);
            minZ=Math.min(minZ,control.z);maxZ=Math.max(maxZ,control.z);
        }
        for(int x=(int)Math.floor(minX/16D);x<=(int)Math.floor(maxX/16D);x++)
            for(int z=(int)Math.floor(minZ/16D);z<=(int)Math.floor(maxZ/16D);z++)
                spatialEdges.computeIfAbsent(ChunkPos.asLong(x,z),k->new ArrayList<>()).add(forward);
    }
    private static List<Vec3d> axes(TrackShape shape) {
        Vec3d second=switch(shape){
            case CR_O -> new Vec3d(1,0,0);case CR_D -> new Vec3d(-1,0,1);
            case CR_PDX,CR_PDZ -> new Vec3d(1,0,1);
            case CR_NDX,CR_NDZ -> new Vec3d(-1,0,1);
            default -> null;
        };
        return second==null?Collections.singletonList(shape.axis()):Arrays.asList(shape.axis(),second);
    }
    /** Shortest route across straight segments, junctions and saved curves. */
    public List<Travel> route(Node source,Node destination) {
        ensureGraph();
        if(source.equals(destination))return Collections.emptyList();
        record State(Node node,Travel incoming) { }
        record Previous(State state,Travel edge) { }
        Map<State,Double> distance=new HashMap<>();
        Map<State,Previous> previous=new HashMap<>();
        record Candidate(State state,double distance){}
        PriorityQueue<Candidate> queue=new PriorityQueue<>(Comparator.comparingDouble(Candidate::distance));
        State origin=new State(source,null),goal=null;
        distance.put(origin,0D);queue.add(new Candidate(origin,0D));
        int visited=0;
        while(!queue.isEmpty()&&visited++<MAX_TRACKS*8) {
            Candidate current=queue.poll();
            State state=current.state;
            if(current.distance>distance.getOrDefault(state,Double.POSITIVE_INFINITY))continue;
            if(state.node.equals(destination)){goal=state;break;}
            for(Travel edge:graph.getOrDefault(state.node,Collections.emptyList())) {
                if(state.incoming!=null) {
                    if(edge.to.equals(state.incoming.from)&&edge.track.equals(state.incoming.track))continue;
                    if(!permitsTransition(state.incoming.track,state.node,state.incoming.tangent(1D),edge))continue;
                }
                double next=current.distance+edge.length;
                State nextState=new State(edge.to,edge);
                if(next>=distance.getOrDefault(nextState,Double.POSITIVE_INFINITY))continue;
                distance.put(nextState,next);previous.put(nextState,new Previous(state,edge));queue.add(new Candidate(nextState,next));
            }
        }
        if(goal==null)return Collections.emptyList();
        List<Travel> result=new ArrayList<>();State cursor=goal;
        while(!cursor.equals(origin)){
            Previous step=previous.get(cursor);if(step==null)return Collections.emptyList();
            result.add(step.edge);cursor=step.state;
        }
        Collections.reverse(result);return result;
    }
    @Override public void readFromNBT(NBTTagCompound root) {
        tracks.clear();targets.clear();sections.clear();signalBoundaries.clear();signalIndexDirty=true;occupied.clear();occupationTrain.clear();NBTTagList list=root.getTagList("Tracks",10);
        for(int i=0;i<list.tagCount()&&i<MAX_TRACKS;i++){
            NBTTagCompound tag=list.getCompoundTagAt(i);BlockPos pos=BlockPos.fromLong(tag.getLong("Pos"));
            TrackShape shape=TrackShape.byMeta(tag.getInteger("Shape"));NBTTagList links=tag.getTagList("Curves",10);
            List<TrackCurve> curves=new ArrayList<>();
            for(int j=0;j<links.tagCount()&&j<64;j++){
                TrackCurve curve=TrackCurve.read(links.getCompoundTagAt(j));
                if(curve.first.equals(pos)||curve.second.equals(pos))curves.add(curve);
            }
            tracks.put(pos,new TrackRecord(shape,curves));
        }
        NBTTagList points=root.getTagList("Targets",10);
        for(int i=0;i<points.tagCount()&&i<MAX_TRACKS;i++) {
            NBTTagCompound tag=points.getCompoundTagAt(i);
            String kind=tag.getString("Kind");
            if(!kind.equals("station")&&!kind.equals("signal")&&!kind.equals("observer"))continue;
            BlockPos owner=BlockPos.fromLong(tag.getLong("Owner"));
            targets.put(owner,new Target(owner,BlockPos.fromLong(tag.getLong("Track")),kind,
                    tag.getBoolean("Front"),tag.getString("Name")));
        }
        NBTTagList trains=root.getTagList("Occupation",10);
        for(int i=0;i<trains.tagCount()&&i<MAX_TRACKS;i++) {
            NBTTagCompound tag=trains.getCompoundTagAt(i);
            if(!tag.hasUniqueId("Train"))continue;
            Set<EdgeKey> edges=new HashSet<>();NBTTagList saved=tag.getTagList("Edges",10);
            for(int j=0;j<saved.tagCount()&&j<4096;j++) {
                NBTTagCompound edge=saved.getCompoundTagAt(j);
                Node a=new Node(edge.getInteger("AX"),edge.getInteger("AY"),edge.getInteger("AZ"));
                Node b=new Node(edge.getInteger("BX"),edge.getInteger("BY"),edge.getInteger("BZ"));
                edges.add(new EdgeKey(a,b,BlockPos.fromLong(edge.getLong("Owner"))));
            }
            if(!edges.isEmpty()) {
                UUID id=tag.getUniqueId("Train");occupied.put(id,edges);
                occupationTrain.put(id,tag.hasUniqueId("TrainGroup")?tag.getUniqueId("TrainGroup"):id);
            }
        }
        rebuild=true;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound root) {
        NBTTagList list=new NBTTagList();
        for(Map.Entry<BlockPos,TrackRecord> entry:tracks.entrySet()){
            NBTTagCompound tag=new NBTTagCompound();tag.setLong("Pos",entry.getKey().toLong());
            tag.setInteger("Shape",entry.getValue().shape.ordinal());NBTTagList curves=new NBTTagList();
            for(TrackCurve curve:entry.getValue().curves)curves.appendTag(curve.write());
            tag.setTag("Curves",curves);list.appendTag(tag);
        }
        root.setTag("Tracks",list);
        NBTTagList points=new NBTTagList();
        for(Target target:targets.values()) {
            NBTTagCompound tag=new NBTTagCompound();tag.setLong("Owner",target.owner.toLong());
            tag.setLong("Track",target.track.toLong());tag.setString("Kind",target.kind);
            tag.setBoolean("Front",target.front);tag.setString("Name",target.name);points.appendTag(tag);
        }
        root.setTag("Targets",points);
        NBTTagList trains=new NBTTagList();
        for(Map.Entry<UUID,Set<EdgeKey>> entry:occupied.entrySet()) {
            NBTTagCompound tag=new NBTTagCompound();tag.setUniqueId("Train",entry.getKey());NBTTagList saved=new NBTTagList();
            tag.setUniqueId("TrainGroup",occupationTrain.getOrDefault(entry.getKey(),entry.getKey()));
            int count=0;
            for(EdgeKey edge:entry.getValue()) {
                if(count++>=4096)break;
                NBTTagCompound item=new NBTTagCompound();
                item.setInteger("AX",edge.first.x);item.setInteger("AY",edge.first.y);item.setInteger("AZ",edge.first.z);
                item.setInteger("BX",edge.second.x);item.setInteger("BY",edge.second.y);item.setInteger("BZ",edge.second.z);
                item.setLong("Owner",edge.owner.toLong());saved.appendTag(item);
            }
            tag.setTag("Edges",saved);trains.appendTag(tag);
        }
        root.setTag("Occupation",trains);return root;
    }
}
