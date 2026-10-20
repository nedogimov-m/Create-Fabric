package com.simibubi.create.content.trains.graph;

import com.simibubi.create.content.trains.graph.sync.RailwayGraphClient;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.SignalEdgePoints;
import com.simibubi.create.content.trains.signal.SignalGroupClient;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;

/** Source signal-group segments, extracted from actual native synchronized graphs and group colors. */
public final class SignalGroupVisualizer {
  private static final float WIDTH = 1 / 8f;

  public record Segment(Vec3 start, Vec3 end, UUID group, int color, float width) {}

  public record Frame(DrawableGizmoPrimitives primitives, List<Segment> segments, int graphs) {
    public Frame {
      segments = List.copyOf(segments);
    }
  }

  private static final Map<LevelRenderState, Frame> FRAMES =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static volatile Frame latest = empty();

  public static Frame latest() {
    return latest;
  }

  private static Frame empty() {
    return new Frame(new DrawableGizmoPrimitives(), List.of(), 0);
  }

  private static boolean active(Minecraft client) {
    if (client.level == null || client.player == null || client.getCameraEntity() == null
        || TrackGraphVisualizer.active(client)) return false;
    var stack = client.player.getMainHandItem();
    return stack.getItem() instanceof TrackTargetingBlockItem targeting
        && targeting.getType(stack) == SignalEdgePoints.SIGNAL;
  }

  private static void line(DrawableGizmoPrimitives primitives, List<Segment> segments,
      Vec3 start, Vec3 end, SignalEdgeGroup group) {
    int color = 0xFF000000 | group.color.rgb();
    segments.add(new Segment(start, end, group.id, color, WIDTH));
    TrackGraphVisualizer.line(primitives, start, end, color, WIDTH);
  }

  /** All live world, held-item, graph and group reads happen during extraction. */
  public static Frame extract(Minecraft client) {
    if (!active(client)) return empty();
    var allGroups = SignalGroupClient.groups(client.level);
    if (allGroups.isEmpty()) return empty();
    var primitives = new DrawableGizmoPrimitives();
    var segments = new ArrayList<Segment>();
    var cameraEntity = client.getCameraEntity();
    var camera = cameraEntity.getEyePosition();
    var range = cameraEntity.getBoundingBox().inflate(50);
    int graphs = 0;
    for (var graph : RailwayGraphClient.graphs(client.level)) {
      var bounds = graph.getBounds(client.level).box;
      if (bounds == null || !bounds.intersects(range)) continue;
      graphs++;
      for (var nodeLocation : graph.getNodes()) {
        if (!client.level.dimension().equals(nodeLocation.dimension)
            || nodeLocation.getLocation().distanceTo(camera) > 50) continue;
        var node = graph.locateNode(nodeLocation);
        int hash = node.hashCode();
        for (var entry : graph.getConnectionsFrom(node).entrySet()) {
          var other = entry.getKey();
          var edge = entry.getValue();
          if (edge.isInterDimensional()) continue;
          if (other.hashCode() > hash && other.getLocation().getLocation().distanceTo(camera) <= 50)
            continue;
          var yOffset = new Vec3(0, (other.hashCode() > hash ? 6 : 5) / 64f, 0);
          if (edge.isTurn())
            curve(graph, node, other, edge, allGroups, yOffset, primitives, segments);
          else
            straight(graph, node, other, edge, allGroups, yOffset, primitives, segments);
        }
      }
    }
    return new Frame(primitives, segments, graphs);
  }

  private static void straight(TrackGraph graph, TrackNode node, TrackNode other, TrackEdge edge,
      Map<UUID, SignalEdgeGroup> groups, Vec3 offset,
      DrawableGizmoPrimitives primitives, List<Segment> segments) {
    var data = edge.getEdgeData();
    if (data.hasSignalBoundaries()) {
      double previous = 0;
      double length = edge.getLength();
      SignalBoundary lastBoundary = null;
      for (var point : data.getPoints()) {
        if (!(point instanceof SignalBoundary boundary)) continue;
        lastBoundary = boundary;
        var group = groups.get(boundary.groupFor(node));
        if (group != null) {
          var start = edge.getPosition(graph, previous + (previous == 0 ? 0 : 1 / 16f / length));
          previous = boundary.getLocationOn(edge) / length;
          line(primitives, segments, start.add(offset),
              edge.getPosition(graph, previous - 1 / 16f / length).add(offset), group);
        }
      }
      if (lastBoundary != null) {
        var group = groups.get(lastBoundary.groupFor(other));
        if (group != null)
          line(primitives, segments,
              edge.getPosition(graph, previous + 1 / 16f / length).add(offset),
              edge.getPosition(graph, 1).add(offset), group);
        return;
      }
    }
    var id = data.getEffectiveEdgeGroupId(graph);
    var group = id == null ? null : groups.get(id);
    if (group != null)
      line(primitives, segments, edge.getPosition(graph, 0).add(offset),
          edge.getPosition(graph, 1).add(offset), group);
  }

  private static void curve(TrackGraph graph, TrackNode node, TrackNode other, TrackEdge edge,
      Map<UUID, SignalEdgeGroup> groups, Vec3 offset,
      DrawableGizmoPrimitives primitives, List<Segment> segments) {
    var data = edge.getEdgeData();
    var turn = edge.getTurn();
    if (data.hasSignalBoundaries()) {
      var points = data.getPoints().iterator();
      SignalBoundary boundary = null;
      double boundaryPosition = 0;
      while (points.hasNext()) {
        var point = points.next();
        if (!(point instanceof SignalBoundary signal)) continue;
        boundary = signal;
        boundaryPosition = signal.getLocationOn(edge);
        break;
      }
      if (boundary == null) return;
      var initial = boundary.groupFor(node);
      if (initial == null) return;
      var currentGroup = groups.get(initial);
      if (currentGroup == null) return;
      Vec3 previous = null;
      for (int i = 0; i <= turn.getSegmentCount(); i++) {
        double fraction = i * 1f / turn.getSegmentCount();
        double position = fraction * turn.getLength();
        var current = edge.getPosition(graph, fraction);
        if (previous != null) {
          if (boundary != null && position > boundaryPosition) {
            current = edge.getPosition(graph, (boundaryPosition - WIDTH) / turn.getLength());
            line(primitives, segments, previous.add(offset), current.add(offset), currentGroup);
            current = edge.getPosition(graph, (boundaryPosition + WIDTH) / turn.getLength());
            previous = current;
            var nextId = boundary.groupFor(other);
            if (nextId != null && groups.containsKey(nextId)) currentGroup = groups.get(nextId);
            boundary = null;
            while (points.hasNext()) {
              var point = points.next();
              if (!(point instanceof SignalBoundary signal)) continue;
              boundary = signal;
              boundaryPosition = signal.getLocationOn(edge);
              break;
            }
          }
          line(primitives, segments, previous.add(offset), current.add(offset), currentGroup);
        }
        previous = current;
      }
    }
    var id = data.getEffectiveEdgeGroupId(graph);
    var group = id == null ? null : groups.get(id);
    if (group == null) return;
    Vec3 previous = null;
    for (int i = 0; i <= turn.getSegmentCount(); i++) {
      var current = edge.getPosition(graph, i * 1f / turn.getSegmentCount());
      if (previous != null)
        line(primitives, segments, previous.add(offset), current.add(offset), group);
      previous = current;
    }
  }

  private static void clear() {
    FRAMES.clear();
    latest = empty();
  }

  public static void register() {
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> clear());
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (!active(client)) clear();
    });
    LevelExtractionEvents.END_EXTRACTION.register(context -> {
      var frame = extract(Minecraft.getInstance());
      latest = frame;
      FRAMES.put(context.levelState(), frame);
    });
    // Submission reads only detached geometry and the native render camera state.
    LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
      var frame = FRAMES.get(context.levelState());
      if (frame != null)
        frame.primitives().submit(context.submitNodeCollector(), context.levelState().cameraRenderState, false);
    });
  }

  private SignalGroupVisualizer() {}
}
