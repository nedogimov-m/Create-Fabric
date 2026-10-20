package com.simibubi.create.test;

import static com.simibubi.create.test.RailGraphTestFixtures.*;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Actual registered TrackBlock -> discovery -> persistent graph lifecycle, not scripted train
 * motion.
 */
public final class RailPropagationGameTests {
  public RailPropagationGameTests() {
    RailGraphTestFixtures.registerTypes();
  }

  private static final String STRUCTURE = "create_test:reservoir";
  private static final BlockPos P = new BlockPos(4, 6, 4), M = P.east(3);

  private static BlockState rail(TrackShape shape) {
    return AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, shape);
  }

  private static RailwayGraphManager manager(GameTestHelper h) {
    return RailwayGraphManager.get(h.getLevel());
  }

  private static void place(GameTestHelper h, BlockPos pos, TrackShape shape) {
    h.setBlock(pos.below(), Blocks.STONE);
    h.setBlock(pos, rail(shape));
  }

  private static void line(GameTestHelper h) {
    for (int i = 0; i < 7; i++) place(h, P.east(i), TrackShape.XO);
    for (int i = 0; i < 7; i++)
      h.assertTrue(
          TrackPropagator.reconcile(
                  h.getLevel(), h.absolutePos(P.east(i)), rail(TrackShape.XO), false)
              .complete(),
          "Real line discovers loaded world");
  }

  private record EdgeIn(TrackGraph graph, TrackEdge edge) {}

  private static List<EdgeIn> through(GameTestHelper h, BlockPos relative, Vec3 axis) {
    var center = Vec3.atBottomCenterOf(h.absolutePos(relative));
    var result = new ArrayList<EdgeIn>();
    for (var graph : manager(h).getTrackNetworks().values())
      for (var location : graph.getNodes()) {
        var a = graph.locateNode(location);
        if (!location.dimension.equals(h.getLevel().dimension())) continue;
        for (var entry : graph.getConnectionsFrom(a).entrySet()) {
          var edge = entry.getValue();
          if (edge.isTurn()
              || edge.isInterDimensional()
              || edge.node1.getNetId() > edge.node2.getNetId()) continue;
          var start = location.getLocation();
          var delta = entry.getKey().getLocation().getLocation().subtract(start);
          double length = delta.lengthSqr();
          if (length < 1e-12 || Math.abs(delta.normalize().dot(axis)) < .99999) continue;
          double t = center.subtract(start).dot(delta) / length;
          if (t >= 0 && t <= 1 && start.add(delta.scale(t)).distanceToSqr(center) < 1e-8)
            result.add(new EdgeIn(graph, edge));
        }
      }
    return result;
  }

  @GameTest(structure = STRUCTURE)
  public void realRailsProduceReciprocalGraphAndRepeatedReconciliationIsStable(GameTestHelper h) {
    line(h);
    var before = through(h, M, new Vec3(1, 0, 0));
    h.assertValueEqual(before.size(), 1, "One real physical edge through middle cell");
    var graph = before.getFirst().graph;
    long revision = graph.revision();
    var result =
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.XO), false);
    h.assertTrue(
        result.complete() && graph.revision() == revision,
        "Identical world reconciliation preserves original topology revision");
    graph.validate();
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void removingActualMiddleRailSplitsNetworkAndRemovesStaleCompressedEdge(GameTestHelper h) {
    line(h);
    var left = through(h, P, new Vec3(1, 0, 0)).getFirst().graph;
    h.setBlock(M, Blocks.AIR);
    var result =
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.XO), true);
    h.assertTrue(
        result.complete() && through(h, M, new Vec3(1, 0, 0)).isEmpty(),
        "Old compressed edge never spans removed physical cell");
    var a = through(h, P, new Vec3(1, 0, 0)).getFirst().graph;
    var b = through(h, P.east(6), new Vec3(1, 0, 0)).getFirst().graph;
    h.assertTrue(
        a != b && (a == left || b == left),
        "Actual disconnection splits ownership while retaining one graph");
    a.validate();
    b.validate();
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void addingActualGapRailMergesBothNetworkOwners(GameTestHelper h) {
    line(h);
    h.setBlock(M, Blocks.AIR);
    TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.XO), true);
    var a = through(h, P, new Vec3(1, 0, 0)).getFirst().graph;
    var b = through(h, P.east(6), new Vec3(1, 0, 0)).getFirst().graph;
    h.assertTrue(a != b, "Two actual disconnected owners before fill");
    place(h, M, TrackShape.XO);
    h.assertTrue(
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.XO), false)
            .complete(),
        "Physical gap placement reconciles");
    var merged = through(h, M, new Vec3(1, 0, 0)).getFirst().graph;
    h.assertTrue(
        through(h, P, new Vec3(1, 0, 0)).getFirst().graph == merged
            && through(h, P.east(6), new Vec3(1, 0, 0)).getFirst().graph == merged,
        "Both sides now have one actual owner");
    merged.validate();
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void sameBlockShapeChangeReplacesOldAxisWithoutLeavingStaleRail(GameTestHelper h) {
    line(h);
    h.setBlock(M, rail(TrackShape.ZO));
    h.assertTrue(
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.XO), false)
            .complete(),
        "Same TrackBlock with changed property reconciles");
    h.assertTrue(
        through(h, M, new Vec3(1, 0, 0)).isEmpty() && through(h, M, new Vec3(0, 0, 1)).size() == 1,
        "Only actual new Z rail passes through changed cell");
    for (var graph : manager(h).getTrackNetworks().values()) graph.validate();
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void sameBlockCrossingOverlayAndRemovalReconcileBothRealAxes(GameTestHelper h) {
    line(h);
    h.setBlock(M, rail(TrackShape.CR_O));
    h.assertTrue(
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.XO), false)
            .complete(),
        "Same-block crossed source state discovers");
    h.assertTrue(
        !through(h, M, new Vec3(1, 0, 0)).isEmpty() && !through(h, M, new Vec3(0, 0, 1)).isEmpty(),
        "Both real crossing axes are represented");
    h.setBlock(M, rail(TrackShape.ZO));
    h.assertTrue(
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.CR_O), false)
            .complete(),
        "Replacing crossing with its Z axis reconciles");
    h.assertTrue(
        through(h, M, new Vec3(1, 0, 0)).isEmpty() && !through(h, M, new Vec3(0, 0, 1)).isEmpty(),
        "Removed X branch leaves no stale compressed physical edge");
    for (var graph : manager(h).getTrackNetworks().values()) graph.validate();
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void realEdgePointOwnerReadinessDefersWholeRailDeletionAndRetryPreservesIdentity(
      GameTestHelper h) {
    line(h);
    var original = through(h, M, new Vec3(1, 0, 0)).getFirst();
    var point = MARKER.create();
    point.setLocation(
        Couple.create(original.edge.node1.getLocation(), original.edge.node2.getLocation()),
        original.edge.getLength() / 2);
    var far = new BlockPos(2400016, 64, 2400016);
    point.owners = List.of(GlobalPos.of(h.getLevel().dimension(), far));
    original.graph.addPoint(MARKER, point);
    long revision = original.graph.revision();
    var edge = original.edge;
    h.setBlock(M, Blocks.AIR);
    var pending =
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.XO), true);
    h.assertTrue(
        !pending.complete() && pending.pending().contains(point.owners.getFirst()),
        "Actual missing point owner is an explicit pending frontier");
    h.assertTrue(
        original.graph.revision() == revision
            && original.graph.getPoint(MARKER, point.id) == point
            && original.graph.getConnectionsFrom(edge.node1).get(edge.node2) == edge
            && point.invalidations == 0,
        "Original point, topology and revision unchanged before readiness");
    h.assertTrue(
        h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
        "Preflight created no native chunk ticket");
    h.assertTrue(
        manager(h).savedData().pending().stream()
            .anyMatch(request -> request.position().pos().equals(h.absolutePos(M))),
        "Retry intent owned by real SavedData");
    point.owners = List.of(GlobalPos.of(h.getLevel().dimension(), h.absolutePos(P.below())));
    var retried =
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(M), rail(TrackShape.XO), true);
    h.assertTrue(
        retried.complete()
            && point.invalidations == 1
            && point.graph() == null
            && through(h, M, new Vec3(1, 0, 0)).isEmpty(),
        "Ready retry invalidates exactly original point and removes actual stale edge");
    h.assertTrue(
        manager(h).savedData().pending().stream()
            .noneMatch(request -> request.position().pos().equals(h.absolutePos(M))),
        "Successful retry clears durable intent");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void explicitCurveEditPreflightNeverMutatesGraphOrPendingQueue(GameTestHelper h) {
    line(h);
    var original = through(h, M, new Vec3(1, 0, 0)).getFirst();
    var point =
        marker(
            original.graph,
            original.edge.node1,
            original.edge.node2,
            original.edge.getLength() / 2);
    var far = new BlockPos(2400032, 64, 2400032);
    point.owners = List.of(GlobalPos.of(h.getLevel().dimension(), far));
    long revision = original.graph.revision();
    var queue = manager(h).savedData().pending();
    boolean deferred = false;
    try {
      TrackPropagator.preflightChanges(h.getLevel(), List.of(h.absolutePos(M)));
    } catch (UnloadedTrackException expected) {
      deferred = expected.frontiers().contains(point.owners.getFirst());
    }
    h.assertTrue(
        deferred
            && original.graph.revision() == revision
            && queue.equals(manager(h).savedData().pending())
            && point.invalidations == 0,
        "Pre-world edit preflight is genuinely read-only");
    point.owners = List.of();
    TrackPropagator.preflightChanges(h.getLevel(), List.of(h.absolutePos(M)));
    h.assertTrue(
        original.graph.getPoint(MARKER, point.id) == point,
        "Ready preflight retains actual world point");
    h.succeed();
  }

  @GameTest
  public void unknownPhysicalFrontierAndNodeRemovalNeverForceChunksOrEraseOwners(GameTestHelper h) {
    var far = new BlockPos(2400048, 64, 2400048);
    boolean pending = false;
    try {
      ITrackBlock.walkConnectedTracks(
          h.getLevel(), new TrackNodeLocation(Vec3.atBottomCenterOf(far)).in(h.getLevel()), false);
    } catch (UnloadedTrackException expected) {
      pending = true;
    }
    h.assertTrue(
        pending
            && h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
        "Unknown native physical frontier remains unavailable without loading");
    var graph = RailGraphTestFixtures.line();
    var a = graph.locateNode(at(0, 0, 0));
    var b = graph.locateNode(at(4, 0, 0));
    var point = marker(graph, a, b, 2);
    point.owners = List.of(GlobalPos.of(h.getLevel().dimension(), far));
    long revision = graph.revision();
    pending = false;
    try {
      graph.removeNode(h.getLevel(), a.getLocation());
    } catch (UnloadedTrackException expected) {
      pending = true;
    }
    h.assertTrue(
        pending
            && graph.locateNode(a.getLocation()) == a
            && graph.getPoint(MARKER, point.id) == point
            && graph.revision() == revision
            && point.invalidations == 0,
        "Direct node removal preflights every real owner before any mutation");
    graph.validate();
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void straightWorldTargetUsesActualCompressedGraphAndRejectsZeroAxis(GameTestHelper h) {
    line(h);
    var pos = h.absolutePos(M);
    var location =
        TrackGraphHelper.getGraphLocationAt(
            h.getLevel(), pos, Direction.AxisDirection.NEGATIVE, new Vec3(1, 0, 0));
    h.assertTrue(
        location != null && location.graph != null,
        "Actual track block resolves to real graph edge");
    var a = location.graph.locateNode(location.edge.getFirst());
    var b = location.graph.locateNode(location.edge.getSecond());
    var edge = location.graph.getConnectionsFrom(a).get(b);
    h.assertTrue(
        edge.getPosition(location.graph, location.position / edge.getLength())
                .distanceToSqr(Vec3.atBottomCenterOf(pos))
            < 1e-8,
        "Resolved scalar addresses exact physical cell center");
    h.assertTrue(
        TrackGraphHelper.getGraphLocationAt(
                h.getLevel(), pos, Direction.AxisDirection.POSITIVE, Vec3.ZERO)
            == null,
        "NONE/zero axis is never a train target");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE, maxTicks = 40)
  public void nativeScheduledTrackBlockTickBuildsActualGraphWithoutCallingPropagatorDirectly(
      GameTestHelper h) {
    for (int i = 0; i < 7; i++) place(h, P.east(i), TrackShape.XO);
    h.succeedWhen(
        () -> {
          var edges = through(h, M, new Vec3(1, 0, 0));
          h.assertValueEqual(
              edges.size(),
              1,
              "Native scheduled TrackBlock tick created one persistent physical path");
          edges.getFirst().graph.validate();
        });
  }

  @GameTest(structure = STRUCTURE)
  public void actualReciprocalTrackBlockEntityCurvePropagatesTargetsAndSplitsOnRemoval(
      GameTestHelper h) {
    var q = P.offset(8, 0, 8);
    place(h, P, TrackShape.XO);
    place(h, q, TrackShape.ZO);
    h.setBlock(P, h.getBlockState(P).setValue(TrackBlock.HAS_BE, true));
    h.setBlock(q, h.getBlockState(q).setValue(TrackBlock.HAS_BE, true));
    var a = h.absolutePos(P);
    var b = h.absolutePos(q);
    var first = h.getBlockEntity(P, TrackBlockEntity.class);
    var second = h.getBlockEntity(q, TrackBlockEntity.class);
    var axisA = new Vec3(1, 0, 0);
    var axisB = new Vec3(0, 0, -1);
    var startA =
        ((ITrackBlock) h.getBlockState(P).getBlock())
            .getCurveStart(h.getLevel(), a, h.getBlockState(P), axisA);
    var startB =
        ((ITrackBlock) h.getBlockState(q).getBlock())
            .getCurveStart(h.getLevel(), b, h.getBlockState(q), axisB);
    var curve =
        new BezierConnection(
            Couple.create(a, b),
            Couple.create(startA, startB),
            Couple.create(axisA, axisB),
            Couple.create(UP, UP),
            true,
            false,
            TrackMaterial.ANDESITE);
    first.addConnection(curve);
    second.addConnection(curve.secondary());
    h.assertTrue(
        TrackPropagator.reconcile(h.getLevel(), a, h.getBlockState(P), false).complete()
            && TrackPropagator.reconcile(h.getLevel(), b, h.getBlockState(q), false).complete(),
        "Actual curve endpoints and null straight-direction metadata discover completely");
    var target =
        TrackGraphHelper.getBezierGraphLocationAt(
            h.getLevel(), a, Direction.AxisDirection.NEGATIVE, new BezierTrackPointLocation(b, 0));
    h.assertTrue(
        target != null && target.position == .5,
        "Actual primary TrackBE resolves first half-block target");
    var from = target.graph.locateNode(target.edge.getFirst());
    var to = target.graph.locateNode(target.edge.getSecond());
    var edge = target.graph.getConnectionsFrom(from).get(to);
    h.assertTrue(
        edge.isTurn()
            && edge.getTurn().equalsSansMaterial(curve)
            && target
                .graph
                .getConnectionsFrom(to)
                .get(from)
                .getTurn()
                .equalsSansMaterial(curve.secondary()),
        "Graph uses genuine reciprocal cubic geometry");
    target.graph.validate();
    TrackPropagator.preflightChanges(h.getLevel(), List.of(a, b));
    first.removeConnection(b);
    second.removeConnection(a);
    h.assertTrue(
        TrackPropagator.reconcile(h.getLevel(), a, rail(TrackShape.XO), false).complete()
            && TrackPropagator.reconcile(h.getLevel(), b, rail(TrackShape.ZO), false).complete(),
        "Both actual connection removals reconcile");
    h.assertTrue(
        manager(h).getTrackNetworks().values().stream()
            .noneMatch(
                graph ->
                    graph.getNodes().stream()
                        .anyMatch(
                            location ->
                                graph
                                    .getConnectionsFrom(graph.locateNode(location))
                                    .values()
                                    .stream()
                                    .anyMatch(
                                        value ->
                                            value.isTurn()
                                                && value.getTurn().equalsSansMaterial(curve)))),
        "Removed physical curve never remains in persistent topology");
    h.assertTrue(
        through(h, P, axisA).getFirst().graph != through(h, q, new Vec3(0, 0, 1)).getFirst().graph,
        "Remaining native endpoint rails have separate real graph owners");
    h.succeed();
  }
}
