package com.simibubi.create.content.trains.track;

import com.simibubi.create.api.event.TrackGraphMergeEvent;
import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/**
 * Source node compression over actual ITrackBlock geometry, with discovery-before-mutation.
 * Existing graph nodes bound local walks; missing native frontier defers the whole local edit.
 */
public final class TrackPropagator {
  public record Result(boolean complete, List<TrackGraph> graphs, Set<GlobalPos> pending) {
    public Result {
      graphs = List.copyOf(graphs);
      pending = Set.copyOf(pending);
    }
  }

  private static final int MAX_DISCOVERY = 65536;
  private static final class Attempt { boolean reentered; }
  private static final ThreadLocal<Map<net.minecraft.server.MinecraftServer, Attempt>> RECONCILING =
      ThreadLocal.withInitial(IdentityHashMap::new);

  private record Plan(List<OldEdge> old, Map<TrackNodeLocation, Vertex> vertices,
                      Set<TrackNodeLocation> selected, List<Planned> edges) {}

  /** Only our post-callback witness/preflight can request this loaded native retry. */
  private static final class DeferredPlan extends RuntimeException {
    final Set<GlobalPos> pending;
    DeferredPlan(Collection<GlobalPos> pending) { this.pending = Set.copyOf(pending); }
  }


  private static final class Vertex {
    final TrackNodeLocation.DiscoveredLocation location;
    final Map<TrackNodeLocation, Link> links = new LinkedHashMap<>();
    boolean boundary;

    Vertex(TrackNodeLocation.DiscoveredLocation location) {
      this.location = location;
    }
  }

  private record Link(
      TrackNodeLocation from,
      TrackNodeLocation to,
      BezierConnection turn,
      TrackMaterial material) {}

  private record Planned(
      TrackNodeLocation from,
      TrackNodeLocation to,
      BezierConnection turn,
      TrackMaterial material) {}

  private record OldEdge(TrackGraph graph, TrackNode first, TrackNode second, TrackEdge edge) {}

  private static TrackNodeLocation.DiscoveredLocation discovered(
      TrackNodeLocation location, Vec3 normal, TrackMaterial material) {
    return new TrackNodeLocation.DiscoveredLocation(
            location.dimension, location.getLocation().subtract(0, location.yOffsetPixels / 16d, 0))
        .withYOffset(location.yOffsetPixels)
        .withNormal(normal)
        .materials(material, material);
  }

  public static TrackGraph onRailAdded(LevelAccessor world, BlockPos pos, BlockState state) {
    if (!(world instanceof ServerLevel server)) return null;
    var result = reconcile(server, pos, state, false);
    return result.graphs.isEmpty() ? null : result.graphs.getFirst();
  }

  public static void onRailRemoved(LevelAccessor world, BlockPos pos, BlockState state) {
    if (world instanceof ServerLevel server) reconcile(server, pos, state, true);
  }

  /**
   * Read-only preflight for explicit curve placement/removal before its world ownership changes. It
   * checks the current loaded discovery region and every affected point's declared real owners; no
   * graph, queue, revision or chunk ticket is changed. The post-edit reconciliation still plans the
   * resulting topology separately. UnloadedTrackException means the caller must leave items,
   * reciprocal curves and blocks untouched.
   */
  public static void preflightChanges(ServerLevel world, Collection<BlockPos> changes) {
    var manager = RailwayGraphManager.get(world);
    var missing = new LinkedHashSet<GlobalPos>();
    for (var pos : changes) {
      if (!LoadedLevelAccess.isLoaded(world, pos)) {
        missing.add(GlobalPos.of(world.dimension(), pos.immutable()));
        continue;
      }
      try {
        var seeds = new ArrayList<TrackNodeLocation.DiscoveredLocation>();
        var state = LoadedLevelAccess.blockState(world, pos);
        if (state.getBlock() instanceof ITrackBlock track)
          seeds.addAll(track.getConnected(world, pos, state, false, null));
        var old = oldEdges(manager, world, pos);
        for (var previous : old) {
          oldSeeds(previous, pos, seeds);
          for (var point : previous.edge.getEdgeData().getPoints()) point.preflightInvalidation(world);
        }
        discover(world, pos, seeds, manager, old);
      } catch (UnloadedTrackException pending) {
        missing.addAll(pending.frontiers());
      }
    }
    if (!missing.isEmpty()) throw new UnloadedTrackException(missing);
  }

  private static boolean touches(TrackEdge edge, ResourceKeyDimension dimension, BlockPos pos) {
    if (edge.isInterDimensional())
      return List.of(edge.node1.getLocation(), edge.node2.getLocation()).stream()
          .anyMatch(node -> Objects.equals(node.dimension, dimension.key) && node.allAdjacent().contains(pos));
    return Objects.equals(edge.node1.getLocation().dimension, dimension.key)
        && (edge.isTurn()
            ? edge.getTurn().bePositions.either(pos::equals)
            : new AABB(pos)
                .inflate(1e-5)
                .intersects(
                    edge.node1.getLocation().getLocation(),
                    edge.node2.getLocation().getLocation()));
  }

  private record ResourceKeyDimension(net.minecraft.resources.ResourceKey<Level> key) {}

  private static List<OldEdge> oldEdges(
      RailwayGraphManager manager, ServerLevel world, BlockPos pos) {
    var result = new ArrayList<OldEdge>();
    var dimension = new ResourceKeyDimension(world.dimension());
    for (var graph : manager.getTrackNetworks().values())
      for (var location : graph.getNodes()) {
        var first = graph.locateNode(location);
        for (var entry : graph.getConnectionsFrom(first).entrySet())
          if (first.getNetId() < entry.getKey().getNetId()
              && touches(entry.getValue(), dimension, pos))
            result.add(new OldEdge(graph, first, entry.getKey(), entry.getValue()));
      }
    return result;
  }

  private static BezierConnection orient(BezierConnection curve, TrackNodeLocation from) {
    if (curve == null) return null;
    return curve.starts.getFirst().distanceToSqr(from.getLocation())
            <= curve.starts.getSecond().distanceToSqr(from.getLocation())
        ? curve
        : curve.secondary();
  }

  private static void oldSeeds(
      OldEdge old, BlockPos pos, List<TrackNodeLocation.DiscoveredLocation> seeds) {
    if (old.edge.isTurn() || old.edge.isInterDimensional()) {
      for (var node : List.of(old.first, old.second))
        seeds.add(
            discovered(node.getLocation(), node.getNormal(), old.edge.getTrackMaterial())
                .forceNode());
      return;
    }
    var a = old.first.getLocation().getLocation();
    var b = old.second.getLocation().getLocation();
    var direction = b.subtract(a);
    double max = Math.max(Math.abs(direction.x), Math.abs(direction.z));
    if (max < 1e-8) return;
    var axis = direction.scale(1 / max);
    var center = Vec3.atBottomCenterOf(pos);
    double t = Math.clamp(center.subtract(a).dot(direction) / direction.lengthSqr(), 0, 1);
    center = a.add(direction.scale(t));
    if (t <= 1e-8 || t >= 1 - 1e-8) {
      seeds.add(
          discovered(old.first.getLocation(), old.first.getNormal(), old.edge.getTrackMaterial()));
      seeds.add(
          discovered(
              old.second.getLocation(), old.second.getNormal(), old.edge.getTrackMaterial()));
      return;
    }
    for (int sign : new int[] {-1, 1})
      seeds.add(
          new TrackNodeLocation.DiscoveredLocation(
                  old.first.getLocation().dimension, center.add(axis.scale(sign * .5)))
              .withNormal(old.first.getNormal())
              .withDirection(axis)
              .materials(old.edge.getTrackMaterial(), old.edge.getTrackMaterial()));
  }

  public static Result reconcile(
      ServerLevel world, BlockPos pos, BlockState formerState, boolean removed) {
    if (!world.getServer().isSameThread())
      throw new IllegalStateException("Native railway reconciliation requires the server thread");
    var manager = RailwayGraphManager.get(world);
    var request = new RailwaySavedData.PendingRail(
        GlobalPos.of(world.dimension(), pos.immutable()), formerState, removed);
    var active = RECONCILING.get();
    var previous = active.get(world.getServer());
    if (previous != null) {
      previous.reentered = true;
      manager.savedData().queue(request);
      return new Result(false, List.of(), Set.of(request.position()));
    }
    active.put(world.getServer(), new Attempt());
    try {
      if (!LoadedLevelAccess.isLoaded(world, pos)) return defer(world, request, Set.of(request.position()));
      Plan plan;
      try { plan = plan(world, pos, formerState, removed, manager); }
      catch (UnloadedTrackException pending) { return defer(world, request, pending.frontiers()); }
      catch (DeferredPlan pending) { return defer(world, request, pending.pending); }
      try {
        // Public merge callbacks run before any old edge/point is invalidated or disconnected.
        // Earlier completed merges remain genuine owners if a later callback changes the plan.
        plan = mergeGraphs(world, pos, formerState, removed, manager, plan, request.position());
        var result = commit(world, pos, manager, plan);
        manager.savedData().complete(request);
        return new Result(true, result, Set.of());
      } catch (DeferredPlan pending) {
        return defer(world, request, pending.pending);
      }
      // A listener's own RuntimeException/Error (including UnloadedTrackException) propagates.
      // It is never reported as a successful native frontier deferral.
    } finally {
      active.remove(world.getServer());
      if (active.isEmpty()) RECONCILING.remove();
    }
  }

  private static Result defer(ServerLevel world, RailwaySavedData.PendingRail request,
                              Collection<GlobalPos> pending) {
    var data = RailwayGraphManager.get(world).savedData();
    // A callback may have queued a newer remove/shape edit for this same real cell.
    // Retain that current request rather than overwriting it with the outer former state.
    if (data.pending().stream().noneMatch(current -> current.position().equals(request.position())))
      data.queue(request);
    return new Result(false, List.of(), Set.copyOf(pending));
  }

  private static Plan plan(ServerLevel world, BlockPos pos, BlockState formerState,
                           boolean removed, RailwayGraphManager manager) {
    var old = oldEdges(manager, world, pos);
    var seeds = new ArrayList<TrackNodeLocation.DiscoveredLocation>();
    var current = LoadedLevelAccess.blockState(world, pos);
    if (current.getBlock() instanceof ITrackBlock track)
      seeds.addAll(track.getConnected(world, pos, current, false, null));
    if (removed && formerState.getBlock() instanceof ITrackBlock track)
      seeds.addAll(track.getConnected(world, pos, formerState, false, null));
    for (var edge : old) oldSeeds(edge, pos, seeds);
    var vertices = discover(world, pos, seeds, manager, old);
    var selected = selectNodes(vertices);
    var planned = compress(vertices, selected);
    var result = new Plan(old, vertices, selected, planned);
    var witness = witness(world, manager, result);
    preflight(world, manager, old, selected, planned);
    var changed = GlobalPos.of(world.dimension(), pos.immutable());
    if (!witness.matches(world)) throw new DeferredPlan(Set.of(changed));
    requireNoReentry(world, changed);
    return result;
  }

  private static TrackReconciliationSnapshot witness(ServerLevel world,
      RailwayGraphManager manager, Plan plan) {
    try { return TrackReconciliationSnapshot.capture(world, manager, plan.vertices.keySet()); }
    catch (UnloadedTrackException pending) { throw new DeferredPlan(pending.frontiers()); }
  }

  private static void verifyCallback(ServerLevel world, RailwayGraphManager manager, Plan plan,
      TrackReconciliationSnapshot witness, GlobalPos changed) {
    try {
      requireNoReentry(world, changed);
      if (!witness.matches(world)) throw new DeferredPlan(Set.of(changed));
      // Actual current points may declare different native owners after an extension callback.
      // Recheck those owners and all merge/geometry conflicts before moving or dropping anything.
      preflight(world, manager, plan.old, plan.selected, plan.edges);
      if (!witness.matches(world)) throw new DeferredPlan(Set.of(changed));
      requireNoReentry(world, changed);
    } catch (UnloadedTrackException pending) { throw new DeferredPlan(pending.frontiers()); }
  }

  private static void requireNoReentry(ServerLevel world, GlobalPos changed) {
    var attempt = RECONCILING.get().get(world.getServer());
    // Native geometry/point-owner getters invoked by validation are extension points too.
    // Their queued edits, including distant cells outside the footprint, precede this transfer.
    if (attempt != null && attempt.reentered) throw new DeferredPlan(Set.of(changed));
  }

  private static Plan mergeGraphs(ServerLevel world, BlockPos changed, BlockState formerState,
      boolean removed, RailwayGraphManager manager, Plan plan, GlobalPos request) {
    while (true) {
      TrackGraph from = null, into = null;
      for (var component : components(plan.selected, plan.edges)) {
        var existing = new LinkedHashSet<TrackGraph>();
        for (var at : component) existing.addAll(manager.getGraphs(world, at));
        var ordered = existing.stream().sorted(Comparator.comparing(graph -> graph.id.toString())).toList();
        if (ordered.size() > 1) { into = ordered.getFirst(); from = ordered.get(1); break; }
      }
      if (from == null) return plan;
      var witness = witness(world, manager, plan);
      requireNoReentry(world, request);
      TrackGraphMergeEvent.EVENT.invoker().onMerge(new TrackGraphMergeEvent(from, into));
      verifyCallback(world, manager, plan, witness, request);
      // Source timing: the event names these exact still-owned objects before transferAll.
      from.transferAll(into);
      if (RailwayGraphManager.get(world) != manager
          || manager.getTrackNetworks().get(from.id) != from || from.manager() != manager
          || manager.getTrackNetworks().get(into.id) != into || into.manager() != manager
          || !from.isEmpty()) throw new DeferredPlan(Set.of(request));
      manager.removeGraph(from);
      try { plan = plan(world, changed, formerState, removed, manager); }
      catch (UnloadedTrackException pending) { throw new DeferredPlan(pending.frontiers()); }
    }
  }

  private static Map<TrackNodeLocation, Vertex> discover(
      ServerLevel world,
      BlockPos changed,
      List<TrackNodeLocation.DiscoveredLocation> seeds,
      RailwayGraphManager manager,
      Collection<OldEdge> changedEdges) {
    var portalEnds = new HashSet<TrackNodeLocation>();
    for (var edge : changedEdges) if (edge.edge.isInterDimensional()) {
      portalEnds.add(edge.first.getLocation());
      portalEnds.add(edge.second.getLocation());
    }
    var vertices = new LinkedHashMap<TrackNodeLocation, Vertex>();
    var queue = new ArrayDeque<TrackNodeLocation>();
    var walked = new HashSet<TrackNodeLocation>();
    for (var seed : seeds)
      if (vertices.putIfAbsent(seed.copy(), new Vertex(seed)) == null) queue.add(seed.copy());
    while (!queue.isEmpty()) {
      var key = queue.remove();
      if (!walked.add(key)) continue;
      if (walked.size() > MAX_DISCOVERY)
        throw new IllegalStateException("Railway local discovery exceeds source work budget");
      var vertex = vertices.get(key);
      var known = manager.getGraph(world, key);
      boolean affected =
          Objects.equals(key.dimension, world.dimension()) && key.allAdjacent().contains(changed)
              || portalEnds.contains(key);
      if (known != null && !affected) {
        vertex.boundary = true;
        continue;
      }
      var outgoing = ITrackBlock.walkConnectedTracks(world, key, false);
      for (var next : outgoing) {
        var nextKey = next.copy();
        var neighbor = vertices.get(nextKey);
        if (neighbor == null) {
          neighbor = new Vertex(next);
          vertices.put(nextKey, neighbor);
        } else if (next.shouldForceNode()) neighbor.location.forceNode();
        var curve = orient(next.getTurn(), key);
        var material = curve == null ? next.getMaterialA() : curve.getMaterial();
        var forward = new Link(key, nextKey, curve, material);
        var reverse = new Link(nextKey, key, curve == null ? null : curve.secondary(), material);
        var previous = vertex.links.putIfAbsent(nextKey, forward);
        if (previous != null
            && ((previous.turn == null) != (curve == null)
                || previous.material != material
                || curve != null && !curve.equalsSansMaterial(previous.turn)))
          throw new IllegalArgumentException("Ambiguous physical railway link at " + key);
        neighbor.links.putIfAbsent(key, reverse);
        if (curve != null) {
          vertex.location.forceNode();
          neighbor.location.forceNode();
        }
        if (!walked.contains(nextKey)) queue.add(nextKey);
      }
    }
    return vertices;
  }

  private static boolean sourceChunkBoundary(TrackNodeLocation location) {
    var vector = location.getLocation();
    boolean centeredX = !Mth.equal(vector.x, Math.round(vector.x)),
        centeredZ = !Mth.equal(vector.z, Math.round(vector.z));
    return centeredX && !centeredZ
        ? Math.round(vector.z) % 16 == 0
        : Math.round(vector.x) % 16 == 0;
  }

  public static boolean isValidGraphNodeLocation(
      TrackNodeLocation.DiscoveredLocation location,
      Collection<TrackNodeLocation.DiscoveredLocation> next,
      boolean first) {
    int count = next.size() - (first ? 1 : 0);
    return count != 1
        || location.shouldForceNode()
        || location.differentMaterials()
        || next.stream().anyMatch(TrackNodeLocation.DiscoveredLocation::shouldForceNode)
        || location.getDirection() != null
            && next.stream().anyMatch(value -> value.notInLineWith(location.getDirection()))
        || sourceChunkBoundary(location);
  }

  private static Set<TrackNodeLocation> selectNodes(Map<TrackNodeLocation, Vertex> vertices) {
    var selected = new LinkedHashSet<TrackNodeLocation>();
    for (var entry : vertices.entrySet()) {
      var vertex = entry.getValue();
      if (vertex.links.isEmpty()) {
        if (vertex.boundary) selected.add(entry.getKey());
        continue;
      }
      boolean select =
          vertex.boundary
              || vertex.links.size() != 2
              || vertex.location.shouldForceNode()
              || vertex.location.differentMaterials()
              || sourceChunkBoundary(vertex.location);
      if (!select) {
        var links = List.copyOf(vertex.links.values());
        var a = links.get(0);
        var b = links.get(1);
        var da = a.to.getLocation().subtract(entry.getKey().getLocation()).normalize();
        var db = b.to.getLocation().subtract(entry.getKey().getLocation()).normalize();
        select =
            a.material != b.material
                || !Objects.equals(a.to.dimension, b.to.dimension)
                || da.dot(db) > -7 / 8f;
      }
      if (select) selected.add(entry.getKey());
    }
    // A closed degree-two component needs at least three anchors: two would collapse its two
    // distinct arcs into one adjacency entry.
    var seen = new HashSet<TrackNodeLocation>();
    for (var start : vertices.keySet())
      if (seen.add(start)) {
        var component = new ArrayList<TrackNodeLocation>();
        var queue = new ArrayDeque<TrackNodeLocation>();
        queue.add(start);
        while (!queue.isEmpty()) {
          var key = queue.remove();
          component.add(key);
          for (var next : vertices.get(key).links.keySet()) if (seen.add(next)) queue.add(next);
        }
        if (component.size() > 2
            && component.stream().allMatch(key -> vertices.get(key).links.size() == 2)) {
          int required = 3 - (int) component.stream().filter(selected::contains).count();
          for (var key : component) if (required > 0 && selected.add(key)) required--;
        } else if (component.size() > 1 && component.stream().noneMatch(selected::contains)) {
          selected.add(component.getFirst());
          selected.add(component.get(component.size() / 2));
        }
      }
    return selected;
  }

  private static List<Planned> compress(
      Map<TrackNodeLocation, Vertex> vertices, Set<TrackNodeLocation> selected) {
    var result = new ArrayList<Planned>();
    var done = new HashSet<String>();
    for (var from : selected)
      for (var first : vertices.get(from).links.values()) {
        var prior = from;
        var current = first.to;
        var curve = first.turn;
        var material = first.material;
        var traversed = new HashSet<TrackNodeLocation>();
        while (!selected.contains(current)) {
          if (!traversed.add(current))
            throw new IllegalArgumentException("Unanchored railway cycle");
          var previous = prior;
          var link =
              vertices.get(current).links.values().stream()
                  .filter(value -> !value.to.equals(previous))
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("Broken physical railway path"));
          if (link.turn != null || link.material != material)
            throw new IllegalArgumentException("Curve/material boundary not selected as node");
          prior = current;
          current = link.to;
        }
        if (current.equals(from))
          throw new IllegalArgumentException(
              "Self-loop railway compression requires another anchor");
        var pair = List.of(key(from), key(current)).stream().sorted().toList();
        if (done.add(pair.getFirst() + "|" + pair.getLast()))
          result.add(new Planned(from, current, curve, material));
      }
    return result;
  }

  private static String key(TrackNodeLocation location) {
    return location.dimension.identifier()
        + ":"
        + location.getX()
        + ","
        + location.getY()
        + ","
        + location.getZ()
        + ","
        + location.yOffsetPixels;
  }

  private static boolean same(OldEdge old, Planned plan) {
    boolean endpoints =
        old.first.getLocation().equals(plan.from) && old.second.getLocation().equals(plan.to)
            || old.first.getLocation().equals(plan.to)
                && old.second.getLocation().equals(plan.from);
    return endpoints
        && old.edge.getTrackMaterial() == plan.material
        && (plan.turn == null
            ? !old.edge.isTurn()
            : old.edge.isTurn() && plan.turn.equalsSansMaterial(old.edge.getTurn()));
  }

  private static List<Set<TrackNodeLocation>> components(
      Set<TrackNodeLocation> selected, List<Planned> planned) {
    var result = new ArrayList<Set<TrackNodeLocation>>();
    var seen = new HashSet<TrackNodeLocation>();
    for (var start : selected)
      if (seen.add(start)) {
        var component = new LinkedHashSet<TrackNodeLocation>();
        var queue = new ArrayDeque<TrackNodeLocation>();
        queue.add(start);
        while (!queue.isEmpty()) {
          var at = queue.remove();
          component.add(at);
          for (var plan : planned) {
            var next = plan.from.equals(at) ? plan.to : plan.to.equals(at) ? plan.from : null;
            if (next != null && seen.add(next)) queue.add(next);
          }
        }
        result.add(component);
      }
    return result;
  }

  private static void preflight(
      ServerLevel world,
      RailwayGraphManager manager,
      List<OldEdge> old,
      Set<TrackNodeLocation> selected,
      List<Planned> planned) {
    for (var edge : old) {
      edge.graph.validate();
      if (planned.stream().noneMatch(plan -> same(edge, plan)))
        for (var point : edge.edge.getEdgeData().getPoints()) point.preflightInvalidation(world);
    }
    // One cached graph can bound several local physical components. Union their future merge
    // groups before checking conflicts so a later A+B then B+C merge cannot discover A/C
    // ownership collisions after A+B has already moved real nodes.
    var groups = new ArrayList<Set<TrackGraph>>();
    for (var component : components(selected, planned)) {
      var existing = new LinkedHashSet<TrackGraph>();
      for (var at : component) existing.addAll(manager.getGraphs(world, at));
      if (existing.isEmpty()) continue;
      boolean combined;
      do {
        combined = false;
        for (var group : List.copyOf(groups))
          if (group.stream().anyMatch(existing::contains)) {
            existing.addAll(group);
            groups.remove(group);
            combined = true;
          }
      } while (combined);
      groups.add(existing);
    }
    for (var group : groups) {
      var graphs = List.copyOf(group);
      for (int a = 0; a < graphs.size(); a++)
        for (int b = a + 1; b < graphs.size(); b++) graphs.get(a).preflightMerge(graphs.get(b));
    }
    // Every replacement must have been discovered as old geometry through the edited cell.
    for (var plan : planned)
      for (var graph : manager.getTrackNetworks().values()) {
        var first = graph.locateNode(plan.from);
        var second = graph.locateNode(plan.to);
        if (first == null || second == null) continue;
        var edge = graph.getConnectionsFrom(first).get(second);
        if (edge == null) continue;
        var existing = new OldEdge(graph, first, second, edge);
        if (!same(existing, plan)
            && old.stream()
                .noneMatch(
                    value ->
                        value.edge == edge
                            || value.graph == graph
                                && value.first == second
                                && value.second == first))
          throw new IllegalArgumentException(
              "Railway edit would replace geometry outside its discovered region");
      }
  }

  private static List<TrackGraph> commit(
      ServerLevel world,
      BlockPos changed,
      RailwayGraphManager manager,
      Plan prepared) {
    var old = prepared.old;
    var vertices = prepared.vertices;
    var selected = prepared.selected;
    var planned = prepared.edges;
    var position = GlobalPos.of(world.dimension(), changed.immutable());
    // All public merge callbacks and their native revalidation finished before edge deletion.
    var affectedGraphs = new LinkedHashSet<TrackGraph>();
    for (var edge : old) affectedGraphs.add(edge.graph);
    for (var key : selected) affectedGraphs.addAll(manager.getGraphs(world, key));
    for (var edge : old)
      if (planned.stream().noneMatch(plan -> same(edge, plan))) {
        for (var point : List.copyOf(edge.edge.getEdgeData().getPoints())) {
          var witness = witness(world, manager, prepared);
          requireNoReentry(world, position);
          point.invalidate(world);
          verifyCallback(world, manager, prepared, witness, position);
        }
        if (manager.getTrackNetworks().get(edge.graph.id) != edge.graph
            || edge.graph.manager() != manager
            || edge.graph.locateNode(edge.first.getLocation()) != edge.first
            || edge.graph.locateNode(edge.second.getLocation()) != edge.second
            || edge.graph.getConnectionsFrom(edge.first).get(edge.second) != edge.edge)
          throw new DeferredPlan(Set.of(position));
        edge.graph.disconnectNodes(edge.first, edge.second);
      }
    var graphFor = new HashMap<TrackNodeLocation, TrackGraph>();
    var outputs = new LinkedHashSet<TrackGraph>();
    for (var component : components(selected, planned)) {
      var existing = new LinkedHashSet<TrackGraph>();
      for (var at : component) existing.addAll(manager.getGraphs(world, at));
      var target =
          existing.stream()
              .sorted(Comparator.comparing(graph -> graph.id.toString()))
              .findFirst()
              .orElseGet(TrackGraph::new);
      if (!manager.getTrackNetworks().containsKey(target.id)) manager.putGraph(target);
      for (var other : existing)
        if (other != target) {
          // A lifecycle callback changed component ownership after the merge phase.
          // Never perform an unannounced transfer from a stale plan.
          throw new DeferredPlan(Set.of(position));
        }
      for (var at : component) {
        graphFor.put(at, target);
        target.createNodeIfAbsent(vertices.get(at).location);
      }
      outputs.add(target);
      affectedGraphs.add(target);
    }
    for (var plan : planned) {
      var graph = graphFor.get(plan.from);
      graph.connectNodes(
          graph.locateNode(plan.from), graph.locateNode(plan.to), plan.turn, plan.material);
    }
    for (var graph : List.copyOf(affectedGraphs)) {
      for (var location : List.copyOf(graph.getNodes()))
        if (Objects.equals(location.dimension, world.dimension())
            && location.allAdjacent().contains(changed)
            && !selected.contains(location)
            && graph.getConnectionsFrom(graph.locateNode(location)).isEmpty())
          graph.removeNode(world, location);
      outputs.addAll(manager.updateSplitGraph(world, graph));
    }
    return outputs.stream()
        .filter(graph -> manager.getTrackNetworks().get(graph.id) == graph)
        .toList();
  }

  private TrackPropagator() {}
}
