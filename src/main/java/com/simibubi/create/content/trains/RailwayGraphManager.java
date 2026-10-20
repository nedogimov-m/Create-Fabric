package com.simibubi.create.content.trains;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.TrackPropagator;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.*;

/**
 * Server-owned real graph registry, persistence, merge/split custody and deferred native discovery.
 */
public final class RailwayGraphManager {
  public enum Kind {
    CHANGED,
    REMOVED,
    MERGED,
    SPLIT,
    NODE_REMOVED,
    INTERSECTION_CHANGED
  }

  public record Mutation(
      Kind kind, UUID graph, UUID other, TrackNodeLocation node, UUID crossing, long revision) {
    public Mutation {
      node = node == null ? null : node.copy();
    }

    @Override
    public TrackNodeLocation node() {
      return node == null ? null : node.copy();
    }
  }

  private static final Map<MinecraftServer, RailwayGraphManager> SERVERS =
      Collections.synchronizedMap(new WeakHashMap<>());
  private final RailwaySavedData data;
  private final MinecraftServer server;
  private final List<Consumer<Mutation>> listeners = new ArrayList<>();
  private final List<Mutation> mutations = new ArrayList<>();
  private final Set<UUID> changed = new LinkedHashSet<>();
  private boolean ticking;

  public RailwayGraphManager(RailwaySavedData data){this(data,null);}

  private RailwayGraphManager(RailwaySavedData data,MinecraftServer server){
    this.data = Objects.requireNonNull(data);
    this.server=server;
    for (var graph : data.getTrackNetworks().values()) graph.bind(this);
  }

  public static RailwayGraphManager get(MinecraftServer server) {
    return SERVERS.computeIfAbsent(
        server, key -> new RailwayGraphManager(RailwaySavedData.load(key),key));
  }

  public static RailwayGraphManager get(LevelAccessor level) {
    if (!(level instanceof Level world) || world.getServer() == null)
      throw new IllegalArgumentException("Railway graph manager requires a native server level");
    return get(world.getServer());
  }

  public static void unload(MinecraftServer server) {
    SERVERS.remove(server);
  }

  public static void register() {
    ServerTickEvents.END_SERVER_TICK.register(server -> get(server).tick(server));
    ServerLifecycleEvents.SERVER_STOPPED.register(RailwayGraphManager::unload);
  }

  public MinecraftServer server(){return server;}

  public RailwaySavedData savedData() {
    return data;
  }

  public Map<UUID, TrackGraph> getTrackNetworks() {
    return Collections.unmodifiableMap(data.getTrackNetworks());
  }

  public TrackGraph getGraph(LevelAccessor level, TrackNodeLocation location) {
    for (var graph : data.getTrackNetworks().values())
      if (graph.locateNode(location) != null) return graph;
    return null;
  }

  public List<TrackGraph> getGraphs(LevelAccessor level, TrackNodeLocation location) {
    return data.getTrackNetworks().values().stream()
        .filter(graph -> graph.locateNode(location) != null)
        .toList();
  }

  public void putGraph(TrackGraph graph) {
    if (graph.manager() != null && graph.manager() != this)
      throw new IllegalArgumentException("Railway graph belongs to another server manager");
    var old = data.mutableTrackNetworks().putIfAbsent(graph.id, graph);
    if (old != null && old != graph)
      throw new IllegalArgumentException("Another railway graph owns UUID " + graph.id);
    graph.bind(this);
    data.setDirty();
    changed.add(graph.id);
  }

  public void removeGraph(TrackGraph graph) {
    if (data.mutableTrackNetworks().remove(graph.id, graph)) {
      graph.bind(null);
      mutations.add(new Mutation(Kind.REMOVED, graph.id, null, null, null, graph.revision()));
      data.setDirty();
    }
  }

  public void graphChanged(TrackGraph graph) {
    changed.add(graph.id);
    data.setDirty();
  }

  public void nodeRemoved(TrackGraph graph, TrackNode node) {
    mutations.add(
        new Mutation(
            Kind.NODE_REMOVED, graph.id, null, node.getLocation(), null, graph.revision()));
  }

  public void graphMerged(TrackGraph from, TrackGraph target) {
    mutations.add(new Mutation(Kind.MERGED, from.id, target.id, null, null, target.revision()));
    data.setDirty();
  }

  public void graphSplit(TrackGraph from, TrackGraph target) {
    putGraph(target);
    mutations.add(new Mutation(Kind.SPLIT, from.id, target.id, null, null, target.revision()));
  }

  public void intersectionChanged(TrackGraph graph, TrackEdgeIntersection crossing) {
    mutations.add(
        new Mutation(
            Kind.INTERSECTION_CHANGED, graph.id, null, null, crossing.id, graph.revision()));
  }

  public void addListener(Consumer<Mutation> listener) {
    listeners.add(Objects.requireNonNull(listener));
  }

  public void removeListener(Consumer<Mutation> listener) {
    listeners.remove(listener);
  }

  public Set<TrackGraph> updateSplitGraph(LevelAccessor level, TrackGraph graph) {
    var result = new LinkedHashSet<TrackGraph>();
    for (var split : graph.findDisconnectedGraphs(level, null)) {
      putGraph(split);
      result.add(split);
    }
    if (graph.isEmpty()) removeGraph(graph);
    else result.add(graph);
    return Collections.unmodifiableSet(result);
  }

  public void tick(MinecraftServer server) {
    if (ticking) return;
    ticking = true;
    try {
      for (var request : data.pending()) {
        var level = server.getLevel(request.position().dimension());
        if (level == null) continue;
        TrackPropagator.reconcile(
            level, request.position().pos(), request.formerState(), request.removed());
      }
      if(this.server!=null&&this.server!=server)throw new IllegalArgumentException("Foreign native server graph tick");
      flushMutations();
      var traffic=this.server==null?null:TrainManager.get(server);
      for(var graph:List.copyOf(data.getTrackNetworks().values()))graph.tickPoints(true);
      for(var graph:List.copyOf(data.getTrackNetworks().values()))graph.resolveIntersectingEdgeGroups(server.overworld());
      if(traffic!=null){traffic.refreshGroups();traffic.beginTick();traffic.tickTrains();}
      for(var graph:List.copyOf(data.getTrackNetworks().values()))graph.tickPoints(false);
      flushMutations();
    } finally {
      ticking = false;
    }
  }

  public void flushMutations() {
    for (var id : List.copyOf(changed)) {
      var graph = data.getTrackNetworks().get(id);
      if (graph != null) {
        graph.validate();
        mutations.add(new Mutation(Kind.CHANGED, id, null, null, null, graph.revision()));
      }
    }
    changed.clear();
    var ready = List.copyOf(mutations);
    mutations.clear();
    for (var mutation : ready)
      for (var listener : List.copyOf(listeners)) listener.accept(mutation);
  }
}
