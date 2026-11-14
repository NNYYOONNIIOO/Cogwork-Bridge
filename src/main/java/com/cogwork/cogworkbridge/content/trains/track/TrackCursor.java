package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import javax.annotation.Nullable;

/** Persistent position on a directed graph edge; progress is a fraction of arc length. */
public final class TrackCursor {
    private RailwayNetworkData.Node from,to;
    private BlockPos owner;
    private double progress;
    private int dimension=Integer.MIN_VALUE;
    public int dimension(RailwayNetworkData graph) { return dimension==Integer.MIN_VALUE?graph.dimension():dimension; }
    public World world(World fallback) {
        if(dimension==Integer.MIN_VALUE||dimension==fallback.provider.getDimension())return fallback;
        return fallback.getMinecraftServer()==null?null:fallback.getMinecraftServer().getWorld(dimension);
    }
    private TrackCursor(RailwayNetworkData.Node from,RailwayNetworkData.Node to,BlockPos owner,double progress) {
        this.from=from;this.to=to;this.owner=owner.toImmutable();this.progress=Math.max(0D,Math.min(1D,progress));
    }
    @Nullable public static TrackCursor atTrack(RailwayNetworkData graph,BlockPos pos,TrackShape shape,boolean front) {
        if(shape.junction())return null;
        Vec3d center=new Vec3d(pos.getX()+.5D,pos.getY()+(shape.slope()?.5D:0D),pos.getZ()+.5D);
        Vec3d axis=shape.axis();
        RailwayNetworkData.Node a=RailwayNetworkData.Node.at(center.subtract(axis.scale(.5D)));
        RailwayNetworkData.Node b=RailwayNetworkData.Node.at(center.add(axis.scale(.5D)));
        RailwayNetworkData.Node middle=RailwayNetworkData.Node.at(center);
        TrackCursor cursor=new TrackCursor(middle,front?b:a,pos,0D);
        cursor.dimension=graph.dimension();
        return cursor.edge(graph)==null?null:cursor;
    }
    public TrackCursor copy(){TrackCursor copy=new TrackCursor(from,to,owner,progress);copy.dimension=dimension;return copy;}
    static TrackCursor onEdge(RailwayNetworkData.Travel edge,double progress) {
        TrackCursor cursor=new TrackCursor(edge.from(),edge.to(),edge.track(),progress);cursor.dimension=edge.dimension();return cursor;
    }
    public RailwayNetworkData.Node from(){return from;}
    public RailwayNetworkData.Node to(){return to;}
    public double progress(){return progress;}
    @Nullable public RailwayNetworkData.Travel edge(RailwayNetworkData graph) {
        graph=graph.forDimension(dimension(graph));if(graph==null)return null;
        for(RailwayNetworkData.Travel edge:graph.edges(from))
            if(edge.to().equals(to)&&edge.track().equals(owner))return edge;
        return null;
    }
    @Nullable public Vec3d position(RailwayNetworkData graph){
        RailwayNetworkData.Travel edge=edge(graph);return edge==null?null:edge.point(progress);
    }
    @Nullable public Vec3d heading(RailwayNetworkData graph){
        RailwayNetworkData.Travel edge=edge(graph);return edge==null?null:edge.tangent(progress).normalize();
    }
    public BlockPos track(){return owner;}
    /** Returns signed distance actually travelled; stops before unloaded or missing edges. */
    public double advance(World world,RailwayNetworkData graph,double distance,int steering) {
        return advance(world,graph,distance,steering,null,null);
    }
    public double advance(World world,RailwayNetworkData graph,double distance,int steering,
            @Nullable java.util.List<RailwayNetworkData.Travel> route,@Nullable java.util.UUID train) {
        double left=Math.abs(distance),moved=0D;boolean forward=distance>=0D;
        for(int transitions=0;left>1E-8D && transitions<256;transitions++) {
            RailwayNetworkData.Travel current=edge(graph);
            if(!usable(world,current))break;
            graph=graph.forDimension(current.dimension());if(graph==null)break;world=graph.world();
            double available=current.length()*(forward?1D-progress:progress);
            double step=Math.min(left,Math.max(0D,available));
            if(current.length()<1E-8D)break;
            progress+=step/current.length()*(forward?1D:-1D);
            left-=step;moved+=step;
            if(left<=1E-8D)break;
            RailwayNetworkData.Node junction=forward?to:from;
            RailwayNetworkData.Node previous=forward?from:to;
            Vec3d tangent=current.tangent(forward?1D:0D).normalize().scale(forward?1D:-1D);
            if(graph.redSignalAt(world,junction,tangent,train))break;
            RailwayNetworkData.Travel next;
            if(route==null) {
                next=choose(world,graph,junction,previous,current.track(),tangent,steering);
                if(next==null){TrackCursor portal=RailwayPortals.next(world,current.track(),junction,tangent);if(portal!=null)next=portal.edge(graph);}
            }
            else {
                if(route.isEmpty())break;
                RailwayNetworkData.Travel expected=route.get(0);next=null;
                if(expected.dimension()!=current.dimension()) {
                    TrackCursor portal=RailwayPortals.next(world,current.track(),junction,tangent);
                    if(portal!=null&&portal.dimension(graph)==expected.dimension()&&portal.from().equals(expected.from())&&portal.to().equals(expected.to()))next=portal.edge(graph);
                } else if(expected.from().equals(junction))for(RailwayNetworkData.Travel candidate:graph.edges(junction))
                    if(candidate.to().equals(expected.to())&&candidate.track().equals(expected.track())
                            &&graph.permitsTransition(current.track(),junction,tangent,candidate)
                            &&usable(world,candidate)){next=candidate;break;}
                if(next!=null)route.remove(0);
            }
            if(next==null)break;
            dimension=next.dimension();
            if(forward){from=next.from();to=next.to();progress=0D;owner=next.track();}
            else {from=next.to();to=next.from();progress=1D;owner=next.track();}
        }
        return forward?moved:-moved;
    }
    @Nullable static RailwayNetworkData.Travel choose(World world,RailwayNetworkData graph,
            RailwayNetworkData.Node at,RailwayNetworkData.Node previous,BlockPos incoming,Vec3d tangent,int steering) {
        RailwayNetworkData.Travel best=null;double bestScore=-Double.MAX_VALUE;
        for(RailwayNetworkData.Travel candidate:graph.edges(at)) {
            if(candidate.to().equals(previous)||!usable(world,candidate)
                    ||!graph.permitsTransition(incoming,at,tangent,candidate))continue;
            Vec3d direction=candidate.tangent(0D).normalize();
            double dot=tangent.dotProduct(direction);
            if(dot<-.95D)continue;
            double turn=tangent.x*direction.z-tangent.z*direction.x;
            double score=dot+(steering==0?0D:Math.signum(steering)*turn*.85D);
            if(score>bestScore){bestScore=score;best=candidate;}
        }
        return best;
    }
    static boolean usable(World world,@Nullable RailwayNetworkData.Travel edge) {
        if(edge!=null&&edge.dimension()!=world.provider.getDimension())world=world.getMinecraftServer()==null?null:world.getMinecraftServer().getWorld(edge.dimension());
        if(world==null)return false;
        if(edge==null||!world.isBlockLoaded(edge.track())
                ||!(world.getBlockState(edge.track()).getBlock() instanceof TrackBlock))return false;
        return edge.curve()==null || world.isBlockLoaded(edge.curve().second)
                && world.getBlockState(edge.curve().second).getBlock() instanceof TrackBlock;
    }
    public NBTTagCompound write() {
        NBTTagCompound tag=new NBTTagCompound();writeNode(tag,"From",from);writeNode(tag,"To",to);
        tag.setLong("Owner",owner.toLong());tag.setDouble("Progress",progress);if(dimension!=Integer.MIN_VALUE)tag.setInteger("Dimension",dimension);return tag;
    }
    @Nullable public static TrackCursor read(NBTTagCompound tag) {
        if(!tag.hasKey("Owner")||!tag.hasKey("FromX")||!tag.hasKey("ToX"))return null;
        double progress=tag.getDouble("Progress");if(!Double.isFinite(progress))return null;
        TrackCursor cursor=new TrackCursor(readNode(tag,"From"),readNode(tag,"To"),BlockPos.fromLong(tag.getLong("Owner")),progress);
        cursor.dimension=tag.hasKey("Dimension")?tag.getInteger("Dimension"):Integer.MIN_VALUE;return cursor;
    }
    private static void writeNode(NBTTagCompound tag,String name,RailwayNetworkData.Node n){
        tag.setInteger(name+"X",n.x());tag.setInteger(name+"Y",n.y());tag.setInteger(name+"Z",n.z());
    }
    private static RailwayNetworkData.Node readNode(NBTTagCompound tag,String name){
        return new RailwayNetworkData.Node(tag.getInteger(name+"X"),tag.getInteger(name+"Y"),tag.getInteger(name+"Z"));
    }
}
