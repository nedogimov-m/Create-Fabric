package com.simibubi.create.content.trains.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;

/**
 * Ordered real edge points, source fallback group identity and reciprocal geometric intersections.
 */
public final class EdgeData {
  public static final UUID passiveGroup = new UUID(0, 0);

  public record PointRef(Identifier type, UUID id) {
    public static final Codec<PointRef> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Identifier.CODEC.fieldOf("Type").forGetter(PointRef::type),
                        UUIDUtil.CODEC.fieldOf("Id").forGetter(PointRef::id))
                    .apply(i, PointRef::new));
  }

  public record Snapshot(
      Optional<UUID> group, List<PointRef> points, List<TrackEdgeIntersection> intersections) {
    public Snapshot {
      points = List.copyOf(points);
      intersections = List.copyOf(intersections);
    }

    public static final Codec<Snapshot> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        UUIDUtil.CODEC.optionalFieldOf("SignalGroup").forGetter(Snapshot::group),
                        PointRef.CODEC
                            .listOf()
                            .optionalFieldOf("Points", List.of())
                            .forGetter(Snapshot::points),
                        TrackEdgeIntersection.CODEC
                            .listOf()
                            .optionalFieldOf("Intersections", List.of())
                            .forGetter(Snapshot::intersections))
                    .apply(i, Snapshot::new));
  }

  private UUID singleSignalGroup = passiveGroup;
  private final List<TrackEdgePoint> points = new ArrayList<>();
  private final List<TrackEdgeIntersection> intersections = new ArrayList<>();
  private final TrackEdge edge;

  public EdgeData(TrackEdge edge) {
    this.edge = Objects.requireNonNull(edge);
  }

  public boolean hasSignalBoundaries() {
    return singleSignalGroup == null;
  }

  public UUID getSingleSignalGroup() {
    return singleSignalGroup;
  }

  public void setSingleSignalGroup(TrackGraph graph, UUID group) {
    if (Objects.equals(singleSignalGroup, group)) return;
    singleSignalGroup = group;
    if (graph != null) {
      refreshIntersectingSignalGroups(graph);
      graph.markDirty();
    }
  }

  public void refreshIntersectingSignalGroups(TrackGraph graph) {
    for (var intersection : intersections) intersection.groupId = null;
    graph.deferIntersectionUpdate(edge);
  }

  public boolean hasPoints() {
    return !points.isEmpty();
  }

  public boolean hasIntersections() {
    return !intersections.isEmpty();
  }

  public List<TrackEdgeIntersection> getIntersections() {
    return Collections.unmodifiableList(intersections);
  }

  public List<TrackEdgePoint> getPoints() {
    return Collections.unmodifiableList(points);
  }

  public void addIntersection(
      TrackGraph graph,
      UUID id,
      double position,
      TrackNode target1,
      TrackNode target2,
      double targetPosition) {
    if (!Double.isFinite(position) || !Double.isFinite(targetPosition))
      throw new IllegalArgumentException("Nonfinite railway intersection");
    for (var value : intersections)
      if (value.isNear(position) && value.targets(target1.getLocation(), target2.getLocation()))
        return;
    var value = new TrackEdgeIntersection();
    value.id = id;
    value.location = position;
    value.target = Couple.create(target1.getLocation(), target2.getLocation());
    value.targetLocation = targetPosition;
    intersections.add(value);
    graph.deferIntersectionUpdate(edge);
    graph.markDirty();
  }

  public void removeIntersection(TrackGraph graph, UUID id) {
    if (intersections.removeIf(value -> value.id.equals(id))) {
      refreshIntersectingSignalGroups(graph);
      graph.markDirty();
    }
  }

  public UUID getEffectiveEdgeGroupId(TrackGraph graph) {
    return singleSignalGroup == null
        ? null
        : passiveGroup.equals(singleSignalGroup) ? graph.id : singleSignalGroup;
  }

  public UUID getGroupAtPosition(TrackGraph graph, double position) {
    if (!hasSignalBoundaries()) return getEffectiveEdgeGroupId(graph);
    UUID group = null;
    for (var point : points)
      if (point instanceof TrackEdgePoint.SignalBoundaryPoint boundary) {
        if (group == null) group = boundary.groupFor(edge.node1);
        if (point.getLocationOn(edge) >= position) return group;
        group = boundary.groupFor(edge.node2);
      }
    return group;
  }

  public void removePoint(TrackGraph graph, TrackEdgePoint point) {
    if (!points.remove(point)) return;
    if (point instanceof TrackEdgePoint.SignalBoundaryPoint)
      setSingleSignalGroup(
          graph,
          points.stream().noneMatch(value -> value instanceof TrackEdgePoint.SignalBoundaryPoint)
              ? passiveGroup
              : null);
  }

  public void addPoint(TrackGraph graph, TrackEdgePoint point) {
    if (points.contains(point)) return;
    if (point instanceof TrackEdgePoint.SignalBoundaryPoint) setSingleSignalGroup(graph, null);
    points.add(point);
    points.sort(Comparator.comparingDouble(value -> value.getLocationOn(edge)));
  }

  @SuppressWarnings("unchecked")
  public <T extends TrackEdgePoint> T next(EdgePointType<T> type, double min) {
    for (var point : points)
      if (point.getType() == type && point.getLocationOn(edge) > min) return (T) point;
    return null;
  }

  public TrackEdgePoint next(double min) {
    for (var point : points) if (point.getLocationOn(edge) > min) return point;
    return null;
  }

  public <T extends TrackEdgePoint> T get(EdgePointType<T> type, double at) {
    var point = next(type, at - .5);
    return point != null && Math.abs(point.getLocationOn(edge) - at) < 1e-8 ? point : null;
  }

  public Snapshot snapshot() {
    return new Snapshot(
        Optional.ofNullable(singleSignalGroup),
        points.stream().map(point -> new PointRef(point.getType().getId(), point.id)).toList(),
        intersections);
  }

  public void restore(Snapshot snapshot, TrackGraph graph) {
    var decoded = new ArrayList<TrackEdgePoint>();
    for (var ref : snapshot.points) {
      var type = EdgePointType.TYPES.get(ref.type);
      if (type == null)
        throw new IllegalArgumentException("Unknown railway point type " + ref.type);
      var point = graph.getPoint(type, ref.id);
      if (point == null || decoded.contains(point))
        throw new IllegalArgumentException("Missing/duplicate railway point reference " + ref.id);
      decoded.add(point);
    }
    boolean boundaries =
        decoded.stream().anyMatch(point -> point instanceof TrackEdgePoint.SignalBoundaryPoint);
    if (snapshot.group.isEmpty() != boundaries)
      throw new IllegalArgumentException(
          "Railway group state disagrees with actual signal boundaries");
    var ids = new HashSet<UUID>();
    for (var crossing : snapshot.intersections)
      if (!ids.add(crossing.id)
          || crossing.location < 0
          || crossing.targetLocation < 0
          || crossing.target.getFirst().equals(crossing.target.getSecond()))
        throw new IllegalArgumentException("Invalid or duplicate railway intersection");
    singleSignalGroup = snapshot.group.orElse(null);
    points.clear();
    points.addAll(decoded);
    points.sort(Comparator.comparingDouble(point -> point.getLocationOn(edge)));
    intersections.clear();
    intersections.addAll(snapshot.intersections);
  }

  public CompoundTag write(DimensionPalette dimensions) {
    return (CompoundTag) Snapshot.CODEC.encodeStart(NbtOps.INSTANCE, snapshot()).getOrThrow();
  }

  public static EdgeData read(
      CompoundTag tag, TrackEdge edge, TrackGraph graph, DimensionPalette dimensions) {
    var result = new EdgeData(edge);
    result.restore(Snapshot.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(), graph);
    return result;
  }
}
