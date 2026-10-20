package com.simibubi.create.content.trains;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.*;

/**
 * Actual registry-aware graph SavedData. It owns topology and pending loaded-only reconciliation,
 * not a placeholder train/cargo registry. Native point codecs receive the original RegistryOps.
 */
public final class RailwaySavedData extends SavedData {
  public record PendingRail(GlobalPos position, BlockState formerState, boolean removed) {
    public static final Codec<PendingRail> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        GlobalPos.CODEC.fieldOf("Position").forGetter(PendingRail::position),
                        BlockState.CODEC.fieldOf("FormerState").forGetter(PendingRail::formerState),
                        Codec.BOOL.fieldOf("Removed").forGetter(PendingRail::removed))
                    .apply(i, PendingRail::new));
  }

  private record Data(int version, List<TrackGraph> graphs, List<PendingRail> pending) {
    static final Codec<Data> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Codec.intRange(1, 1).fieldOf("GraphFormatVersion").forGetter(Data::version),
                        TrackGraph.CODEC.listOf().fieldOf("RailGraphs").forGetter(Data::graphs),
                        PendingRail.CODEC
                            .listOf()
                            .optionalFieldOf("PendingRails", List.of())
                            .forGetter(Data::pending))
                    .apply(i, Data::new));
  }

  public static final Codec<RailwaySavedData> CODEC =
      Data.CODEC.flatXmap(
          value -> {
            try {
              return DataResult.success(new RailwaySavedData(value));
            } catch (IllegalArgumentException invalid) {
              return DataResult.error(invalid::getMessage);
            }
          },
          value ->
              DataResult.success(
                  new Data(
                      1, List.copyOf(value.trackNetworks.values()), List.copyOf(value.pending))));
  public static final SavedDataType<RailwaySavedData> TYPE =
      new SavedDataType<>(Create.asResource("tracks"), RailwaySavedData::new, CODEC, null);
  private final Map<UUID, TrackGraph> trackNetworks = new LinkedHashMap<>();
  private final LinkedHashSet<PendingRail> pending = new LinkedHashSet<>();

  public RailwaySavedData() {}

  private RailwaySavedData(Data data) {
    for (var graph : data.graphs)
      if (trackNetworks.putIfAbsent(graph.id, graph) != null)
        throw new IllegalArgumentException("Duplicate persistent railway graph UUID");
    var positions = new HashSet<GlobalPos>();
    for (var request : data.pending)
      if (!positions.add(request.position))
        throw new IllegalArgumentException("Duplicate pending railway position");
    pending.addAll(data.pending);
  }

  public Map<UUID, TrackGraph> getTrackNetworks() {
    return Collections.unmodifiableMap(trackNetworks);
  }

  Map<UUID, TrackGraph> mutableTrackNetworks() {
    return trackNetworks;
  }

  public List<PendingRail> pending() {
    return List.copyOf(pending);
  }

  public void queue(PendingRail request) {
    if (pending.contains(request)) return;
    pending.removeIf(old -> old.position.equals(request.position));
    pending.add(request);
    setDirty();
  }

  public void complete(PendingRail request) {
    if (pending.remove(request)) setDirty();
  }

  public static RailwaySavedData load(MinecraftServer server) {
    return NeoForgeRailwayImport.load(server).first();
  }
}
