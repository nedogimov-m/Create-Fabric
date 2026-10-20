package com.simibubi.create.content.trains.graph.sync;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

/**
 * Full actual graph/point codec plus source transport IDs, which persistence intentionally
 * rebuilds.
 */
public record GraphSyncEntry(TrackGraph graph, int netId, List<NodeId> nodeIds) {
  public record NodeId(TrackNodeLocation location, int id) {
    public static final Codec<NodeId> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        TrackNodeLocation.CODEC.fieldOf("Location").forGetter(NodeId::location),
                        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("Id").forGetter(NodeId::id))
                    .apply(i, NodeId::new));

    public NodeId {
      location = location.copy();
      if (id <= 0) throw new IllegalArgumentException("Invalid railway node transport id");
    }

    @Override
    public TrackNodeLocation location() {
      return location.copy();
    }
  }

  private static final Codec<GraphSyncEntry> DATA =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      TrackGraph.TRANSPORT_CODEC.fieldOf("Graph").forGetter(GraphSyncEntry::graph),
                      Codec.intRange(1, Integer.MAX_VALUE)
                          .fieldOf("NetId")
                          .forGetter(GraphSyncEntry::netId),
                      NodeId.CODEC.listOf().fieldOf("NodeIds").forGetter(GraphSyncEntry::nodeIds))
                  .apply(i, GraphSyncEntry::new));
  public static final Codec<GraphSyncEntry> CODEC =
      DATA.flatXmap(
          entry -> {
            try {
              entry.graph.restoreTransportIdentity(entry.netId, entry.identityMap());
              return DataResult.success(entry);
            } catch (IllegalArgumentException | IllegalStateException failure) {
              return DataResult.error(failure::getMessage);
            }
          },
          DataResult::success);

  public GraphSyncEntry {
    Objects.requireNonNull(graph);
    nodeIds = List.copyOf(nodeIds);
    if (netId <= 0) throw new IllegalArgumentException("Invalid railway graph transport id");
    var locations = new HashSet<TrackNodeLocation>();
    var ids = new HashSet<Integer>();
    for (var node : nodeIds)
      if (!locations.add(node.location) || !ids.add(node.id))
        throw new IllegalArgumentException("Duplicate railway transport identity");
    if (!locations.equals(graph.getNodes()))
      throw new IllegalArgumentException("Incomplete railway transport identity map");
    graph.validate();
  }

  public Map<TrackNodeLocation, Integer> identityMap() {
    var result = new LinkedHashMap<TrackNodeLocation, Integer>();
    for (var node : nodeIds) result.put(node.location(), node.id);
    return Collections.unmodifiableMap(result);
  }

  public static GraphSyncEntry capture(TrackGraph graph) {
    var nodes =
        graph.getNodes().stream()
            .map(graph::locateNode)
            .sorted(Comparator.comparingInt(TrackNode::getNetId))
            .map(node -> new NodeId(node.getLocation(), node.getNetId()))
            .toList();
    return new GraphSyncEntry(graph, graph.getNetId(), nodes);
  }

  public CompoundTag tag(HolderLookup.Provider registries) {
    return (CompoundTag)
        CODEC
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this)
            .getOrThrow();
  }
}
