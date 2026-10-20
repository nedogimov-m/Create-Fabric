package com.simibubi.create.content.trains.graph;

import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Source-shaped world targeting against actual graph nodes and actual reciprocal TrackBE curves. A
 * missing physical frontier is retryable via UnloadedTrackException, never read as air.
 */
public final class TrackGraphHelper {
  private static final double EPSILON = 1 / 4096d;
  private static volatile java.util.function.Function<Level, Collection<TrackGraph>> clientGraphs =
      level -> List.of();

  /**
   * Installed by the real native client graph cache; dedicated servers never load client classes.
   */
  public static void setClientGraphLookup(
      java.util.function.Function<Level, Collection<TrackGraph>> lookup) {
    clientGraphs = Objects.requireNonNull(lookup);
  }

  private static Collection<TrackGraph> networks(Level level) {
    return level.isClientSide()
        ? clientGraphs.apply(level)
        : RailwayGraphManager.get(level).getTrackNetworks().values();
  }

  /** Actual server manager/client-cache lookup; no topology is synthesized. */
  public static TrackGraph getGraph(Level level, UUID id) {
    return networks(level).stream().filter(graph -> graph.id.equals(id)).findFirst().orElse(null);
  }

  private static List<TrackGraph> graphsAt(Level level, TrackNodeLocation location) {
    return networks(level).stream().filter(graph -> graph.locateNode(location) != null).toList();
  }

  private static TrackGraph graphAt(Level level, TrackNodeLocation location) {
    return graphsAt(level, location).stream().findFirst().orElse(null);
  }

  private static void loaded(Level level, BlockPos pos) {
    if (!LoadedLevelAccess.isLoaded(level, pos)) throw new UnloadedTrackException(level, pos);
  }

  @Nullable
  public static TrackGraphLocation getGraphLocationAt(
      Level level, BlockPos pos, AxisDirection targetDirection, Vec3 targetAxis) {
    if (!level.isClientSide() && level.getServer() == null
        || level.isClientSide() && networks(level).isEmpty()) return null;
    loaded(level, pos);
    var state = LoadedLevelAccess.blockState(level, pos);
    if (!(state.getBlock() instanceof ITrackBlock track)) return null;
    if (targetAxis.lengthSqr() < 1e-12
        || !Double.isFinite(targetAxis.lengthSqr())
        || track.getTrackAxes(level, pos, state).stream()
            .noneMatch(
                axis ->
                    axis.lengthSqr() > 1e-12
                        && Math.abs(axis.normalize().dot(targetAxis.normalize())) > 1 - EPSILON))
      return null;
    var axis = targetAxis.scale(targetDirection.getStep());
    double length = axis.length();
    var start = Vec3.atBottomCenterOf(pos).add(0, track.getElevationAtCenter(level, pos, state), 0);
    var center = new TrackNodeLocation(start).in(level);
    for (var graph : graphsAt(level, center)) {
      var node = graph.locateNode(center);
      for (var entry : graph.getConnectionsFrom(node).entrySet())
        if (entry.getValue().getDirection(true).scale(length).distanceToSqr(axis.scale(-1))
            <= EPSILON) return location(graph, node, entry.getKey(), 0);
    }
    TrackNode front = null, back = null;
    TrackGraph owner = null;
    double position = 0;
    boolean single = true;
    for (var end : track.getConnected(level, pos, state, true, null)) {
      var horizontal = end.getLocation().subtract(start).multiply(1, 0, 1).normalize();
      boolean forward = horizontal.distanceToSqr(axis.multiply(-1, 0, -1).normalize()) < EPSILON;
      boolean backward = horizontal.distanceToSqr(axis.multiply(1, 0, 1).normalize()) < EPSILON;
      if (!forward && !backward) continue;
      TrackNodeLocation current = end, previous = null;
      double distance = 0;
      var visited = new HashSet<TrackNodeLocation>();
      for (int step = 0; step < 100 && distance < 32 && visited.add(current.copy()); step++) {
        var graph = graphAt(level, current);
        if (graph != null) {
          if (owner != null && owner != graph) return null;
          owner = graph;
          var node = graph.locateNode(current);
          if (forward) front = node;
          if (backward) {
            back = node;
            position = distance + length / 2;
          }
          break;
        }
        single = false;
        TrackNodeLocation next = null;
        double increment = 0;
        for (var candidate : ITrackBlock.walkConnectedTracks(level, current, true)) {
          if (candidate.equals(previous)) continue;
          var diff = candidate.getLocation().subtract(current.getLocation());
          if (diff.lengthSqr() < 1e-12
              || (forward ? axis.scale(-1) : axis).distanceToSqr(diff.normalize().scale(length))
                  > EPSILON) continue;
          if (next != null && !next.equals(candidate)) return null;
          next = candidate;
          increment = diff.length();
        }
        if (next == null) break;
        previous = current;
        current = next;
        distance += increment;
      }
    }
    if (front == null || back == null || owner == null) return null;
    var edge = owner.getConnectionsFrom(back).get(front);
    if (edge == null || edge.isTurn()) return null;
    if (single) position = edge.getLength() / 2;
    if (position < 0 || position > edge.getLength() + 1e-6) return null;
    return location(owner, back, front, position);
  }

  @Nullable
  public static TrackGraphLocation getBezierGraphLocationAt(
      Level level, BlockPos pos, AxisDirection direction, BezierTrackPointLocation target) {
    if (!level.isClientSide() && level.getServer() == null
        || level.isClientSide() && networks(level).isEmpty()) return null;
    loaded(level, pos);
    var state = LoadedLevelAccess.blockState(level, pos);
    if (!(state.getBlock() instanceof ITrackBlock)
        || !(com.simibubi.create.content.trains.track.TrackBlockEntityAccess.existing(level, pos)
            instanceof TrackBlockEntity entity)) return null;
    var curve = entity.getConnections().get(target.curveTarget());
    if (curve == null || !curve.isPrimary()) return null;
    var first = new TrackNodeLocation(curve.starts.getFirst()).in(level);
    var second = new TrackNodeLocation(curve.starts.getSecond()).in(level);
    if (curve.smoothing != null) {
      first.yOffsetPixels = curve.smoothing.getFirst();
      second.yOffsetPixels = curve.smoothing.getSecond();
    }
    for (var graph : graphsAt(level, first)) {
      var a = graph.locateNode(first);
      var b = graph.locateNode(second);
      if (a == null || b == null) continue;
      var edge = graph.getConnectionsFrom(a).get(b);
      if (edge == null || !edge.isTurn() || !edge.getTurn().equalsSansMaterial(curve)) continue;
      double position = (target.segment() + 1) / 2d;
      if (position > edge.getLength()) return null;
      return direction == AxisDirection.POSITIVE
          ? location(graph, b, a, edge.getLength() - position)
          : location(graph, a, b, position);
    }
    return null;
  }

  private static TrackGraphLocation location(
      TrackGraph graph, TrackNode a, TrackNode b, double position) {
    var result = new TrackGraphLocation();
    result.graph = graph;
    result.edge = Couple.create(a.getLocation(), b.getLocation());
    result.position = position;
    return result;
  }

  private TrackGraphHelper() {}
}
