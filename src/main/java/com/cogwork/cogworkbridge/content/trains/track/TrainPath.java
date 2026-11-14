package com.cogwork.cogworkbridge.content.trains.track;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The ordered stretch of track between the first and last bogeys of a train. */
final class TrainPath {
    private static final double EPSILON = 1E-8D;
    private static final int MAX_EDGES = 4096;
    private final List<TrackCursor> edges = new ArrayList<>();
    private double reference;
    private double span;

    @Nullable static TrainPath create(World world, RailwayNetworkData graph, TrackCursor first, double span) {
        RailwayNetworkData.Travel edge = first.edge(graph);
        if (!TrackCursor.usable(world, edge) || !Double.isFinite(span) || span < 0D) return null;
        TrainPath path = new TrainPath();
        path.edges.add(TrackCursor.onEdge(edge, 0D));
        path.reference = first.progress() * edge.length();
        path.span = span;
        double required = path.reference + span;
        double length = edge.length();
        while (length + EPSILON < required && path.edges.size() < MAX_EDGES) {
            TrackCursor nextCursor = path.next(world, graph, true, 0, null, null, false);
            RailwayNetworkData.Travel next=nextCursor==null?null:nextCursor.edge(graph);
            if (next == null) return null;
            path.edges.add(TrackCursor.onEdge(next, 0D));
            length += next.length();
        }
        return length + EPSILON < required ? null : path;
    }

    TrainPath copy() {
        TrainPath result = new TrainPath();
        for (TrackCursor edge : edges) result.edges.add(edge.copy());
        result.reference = reference;
        result.span = span;
        return result;
    }

    double span() { return span; }

    List<RailwayNetworkData.Travel> occupiedEdges(RailwayNetworkData graph) {
        List<RailwayNetworkData.Travel> result = new ArrayList<>();
        for (TrackCursor cursor : edges) {
            RailwayNetworkData.Travel edge = cursor.edge(graph);
            if (edge != null) result.add(edge);
        }
        return result;
    }

    boolean usable(World world, RailwayNetworkData graph) {
        if (edges.isEmpty()) return false;
        RailwayNetworkData.Travel previous=null;
        for (TrackCursor cursor : edges) {
            RailwayNetworkData.Travel edge = cursor.edge(graph);
            if (!TrackCursor.usable(world, edge) || edge.length() <= EPSILON) return false;
            if(previous!=null) {
                if(previous.dimension()==edge.dimension()) {if(!previous.to().equals(edge.from()))return false;}
                else {
                    var level=graph.forDimension(previous.dimension());if(level==null)return false;
                    TrackCursor exit=RailwayPortals.next(level.world(),previous.track(),previous.to(),previous.tangent(1).normalize());
                    if(exit==null||exit.dimension(graph)!=edge.dimension()||!exit.from().equals(edge.from())||!exit.to().equals(edge.to()))return false;
                }
            }
            previous=edge;
        }
        return true;
    }

    @Nullable TrackCursor at(RailwayNetworkData graph, double offset) {
        double location = reference + offset;
        if (!Double.isFinite(location) || location < -EPSILON) return null;
        for (int i = 0; i < edges.size(); i++) {
            RailwayNetworkData.Travel edge = edges.get(i).edge(graph);
            if (edge == null || edge.length() <= EPSILON) return null;
            if (location < edge.length() - EPSILON || i == edges.size() - 1)
                return location <= edge.length() + EPSILON
                        ? TrackCursor.onEdge(edge, Math.max(0D, Math.min(1D, location / edge.length()))) : null;
            location -= edge.length();
        }
        return null;
    }

    /** Only the leading bogey chooses new edges; the rest follow this saved path. */
    double advance(World world, RailwayNetworkData graph, double distance, int steering,
                   @Nullable List<RailwayNetworkData.Travel> route, UUID train) {
        return advance(world, graph, distance, steering, route, train, true);
    }

    double advance(World world, RailwayNetworkData graph, double distance, int steering,
                   @Nullable List<RailwayNetworkData.Travel> route, UUID train, boolean checkSignal) {
        if (Math.abs(distance) <= EPSILON || !usable(world, graph)) return 0D;
        boolean forward = distance > 0D;
        double length = length(graph);
        if (length < 0D) return 0D;
        if (forward) {
            double leading = reference + span;
            double wanted = leading + distance;
            while (wanted > length + EPSILON && edges.size() < MAX_EDGES) {
                TrackCursor nextCursor=next(world, graph, true, steering, route, train, checkSignal);
                RailwayNetworkData.Travel next=nextCursor==null?null:nextCursor.edge(graph);
                if (next == null) break;
                edges.add(TrackCursor.onEdge(next, 0D));
                length += next.length();
            }
            double moved = Math.max(0D, Math.min(distance, length - leading));
            reference += moved;
            trim(graph);
            return moved;
        }
        double wanted = reference + distance;
        while (wanted < -EPSILON && edges.size() < MAX_EDGES) {
            TrackCursor nextCursor=next(world, graph, false, steering, route, train, checkSignal);
            RailwayNetworkData.Travel next=nextCursor==null?null:nextCursor.edge(graph);
            if (next == null) break;
            RailwayNetworkData.Travel reverse = reverse(graph, next);
            if (reverse == null) break;
            edges.add(0, TrackCursor.onEdge(reverse, 0D));
            reference += reverse.length();
            wanted += reverse.length();
        }
        double moved = Math.min(0D, Math.max(distance, -reference));
        reference += moved;
        trim(graph);
        return moved;
    }

    @Nullable private TrackCursor next(World world, RailwayNetworkData graph, boolean forward,
            int steering, @Nullable List<RailwayNetworkData.Travel> route, @Nullable UUID train, boolean checkSignal) {
        RailwayNetworkData.Travel current = edges.get(forward ? edges.size() - 1 : 0).edge(graph);
        if (current == null) return null;
        graph=graph.forDimension(current.dimension());if(graph==null||graph.world()==null)return null;world=graph.world();
        RailwayNetworkData.Node junction = forward ? current.to() : current.from();
        RailwayNetworkData.Node previous = forward ? current.from() : current.to();
        Vec3d tangent = current.tangent(forward ? 1D : 0D).normalize().scale(forward ? 1D : -1D);
        boolean passingRed = checkSignal && graph.redSignalAt(world, junction, tangent, train);
        net.minecraft.entity.player.EntityPlayerMP manualDriver = passingRed ? RailwayCarriageEntity.manualDriver(world, train) : null;
        if (passingRed && manualDriver == null) return null;
        if (route == null) {
            var local=TrackCursor.choose(world, graph, junction, previous, current.track(), tangent, steering);
            TrackCursor next = local != null ? TrackCursor.onEdge(local,0) : RailwayPortals.next(world,current.track(),junction,tangent);
            if (next != null && passingRed) com.cogwork.cogworkbridge.content.advancement.AllAdvancements.award(manualDriver, "red_signal");
            return next;
        }
        if (route.isEmpty()) return null;
        RailwayNetworkData.Travel expected = route.get(0);
        if(expected.dimension()!=current.dimension()) {
            TrackCursor portal=RailwayPortals.next(world,current.track(),junction,tangent);
            if(portal==null||portal.dimension(graph)!=expected.dimension()||!portal.from().equals(expected.from())||!portal.to().equals(expected.to()))return null;
            route.remove(0);return portal;
        }
        if (!expected.from().equals(junction)) return null;
        for (RailwayNetworkData.Travel candidate : graph.edges(junction)) {
            if (candidate.to().equals(expected.to()) && candidate.track().equals(expected.track())
                    && graph.permitsTransition(current.track(), junction, tangent, candidate)
                    && TrackCursor.usable(world, candidate)) {
                route.remove(0);
                return TrackCursor.onEdge(candidate,0);
            }
        }
        return null;
    }

    @Nullable private static RailwayNetworkData.Travel reverse(RailwayNetworkData graph, RailwayNetworkData.Travel edge) {
        graph=graph.forDimension(edge.dimension());if(graph==null)return null;
        for (RailwayNetworkData.Travel candidate : graph.edges(edge.to()))
            if (candidate.to().equals(edge.from()) && candidate.track().equals(edge.track())) return candidate;
        return null;
    }

    private double length(RailwayNetworkData graph) {
        double result = 0D;
        for (TrackCursor cursor : edges) {
            RailwayNetworkData.Travel edge = cursor.edge(graph);
            if (edge == null) return -1D;
            result += edge.length();
        }
        return result;
    }

    private void trim(RailwayNetworkData graph) {
        while (edges.size() > 1) {
            RailwayNetworkData.Travel first = edges.get(0).edge(graph);
            if (first == null || reference < first.length() - EPSILON) break;
            reference = Math.max(0D, reference - first.length());
            edges.remove(0);
        }
        double length = length(graph);
        while (edges.size() > 1 && length >= 0D) {
            RailwayNetworkData.Travel last = edges.get(edges.size() - 1).edge(graph);
            if (last == null || length - last.length() < reference + span - EPSILON) break;
            length -= last.length();
            edges.remove(edges.size() - 1);
        }
    }

    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("Reference", reference);
        tag.setDouble("Span", span);
        NBTTagList list = new NBTTagList();
        for (TrackCursor edge : edges) list.appendTag(edge.write());
        tag.setTag("Edges", list);
        return tag;
    }

    @Nullable static TrainPath read(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("Edges", 10);
        double reference = tag.getDouble("Reference"), span = tag.getDouble("Span");
        if (list.tagCount() == 0 || list.tagCount() > MAX_EDGES || !Double.isFinite(reference)
                || !Double.isFinite(span) || reference < 0D || span < 0D) return null;
        TrainPath path = new TrainPath();
        for (int i = 0; i < list.tagCount(); i++) {
            TrackCursor edge = TrackCursor.read(list.getCompoundTagAt(i));
            if (edge == null) return null;
            path.edges.add(edge);
        }
        path.reference = reference;
        path.span = span;
        return path;
    }
}
