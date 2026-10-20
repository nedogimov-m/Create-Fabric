package com.simibubi.create.content.trains.graph;

import com.simibubi.create.content.trains.signal.TrackEdgePoint;

/** Installs/removes one actual point on both directed representations of the same real edge. */
public final class EdgePointManager {
  public static void requireEndpoints(TrackGraph graph, TrackEdgePoint point) {
    if (point.edgeLocation == null || !Double.isFinite(point.position))
      throw new IllegalArgumentException("Invalid railway point coordinate");
    var first = graph.locateNode(point.edgeLocation.getFirst());
    var second = graph.locateNode(point.edgeLocation.getSecond());
    if (first == null || second == null)
      throw new IllegalArgumentException("Railway point endpoints are not actual graph vertices");
    var edge = graph.getConnectionsFrom(first).get(second);
    if (edge == null
        || graph.getConnectionsFrom(second).get(first) == null
        || point.position < 0
        || point.position > edge.getLength() + 1e-8)
      throw new IllegalArgumentException("Railway point has no valid reciprocal edge");
  }

  public static <T extends TrackEdgePoint> void onEdgePointAdded(
      TrackGraph graph, T point, EdgePointType<T> type) {
    requireEndpoints(graph, point);
    var first = graph.locateNode(point.edgeLocation.getFirst());
    var second = graph.locateNode(point.edgeLocation.getSecond());
    graph.getConnectionsFrom(first).get(second).getEdgeData().addPoint(graph, point);
    graph.getConnectionsFrom(second).get(first).getEdgeData().addPoint(graph, point);
    point.bind(graph);
  }

  public static <T extends TrackEdgePoint> void onEdgePointRemoved(
      TrackGraph graph, T point, EdgePointType<T> type) {
    var first = graph.locateNode(point.edgeLocation.getFirst());
    var second = graph.locateNode(point.edgeLocation.getSecond());
    var forward = first == null || second == null ? null : graph.getConnectionsFrom(first).get(second);
    var reverse = first == null || second == null ? null : graph.getConnectionsFrom(second).get(first);
    if (forward != null) forward.getEdgeData().removePoint(graph, point);
    if (reverse != null) reverse.getEdgeData().removePoint(graph, point);
    point.onRemoved(graph);
    point.bind(null);
  }

  private EdgePointManager() {}
}
