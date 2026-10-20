package com.simibubi.create.content.trains.graph;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.graph.sync.RailwayGraphClient;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.createmod.catnip.math.AngleHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Source graph debug geometry extracted from the actual synchronized native railway topology. */
public final class TrackGraphVisualizer {
  public record Material(Vec3 position, ItemStackRenderState model) {}

  public record Frame(
      DrawableGizmoPrimitives primitives,
      List<Material> materials,
      int graphs,
      int nodes,
      int edges,
      int curveSegments,
      boolean extended,
      boolean bothDirections) {
    public Frame {
      materials = List.copyOf(materials);
    }
  }

  private static final Map<LevelRenderState, Frame> FRAMES =
      Collections.synchronizedMap(new WeakHashMap<>());
  private static volatile Frame latest = empty();

  public static Frame latest() {
    return latest;
  }

  private static Frame empty() {
    return new Frame(new DrawableGizmoPrimitives(), List.of(), 0, 0, 0, 0, false, false);
  }

  static boolean active(Minecraft client) {
    return client.level != null
        && client.getCameraEntity() != null
        && client.getDebugOverlay().showDebugScreen()
        && AllConfigs.client().showTrackGraphOnF3.get();
  }

  private static void clear() {
    FRAMES.clear();
    latest = empty();
  }

  /** Reads world, configuration, keys and item models only during native level extraction. */
  public static Frame extract(Minecraft client, Vec3 renderCamera) {
    if (!active(client)) return empty();
    var level = client.level;
    var cameraEntity = client.getCameraEntity();
    var camera = cameraEntity.getEyePosition();
    var range = cameraEntity.getBoundingBox().inflate(50);
    boolean extended = AllConfigs.client().showExtendedTrackGraphOnF3.get();
    boolean bothDirections = InputConstants.isKeyDown(InputConstants.KEY_LCONTROL);
    var primitives = new DrawableGizmoPrimitives();
    var materials = new ArrayList<Material>();
    int graphs = 0, nodes = 0, edges = 0, curveSegments = 0;

    for (var graph : RailwayGraphClient.graphs(level)) {
      var bounds = graph.getBounds(level).box;
      if (bounds == null || !bounds.intersects(range)) continue;
      graphs++;
      int color = 0xFF000000 | graph.color;
      for (var location : graph.getNodes()) {
        if (!level.dimension().equals(location.dimension)) continue;
        var point = location.getLocation();
        if (point.distanceTo(camera) > 50) continue;
        var node = graph.locateNode(location);
        var yOffset = new Vec3(0, 3 / 16f, 0);
        var markerStart = point.add(yOffset);
        float markerWidth = 1 / 8f;
        int hash = node.hashCode();

        for (var entry : graph.getConnectionsFrom(node).entrySet()) {
          var other = entry.getKey();
          var edge = entry.getValue();
          if (edge.isInterDimensional()) {
            // Source Outliner replaces the same node-id marker instead of adding another line.
            markerStart = point.add(yOffset);
            markerWidth = 1 / 4f;
            continue;
          }
          if (other.hashCode() > hash && !bothDirections) continue;
          yOffset = new Vec3(0, (other.hashCode() > hash ? 6 : 4) / 16f, 0);
          edges++;

          if (extended) {
            var position = edge.getPosition(graph, .5).add(0, 1, 0);
            var model = new ItemStackRenderState();
            client
                .getItemModelResolver()
                .updateForTopItem(
                    model,
                    edge.getTrackMaterial().asStack(),
                    ItemDisplayContext.FIXED,
                    null,
                    null,
                    0);
            materials.add(new Material(position, model));
            box(
                primitives,
                AABB.ofSize(position, .25, 0, .25).move(0, -.5, 0),
                renderCamera,
                color,
                1 / 16f);
          }

          if (!edge.isTurn()) {
            line(
                primitives,
                edge.getPosition(graph, 0).add(yOffset),
                edge.getPosition(graph, 1).add(yOffset),
                color,
                1 / 16f);
            continue;
          }
          int segments = edge.getTurn().getSegmentCount();
          Vec3 previous = null;
          for (int i = 0; i <= segments; i++) {
            var current = edge.getPosition(graph, i * 1f / segments);
            if (previous != null) {
              line(primitives, previous.add(yOffset), current.add(yOffset), color, 1 / 16f);
              curveSegments++;
            }
            previous = current;
          }
        }
        line(
            primitives,
            markerStart,
            markerStart.add(node.getNormal().scale(3 / 16f)),
            color,
            markerWidth);
        nodes++;
      }
    }
    return new Frame(
        primitives, materials, graphs, nodes, edges, curveSegments, extended, bothDirections);
  }

  /**
   * Catnip Outline.bufferCuboidLine uses world-space cuboids, including half-width end caps.
   * Native gizmo addLine uses screen-space strokes; submit the real six cuboid faces instead.
   */
  static void line(
      DrawableGizmoPrimitives output, Vec3 start, Vec3 end, int color, float width) {
    float dx = (float) (end.x - start.x);
    float dy = (float) (end.y - start.y);
    float dz = (float) (end.z - start.z);
    float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
    float yaw = AngleHelper.deg(Mth.atan2(dx, dz));
    float horizontal = Mth.sqrt(dx * dx + dz * dz);
    float pitch = AngleHelper.deg(Mth.atan2(horizontal, dy)) - 90;
    var rotation = new Matrix4f().rotateY(AngleHelper.rad(yaw)).rotateX(AngleHelper.rad(pitch));
    float half = width / 2;
    var points = new Vec3[8];
    for (int i = 0; i < points.length; i++) {
      var local =
          rotation.transformPosition(
              new Vector3f(
                  (i & 1) == 0 ? -half : half,
                  (i & 2) == 0 ? -half : half,
                  (i & 4) == 0 ? -half : length + half));
      points[i] = start.add(local.x, local.y, local.z);
    }
    cuboid(output, points, color);
  }

  private static void cuboid(DrawableGizmoPrimitives output, Vec3[] p, int color) {
    output.addQuad(p[4], p[0], p[1], p[5], color);
    output.addQuad(p[2], p[6], p[7], p[3], color);
    output.addQuad(p[3], p[1], p[0], p[2], color);
    output.addQuad(p[6], p[4], p[5], p[7], color);
    output.addQuad(p[2], p[0], p[4], p[6], color);
    output.addQuad(p[7], p[5], p[1], p[3], color);
  }

  private static void box(
      DrawableGizmoPrimitives output, AABB bounds, Vec3 camera, int color, float width) {
    // Source AABBOutline expands its edges by 1/128 even for the flat material marker.
    bounds = bounds.inflate(bounds.contains(camera) ? -1 / 128f : 1 / 128f);
    var points = new Vec3[8];
    for (int i = 0; i < points.length; i++)
      points[i] =
          new Vec3(
              (i & 1) == 0 ? bounds.minX : bounds.maxX,
              (i & 2) == 0 ? bounds.minY : bounds.maxY,
              (i & 4) == 0 ? bounds.minZ : bounds.maxZ);
    for (int i = 0; i < points.length; i++)
      for (int bit : new int[] {1, 2, 4})
        if ((i & bit) == 0) line(output, points[i], points[i | bit], color, width);
  }

  public static void register() {
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> clear());
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (!active(client)) clear();
        });
    LevelExtractionEvents.END_EXTRACTION.register(
        context -> {
          var frame =
              extract(Minecraft.getInstance(), context.levelState().cameraRenderState.pos);
          latest = frame;
          FRAMES.put(context.levelState(), frame);
        });
    LevelRenderEvents.COLLECT_SUBMITS.register(
        context -> {
          var frame = FRAMES.get(context.levelState());
          if (frame == null) return;
          var camera = context.levelState().cameraRenderState;
          frame.primitives().submit(context.submitNodeCollector(), camera, false);
          for (var material : frame.materials()) {
            var pose = new PoseStack();
            var position = material.position();
            pose.translate(
                position.x - camera.pos.x,
                position.y - camera.pos.y,
                position.z - camera.pos.z);
            material
                .model()
                .submit(
                    pose,
                    context.submitNodeCollector(),
                    LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    0);
          }
        });
  }

  private TrackGraphVisualizer() {}
}
