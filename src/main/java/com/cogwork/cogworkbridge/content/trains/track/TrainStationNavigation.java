package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A bounded look-ahead along the driver's selected route, never a dimension/chunk load. */
final class TrainStationNavigation {
    record Approach(RailwayNetworkData.Target station,RailwayNetworkData.Node arrival,int direction,
                    double distance,List<RailwayNetworkData.Travel> route) { }
    private TrainStationNavigation() { }

    @Nullable static RailwayTargetTile.Station parkedAt(World world,RailwayCarriageEntity train) {
        for(var target:RailwayNetworkData.get(world).targets()) {
            if(target.kind().equals("station")&&world.isBlockLoaded(target.owner())
                    &&world.getTileEntity(target.owner()) instanceof RailwayTargetTile.Station station
                    &&!station.assembling()&&station.stoppedTrain()==train)return station;
        }
        return null;
    }

    @Nullable static Approach find(World world,RailwayNetworkData graph,TrackCursor leading,int direction,
                                   int steering,double speed,UUID train) {
        RailwayNetworkData.Travel edge=leading.edge(graph);
        if(edge==null||edge.dimension()!=world.provider.getDimension())return null;
        double stopping=speed*speed/(2D*.0075D),minimum=.75D*stopping,maximum=Math.max(32D,1.5D*stopping);
        Map<RailwayNetworkData.Node,List<RailwayNetworkData.Target>> stations=new HashMap<>();
        for(var station:graph.targets()) {
            if(!station.kind().equals("station")||!world.isBlockLoaded(station.owner())
                    ||!(world.getTileEntity(station.owner()) instanceof RailwayTargetTile.Station tile)
                    ||tile.assembling()||!tile.hasValidTarget())continue;
            RailwayNetworkData.Node arrival=graph.stationArrival(station);
            if(arrival!=null)stations.computeIfAbsent(arrival,key->new ArrayList<>()).add(station);
        }
        if(stations.isEmpty())return null;
        double distance=edge.length()*(direction>0?1D-leading.progress():leading.progress());
        if(direction<0)edge=reverse(edge);
        List<RailwayNetworkData.Travel> route=new ArrayList<>();
        for(int steps=0;steps<1024&&distance<=maximum;steps++) {
            if(!TrackCursor.usable(world,edge))return null;
            for(var station:stations.getOrDefault(edge.to(),List.of())) {
                if(distance<minimum||!graph.stationApproach(station,edge))continue;
                var tile=(RailwayTargetTile.Station)world.getTileEntity(station.owner());
                RailwayCarriageEntity present=tile.stoppedTrain();
                if(present==null||present.trainId().equals(train))
                    return new Approach(station,edge.to(),direction,distance,new ArrayList<>(route));
            }
            Vec3d tangent=edge.tangent(1D).normalize();
            var next=TrackCursor.choose(world,graph,edge.to(),edge.from(),edge.track(),tangent,steering);
            if(next==null||next.dimension()!=graph.dimension())return null;
            route.add(next);distance+=next.length();edge=next;
        }
        return null;
    }

    static RailwayNetworkData.Travel reverse(RailwayNetworkData.Travel edge) {
        return new RailwayNetworkData.Travel(edge.to(),edge.from(),edge.length(),edge.track(),edge.curve(),
                !edge.reversed(),edge.dimension());
    }
}
