package com.simibubi.create.test;

import static com.simibubi.create.test.RailGraphTestFixtures.*;

import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class RailGraphGameTests {
  public RailGraphGameTests() {
    RailGraphTestFixtures.registerTypes();
  }

  @GameTest
  public void nodeIdentityRetainsHalfGridDimensionAndSmoothing(GameTestHelper h) {
    var a = at(1.5, 7.9, -2.5);
    h.assertTrue(
        a.getLocation().equals(new Vec3(1.5, 7, -2.5)), "Source half-grid identity and floor Y");
    var copy = a.copy();
    copy.yOffsetPixels = 3;
    var nether = a.copy().in(Level.NETHER);
    h.assertValueEqual(
        Set.of(a, copy, nether).size(), 3, "Dimension and smoothing remain distinct keys");
    var graph = new TrackGraph();
    var node = node(graph, 1.5, 7, -2.5);
    var exposed = node.getLocation();
    exposed.yOffsetPixels = 9;
    h.assertTrue(graph.locateNode(a) == node, "External location copy cannot corrupt graph key");
    h.succeed();
  }

  @GameTest
  public void pointUsesOneActualInstanceOnBothDirectionsAndNativeRoundtrip(GameTestHelper h) {
    var graph = line();
    var a = graph.locateNode(at(0, 0, 0));
    var b = graph.locateNode(at(4, 0, 0));
    var point = marker(graph, a, b, 1.25);
    h.assertTrue(
        graph.getConnectionsFrom(a).get(b).getEdgeData().getPoints().getFirst() == point
            && graph.getConnectionsFrom(b).get(a).getEdgeData().getPoints().getFirst() == point,
        "Both directional lists own the same point");
    var loaded =
        decode(encode(graph, h.getLevel().registryAccess()), h.getLevel().registryAccess());
    var actual = loaded.getPoint(MARKER, point.id);
    var la = loaded.locateNode(a.getLocation());
    var lb = loaded.locateNode(b.getLocation());
    h.assertTrue(
        actual != point
            && actual.graph() == loaded
            && loaded.getConnectionsFrom(la).get(lb).getEdgeData().getPoints().getFirst() == actual
            && loaded.getConnectionsFrom(lb).get(la).getEdgeData().getPoints().getFirst() == actual,
        "Registry storage and both decoded edge refs agree by identity");
    loaded.validate();
    h.succeed();
  }

  @GameTest
  public void fullPointCodecReceivesOriginalNativeRegistryOps(GameTestHelper h) {
    var graph = line();
    var point = REGISTRY_MARKER.create();
    point.setLocation(Couple.create(at(0, 0, 0), at(4, 0, 0)), 2);
    point.biome =
        h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    graph.addPoint(REGISTRY_MARKER, point);
    var encoded = encode(graph, h.getLevel().registryAccess());
    h.assertTrue(
        TrackGraph.CODEC.parse(NbtOps.INSTANCE, encoded).error().isPresent(),
        "Bare NbtOps cannot resolve the registry holder");
    var loaded = decode(encoded, h.getLevel().registryAccess());
    h.assertTrue(
        loaded.getPoint(REGISTRY_MARKER, point.id).biome.is(Biomes.PLAINS),
        "Native biome registry holder survived full point codec");
    h.succeed();
  }

  @GameTest
  public void malformedDuplicateNodeAndMissingReciprocalAreRejected(GameTestHelper h) {
    var graph = line();
    var tag = encode(graph, h.getLevel().registryAccess());
    var nodes = tag.getListOrEmpty("Nodes");
    nodes.add(nodes.getFirst().copy());
    h.assertTrue(
        TrackGraph.CODEC
            .parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
            .error()
            .isPresent(),
        "Duplicate persistent vertex rejected");
    tag = encode(graph, h.getLevel().registryAccess());
    var node = (CompoundTag) tag.getListOrEmpty("Nodes").getFirst();
    node.put("Connections", new ListTag());
    h.assertTrue(
        TrackGraph.CODEC
            .parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
            .error()
            .isPresent(),
        "One-way persistent topology rejected");
    h.succeed();
  }

  @GameTest
  public void malformedPointReferenceAndUnknownTypeAreRejected(GameTestHelper h) {
    var graph = line();
    var unowned = MARKER.create();
    unowned.setLocation(Couple.create(at(0, 0, 0), at(8, 0, 0)), 1);
    rejects(h, () -> graph.addPoint(MARKER, unowned), "A missing actual endpoint is rejected before point publication");
    h.assertTrue(unowned.graph() == null && graph.getPoint(MARKER, unowned.id) == null,
        "Rejected point remains unbound and cannot enter the real graph registry");
    marker(graph, graph.locateNode(at(0, 0, 0)), graph.locateNode(at(4, 0, 0)), 2);
    var tag = encode(graph, h.getLevel().registryAccess());
    ((CompoundTag) tag.getListOrEmpty("Points").getFirst())
        .putString("Type", "create:missing_real_type");
    h.assertTrue(
        TrackGraph.CODEC
            .parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
            .error()
            .isPresent(),
        "Unknown real point type has no fallback");
    tag = encode(graph, h.getLevel().registryAccess());
    tag.put("Points", new ListTag());
    h.assertTrue(
        TrackGraph.CODEC
            .parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
            .error()
            .isPresent(),
        "Dangling edge point reference rejected");
    h.succeed();
  }

  @GameTest
  public void mergeAndSplitRetainActualNodeEdgeAndPointIdentities(GameTestHelper h) {
    var first = line();
    var originalA = first.locateNode(at(0, 0, 0));
    var originalB = first.locateNode(at(4, 0, 0));
    var point = marker(first, originalA, originalB, 2);
    var originalEdge = first.getConnectionsFrom(originalA).get(originalB);
    var second = new TrackGraph();
    var c = node(second, 12, 0, 0);
    var d = node(second, 16, 0, 0);
    link(second, c, d);
    second.transferAll(first);
    h.assertTrue(
        second.isEmpty() && first.locateNode(c.getLocation()) == c,
        "Merge moved the actual vertex");
    link(first, first.locateNode(at(8, 0, 0)), c);
    first.disconnectNodes(first.locateNode(at(8, 0, 0)), c);
    var split = first.findDisconnectedGraphs(h.getLevel(), null);
    h.assertValueEqual(split.size(), 1, "Exactly two connected components");
    h.assertTrue(
        first.getPoint(MARKER, point.id) == point
            && first.getConnectionsFrom(originalA).get(originalB) == originalEdge
            && point.graph() == first,
        "Live occupied component retains exact original objects");
    h.assertTrue(
        split.iterator().next().locateNode(c.getLocation()) == c,
        "Split retains original moved vertex identity");
    first.validate();
    split.iterator().next().validate();
    h.succeed();
  }

  @GameTest
  public void mergeConflictFailsBeforeAnyGraphMutation(GameTestHelper h) {
    var first = line();
    var second = line();
    long a = first.revision(), b = second.revision();
    rejects(
        h,
        () -> second.transferAll(first),
        "Duplicate edge cannot silently lose crossing/point data");
    h.assertTrue(
        first.revision() == a
            && second.revision() == b
            && first.getNodes().size() == 3
            && second.getNodes().size() == 3,
        "Both original owners untouched");
    first.validate();
    second.validate();
    h.succeed();
  }

  @GameTest
  public void travelReverseAndDeadEndUseRealAdjacency(GameTestHelper h) {
    var graph = line();
    var a = graph.locateNode(at(0, 0, 0));
    var b = graph.locateNode(at(4, 0, 0));
    var c = graph.locateNode(at(8, 0, 0));
    var point = new TravellingPoint(a, b, graph.getConnectionsFrom(a).get(b), 1, false);
    h.assertTrue(
        Math.abs(point.travel(graph, 5, point.random()) - 5) < 1e-8
            && point.node1 == b
            && point.node2 == c
            && Math.abs(point.position - 2) < 1e-8,
        "Crossed actual reciprocal node");
    var before = point.getPosition(graph);
    point.reverse(graph);
    h.assertTrue(
        before.distanceToSqr(point.getPosition(graph)) < 1e-8, "Reverse preserves world location");
    h.assertTrue(
        Math.abs(point.travel(graph, 20, point.random()) - 6) < 1e-8 && point.blocked,
        "Dead end clamps actual remaining travel");
    h.succeed();
  }

  @GameTest
  public void travelStopsAtActualPointInBothDirections(GameTestHelper h) {
    var graph = line();
    var a = graph.locateNode(at(0, 0, 0));
    var b = graph.locateNode(at(4, 0, 0));
    var signal = marker(graph, a, b, 2);
    var point = new TravellingPoint(a, b, graph.getConnectionsFrom(a).get(b), 1, false);
    var seen = new AtomicInteger();
    double moved =
        point.travel(
            graph,
            2,
            point.random(),
            (distance, entry) -> {
              h.assertTrue(
                  entry.getFirst() == signal && Math.abs(distance - 1) < 1e-8,
                  "Listener receives actual marker and traveled distance");
              seen.incrementAndGet();
              return true;
            });
    h.assertTrue(
        moved == 1 && point.position == 2 && seen.get() == 1, "Stopped exactly at source point");
    point.position = 3;
    h.assertTrue(
        point.travel(graph, -2, point.random(), (distance, entry) -> entry.getFirst() == signal)
                == -1
            && point.position == 2,
        "Backward listener uses mirrored source position");
    h.succeed();
  }

  @GameTest
  public void steerAndFollowChooseActualForkEdges(GameTestHelper h) {
    var graph = line();
    var b = graph.locateNode(at(4, 0, 0));
    var left = node(graph, 8, 0, -1);
    var right = node(graph, 8, 0, 1);
    link(graph, b, left);
    link(graph, b, right);
    var a = graph.locateNode(at(0, 0, 0));
    var p = new TravellingPoint(a, b, graph.getConnectionsFrom(a).get(b), 3, false);
    p.travel(graph, 2, p.steer(TravellingPoint.SteerDirection.LEFT, UP));
    h.assertTrue(p.node2 == left, "Left steering selects actual negative-z edge");
    var lead = new TravellingPoint(b, right, graph.getConnectionsFrom(b).get(right), 1, false);
    var follow = new TravellingPoint(a, b, graph.getConnectionsFrom(a).get(b), 3, false);
    var success = new ArrayList<Boolean>();
    follow.travel(graph, 2, follow.follow(lead, success::add));
    h.assertTrue(
        follow.node2 == right && success.equals(List.of(true)),
        "Follower selects lead's actual edge");
    h.succeed();
  }

  @GameTest
  public void unresolvedSavedPointRetainsEndpointPairAndResolvesLater(GameTestHelper h) {
    var graph = line();
    var a = graph.locateNode(at(0, 0, 0));
    var b = graph.locateNode(at(4, 0, 0));
    var point = new TravellingPoint(a, b, graph.getConnectionsFrom(a).get(b), 1.75, true);
    var dims = new DimensionPalette();
    var tag = point.write(dims);
    var waiting = TravellingPoint.read(tag, null, dims);
    h.assertTrue(
        waiting.write(dims).equals(tag) && waiting.position == 1.75 && waiting.upsideDown,
        "Missing graph retains complete source coordinates");
    rejects(h, () -> waiting.getPosition(null), "Unresolved graph cannot fabricate world position");
    var partial = new TrackGraph();
    h.assertTrue(!waiting.resolve(partial) && waiting.write(dims).equals(tag),
        "A real empty graph preserves unresolved coordinates without a null-key lookup");
    var partialA = node(partial, 0, 0, 0);
    h.assertTrue(!waiting.resolve(partial) && waiting.write(dims).equals(tag),
        "One genuine endpoint remains pending until the real second endpoint exists");
    h.assertTrue(partial.getConnection(Couple.create(partialA, null)) == null,
        "Missing reciprocal endpoint cannot become a real connection");
    partial.disconnectNodes(partialA, null);
    partial.validate();
    var migration = new TrackGraphLocation();
    migration.graph = partial;
    migration.edge = Couple.create(at(0, 0, 0), at(4, 0, 0));
    migration.position = 1.75;
    var pending = new ArrayList<TrackGraphLocation>();
    pending.add(migration);
    rejects(h, () -> waiting.migrateTo(pending), "Unresolved migration is rejected before consuming the receipt");
    h.assertTrue(pending.size() == 1 && pending.getFirst() == migration && waiting.write(dims).equals(tag),
        "Rejected migration retains exact saved coordinates and the original receipt");
    h.assertTrue(
        waiting.resolve(graph) && waiting.node1 == a && waiting.node2 == b,
        "Actual graph later resolves original endpoints");
    h.assertTrue(waiting.getPosition(graph).y == -1, "Upside-down normal offset");
    h.succeed();
  }

  @GameTest
  public void zeroLengthPortalHonorsBlockingAndCanEnterRealNextEdge(GameTestHelper h) {
    var graph = new TrackGraph();
    var a = node(graph, 0, 0, 0);
    var nether = at(0, 0, 0).in(Level.NETHER);
    graph.loadNode(nether, TrackGraph.nextNodeId(), UP);
    var b = graph.locateNode(nether);
    var cLoc = at(4, 0, 0).in(Level.NETHER);
    graph.loadNode(cLoc, TrackGraph.nextNodeId(), UP);
    var c = graph.locateNode(cLoc);
    link(graph, a, b);
    link(graph, b, c);
    var blocked = new TravellingPoint(a, b, graph.getConnectionsFrom(a).get(b), 0, false);
    h.assertTrue(
        blocked.travel(
                    graph,
                    1,
                    blocked.random(),
                    blocked.ignoreEdgePoints(),
                    blocked.ignoreTurns(),
                    ends -> true)
                == 0
            && blocked.blocked,
        "Blocked portal does not consume travel");
    var allowed = new TravellingPoint(a, b, graph.getConnectionsFrom(a).get(b), 0, false);
    h.assertTrue(
        allowed.travel(graph, 1, allowed.random()) == 1
            && allowed.node1 == b
            && allowed.node2 == c
            && allowed.position == 1,
        "Zero-length portal crosses into native next edge");
    graph.validate();
    h.succeed();
  }

  @GameTest
  public void savedDataPreservesNativeGraphAndCoalescedPendingWork(GameTestHelper h) {
    var data = new RailwaySavedData();
    var manager = new RailwayGraphManager(data);
    var graph = line();
    manager.putGraph(graph);
    var position = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(2, 2, 2)));
    var first = new RailwaySavedData.PendingRail(position, Blocks.STONE.defaultBlockState(), true);
    var latest = new RailwaySavedData.PendingRail(position, Blocks.AIR.defaultBlockState(), false);
    data.queue(first);
    data.queue(latest);
    h.assertTrue(
        data.isDirty() && data.pending().equals(List.of(latest)),
        "Actual SavedData owns dirty graph and latest request");
    var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var encoded = RailwaySavedData.CODEC.encodeStart(ops, data).getOrThrow();
    var loaded = RailwaySavedData.CODEC.parse(ops, encoded).getOrThrow();
    h.assertTrue(
        loaded.pending().equals(List.of(latest)) && loaded.getTrackNetworks().containsKey(graph.id),
        "Full native SavedData codec preserves owners");
    var nativeData = RailwaySavedData.load(h.getLevel().getServer());
    h.assertTrue(
        nativeData == RailwaySavedData.load(h.getLevel().getServer()),
        "Native DimensionDataStorage owns one data instance");
    h.succeed();
  }

  @GameTest
  public void managerPublishesValidatedImmutableMutationEvents(GameTestHelper h) {
    var manager = new RailwayGraphManager(new RailwaySavedData());
    var events = new ArrayList<RailwayGraphManager.Mutation>();
    manager.addListener(events::add);
    var graph = line();
    manager.putGraph(graph);
    manager.flushMutations();
    h.assertTrue(
        events.stream()
            .anyMatch(
                event ->
                    event.kind() == RailwayGraphManager.Kind.CHANGED
                        && event.graph().equals(graph.id)
                        && event.revision() == graph.revision()),
        "Actual graph revision published after validation");
    long prior = graph.revision();
    node(graph, 12, 0, 0);
    h.assertTrue(
        events.stream().noneMatch(event -> event.revision() > prior),
        "No half-mutated eager event");
    manager.flushMutations();
    h.assertTrue(
        events.getLast().revision() == graph.revision(), "Next validated batch has final revision");
    h.succeed();
  }
}
