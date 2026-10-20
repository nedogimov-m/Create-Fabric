package com.simibubi.create.content.trains.track;

import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Read-only identity/geometry witnesses around public railway callbacks; never a rollback copy. */
final class TrackReconciliationSnapshot {
  private record Identity(Object value) {
    @Override public boolean equals(Object other) {
      return other instanceof Identity identity && value == identity.value;
    }
    @Override public int hashCode() { return System.identityHashCode(value); }
  }

  private record Curve(
      List<BlockPos> positions, List<Vec3> starts, List<Vec3> axes, List<Vec3> normals,
      boolean primary, boolean girder, List<Integer> smoothing, Identity material) {
    static Curve of(BezierConnection curve) {
      return curve == null ? null : new Curve(
          List.of(curve.bePositions.getFirst().immutable(), curve.bePositions.getSecond().immutable()),
          List.of(curve.starts.getFirst(), curve.starts.getSecond()),
          List.of(curve.axes.getFirst(), curve.axes.getSecond()),
          List.of(curve.normals.getFirst(), curve.normals.getSecond()),
          curve.primary, curve.hasGirder,
          curve.smoothing == null ? List.of() : List.of(curve.smoothing.getFirst(), curve.smoothing.getSecond()),
          new Identity(curve.getMaterial()));
    }
  }

  private record Point(Identity point, UUID id, Identity type, Identity graph,
                       TrackNodeLocation first, TrackNodeLocation second, double position) {
    static Point of(TrackEdgePoint point) {
      return new Point(new Identity(point), point.id, new Identity(point.getType()),
          new Identity(point.graph()), point.edgeLocation.getFirst().copy(),
          point.edgeLocation.getSecond().copy(), point.position);
    }
  }

  private record Edge(Identity edge, Identity from, Identity to, Identity material,
                      boolean interDimensional, Curve turn, List<Point> points) {}
  private record Node(Identity node, TrackNodeLocation location, int netId, Vec3 normal,
                      List<Edge> edges) {}
  private record Graph(Identity graph, UUID id, int netId, Identity manager, List<Node> nodes) {}
  private record End(TrackNodeLocation location, Vec3 normal, Vec3 direction,
                     boolean forced, Identity materialA, Identity materialB, Curve turn) {
    static End of(TrackNodeLocation.DiscoveredLocation end) {
      return new End(end.copy(), end.getNormal(), end.getDirection(), end.shouldForceNode(),
          new Identity(end.getMaterialA()), new Identity(end.getMaterialB()), Curve.of(end.getTurn()));
    }
  }
  private record Cell(BlockState state, Identity owner, Set<End> ends) {}

  private final RailwayGraphManager manager;
  private final Map<UUID, Graph> graphs;
  private final Map<GlobalPos, Cell> cells;

  private TrackReconciliationSnapshot(
      RailwayGraphManager manager, Map<UUID, Graph> graphs, Map<GlobalPos, Cell> cells) {
    this.manager = manager;
    this.graphs = graphs;
    this.cells = cells;
  }

  static TrackReconciliationSnapshot capture(
      ServerLevel world, RailwayGraphManager manager, Collection<TrackNodeLocation> locations) {
    var cells = new LinkedHashMap<GlobalPos, Cell>();
    for (var location : locations)
      for (var pos : location.allAdjacent())
        cells.putIfAbsent(GlobalPos.of(location.dimension, pos), null);
    cells.replaceAll((pos, ignored) -> cell(world, pos));
    return new TrackReconciliationSnapshot(manager, graphs(manager), cells);
  }

  boolean matches(ServerLevel world) {
    if (RailwayGraphManager.get(world) != manager || !graphs.equals(graphs(manager))) return false;
    for (var entry : cells.entrySet())
      if (!entry.getValue().equals(cell(world, entry.getKey()))) return false;
    // Native ITrackBlock getters above are extension points too. Do not accept a graph changed
    // while the physical witness was being checked.
    return RailwayGraphManager.get(world) == manager && graphs.equals(graphs(manager));
  }

  private static Cell cell(ServerLevel source, GlobalPos at) {
    var world = source.getServer().getLevel(at.dimension());
    if (world == null || !LoadedLevelAccess.isLoaded(world, at.pos()))
      throw new UnloadedTrackException(Set.of(at));
    var state = LoadedLevelAccess.blockState(world, at.pos());
    if (!(state.getBlock() instanceof ITrackBlock track))
      return new Cell(state, new Identity(null), Set.of());
    var ends = new HashSet<End>();
    for (var end : track.getConnected(world, at.pos(), state, false, null)) ends.add(End.of(end));
    return new Cell(state, new Identity(LoadedLevelAccess.blockEntity(world, at.pos())), Set.copyOf(ends));
  }

  private static Map<UUID, Graph> graphs(RailwayGraphManager manager) {
    var result = new LinkedHashMap<UUID, Graph>();
    for (var entry : manager.getTrackNetworks().entrySet()) {
      var graph = entry.getValue();
      graph.validate();
      var nodes = new ArrayList<Node>();
      for (var location : graph.getNodes()) {
        var node = graph.locateNode(location);
        var edges = new ArrayList<Edge>();
        for (var connection : graph.getConnectionsFrom(node).entrySet()) {
          var edge = connection.getValue();
          edges.add(new Edge(new Identity(edge), new Identity(edge.node1), new Identity(edge.node2),
              new Identity(edge.getTrackMaterial()), edge.isInterDimensional(), Curve.of(edge.getTurn()),
              edge.getEdgeData().getPoints().stream().map(Point::of).toList()));
        }
        // Directed adjacency uses IdentityHashMap, whose iteration order can change on resize.
        edges.sort(Comparator.comparingInt(edge -> ((TrackNode) edge.to.value).getNetId()));
        nodes.add(new Node(new Identity(node), location.copy(), node.getNetId(), node.getNormal(), List.copyOf(edges)));
      }
      nodes.sort(Comparator.comparingInt(Node::netId));
      result.put(entry.getKey(), new Graph(new Identity(graph), graph.id, graph.getNetId(),
          new Identity(graph.manager()), List.copyOf(nodes)));
    }
    return Map.copyOf(result);
  }
}
