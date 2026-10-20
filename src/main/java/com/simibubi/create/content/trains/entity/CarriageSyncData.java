package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.graph.RailCodecs;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;

/** Genuine source graph-point interpolation, with bounded complete native snapshots. */
public class CarriageSyncData {
  public static final StreamCodec<RegistryFriendlyByteBuf, CarriageSyncData> STREAM_CODEC =
      StreamCodec.ofMember(CarriageSyncData::write, CarriageSyncData::new);
  public static final EntityDataSerializer<CarriageSyncData> SERIALIZER =
      new EntityDataSerializer<>() {
        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CarriageSyncData> codec() {
          return STREAM_CODEC;
        }

        @Override
        public CarriageSyncData copy(CarriageSyncData value) {
          return value.copy();
        }
      };
  private static boolean registered;

  public static void register() {
    if (registered) return;
    FabricEntityDataRegistry.register(
        com.simibubi.create.Create.asResource("carriage_sync_data"), SERIALIZER);
    registered = true;
  }

  private static Vec3 readVector(FriendlyByteBuf buffer) {
    var value = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    if (!RailCodecs.finite(value))
      throw new IllegalArgumentException("Nonfinite actual carriage fallback vector");
    return value;
  }

  private static void writeVector(FriendlyByteBuf buffer, Vec3 value) {
    if (value == null || !RailCodecs.finite(value))
      throw new IllegalArgumentException("Missing actual carriage fallback vector");
    buffer.writeDouble(value.x);
    buffer.writeDouble(value.y);
    buffer.writeDouble(value.z);
  }

  public List<Pair<Couple<Integer>, Double>> wheelLocations;
  public Pair<Vec3, Couple<Vec3>> fallbackLocations;
  public float distanceToDestination;
  public boolean leadingCarriage;

  // For Client interpolation
  private Pair<Vec3, Couple<Vec3>> fallbackPointSnapshot;
  private TravellingPoint[] pointsToApproach;
  private float[] pointDistanceSnapshot;
  private float destinationDistanceSnapshot;
  private int ticksSince;

  public CarriageSyncData() {
    wheelLocations = new ArrayList<>(4);
    fallbackLocations = null;
    pointDistanceSnapshot = new float[4];
    pointsToApproach = new TravellingPoint[4];
    fallbackPointSnapshot = null;
    destinationDistanceSnapshot = 0;
    leadingCarriage = false;
    ticksSince = 0;
    for (int i = 0; i < 4; i++) {
      wheelLocations.add(null);
      pointsToApproach[i] = new TravellingPoint();
    }
  }

  public CarriageSyncData(FriendlyByteBuf buf) {
    this();
    read(buf);
  }

  public CarriageSyncData copy() {
    CarriageSyncData data = new CarriageSyncData();
    for (int i = 0; i < 4; i++) data.wheelLocations.set(i, wheelLocations.get(i));
    if (fallbackLocations != null)
      data.fallbackLocations =
          Pair.of(fallbackLocations.getFirst(), fallbackLocations.getSecond().copy());
    data.distanceToDestination = distanceToDestination;
    data.leadingCarriage = leadingCarriage;
    return data;
  }

  public void write(FriendlyByteBuf buffer) {
    if (!Float.isFinite(distanceToDestination))
      throw new IllegalArgumentException("Nonfinite actual carriage destination distance");
    buffer.writeBoolean(leadingCarriage);
    buffer.writeBoolean(fallbackLocations != null);
    if (fallbackLocations != null) {
      writeVector(buffer, fallbackLocations.getFirst());
      writeVector(buffer, fallbackLocations.getSecond().getFirst());
      writeVector(buffer, fallbackLocations.getSecond().getSecond());
    } else {
      int count = wheelLocations.get(2) == null ? 2 : 4;
      buffer.writeVarInt(count);
      for (int i = 0; i < count; i++) {
        var wheel = wheelLocations.get(i);
        if (wheel == null
            || wheel.getFirst().either(id -> id == null || id < 0)
            || !Double.isFinite(wheel.getSecond())
            || wheel.getSecond() < 0)
          throw new IllegalArgumentException("Incomplete actual carriage wheel snapshot");
        wheel.getFirst().forEach(buffer::writeVarInt);
        buffer.writeDouble(wheel.getSecond());
      }
    }
    buffer.writeFloat(distanceToDestination);
  }

  public void read(FriendlyByteBuf buffer) {
    leadingCarriage = buffer.readBoolean();
    boolean fallback = buffer.readBoolean();
    ticksSince = 0;
    for (int i = 0; i < 4; i++) {
      wheelLocations.set(i, null);
      pointDistanceSnapshot[i] = 0;
    }
    if (fallback)
      fallbackLocations =
          Pair.of(readVector(buffer), Couple.create(readVector(buffer), readVector(buffer)));
    else {
      fallbackLocations = null;
      int count = buffer.readVarInt();
      if (count != 2 && count != 4)
        throw new IllegalArgumentException(
            "Actual carriage snapshot requires two or four wheel points");
      for (int i = 0; i < count; i++) {
        int first = buffer.readVarInt(), second = buffer.readVarInt();
        double position = buffer.readDouble();
        if (first < 0 || second < 0 || !Double.isFinite(position) || position < 0)
          throw new IllegalArgumentException("Invalid actual carriage wheel snapshot");
        wheelLocations.set(i, Pair.of(Couple.create(first, second), position));
      }
    }
    distanceToDestination = buffer.readFloat();
    if (!Float.isFinite(distanceToDestination))
      throw new IllegalArgumentException("Nonfinite actual carriage navigation snapshot");
  }

  public void update(CarriageContraptionEntity entity, Carriage carriage) {
    DimensionalCarriageEntity dce = carriage.getDimensional(entity.level());

    distanceToDestination = (float) carriage.train.navigation.distanceToDestination;
    for (int i = 0; i < 4; i++) wheelLocations.set(i, null);
    TrackGraph graph = carriage.train.graph;
    if (graph == null) {
      updateFallbackLocations(dce);
      return;
    }

    fallbackLocations = null;
    leadingCarriage =
        entity.carriageIndex
            == (carriage.train.speed >= 0 ? 0 : carriage.train.carriages.size() - 1);

    for (boolean first : new boolean[] {true, false}) {
      if (!first && !carriage.isOnTwoBogeys()) break;

      CarriageBogey bogey = carriage.bogeys.get(first);
      for (boolean firstPoint : new boolean[] {true, false}) {
        TravellingPoint point = bogey.points.get(firstPoint);
        int index = (first ? 0 : 2) + (firstPoint ? 0 : 1);
        Couple<TrackNode> nodes = Couple.create(point.node1, point.node2);

        if (nodes.either(Objects::isNull)) {
          updateFallbackLocations(dce);
          return;
        }

        wheelLocations.set(index, Pair.of(nodes.map(TrackNode::getNetId), point.position));
      }
    }

    distanceToDestination = (float) carriage.train.navigation.distanceToDestination;
    setDirty(true);
  }

  private void updateFallbackLocations(DimensionalCarriageEntity dce) {
    if (dce.positionAnchor == null || dce.rotationAnchors.either(Objects::isNull)) {
      setDirty(false);
      return;
    }
    fallbackLocations = Pair.of(dce.positionAnchor, dce.rotationAnchors.copy());
    dce.pointsInitialised = true;
    setDirty(true);
  }

  public boolean apply(CarriageContraptionEntity entity, Carriage carriage) {
    DimensionalCarriageEntity dce = carriage.getDimensional(entity.level());

    fallbackPointSnapshot = null;
    if (fallbackLocations != null) {
      fallbackPointSnapshot = Pair.of(dce.positionAnchor, dce.rotationAnchors.copy());
      dce.pointsInitialised = true;
      return true;
    }

    TrackGraph graph = carriage.train.graph;
    if (graph == null) return false;

    int expected = carriage.isOnTwoBogeys() ? 4 : 2;
    for (int i = 0; i < 4; i++) {
      var wheel = wheelLocations.get(i);
      if (i >= expected) {
        if (wheel != null)
          throw new IllegalArgumentException("Snapshot bogey count disagrees with actual carriage");
        continue;
      }
      if (wheel == null) return false;
      var first = graph.getNode(wheel.getFirst().getFirst());
      var second = graph.getNode(wheel.getFirst().getSecond());
      if (first == null || second == null) return false;
      var edge = graph.getConnectionsFrom(first).get(second);
      if (edge == null) return false;
      if (wheel.getSecond() > edge.getLength() + 1e-5)
        throw new IllegalArgumentException(
            "Actual carriage wheel snapshot lies outside the real edge");
    }
    for (int i = 0; i < wheelLocations.size(); i++) {
      Pair<Couple<Integer>, Double> pair = wheelLocations.get(i);
      if (pair == null) break;

      CarriageBogey bogey = carriage.bogeys.get(i / 2 == 0);
      TravellingPoint bogeyPoint = bogey.points.get(i % 2 == 0);
      TravellingPoint point = dce.pointsInitialised ? pointsToApproach[i] : bogeyPoint;

      Couple<TrackNode> nodes = pair.getFirst().map(graph::getNode);
      if (nodes.either(Objects::isNull)) continue;
      TrackEdge edge = graph.getConnectionsFrom(nodes.getFirst()).get(nodes.getSecond());
      if (edge == null) continue;

      bindActual(point, graph, nodes, pair.getSecond());

      if (dce.pointsInitialised) {
        float foundDistance = -1;
        boolean direction = false;
        for (boolean forward : new boolean[] {true, false}) {
          float distanceTo = getDistanceTo(graph, bogeyPoint, point, foundDistance, forward);
          if (distanceTo > 0 && (foundDistance == -1 || distanceTo < foundDistance)) {
            foundDistance = distanceTo;
            direction = forward;
          }
        }

        if (foundDistance != -1) {
          pointDistanceSnapshot[i] = (direction ? 1 : -1) * foundDistance;
        } else {
          // could not pathfind to server location
          bindActual(bogeyPoint, graph, Couple.create(point.node1, point.node2), point.position);
          pointDistanceSnapshot[i] = 0;
        }
      }
    }

    if (!dce.pointsInitialised) {
      carriage.train.navigation.distanceToDestination = distanceToDestination;
      dce.pointsInitialised = true;
      return true;
    }

    if (!leadingCarriage) return true;

    destinationDistanceSnapshot =
        (float) (distanceToDestination - carriage.train.navigation.distanceToDestination);
    return true;
  }

  public void approach(CarriageContraptionEntity entity, Carriage carriage, float partialIn) {
    DimensionalCarriageEntity dce = carriage.getDimensional(entity.level());

    int updateInterval = entity.getType().updateInterval();
    if (ticksSince >= updateInterval * 2) partialIn /= ticksSince - updateInterval * 2 + 1;
    partialIn *= entity.level().getRelativeTickSpeed()*com.simibubi.create.foundation.utility.ServerSpeedProvider.get();
    final float partial = partialIn;

    ticksSince++;

    if (fallbackLocations != null && fallbackPointSnapshot != null) {
      dce.positionAnchor =
          approachVector(
              partial,
              dce.positionAnchor,
              fallbackLocations.getFirst(),
              fallbackPointSnapshot.getFirst());
      dce.rotationAnchors.replaceWithContext(
          (current, first) ->
              approachVector(
                  partial,
                  current,
                  fallbackLocations.getSecond().get(first),
                  fallbackPointSnapshot.getSecond().get(first)));
      return;
    }

    TrackGraph graph = carriage.train.graph;
    if (graph == null) return;

    carriage.train.navigation.distanceToDestination += partial * destinationDistanceSnapshot;

    for (boolean first : new boolean[] {true, false}) {
      if (!first && !carriage.isOnTwoBogeys()) break;
      CarriageBogey bogey = carriage.bogeys.get(first);
      for (boolean firstPoint : new boolean[] {true, false}) {
        int index = (first ? 0 : 2) + (firstPoint ? 0 : 1);
        float f = pointDistanceSnapshot[index];
        if (Mth.equal(f, 0)) continue;

        TravellingPoint point = bogey.points.get(firstPoint);
        MutableBoolean success = new MutableBoolean(true);
        TravellingPoint toApproach = pointsToApproach[index];

        ITrackSelector trackSelector =
            point.follow(toApproach, b -> success.setValue(success.booleanValue() && b));
        point.travel(graph, partial * f, trackSelector);

        // could not pathfind to server location
        if (!success.booleanValue()) {
          if (toApproach.resolve(graph))
            bindActual(
                point,
                graph,
                Couple.create(toApproach.node1, toApproach.node2),
                toApproach.position);
          pointDistanceSnapshot[index] = 0;
        }
      }
    }
  }

  private static void bindActual(
      TravellingPoint point, TrackGraph graph, Couple<TrackNode> nodes, double position) {
    var location = new com.simibubi.create.content.trains.graph.TrackGraphLocation();
    location.graph = graph;
    location.edge = nodes.map(TrackNode::getLocation);
    location.position = position;
    point.migrateTo(new ArrayList<>(List.of(location)));
  }

  private Vec3 approachVector(float partial, Vec3 current, Vec3 target, Vec3 snapshot) {
    if (current == null || snapshot == null) return target;
    return current.add(target.subtract(snapshot).scale(partial));
  }

  public float getDistanceTo(
      TrackGraph graph,
      TravellingPoint current,
      TravellingPoint target,
      float maxDistance,
      boolean forward) {
    if (!Float.isFinite(maxDistance) || maxDistance < -1)
      throw new IllegalArgumentException("Invalid actual carriage interpolation search distance");
    if (graph == null || !current.resolve(graph) || !target.resolve(graph)) return -1;
    if (maxDistance == -1) maxDistance = 32;

    Set<TrackEdge> visited = new HashSet<>();
    Map<TrackEdge, Pair<Boolean, TrackEdge>> reachedVia = new IdentityHashMap<>();
    PriorityQueue<Pair<Double, Pair<Couple<TrackNode>, TrackEdge>>> frontier =
        new PriorityQueue<>((p1, p2) -> Double.compare(p1.getFirst(), p2.getFirst()));

    TrackNode initialNode1 = forward ? current.node1 : current.node2;
    TrackNode initialNode2 = forward ? current.node2 : current.node1;

    Map<TrackNode, TrackEdge> connectionsFromInitial = graph.getConnectionsFrom(initialNode1);
    if (connectionsFromInitial == null) return -1;

    TrackEdge initialEdge = connectionsFromInitial.get(initialNode2);
    if (initialEdge == null) return -1; // graph changed

    TrackNode targetNode1 = forward ? target.node1 : target.node2;
    TrackNode targetNode2 = forward ? target.node2 : target.node1;
    TrackEdge targetEdge = graph.getConnectionsFrom(targetNode1).get(targetNode2);

    double distanceToNode2 =
        forward ? initialEdge.getLength() - current.position : current.position;

    frontier.add(
        Pair.of(distanceToNode2, Pair.of(Couple.create(initialNode1, initialNode2), initialEdge)));

    while (!frontier.isEmpty()) {
      Pair<Double, Pair<Couple<TrackNode>, TrackEdge>> poll = frontier.poll();
      double distance = poll.getFirst();

      Pair<Couple<TrackNode>, TrackEdge> currentEntry = poll.getSecond();
      TrackNode node2 = currentEntry.getFirst().getSecond();
      TrackEdge edge = currentEntry.getSecond();

      if (edge == targetEdge)
        return (float)
            (distance - (forward ? edge.getLength() - target.position : target.position));

      if (distance > maxDistance) continue;

      List<Entry<TrackNode, TrackEdge>> validTargets = new ArrayList<>();
      Map<TrackNode, TrackEdge> connectionsFrom = graph.getConnectionsFrom(node2);
      for (Entry<TrackNode, TrackEdge> entry : connectionsFrom.entrySet()) {
        TrackEdge newEdge = entry.getValue();
        Vec3 currentDirection = edge.getDirection(false);
        Vec3 newDirection = newEdge.getDirection(true);
        if (currentDirection.dot(newDirection) < 7 / 8f) continue;
        if (!visited.add(entry.getValue())) continue;
        validTargets.add(entry);
      }

      if (validTargets.isEmpty()) continue;

      for (Entry<TrackNode, TrackEdge> entry : validTargets) {
        TrackNode newNode = entry.getKey();
        TrackEdge newEdge = entry.getValue();
        reachedVia.put(newEdge, Pair.of(validTargets.size() > 1, edge));
        frontier.add(
            Pair.of(
                newEdge.getLength() + distance, Pair.of(Couple.create(node2, newNode), newEdge)));
      }
    }

    return -1;
  }

  //

  private boolean isDirty;

  public void setDirty(boolean dirty) {
    isDirty = dirty;
  }

  public boolean isDirty() {
    return isDirty;
  }
}
