package com.cogwork.cogworkbridge.content.contraptions.bearing;

import com.cogwork.cogworkbridge.content.contraptions.glue.Direction;
import com.cogwork.cogworkbridge.content.contraptions.glue.GridPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Pure face connectivity traversal shared by capture and live removal. */
public final class ContraptionConnectivity {

    private ContraptionConnectivity() {
    }

    /**
     * Visits only occupied nodes reachable from {@code root} through accepted
     * face edges. Terminal nodes are included but are not expanded.
     */
    public static Set<GridPos> connectedFrom(
            GridPos root,
            Predicate<GridPos> present,
            BiPredicate<GridPos, GridPos> connected,
            Predicate<GridPos> terminal,
            int limit) {
        return connectedFrom(Set.of(root), present, connected, terminal, limit);
    }

    public static Set<GridPos> connectedFrom(
            Set<GridPos> roots,
            Predicate<GridPos> present,
            BiPredicate<GridPos, GridPos> connected,
            Predicate<GridPos> terminal,
            int limit) {
        if (limit <= 0 || roots == null || roots.isEmpty()) {
            return Set.of();
        }

        Deque<GridPos> frontier = new ArrayDeque<>();
        Set<GridPos> visited = new LinkedHashSet<>();
        for (GridPos root : roots) {
            if (root != null && present.test(root)) {
                frontier.addLast(root);
            }
        }
        while (!frontier.isEmpty() && visited.size() < limit) {
            GridPos current = frontier.removeFirst();
            if (!visited.add(current) || terminal.test(current)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                if (visited.size() >= limit) {
                    break;
                }
                GridPos next = current.offset(direction);
                if (visited.contains(next) || !present.test(next)
                        || !connected.test(current, next)) {
                    continue;
                }
                frontier.addLast(next);
            }
        }
        return visited;
    }
}
