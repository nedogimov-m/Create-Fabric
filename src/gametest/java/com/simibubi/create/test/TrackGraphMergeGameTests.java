package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.event.TrackGraphMergeEvent;
import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Authored native rail discovery -> public event -> actual ownership transfer fixtures. UNRUN. */
public final class TrackGraphMergeGameTests {
  private static final String STRUCTURE = "create_test:reservoir";
  private static final BlockPos P = new BlockPos(4, 6, 4), GAP = P.east(3);
  private record EdgeIn(TrackGraph graph, TrackEdge edge) {}
  private record Scene(RailwayGraphManager manager, TrackGraph from, TrackGraph into,
                       TrackEdge fromEdge, TrackEdge intoEdge, RailGraphTestFixtures.Marker marker) {}

  public TrackGraphMergeGameTests() { RailGraphTestFixtures.registerTypes(); }
  private static BlockState rail(TrackShape shape) {
    return AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, shape);
  }
  private static void place(GameTestHelper h, BlockPos pos, TrackShape shape) {
    h.setBlock(pos.below(), Blocks.STONE);
    h.setBlock(pos, rail(shape));
  }
  private static BlockPos origin(GameTestHelper h) {
    int boundary = Math.floorMod(h.absolutePos(GAP).getX(), 16);
    // Both old compressed endpoints at the gap must be ordinary degree-two vertices after fill,
    // so the point-owner fixture necessarily exercises real invalidation on every structure grid.
    return P.east(boundary == 0 || boundary == 15 ? 2 : 0);
  }
  private static BlockPos gap(GameTestHelper h) { return origin(h).east(3); }
  private static TrackPropagator.Result reconcile(GameTestHelper h, boolean removed) {
    return TrackPropagator.reconcile(h.getLevel(), h.absolutePos(gap(h)), rail(TrackShape.XO), removed);
  }
  private static List<EdgeIn> through(GameTestHelper h, BlockPos relative, Vec3 axis) {
    var center = Vec3.atBottomCenterOf(h.absolutePos(relative));
    var result = new ArrayList<EdgeIn>();
    for (var graph : RailwayGraphManager.get(h.getLevel()).getTrackNetworks().values())
      for (var location : graph.getNodes()) {
        if (!location.dimension.equals(h.getLevel().dimension())) continue;
        var node = graph.locateNode(location);
        for (var entry : graph.getConnectionsFrom(node).entrySet()) {
          var edge = entry.getValue();
          if (edge.isTurn() || edge.isInterDimensional()
              || node.getNetId() > entry.getKey().getNetId()) continue;
          var start = location.getLocation();
          var delta = entry.getKey().getLocation().getLocation().subtract(start);
          if (delta.lengthSqr() < 1e-12 || Math.abs(delta.normalize().dot(axis)) < .99999) continue;
          double t = center.subtract(start).dot(delta) / delta.lengthSqr();
          if (t >= 0 && t <= 1 && start.add(delta.scale(t)).distanceToSqr(center) < 1e-8)
            result.add(new EdgeIn(graph, edge));
        }
      }
    return result;
  }
  private static Scene scene(GameTestHelper h) {
    for (int i = 0; i < 7; i++) place(h, origin(h).east(i), TrackShape.XO);
    for (int i = 0; i < 7; i++)
      h.assertTrue(TrackPropagator.reconcile(h.getLevel(), h.absolutePos(origin(h).east(i)),
          rail(TrackShape.XO), false).complete(), "Native registered rails discover their actual geometry");
    h.setBlock(gap(h), Blocks.AIR);
    h.assertTrue(reconcile(h, true).complete(), "Real gap deletion splits the native graph");
    var left = through(h, origin(h).east(2), new Vec3(1, 0, 0)).getFirst();
    var right = through(h, origin(h).east(4), new Vec3(1, 0, 0)).getFirst();
    h.assertTrue(left.graph != right.graph, "The two physical rail spans have distinct native graph owners");
    var ordered = List.of(left, right).stream()
        .sorted(Comparator.comparing(value -> value.graph.id.toString())).toList();
    var from = ordered.get(1); var into = ordered.getFirst();
    var marker = RailGraphTestFixtures.MARKER.create();
    marker.setLocation(Couple.create(from.edge.node1.getLocation(), from.edge.node2.getLocation()),
        from.edge.getLength() / 2);
    from.graph.addPoint(RailGraphTestFixtures.MARKER, marker);
    place(h, gap(h), TrackShape.XO);
    return new Scene(RailwayGraphManager.get(h.getLevel()), from.graph, into.graph,
        from.edge, into.edge, marker);
  }
  private static boolean owns(TrackGraph graph, TrackEdge edge) {
    return graph.locateNode(edge.node1.getLocation()) == edge.node1
        && graph.locateNode(edge.node2.getLocation()) == edge.node2
        && graph.getConnectionsFrom(edge.node1).get(edge.node2) == edge;
  }
  private static void beforeTransfer(GameTestHelper h, Scene scene, TrackGraphMergeEvent event) {
    h.assertTrue(event.getGraphMergedFrom() == scene.from && event.getGraphMergedInto() == scene.into,
        "Source constructor/getters expose the exact real from/into pair");
    h.assertTrue(scene.manager.getTrackNetworks().get(scene.from.id) == scene.from
        && scene.manager.getTrackNetworks().get(scene.into.id) == scene.into
        && scene.from.manager() == scene.manager && scene.into.manager() == scene.manager,
        "Both event graphs are still the actual native manager-owned instances");
    h.assertTrue(owns(scene.from, scene.fromEdge) && owns(scene.into, scene.intoEdge)
        && scene.from.getPoint(RailGraphTestFixtures.MARKER, scene.marker.id) == scene.marker
        && scene.marker.graph() == scene.from && scene.marker.invalidations == 0,
        "Event precedes the first real edge deletion, point invalidation and ownership transfer");
  }
  private static void untouched(GameTestHelper h, Scene scene) {
    h.assertTrue(scene.manager.getTrackNetworks().get(scene.from.id) == scene.from
        && scene.manager.getTrackNetworks().get(scene.into.id) == scene.into
        && owns(scene.from, scene.fromEdge) && owns(scene.into, scene.intoEdge)
        && scene.marker.graph() == scene.from && scene.marker.invalidations == 0,
        "Native original graph/edge/point identities survive without manual graph restoration");
    scene.from.validate(); scene.into.validate();
  }

  @GameTest(structure = STRUCTURE)
  public void actualFabricEventPrecedesTransferAndNativeGapMerge(GameTestHelper h) throws Exception {
    var scene = scene(h); var calls = new AtomicInteger();
    try (var scope = TrackGraphMergeTestFixtures.observe(event -> {
      beforeTransfer(h, scene, event); calls.incrementAndGet();
    })) { h.assertTrue(reconcile(h, false).complete(), "Real physical gap merge completes"); }
    h.assertTrue(calls.get() == 1 && scene.from.isEmpty() && scene.from.manager() == null
        && !scene.manager.getTrackNetworks().containsKey(scene.from.id)
        && through(h, gap(h), new Vec3(1, 0, 0)).getFirst().graph == scene.into,
        "Exactly one public event accompanies the actual native transfer and donor retirement");
    scene.into.validate(); h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void exactListenerErrorLeavesOriginalNativeEdgesAndRetryUsesRealDiscovery(GameTestHelper h) throws Exception {
    var scene = scene(h); var exact = new TrackGraphMergeTestFixtures.ScopedFault(); boolean thrown = false;
    try (var scope = TrackGraphMergeTestFixtures.observe(event -> {
      beforeTransfer(h, scene, event); throw exact;
    })) { try { reconcile(h, false); } catch (TrackGraphMergeTestFixtures.ScopedFault fault) {
      h.assertTrue(fault == exact, "Original public listener Error remains primary"); thrown = true;
    } }
    h.assertTrue(thrown, "Actual real merge publication reached the scoped listener"); untouched(h, scene);
    h.assertTrue(reconcile(h, false).complete() && scene.from.isEmpty(),
        "Native retry performs the genuine transfer without restoring a serialized graph copy"); h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void listenerOwnUnloadedExceptionRemainsAnErrorInsteadOfInventedDeferral(GameTestHelper h) throws Exception {
    var scene = scene(h);
    var before = scene.manager.savedData().pending();
    var exact = new UnloadedTrackException(Set.of(
        GlobalPos.of(h.getLevel().dimension(), new BlockPos(25000000, 64, 25000000))));
    boolean thrown = false;
    try (var scope = TrackGraphMergeTestFixtures.observe(event -> {
      beforeTransfer(h, scene, event); throw exact;
    })) { try { reconcile(h, false); } catch (UnloadedTrackException failure) {
      h.assertTrue(failure == exact, "The listener's exact RuntimeException is not rewritten as our own frontier");
      thrown = true;
    } }
    h.assertTrue(thrown && scene.manager.savedData().pending().equals(before),
        "A failed public listener is never reported as a successful native retry request");
    untouched(h, scene); h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void callbackWorldGeometryChangeDefersBeforeAnyNativeEdgeDrop(GameTestHelper h) throws Exception {
    var scene = scene(h);
    try (var scope = TrackGraphMergeTestFixtures.observe(event -> {
      beforeTransfer(h, scene, event); h.setBlock(gap(h), rail(TrackShape.ZO));
    })) { h.assertFalse(reconcile(h, false).complete(), "Actual changed native geometry invalidates the old plan"); }
    untouched(h, scene);
    h.assertTrue(scene.manager.savedData().pending().stream()
        .anyMatch(request -> request.position().equals(GlobalPos.of(h.getLevel().dimension(), h.absolutePos(gap(h))))),
        "Real SavedData retains the physical reconciliation request");
    scene.manager.tick(h.getLevel().getServer());
    h.assertTrue(through(h, gap(h), new Vec3(1, 0, 0)).isEmpty()
        && !through(h, gap(h), new Vec3(0, 0, 1)).isEmpty(),
        "Native pending retry discovers the callback's actual Z rail and never installs the stale X link");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void sameUuidReplacementFromListenerKeepsTheActualNewNativeOwner(GameTestHelper h) throws Exception {
    var scene = scene(h); var replacement = new AtomicReference<TrackGraph>();
    try (var scope = TrackGraphMergeTestFixtures.observe(event -> {
      beforeTransfer(h, scene, event);
      var actual = new TrackGraph(scene.from.id);
      scene.from.transferAll(actual); scene.manager.removeGraph(scene.from); scene.manager.putGraph(actual);
      replacement.set(actual);
    })) { h.assertFalse(reconcile(h, false).complete(), "Same UUID cannot authorize a transfer from a replaced instance"); }
    var actual = replacement.get();
    h.assertTrue(actual != null && scene.manager.getTrackNetworks().get(actual.id) == actual
        && actual.manager() == scene.manager && owns(actual, scene.fromEdge)
        && actual.getPoint(RailGraphTestFixtures.MARKER, scene.marker.id) == scene.marker
        && scene.marker.graph() == actual && scene.marker.invalidations == 0
        && owns(scene.into, scene.intoEdge),
        "Outer propagation never empties or removes the listener's genuine replacement owner");
    scene.manager.tick(h.getLevel().getServer());
    h.assertTrue(!through(h, gap(h), new Vec3(1, 0, 0)).isEmpty(),
        "Real pending discovery can subsequently merge the actual replacement"); h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void callbackNewUnloadedPointOwnerIsPreflightedBeforeTransfer(GameTestHelper h) throws Exception {
    var scene = scene(h); var far = new BlockPos(25000000, 64, 25000000);
    var target = GlobalPos.of(h.getLevel().dimension(), far);
    try (var scope = TrackGraphMergeTestFixtures.observe(event -> {
      beforeTransfer(h, scene, event); scene.marker.owners = List.of(target);
    })) {
      var result = reconcile(h, false);
      h.assertTrue(!result.complete() && result.pending().contains(target),
          "Current declared native point owners are rechecked after the event");
    }
    untouched(h, scene);
    h.assertTrue(!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(h.getLevel(), far),
        "Revalidation never creates a native chunk ticket for the missing owner"); h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void reentrantNativeReconcileQueuesWithoutRecursiveMergePublication(GameTestHelper h) throws Exception {
    var scene = scene(h); var calls = new AtomicInteger();
    try (var scope = TrackGraphMergeTestFixtures.observe(event -> {
      beforeTransfer(h, scene, event); calls.incrementAndGet();
      h.assertFalse(reconcile(h, false).complete(), "Nested actual reconciliation queues instead of mutating the active plan");
      beforeTransfer(h, scene, event);
    })) { h.assertFalse(reconcile(h, false).complete(), "Outer transfer waits for the callback's genuine queued reconciliation"); }
    untouched(h, scene);
    scene.manager.tick(h.getLevel().getServer());
    h.assertTrue(calls.get() == 1 && scene.from.isEmpty()
        && !through(h, gap(h), new Vec3(1, 0, 0)).isEmpty(),
        "Native pending retry moves graph custody once, without recursive public event publication"); h.succeed();
  }
}
