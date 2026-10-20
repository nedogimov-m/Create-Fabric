package com.simibubi.create.content.trains.signal;

import com.simibubi.create.content.trains.graph.*;
import java.util.*;

/** Actual graph partitions separated by real boundary points. Does not invent traffic occupancy. */
public final class SignalTopology {
    private record NodeKey(TrackNodeLocation location) {}
    private record SideKey(UUID point, boolean primary) {}
    private record PointShape(UUID point, double position) {}
    private record EdgeShape(TrackNodeLocation first, TrackNodeLocation second, List<PointShape> points) {}
    private record Shape(List<TrackNodeLocation> nodes, List<EdgeShape> edges) {}
    private static final Map<TrackGraph, Shape> CURRENT = new WeakHashMap<>();

    private static final class Union {
        private final Map<Object, Object> parent = new LinkedHashMap<>();
        Object root(Object key) {
            var previous = parent.putIfAbsent(key, key);
            if (previous == null || previous.equals(key)) return key;
            var root = root(previous); parent.put(key, root); return root;
        }
        void join(Object first, Object second) {
            var a = root(first); var b = root(second); if (!a.equals(b)) parent.put(b, a);
        }
    }
    private static List<TrackEdge> edges(TrackGraph graph) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<TrackEdge, Boolean>());
        var result = new ArrayList<TrackEdge>();
        for (var location : graph.getNodes()) {
            var node = graph.locateNode(location);
            for (var entry : graph.getConnectionsFrom(node).entrySet()) {
                if (!seen.add(entry.getValue())) continue;
                var reverse = graph.getConnectionsFrom(entry.getKey()).get(node);
                if (reverse == null) throw new IllegalStateException("Signal topology requires actual reciprocal railway edge");
                seen.add(reverse); result.add(entry.getValue());
            }
        }
        return result;
    }
    private static List<SignalBoundary> boundaries(TrackEdge edge) {
        return edge.getEdgeData().getPoints().stream().filter(SignalBoundary.class::isInstance)
            .map(SignalBoundary.class::cast).toList();
    }
    private static Shape shape(TrackGraph graph, List<TrackEdge> edges) {
        return new Shape(graph.getNodes().stream().map(TrackNodeLocation::copy).toList(),
            edges.stream().map(edge -> new EdgeShape(edge.node1.getLocation().copy(), edge.node2.getLocation().copy(), boundaries(edge).stream()
                .map(point -> new PointShape(point.id, point.getLocationOn(edge))).toList())).toList());
    }
    public static void refresh(TrackGraph graph) {
        var edges = edges(graph); var shape = shape(graph, edges);
        boolean queued = graph.getPoints(SignalEdgePoints.SIGNAL).stream()
            .anyMatch(point -> point.updateQueued(true) || point.updateQueued(false));
        if (queued || !shape.equals(CURRENT.get(graph))) rebuild(graph, edges, shape);
    }
    public static void rebuild(TrackGraph graph) {
        var edges = edges(graph); rebuild(graph, edges, shape(graph, edges));
    }
    private static void rebuild(TrackGraph graph, List<TrackEdge> edges, Shape shape) {
        var points = graph.getPoints(SignalEdgePoints.SIGNAL);
        if (points.isEmpty()) {
            for (var edge : edges) {
                edge.getEdgeData().setSingleSignalGroup(graph, EdgeData.passiveGroup);
                graph.getConnectionsFrom(edge.node2).get(edge.node1).getEdgeData()
                    .setSingleSignalGroup(graph, EdgeData.passiveGroup);
            }
            CURRENT.put(graph, shape); return;
        }
        var union = new Union();
        for (var location : graph.getNodes()) union.root(new NodeKey(location));
        for (var edge : edges) {
            Object previous = new NodeKey(edge.node1.getLocation());
            for (var boundary : boundaries(edge)) {
                var incoming = new SideKey(boundary.id, boundary.isPrimary(edge.node1));
                var outgoing = new SideKey(boundary.id, boundary.isPrimary(edge.node2));
                union.join(previous, incoming); union.root(outgoing); previous = outgoing;
            }
            union.join(previous, new NodeKey(edge.node2.getLocation()));
        }
        var identities = new LinkedHashMap<Object, UUID>();
        var used = new HashSet<UUID>();
        // Preserve genuine saved group identities when a partition remains; split regions receive new identities.
        for (var boundary : points) for (boolean primary : new boolean[] {true, false}) {
            var root = union.root(new SideKey(boundary.id, primary));
            var previous = boundary.group(primary);
            if (!identities.containsKey(root) && previous != null && !previous.equals(EdgeData.passiveGroup)
                && used.add(previous)) identities.put(root, previous);
        }
        for (var boundary : points) for (boolean primary : new boolean[] {true, false}) {
            var root = union.root(new SideKey(boundary.id, primary));
            var id = identities.get(root);
            if (id == null) { do { id = UUID.randomUUID(); } while (!used.add(id)); identities.put(root, id); }
            boundary.assignGroup(primary, id);
        }
        for (var edge : edges) {
            if (!boundaries(edge).isEmpty()) {
                edge.getEdgeData().refreshIntersectingSignalGroups(graph);
                graph.getConnectionsFrom(edge.node2).get(edge.node1).getEdgeData().refreshIntersectingSignalGroups(graph);
                continue;
            }
            var root = union.root(new NodeKey(edge.node1.getLocation()));
            var id = identities.get(root);
            if (id == null) { do { id = UUID.randomUUID(); } while (!used.add(id)); identities.put(root, id); }
            edge.getEdgeData().setSingleSignalGroup(graph, id);
            graph.getConnectionsFrom(edge.node2).get(edge.node1).getEdgeData().setSingleSignalGroup(graph, id);
        }
        CURRENT.put(graph, shape);
    }
    private SignalTopology() {}
}
