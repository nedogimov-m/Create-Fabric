package com.simibubi.create.content.trains.graph;

import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

/**
 * Actual typed points; graph edge lists reference these exact instances, never redecoded
 * duplicates.
 */
public final class EdgePointStorage {
  private final Map<EdgePointType<?>, Map<UUID, TrackEdgePoint>> pointsByType =
      new LinkedHashMap<>();

  public void put(EdgePointType<?> type, TrackEdgePoint point) {
    if (point.getType() != type) throw new IllegalArgumentException("Railway point type mismatch");
    var map = pointsByType.computeIfAbsent(type, key -> new LinkedHashMap<>());
    var previous = map.putIfAbsent(point.getId(), point);
    if (previous != null && previous != point)
      throw new IllegalArgumentException("Duplicate railway point id " + point.getId());
  }

  @SuppressWarnings("unchecked")
  public <T extends TrackEdgePoint> T get(EdgePointType<T> type, UUID id) {
    return (T) pointsByType.getOrDefault(type, Map.of()).get(id);
  }

  @SuppressWarnings("unchecked")
  public <T extends TrackEdgePoint> T remove(EdgePointType<T> type, UUID id) {
    var map = pointsByType.get(type);
    return map == null ? null : (T) map.remove(id);
  }

  @SuppressWarnings("unchecked")
  public <T extends TrackEdgePoint> Collection<T> values(EdgePointType<T> type) {
    return pointsByType.getOrDefault(type, Map.of()).values().stream()
        .map(point -> (T) point)
        .toList();
  }

  public Map<UUID, TrackEdgePoint> getMap(EdgePointType<?> type) {
    return Collections.unmodifiableMap(pointsByType.getOrDefault(type, Map.of()));
  }

  public List<TrackEdgePoint> all() {
    return pointsByType.values().stream().flatMap(map -> map.values().stream()).toList();
  }

  public void tick(TrackGraph graph, boolean preTrains) {
    for (var point : all())
      if (get(point.getType(), point.id) == point) point.tick(graph, preTrains);
  }

  public void transferAll(TrackGraph target, EdgePointStorage other) {
    for (var point : all()) {
      point.bind(null);
      other.put(point.getType(), point);
      point.bind(target);
    }
    pointsByType.clear();
  }

  public CompoundTag write(HolderLookup.Provider registries, DimensionPalette dimensions) {
    return (CompoundTag)
        TrackEdgePoint.CODEC
            .listOf()
            .fieldOf("Values")
            .codec()
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), all())
            .getOrThrow();
  }

  public void read(CompoundTag tag, HolderLookup.Provider registries, DimensionPalette dimensions) {
    var decoded =
        TrackEdgePoint.CODEC
            .listOf()
            .fieldOf("Values")
            .codec()
            .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
            .getOrThrow();
    var check = new EdgePointStorage();
    for (var point : decoded) check.put(point.getType(), point);
    pointsByType.clear();
    pointsByType.putAll(check.pointsByType);
  }
}
