package com.simibubi.create.content.trains.graph;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;

/**
 * Real persistent railway topology: reciprocal edges, typed points, crossings, merge and split.
 * Runtime carriage/signal consumers subscribe to actual mutation events, not fake Train facades.
 */
public final class TrackGraph {
  public static final AtomicInteger graphNetIdGenerator = new AtomicInteger(),
      nodeNetIdGenerator = new AtomicInteger();

  private record ConnectionData(
      int to, TrackMaterial material, Optional<BezierConnection> turn, EdgeData.Snapshot data) {
    static final Codec<ConnectionData> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .fieldOf("To")
                            .forGetter(ConnectionData::to),
                        TrackMaterial.CODEC.fieldOf("Material").forGetter(ConnectionData::material),
                        BezierConnection.CODEC
                            .optionalFieldOf("Turn")
                            .forGetter(ConnectionData::turn),
                        EdgeData.Snapshot.CODEC.fieldOf("EdgeData").forGetter(ConnectionData::data))
                    .apply(i, ConnectionData::new));
  }

  private record NodeData(
      TrackNodeLocation location, Vec3 normal, List<ConnectionData> connections) {
    static final Codec<NodeData> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        TrackNodeLocation.CODEC.fieldOf("Location").forGetter(NodeData::location),
                        RailCodecs.VECTOR.fieldOf("Normal").forGetter(NodeData::normal),
                        ConnectionData.CODEC
                            .listOf()
                            .fieldOf("Connections")
                            .forGetter(NodeData::connections))
                    .apply(i, NodeData::new));
  }

  private record Data(
      UUID id, int color, long revision, List<NodeData> nodes, List<TrackEdgePoint> points) {
    static final Codec<Data> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        net.minecraft.core.UUIDUtil.CODEC.fieldOf("Id").forGetter(Data::id),
                        Codec.INT.fieldOf("Color").forGetter(Data::color),
                        Codec.LONG.optionalFieldOf("Revision", 0L).forGetter(Data::revision),
                        NodeData.CODEC.listOf().fieldOf("Nodes").forGetter(Data::nodes),
                        TrackEdgePoint.CODEC
                            .listOf()
                            .optionalFieldOf("Points", List.of())
                            .forGetter(Data::points))
                    .apply(i, Data::new));
  }

  public static final Codec<TrackGraph> CODEC =
      Data.CODEC.flatXmap(
          data -> {
            try {
              return DataResult.success(decode(data));
            } catch (IllegalArgumentException invalid) {
              return DataResult.error(invalid::getMessage);
            }
          },
          graph -> {
            try {
              return DataResult.success(graph.snapshot());
            } catch (IllegalArgumentException invalid) {
              return DataResult.error(invalid::getMessage);
            }
          });

  /**
   * Full native wire content without consuming integrated-server identity generators. Source
   * graph/node IDs are restored before this detached graph can be published.
   */
  public static final Codec<TrackGraph> TRANSPORT_CODEC =
      Data.CODEC.flatXmap(
          data -> {
            try {
              return DataResult.success(decode(data, false));
            } catch (IllegalArgumentException invalid) {
              return DataResult.error(invalid::getMessage);
            }
          },
          graph -> {
            try {
              return DataResult.success(graph.snapshot());
            } catch (IllegalArgumentException invalid) {
              return DataResult.error(invalid::getMessage);
            }
          });

  public UUID id;
  public int color;
  final Map<TrackNodeLocation, TrackNode> nodes = new LinkedHashMap<>();
  final Map<Integer, TrackNode> nodesById = new HashMap<>();
  final Map<TrackNode, Map<TrackNode, TrackEdge>> connectionsByNode = new IdentityHashMap<>();
  final EdgePointStorage edgePoints = new EdgePointStorage();
  private final Set<TrackEdge> deferredIntersectionUpdates =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private RailwayGraphManager manager;
  private int netId;
  private long revision;

  public TrackGraph() {
    this(UUID.randomUUID());
  }

  public TrackGraph(UUID id) {
    this(id, true);
  }

  private TrackGraph(UUID id, boolean allocateIdentity) {
    setId(id);
    netId = allocateIdentity ? nextGraphId() : 1;
  }

  public static int nextNodeId() {
    return nodeNetIdGenerator.incrementAndGet();
  }

  public static int nextGraphId() {
    return graphNetIdGenerator.incrementAndGet();
  }

  public void setId(UUID id) {
    if (manager != null) throw new IllegalStateException("Cannot rename an attached railway graph");
    this.id = Objects.requireNonNull(id);
    color = 0xFF000000 | new Random(id.getLeastSignificantBits()).nextInt(0x1000000);
  }

  public int getNetId() {
    return netId;
  }

  public void setNetId(int id) {
    if (id <= 0) throw new IllegalArgumentException("Invalid railway transport id");
    netId = id;
    graphNetIdGenerator.accumulateAndGet(id, Math::max);
  }

  public long revision() {
    return revision;
  }

  public RailwayGraphManager manager() {
    return manager;
  }

  public void bind(RailwayGraphManager owner) {
    if (manager != null && owner != null && manager != owner)
      throw new IllegalStateException("Railway graph already has an owner");
    manager = owner;
  }

  /**
   * Installs source network IDs on a fully decoded detached graph before atomic client publication.
   * No world, manager, revision, point instance or persistent geometry is changed.
   */
  public void restoreTransportIdentity(int graphId, Map<TrackNodeLocation, Integer> identities) {
    if (manager != null)
      throw new IllegalStateException("Cannot rewrite transport identity of a server-owned graph");
    validate();
    if (graphId <= 0
        || !identities.keySet().equals(nodes.keySet())
        || new HashSet<>(identities.values()).size() != identities.size()
        || identities.values().stream().anyMatch(id -> id == null || id <= 0))
      throw new IllegalArgumentException("Invalid complete railway transport identity");
    var replacements = new IdentityHashMap<TrackNode, TrackNode>();
    var newNodes = new LinkedHashMap<TrackNodeLocation, TrackNode>();
    var newIds = new HashMap<Integer, TrackNode>();
    for (var node : nodes.values()) {
      var copy = new TrackNode(node.location, identities.get(node.location), node.normal);
      replacements.put(node, copy);
      newNodes.put(copy.location, copy);
      newIds.put(copy.netId, copy);
    }
    var adjacency = new IdentityHashMap<TrackNode, Map<TrackNode, TrackEdge>>();
    for (var from : nodes.values())
      for (var entry : getConnectionsFrom(from).entrySet()) {
        var a = replacements.get(from);
        var b = replacements.get(entry.getKey());
        var edge = entry.getValue();
        edge.node1 = a;
        edge.node2 = b;
        adjacency.computeIfAbsent(a, key -> new IdentityHashMap<>()).put(b, edge);
      }
    nodes.clear();
    nodes.putAll(newNodes);
    nodesById.clear();
    nodesById.putAll(newIds);
    connectionsByNode.clear();
    connectionsByNode.putAll(adjacency);
    netId = graphId;
    validate();
  }

  public void markDirty() {
    revision++;
    if (manager != null) manager.graphChanged(this);
  }

  public Set<TrackNodeLocation> getNodes() {
    var result = new LinkedHashSet<TrackNodeLocation>();
    for (var location : nodes.keySet()) result.add(location.copy());
    return Collections.unmodifiableSet(result);
  }

  public TrackNode locateNode(Level level, Vec3 position) {
    return locateNode(new TrackNodeLocation(position).in(level));
  }

  public TrackNode locateNode(TrackNodeLocation location) {
    return nodes.get(location);
  }

  public TrackNode getNode(int id) {
    return nodesById.get(id);
  }

  public boolean createNodeIfAbsent(TrackNodeLocation.DiscoveredLocation location) {
    return !nodes.containsKey(location)
        && addNodeIfAbsent(new TrackNode(location, nextNodeId(), location.getNormal()));
  }

  public void loadNode(TrackNodeLocation location, int id, Vec3 normal) {
    addNode(new TrackNode(location, id, normal));
    nodeNetIdGenerator.accumulateAndGet(id, Math::max);
  }

  public void addNode(TrackNode node) {
    var old = nodes.get(node.location);
    if (old != null && old != node)
      throw new IllegalArgumentException("Duplicate railway vertex " + node.location);
    if (!addNodeIfAbsent(node) && old != node)
      throw new IllegalArgumentException("Duplicate railway transport id");
  }

  public boolean addNodeIfAbsent(TrackNode node) {
    if (nodes.containsKey(node.location)) return false;
    if (nodesById.containsKey(node.netId))
      throw new IllegalArgumentException("Duplicate railway node transport id");
    nodes.put(node.location, node);
    nodesById.put(node.netId, node);
    markDirty();
    return true;
  }

  public boolean isEmpty() {
    return nodes.isEmpty();
  }

  public Map<TrackNode, TrackEdge> getConnectionsFrom(TrackNode node) {
    return node == null
        ? Map.of()
        : Collections.unmodifiableMap(connectionsByNode.getOrDefault(node, Map.of()));
  }

  public TrackEdge getConnection(Couple<TrackNode> endpoints) {
    if (endpoints.getFirst() == null || endpoints.getSecond() == null) return null;
    return getConnectionsFrom(endpoints.getFirst()).get(endpoints.getSecond());
  }

  public boolean putConnection(TrackNode from, TrackNode to, TrackEdge edge) {
    if (nodes.get(from.location) != from
        || nodes.get(to.location) != to
        || from == to
        || edge.node1 != from
        || edge.node2 != to)
      throw new IllegalArgumentException("Railway edge endpoints are not actual graph vertices");
    var map = connectionsByNode.computeIfAbsent(from, key -> new IdentityHashMap<>());
    var old = map.get(to);
    if (old != null && old != edge && old.getEdgeData().hasPoints()) return false;
    map.put(to, edge);
    markDirty();
    return old == null;
  }

  public void connectNodes(
      LevelAccessor world,
      TrackNodeLocation.DiscoveredLocation first,
      TrackNodeLocation.DiscoveredLocation second,
      BezierConnection turn) {
    connectNodes(
        nodes.get(first),
        nodes.get(second),
        turn,
        turn == null ? second.getMaterialA() : turn.getMaterial());
  }

  public void connectNodes(
      TrackNode first, TrackNode second, BezierConnection turn, TrackMaterial material) {
    if (first == null
        || second == null
        || first == second
        || nodes.get(first.location) != first
        || nodes.get(second.location) != second)
      throw new IllegalArgumentException("Missing railway endpoints");
    var old = getConnectionsFrom(first).get(second);
    if (old != null) {
      boolean equal =
          old.getTrackMaterial() == material
              && (turn == null
                  ? !old.isTurn()
                  : old.isTurn() && turn.equalsSansMaterial(old.getTurn()));
      if (equal) return;
      if (old.getEdgeData().hasPoints())
        throw new IllegalArgumentException(
            "Cannot replace a railway edge while its points are attached");
      disconnectNodes(first, second);
    }
    var forward = new TrackEdge(first, second, turn, material);
    var backward = new TrackEdge(second, first, turn == null ? null : turn.secondary(), material);
    putConnection(first, second, forward);
    putConnection(second, first, backward);
    discoverIntersections(forward, backward);
    markDirty();
  }

  private Collection<TrackGraph> relatedGraphs() {
    return manager == null ? List.of(this) : manager.getTrackNetworks().values();
  }

  private record Undirected(
      TrackNode first, TrackNode second, TrackEdge forward, TrackEdge reverse) {}

  private List<Undirected> undirected() {
    var result = new ArrayList<Undirected>();
    for (var first : nodes.values())
      for (var entry : getConnectionsFrom(first).entrySet())
        if (first.netId < entry.getKey().netId)
          result.add(
              new Undirected(
                  first,
                  entry.getKey(),
                  entry.getValue(),
                  getConnectionsFrom(entry.getKey()).get(first)));
    return result;
  }

  private void discoverIntersections(TrackEdge forward, TrackEdge backward) {
    if (forward.isInterDimensional()) return;
    for (var otherGraph : List.copyOf(relatedGraphs()))
      for (var other : otherGraph.undirected()) {
        if (other.reverse == null
            || other.forward == forward
            || other.forward == backward
            || other.first.location.equals(forward.node1.location)
            || other.first.location.equals(forward.node2.location)
            || other.second.location.equals(forward.node1.location)
            || other.second.location.equals(forward.node2.location)
            || !Objects.equals(other.first.location.dimension, forward.node1.location.dimension))
          continue;
        if (!forward.isTurn() && !other.forward.isTurn()) continue;
        for (var intersection :
            forward.getIntersection(
                forward.node1, forward.node2, other.forward, other.first, other.second)) {
          if (intersection == null) continue;
          var id = UUID.randomUUID();
          double a = intersection[0], b = intersection[1];
          forward.getEdgeData().addIntersection(this, id, a, other.first, other.second, b);
          backward
              .getEdgeData()
              .addIntersection(this, id, forward.getLength() - a, other.first, other.second, b);
          other
              .forward
              .getEdgeData()
              .addIntersection(otherGraph, id, b, forward.node1, forward.node2, a);
          other
              .reverse
              .getEdgeData()
              .addIntersection(
                  otherGraph, id, other.forward.getLength() - b, forward.node1, forward.node2, a);
        }
      }
  }

  public void disconnectNodes(TrackNode first, TrackNode second) {
    if (first == null || second == null) return;
    var edge = getConnectionsFrom(first).get(second);
    if (edge == null) return;
    for (var point : List.copyOf(edge.getEdgeData().getPoints()))
      removePoint(point.getType(), point.id);
    var intersections =
        edge.getEdgeData().getIntersections().stream().map(value -> value.id).toList();
    var forward = connectionsByNode.get(first);
    var reverse = connectionsByNode.get(second);
    if (forward != null) forward.remove(second);
    if (reverse != null) reverse.remove(first);
    for (var graph : List.copyOf(relatedGraphs()))
      for (var crossing : graph.undirected())
        for (var id : intersections) {
          crossing.forward.getEdgeData().removeIntersection(graph, id);
          if (crossing.reverse != null)
            crossing.reverse.getEdgeData().removeIntersection(graph, id);
        }
    markDirty();
  }

  public boolean removeNode(LevelAccessor world, TrackNodeLocation location) {
    var node = nodes.get(location);
    if (node == null) return false;
    var affected =
        edgePoints.all().stream()
            .filter(
                point ->
                    point.edgeLocation.getFirst().equals(location)
                        || point.edgeLocation.getSecond().equals(location))
            .toList();
    if (world != null) for (var point : affected) point.preflightInvalidation(world);
    for (var point : affected) {
      if (world != null) point.invalidate(world);
      removePoint(point.getType(), point.id);
    }
    for (var other : List.copyOf(getConnectionsFrom(node).keySet())) disconnectNodes(node, other);
    nodes.remove(node.location);
    nodesById.remove(node.netId);
    connectionsByNode.remove(node);
    markDirty();
    if (manager != null) manager.nodeRemoved(this, node);
    return true;
  }

  public <T extends TrackEdgePoint> void addPoint(EdgePointType<T> type, T point) {
    if (point.getType() != type) throw new IllegalArgumentException("Railway point type mismatch");
    if (point.graph() != null)
      throw new IllegalArgumentException("Railway point already has a graph owner");
    EdgePointManager.requireEndpoints(this, point);
    if (edgePoints.get(type, point.id) != null)
      throw new IllegalArgumentException("Duplicate railway point UUID");
    edgePoints.put(type, point);
    EdgePointManager.onEdgePointAdded(this, point, type);
    markDirty();
  }

  public <T extends TrackEdgePoint> T getPoint(EdgePointType<T> type, UUID id) {
    return edgePoints.get(type, id);
  }

  public <T extends TrackEdgePoint> Collection<T> getPoints(EdgePointType<T> type) {
    return edgePoints.values(type);
  }

  public <T extends TrackEdgePoint> T removePoint(EdgePointType<T> type, UUID id) {
    var point = edgePoints.remove(type, id);
    if (point != null) {
      EdgePointManager.onEdgePointRemoved(this, point, type);
      markDirty();
    }
    return point;
  }

  public void tickPoints(boolean preTrains) {
    edgePoints.tick(this, preTrains);
  }

  public void preflightMerge(TrackGraph target) {
    if (target == this) return;
    validate();
    target.validate();
    if (manager != null && target.manager != null && manager != target.manager)
      throw new IllegalArgumentException("Cannot merge railway graphs from different servers");
    for (var node : nodes.values()) {
      var collision = target.nodesById.get(node.netId);
      if (collision != null && !collision.location.equals(node.location))
        throw new IllegalArgumentException("Conflicting railway transport id during merge");
    }
    for (var point : edgePoints.all()) {
      var existing = target.getPoint(point.getType(), point.id);
      if (existing != null && existing != point)
        throw new IllegalArgumentException("Conflicting railway point identity during merge");
    }
    for (var first : nodes.values())
      for (var entry : getConnectionsFrom(first).entrySet()) {
        var a = target.locateNode(first.location);
        var b = target.locateNode(entry.getKey().location);
        if (a == null || b == null) continue;
        var existing = target.getConnectionsFrom(a).get(b);
        if (existing != null && existing != entry.getValue())
          throw new IllegalArgumentException("Cannot silently coalesce duplicate railway edges");
      }
  }

  public void transferAll(TrackGraph target) {
    if (target == this) return;
    preflightMerge(target);
    for (var node : List.copyOf(nodes.values())) target.addNodeIfAbsent(node);
    for (var first : List.copyOf(nodes.values()))
      for (var entry : List.copyOf(getConnectionsFrom(first).entrySet())) {
        var a = target.locateNode(first.location);
        var b = target.locateNode(entry.getKey().location);
        var edge = entry.getValue();
        edge.node1 = a;
        edge.node2 = b;
        target.putConnection(a, b, edge);
        if (edge.getEdgeData().hasIntersections())
          edge.getEdgeData().refreshIntersectingSignalGroups(target);
      }
    edgePoints.transferAll(target, target.edgePoints);
    target.deferredIntersectionUpdates.addAll(deferredIntersectionUpdates);
    deferredIntersectionUpdates.clear();
    nodes.clear();
    nodesById.clear();
    connectionsByNode.clear();
    markDirty();
    target.markDirty();
    if (manager != null) manager.graphMerged(this, target);
  }

  public Set<TrackGraph> findDisconnectedGraphs(
      LevelAccessor world, Map<Integer, Pair<Integer, UUID>> ids) {
    var remaining = Collections.newSetFromMap(new IdentityHashMap<TrackNode, Boolean>());
    remaining.addAll(nodes.values());
    var components = new ArrayList<Set<TrackNode>>();
    while (!remaining.isEmpty()) {
      var component = Collections.newSetFromMap(new IdentityHashMap<TrackNode, Boolean>());
      var queue = new ArrayDeque<TrackNode>();
      var first = remaining.iterator().next();
      queue.add(first);
      remaining.remove(first);
      while (!queue.isEmpty()) {
        var current = queue.remove();
        component.add(current);
        for (var next : getConnectionsFrom(current).keySet())
          if (remaining.remove(next)) queue.add(next);
      }
      components.add(component);
    }
    components.sort(
        Comparator.<Set<TrackNode>>comparingInt(Set::size)
            .reversed()
            .thenComparingInt(set -> set.stream().mapToInt(TrackNode::getNetId).min().orElse(0)));
    var result = new LinkedHashSet<TrackGraph>();
    for (int i = 1; i < components.size(); i++) {
      var target = new TrackGraph();
      if (ids != null)
        for (var node : components.get(i)) {
          var saved = ids.get(node.netId);
          if (saved != null) {
            target.setId(saved.getSecond());
            target.setNetId(saved.getFirst());
            break;
          }
        }
      transferComponent(components.get(i), target);
      result.add(target);
    }
    return result;
  }

  private void transferComponent(Set<TrackNode> component, TrackGraph target) {
    if (target == this)
      throw new IllegalArgumentException("Cannot split a railway component into itself");
    if (manager != null && target.manager != null && manager != target.manager)
      throw new IllegalArgumentException(
          "Cannot transfer railway components between server managers");
    for (var node : component) {
      if (nodes.get(node.location) != node
          || target.locateNode(node.location) != null
          || target.getNode(node.netId) != null)
        throw new IllegalArgumentException("Conflicting railway component destination");
      for (var next : getConnectionsFrom(node).keySet())
        if (!component.contains(next))
          throw new IllegalArgumentException("Railway transfer must include a complete component");
    }
    var moving =
        edgePoints.all().stream()
            .filter(
                point ->
                    component.stream()
                        .anyMatch(node -> node.location.equals(point.edgeLocation.getFirst())))
            .toList();
    for (var point : moving)
      if (target.getPoint(point.getType(), point.id) != null)
        throw new IllegalArgumentException("Conflicting railway point in component destination");
    for (var node : component) {
      target.addNode(node);
      var edges = connectionsByNode.remove(node);
      if (edges != null) {
        target.connectionsByNode.put(node, edges);
        for (var edge : edges.values())
          if (edge.getEdgeData().hasIntersections())
            edge.getEdgeData().refreshIntersectingSignalGroups(target);
      }
      nodes.remove(node.location);
      nodesById.remove(node.netId);
    }
    for (var point : moving) {
      edgePoints.remove(point.getType(), point.id);
      point.bind(null);
      target.edgePoints.put(point.getType(), point);
      point.bind(target);
    }
    for (var edge : List.copyOf(deferredIntersectionUpdates))
      if (component.contains(edge.node1)) {
        deferredIntersectionUpdates.remove(edge);
        target.deferredIntersectionUpdates.add(edge);
      }
    markDirty();
    target.markDirty();
    if (manager != null) manager.graphSplit(this, target);
  }

  /** Transferring a connected component preserves reciprocal edges and live point identity. */
  public void transfer(LevelAccessor world, TrackNode node, TrackGraph target) {
    var component = Collections.newSetFromMap(new IdentityHashMap<TrackNode, Boolean>());
    var queue = new ArrayDeque<TrackNode>();
    queue.add(node);
    while (!queue.isEmpty()) {
      var current = queue.remove();
      if (!component.add(current)) continue;
      queue.addAll(getConnectionsFrom(current).keySet());
    }
    transferComponent(component, target);
  }

  public int getChecksum() {
    return nodes.values().stream().mapToInt(TrackNode::getNetId).sum();
  }

  public void invalidateBounds() {
    markDirty();
  }

  public TrackGraphBounds getBounds(Level level) {
    return new TrackGraphBounds(this, level.dimension());
  }

  public float distanceToLocationSqr(Level level, Vec3 position) {
    float result = Float.MAX_VALUE;
    for (var node : nodes.values())
      if (Objects.equals(node.location.dimension, level.dimension()))
        result = Math.min(result, (float) node.location.getLocation().distanceToSqr(position));
    return result;
  }

  public void deferIntersectionUpdate(TrackEdge edge) {
    deferredIntersectionUpdates.add(edge);
  }

  public void resolveIntersectingEdgeGroups(Level level) {
    for (var edge : List.copyOf(deferredIntersectionUpdates)) {
      if (getConnectionsFrom(edge.node1).get(edge.node2) != edge) continue;
      for (var crossing : edge.getEdgeData().getIntersections()) {
        var group = edge.getEdgeData().getGroupAtPosition(this, crossing.location);
        if (!Objects.equals(group, crossing.groupId)) {
          crossing.groupId = group;
          markDirty();
          if (manager != null) manager.intersectionChanged(this, crossing);
        }
      }
    }
    deferredIntersectionUpdates.clear();
  }

  public void validate() {
    if (nodesById.size() != nodes.size())
      throw new IllegalArgumentException("Invalid railway transport index");
    for (var node : nodes.values()) {
      if (node.location.dimension == null
          || nodesById.get(node.netId) != node
          || nodes.get(node.location) != node)
        throw new IllegalArgumentException("Invalid railway node identity");
      for (var entry : getConnectionsFrom(node).entrySet()) {
        var edge = entry.getValue();
        var reverse = getConnectionsFrom(entry.getKey()).get(node);
        if (nodes.get(entry.getKey().location) != entry.getKey()
            || edge.node1 != node
            || edge.node2 != entry.getKey()
            || reverse == null)
          throw new IllegalArgumentException("Missing reciprocal railway edge");
        if (!Double.isFinite(edge.getLength())
            || edge.getLength() < 0
            || Math.abs(edge.getLength() - reverse.getLength()) > 1e-6
            || edge.getTrackMaterial() != reverse.getTrackMaterial()
            || edge.isTurn() != reverse.isTurn()
            || edge.isTurn() && !edge.getTurn().equalsSansMaterial(reverse.getTurn()))
          throw new IllegalArgumentException("Inconsistent reciprocal railway geometry");
        for (var point : edge.getEdgeData().getPoints()) {
          if (edgePoints.get(point.getType(), point.id) != point
              || !(point.edgeLocation.getFirst().equals(node.location)
                      && point.edgeLocation.getSecond().equals(entry.getKey().location)
                  || point.edgeLocation.getSecond().equals(node.location)
                      && point.edgeLocation.getFirst().equals(entry.getKey().location)))
            throw new IllegalArgumentException("Foreign railway point on directed edge");
        }
        for (var crossing : edge.getEdgeData().getIntersections())
          if (crossing.location < 0 || crossing.location > edge.getLength() + 1e-6)
            throw new IllegalArgumentException("Railway intersection outside edge");
      }
    }
    for (var point : edgePoints.all()) {
      EdgePointManager.requireEndpoints(this, point);
      if (point.graph() != this || edgePoints.get(point.getType(), point.id) != point)
        throw new IllegalArgumentException("Invalid railway point ownership");
      var first = locateNode(point.edgeLocation.getFirst());
      var second = locateNode(point.edgeLocation.getSecond());
      if (!getConnectionsFrom(first).get(second).getEdgeData().getPoints().contains(point)
          || !getConnectionsFrom(second).get(first).getEdgeData().getPoints().contains(point))
        throw new IllegalArgumentException("Railway point missing directed reference");
    }
  }

  private Data snapshot() {
    validate();
    var ordered = new ArrayList<>(nodes.values());
    ordered.sort(
        Comparator.comparing((TrackNode node) -> node.location.dimension.identifier().toString())
            .thenComparingInt(node -> node.location.getX())
            .thenComparingInt(node -> node.location.getY())
            .thenComparingInt(node -> node.location.getZ())
            .thenComparingInt(node -> node.location.yOffsetPixels));
    var indices = new IdentityHashMap<TrackNode, Integer>();
    for (int i = 0; i < ordered.size(); i++) indices.put(ordered.get(i), i);
    var encoded = new ArrayList<NodeData>();
    for (var node : ordered) {
      var connections = new ArrayList<ConnectionData>();
      for (var entry : getConnectionsFrom(node).entrySet()) {
        var edge = entry.getValue();
        connections.add(
            new ConnectionData(
                indices.get(entry.getKey()),
                edge.getTrackMaterial(),
                Optional.ofNullable(edge.getTurn()),
                edge.getEdgeData().snapshot()));
      }
      connections.sort(Comparator.comparingInt(ConnectionData::to));
      encoded.add(new NodeData(node.location, node.normal, List.copyOf(connections)));
    }
    return new Data(id, color, revision, List.copyOf(encoded), edgePoints.all());
  }

  private static TrackGraph decode(Data data) {
    return decode(data, true);
  }

  private static TrackGraph decode(Data data, boolean allocateIdentity) {
    var graph = new TrackGraph(data.id, allocateIdentity);
    graph.color = data.color;
    if (data.revision < 0) throw new IllegalArgumentException("Invalid railway graph revision");
    var ordered = new ArrayList<TrackNode>();
    for (var entry : data.nodes) {
      var node =
          new TrackNode(
              entry.location, allocateIdentity ? nextNodeId() : ordered.size() + 1, entry.normal);
      if (!graph.addNodeIfAbsent(node))
        throw new IllegalArgumentException("Duplicate railway node position");
      ordered.add(node);
    }
    for (var point : data.points) graph.edgePoints.put(point.getType(), point);
    for (int i = 0; i < ordered.size(); i++)
      for (var connection : data.nodes.get(i).connections) {
        if (connection.to >= ordered.size() || connection.to == i)
          throw new IllegalArgumentException("Invalid railway edge index");
        var from = ordered.get(i);
        var to = ordered.get(connection.to);
        if (graph.getConnectionsFrom(from).containsKey(to))
          throw new IllegalArgumentException("Duplicate directed railway edge");
        var edge = new TrackEdge(from, to, connection.turn.orElse(null), connection.material);
        edge.getEdgeData().restore(connection.data, graph);
        graph.putConnection(from, to, edge);
      }
    for (var point : graph.edgePoints.all()) point.bind(graph);
    graph.validate();
    graph.revision = data.revision;
    return graph;
  }

  public CompoundTag write(HolderLookup.Provider registries, DimensionPalette dimensions) {
    return (CompoundTag)
        CODEC
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
            .getOrThrow();
  }

  public static TrackGraph read(
      CompoundTag tag, HolderLookup.Provider registries, DimensionPalette dimensions) {
    return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
  }
}
