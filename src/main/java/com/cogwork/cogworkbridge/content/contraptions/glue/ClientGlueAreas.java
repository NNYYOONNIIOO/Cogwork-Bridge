package com.cogwork.cogworkbridge.content.contraptions.glue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client-side mirrors used only for outlines and hit testing. */
public final class ClientGlueAreas {

    private static final Map<Integer, Area> AREAS = new LinkedHashMap<>();
    private static final Deque<GlueBox> CREATED = new ArrayDeque<>();
    private static WorldClient boundWorld;

    private ClientGlueAreas() {
    }

    public static void clear() {
        AREAS.clear();
        CREATED.clear();
        boundWorld = null;
    }

    public static void put(int entityId, GlueBox box) {
        put(entityId, box, box.positions());
    }

    public static void put(int entityId, GlueBox box, Set<GridPos> cells) {
        put(entityId, box, cells, false);
    }

    public static void put(int entityId, GlueBox box, Set<GridPos> cells,
                           boolean created) {
        WorldClient world = Minecraft.getMinecraft().world;
        if (world != boundWorld) {
            AREAS.clear();
            CREATED.clear();
            boundWorld = world;
        }
        boolean fresh = created && !AREAS.containsKey(entityId);
        AREAS.put(entityId, new Area(box, Set.copyOf(cells)));
        if (fresh && world != null) {
            CREATED.addLast(box);
        }
    }

    public static void remove(int entityId) {
        AREAS.remove(entityId);
    }

    public static GlueBox box(int entityId) {
        Area area = AREAS.get(entityId);
        return area == null ? null : area.box;
    }

    public static GlueBox pollCreated() {
        return CREATED.pollFirst();
    }

    public static List<Map.Entry<Integer, GlueBox>> snapshot() {
        List<Map.Entry<Integer, GlueBox>> result = new ArrayList<>();
        for (Map.Entry<Integer, Area> entry : AREAS.entrySet()) {
            result.add(Map.entry(entry.getKey(), entry.getValue().box));
        }
        return result;
    }

    public static Integer findByRay(net.minecraft.util.math.Vec3d start,
                                    net.minecraft.util.math.Vec3d end) {
        Integer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Integer, Area> entry : AREAS.entrySet()) {
            net.minecraft.util.math.RayTraceResult hit = SuperGlueEntity.toAabb(entry.getValue().box)
                    .calculateIntercept(start, end);
            if (hit == null) {
                continue;
            }
            double distance = hit.hitVec.squareDistanceTo(start);
            if (distance < bestDistance) {
                best = entry.getKey();
                bestDistance = distance;
            }
        }
        return best;
    }

    public static Set<GridPos> volume(int entityId) {
        Area area = AREAS.get(entityId);
        return area == null ? Set.of() : area.cells;
    }

    private record Area(GlueBox box, Set<GridPos> cells) {
    }
}
