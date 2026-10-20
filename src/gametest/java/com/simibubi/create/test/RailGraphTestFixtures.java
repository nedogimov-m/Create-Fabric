package com.simibubi.create.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Test-only real typed points and graph geometry; no production train/carriage facades. */
public final class RailGraphTestFixtures {
  public static final Vec3 UP = new Vec3(0, 1, 0);
  public static final EdgePointType<Marker> MARKER =
      EdgePointType.register(
          Identifier.parse("create:rail_graph_test_marker"),
          Marker::new,
          TrackEdgePoint.baseCodec(Marker::new));
  public static final EdgePointType<RegistryMarker> REGISTRY_MARKER =
      EdgePointType.register(
          Identifier.parse("create:rail_graph_test_registry_marker"),
          RegistryMarker::new,
          RegistryMarker.CODEC);

  /** Invoked while Fabric instantiates the test entrypoints, before native SavedData is read. */
  public static void registerTypes() {}

  public static class Marker extends TrackEdgePoint {
    public List<GlobalPos> owners = List.of();
    public int invalidations, ticks;

    @Override
    public boolean canMerge() {
      return false;
    }

    @Override
    public Collection<GlobalPos> invalidationTargets(LevelAccessor level) {
      return owners;
    }

    @Override
    public void invalidate(LevelAccessor level) {
      preflightInvalidation(level);
      invalidations++;
      for (var owner : owners) {
        var world =
            level instanceof Level nativeLevel && nativeLevel.dimension().equals(owner.dimension())
                ? nativeLevel
                : ((Level) level).getServer().getLevel(owner.dimension());
        invalidateAt(world, owner.pos());
      }
    }

    @Override
    public void blockEntityAdded(BlockEntity entity, boolean front) {}

    @Override
    public void blockEntityRemoved(BlockPos pos, boolean front) {}

    @Override
    public void tick(TrackGraph graph, boolean preTrains) {
      ticks++;
    }
  }

  public static final class RegistryMarker extends Marker {
    public Holder<Biome> biome;
    public static final Codec<RegistryMarker> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        TrackEdgePoint.baseCodec(RegistryMarker::new)
                            .fieldOf("Geometry")
                            .forGetter(point -> point),
                        Biome.CODEC.fieldOf("Biome").forGetter(point -> point.biome))
                    .apply(
                        i,
                        (point, biome) -> {
                          point.biome = biome;
                          return point;
                        }));
  }

  public static TrackNodeLocation at(double x, double y, double z) {
    return new TrackNodeLocation(x, y, z).in(Level.OVERWORLD);
  }

  public static TrackNode node(TrackGraph graph, double x, double y, double z) {
    var location = new TrackNodeLocation.DiscoveredLocation(Level.OVERWORLD, new Vec3(x, y, z));
    graph.createNodeIfAbsent(location);
    return graph.locateNode(location);
  }

  public static void link(TrackGraph graph, TrackNode a, TrackNode b) {
    graph.connectNodes(a, b, null, TrackMaterial.ANDESITE);
  }

  public static Marker marker(TrackGraph graph, TrackNode a, TrackNode b, double position) {
    var marker = MARKER.create();
    marker.setLocation(Couple.create(a.getLocation(), b.getLocation()), position);
    graph.addPoint(MARKER, marker);
    return marker;
  }

  public static TrackGraph line() {
    var graph = new TrackGraph();
    var a = node(graph, 0, 0, 0);
    var b = node(graph, 4, 0, 0);
    var c = node(graph, 8, 0, 0);
    link(graph, a, b);
    link(graph, b, c);
    return graph;
  }

  public static BezierConnection curve() {
    return new BezierConnection(
        Couple.create(new BlockPos(0, 0, 0), new BlockPos(8, 0, 8)),
        Couple.create(new Vec3(.5, 0, .5), new Vec3(8.5, 0, 8.5)),
        Couple.create(new Vec3(1, 0, 0), new Vec3(0, 0, -1)),
        Couple.create(UP, UP),
        true,
        true,
        TrackMaterial.ANDESITE);
  }

  public static CompoundTag encode(TrackGraph graph, HolderLookup.Provider registries) {
    return (CompoundTag)
        TrackGraph.CODEC
            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), graph)
            .getOrThrow();
  }

  public static TrackGraph decode(CompoundTag tag, HolderLookup.Provider registries) {
    return TrackGraph.CODEC
        .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
        .getOrThrow();
  }

  public static void rejects(
      net.minecraft.gametest.framework.GameTestHelper h, Runnable operation, String message) {
    boolean failed = false;
    try {
      operation.run();
    } catch (IllegalArgumentException | IllegalStateException expected) {
      failed = true;
    }
    h.assertTrue(failed, message);
  }

  private RailGraphTestFixtures() {}
}
