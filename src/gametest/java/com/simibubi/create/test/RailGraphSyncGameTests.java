package com.simibubi.create.test;

import static com.simibubi.create.test.RailGraphTestFixtures.*;

import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.graph.sync.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.biome.Biomes;

/**
 * Proposed actual codec/identity/atomic-publication lifecycle tests, no fake gameplay consumers.
 */
public final class RailGraphSyncGameTests {
  public RailGraphSyncGameTests() {
    RailGraphTestFixtures.registerTypes();
  }

  private static GraphSyncFrame wire(GameTestHelper h, GraphSyncFrame frame) {
    return GraphSyncFrame.decode(
        frame.encode(h.getLevel().registryAccess()), h.getLevel().registryAccess());
  }

  private static GraphSyncFrame snapshot(TrackGraph graph, UUID epoch, long sequence) {
    return new GraphSyncFrame(
        epoch, sequence, true, List.of(GraphSyncEntry.capture(graph)), List.of());
  }

  private static RailwayGraphCache cache(
      GameTestHelper h, TrackGraph graph, UUID nonce, UUID epoch) {
    var cache = new RailwayGraphCache();
    cache.begin(nonce);
    h.assertTrue(
        cache.accept(nonce, wire(h, snapshot(graph, epoch, 0)), h.getLevel().registryAccess())
            == RailwayGraphCache.Outcome.APPLIED,
        "Real login snapshot publishes");
    return cache;
  }

  @GameTest
  public void nativeFramePreservesGraphNodePointIdentityAndFullRegistryPayload(GameTestHelper h) {
    var graph = line();
    var point = REGISTRY_MARKER.create();
    point.biome =
        h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    point.setLocation(Couple.create(at(0, 0, 0), at(4, 0, 0)), 2);
    graph.addPoint(REGISTRY_MARKER, point);
    int originalGraphGenerator = TrackGraph.graphNetIdGenerator.get();
    int originalNodeGenerator = TrackGraph.nodeNetIdGenerator.get();
    var decoded = wire(h, snapshot(graph, UUID.randomUUID(), 0));
    h.assertTrue(
        TrackGraph.graphNetIdGenerator.get() == originalGraphGenerator
            && TrackGraph.nodeNetIdGenerator.get() == originalNodeGenerator,
        "Native client decode must not consume authoritative integrated-server transport IDs");
    var actual = decoded.graphs().getFirst().graph();
    h.assertTrue(
        actual != graph
            && actual.id.equals(graph.id)
            && actual.getNetId() == graph.getNetId()
            && actual.revision() == graph.revision(),
        "Actual native source graph IDs and revision restored");
    for (var location : graph.getNodes())
      h.assertTrue(
          actual.locateNode(location).getNetId() == graph.locateNode(location).getNetId(),
          "Every exact source transport node ID retained");
    var copied = actual.getPoint(REGISTRY_MARKER, point.id);
    h.assertTrue(
        copied != point && copied.biome.is(Biomes.PLAINS) && copied.graph() == actual,
        "Complete actual point codec receives native biome RegistryOps");
    var a = actual.locateNode(at(0, 0, 0));
    var b = actual.locateNode(at(4, 0, 0));
    h.assertTrue(
        actual.getConnectionsFrom(a).get(b).getEdgeData().getPoints().getFirst() == copied
            && actual.getConnectionsFrom(b).get(a).getEdgeData().getPoints().getFirst() == copied,
        "Both network directions reference exactly one decoded point instance");
    h.succeed();
  }

  @GameTest
  public void nativeFragmentCodecAndOutOfOrderAssemblyDoNotPublishPartialFrame(GameTestHelper h) {
    var graph = line();
    var frame = snapshot(graph, UUID.randomUUID(), 0);
    var data = frame.encode(h.getLevel().registryAccess());
    var padded = new byte[GraphSyncPart.PART_BYTES * 2 + 17];
    new Random(45).nextBytes(padded);
    var nonce = UUID.randomUUID();
    var parts = GraphSyncPart.split(nonce, frame, padded);
    var assembly = new GraphSyncPart.Assembly(parts.getFirst());
    h.assertTrue(
        assembly.accept(parts.getLast()).isEmpty()
            && assembly.accept(parts.getLast()).isEmpty()
            && assembly.accept(parts.getFirst()).isEmpty(),
        "Missing and duplicate pieces never fabricate completion");
    var complete = assembly.accept(parts.get(1));
    h.assertTrue(
        complete.isPresent() && Arrays.equals(complete.get(), padded),
        "Out-of-order fragments assemble exactly once with full digest");
    var real = GraphSyncPart.split(nonce, frame, data).getFirst();
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      GraphSyncPart.CODEC.encode(buffer, real);
      var copied = GraphSyncPart.CODEC.decode(buffer);
      h.assertTrue(
          copied.nonce().equals(nonce)
              && copied.transfer().equals(real.transfer())
              && Arrays.equals(copied.bytes(), real.bytes())
              && buffer.readableBytes() == 0,
          "Actual registered native payload codec consumes exact fragment");
    } finally {
      buffer.release();
    }
    h.succeed();
  }

  @GameTest
  public void conflictingMixedAndCorruptedFragmentsAreRejectedWithoutPartialPublication(
      GameTestHelper h) {
    var frame = snapshot(line(), UUID.randomUUID(), 0);
    var bytes = new byte[GraphSyncPart.PART_BYTES + 9];
    var nonce = UUID.randomUUID();
    var parts = GraphSyncPart.split(nonce, frame, bytes);
    var assembly = new GraphSyncPart.Assembly(parts.getFirst());
    assembly.accept(parts.getFirst());
    var old = parts.getFirst();
    var changed = old.bytes();
    changed[0] = 3;
    var conflicting =
        new GraphSyncPart(
            nonce,
            old.epoch(),
            old.transfer(),
            old.sequence(),
            old.snapshot(),
            old.length(),
            old.index(),
            old.count(),
            old.digest(),
            changed);
    rejects(h, () -> assembly.accept(conflicting), "Conflicting duplicate source piece rejected");
    var mixed = GraphSyncPart.split(nonce, frame, bytes).getLast();
    rejects(h, () -> assembly.accept(mixed), "A different transfer cannot complete this snapshot");
    var last = parts.getLast();
    var corrupt = last.bytes();
    corrupt[0] = 1;
    rejects(
        h,
        () ->
            assembly.accept(
                new GraphSyncPart(
                    nonce,
                    last.epoch(),
                    last.transfer(),
                    last.sequence(),
                    last.snapshot(),
                    last.length(),
                    last.index(),
                    last.count(),
                    last.digest(),
                    corrupt)),
        "Complete byte count without correct digest is not completion");
    h.succeed();
  }

  @GameTest
  public void connectionNonceAndNativeDisconnectRejectOldLoginState(GameTestHelper h) {
    var graph = line();
    var epoch = UUID.randomUUID();
    var first = UUID.randomUUID();
    var cache = cache(h, graph, first, epoch);
    cache.disconnect();
    h.assertTrue(
        cache.view().graphs().isEmpty() && !cache.view().ready(),
        "Disconnect clears actual published graph ownership");
    var second = UUID.randomUUID();
    cache.begin(second);
    h.assertTrue(
        cache.accept(first, wire(h, snapshot(graph, epoch, 1)), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.IGNORED
            && cache.view().graphs().isEmpty(),
        "Late snapshot from old native connection cannot seed new cache");
    h.assertTrue(
        cache.accept(
                second,
                wire(h, snapshot(graph, UUID.randomUUID(), 0)),
                h.getLevel().registryAccess())
            == RailwayGraphCache.Outcome.APPLIED,
        "Actual fresh connection gets its own epoch and login snapshot");
    h.succeed();
  }

  @GameTest
  public void sourceSequenceGapKeepsCompleteTopologyUntilRealSnapshotRecovery(GameTestHelper h) {
    var graph = line();
    var nonce = UUID.randomUUID();
    var epoch = UUID.randomUUID();
    var cache = cache(h, graph, nonce, epoch);
    var original = cache.view().graphs().get(graph.id);
    node(graph, 12, 0, 0);
    var gap =
        new GraphSyncFrame(epoch, 2, false, List.of(GraphSyncEntry.capture(graph)), List.of());
    h.assertTrue(
        cache.accept(nonce, wire(h, gap), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.NEEDS_SNAPSHOT
            && !cache.view().ready()
            && cache.view().retryable()
            && cache.view().graphs().get(graph.id) == original,
        "Missing lifecycle never publishes a partial/newer subset");
    h.assertTrue(
        cache.accept(nonce, wire(h, snapshot(graph, epoch, 2)), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.APPLIED
            && cache.view().ready()
            && cache.view().graphs().get(graph.id).getNodes().size() == 4,
        "Actual full resync atomically replaces cache membership");
    h.succeed();
  }

  @GameTest
  public void regressedVersionOrWrongTransportIdentityRejectsEntireNativeBatch(GameTestHelper h) {
    var graph = line();
    var nonce = UUID.randomUUID();
    var epoch = UUID.randomUUID();
    var cache = cache(h, graph, nonce, epoch);
    var original = cache.view().graphs().get(graph.id);
    var unrelated = new TrackGraph();
    node(unrelated, 100, 0, 0);
    var invalid =
        new GraphSyncFrame(
            epoch,
            1,
            false,
            List.of(GraphSyncEntry.capture(unrelated)),
            List.of(new GraphSyncFrame.Removed(graph.id, graph.getNetId() + 1, graph.revision())));
    h.assertTrue(
        cache.accept(nonce, wire(h, invalid), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.REJECTED
            && cache.view().graphs().size() == 1
            && cache.view().graphs().get(graph.id) == original,
        "Earlier valid additions in a rejected batch never leak into publication");
    h.succeed();
  }

  @GameTest
  public void staleUpdateCannotResurrectDeletedActualGraph(GameTestHelper h) {
    var graph = line();
    var nonce = UUID.randomUUID();
    var epoch = UUID.randomUUID();
    var cache = cache(h, graph, nonce, epoch);
    var original = wire(h, snapshot(graph, epoch, 0)).graphs().getFirst();
    var deletion =
        new GraphSyncFrame(
            epoch,
            1,
            false,
            List.of(),
            List.of(new GraphSyncFrame.Removed(graph.id, graph.getNetId(), graph.revision() + 1)));
    h.assertTrue(
        cache.accept(nonce, wire(h, deletion), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.APPLIED
            && cache.view().graphs().isEmpty(),
        "Actual removed graph retires");
    var stale = new GraphSyncFrame(epoch, 2, false, List.of(original), List.of());
    h.assertTrue(
        cache.accept(nonce, wire(h, stale), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.REJECTED
            && cache.view().graphs().isEmpty(),
        "Higher transport sequence cannot resurrect stale graph revision");
    h.succeed();
  }

  @GameTest
  public void sameRevisionDuplicatePreservesClientGraphObjectAndEmptySnapshotRemovesIt(
      GameTestHelper h) {
    var graph = line();
    var nonce = UUID.randomUUID();
    var epoch = UUID.randomUUID();
    var cache = cache(h, graph, nonce, epoch);
    var original = cache.view().graphs().get(graph.id);
    var repeat =
        new GraphSyncFrame(epoch, 1, false, List.of(GraphSyncEntry.capture(graph)), List.of());
    h.assertTrue(
        cache.accept(nonce, wire(h, repeat), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.APPLIED
            && cache.view().graphs().get(graph.id) == original,
        "Same complete source revision retains client object identity");
    h.assertTrue(
        cache.accept(
                    nonce,
                    new GraphSyncFrame(epoch, 2, true, List.of(), List.of()),
                    h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.APPLIED
            && cache.view().graphs().isEmpty(),
        "Genuine empty login snapshot clears stale graph membership");
    h.succeed();
  }

  @GameTest
  public void completePublicationRejectsDuplicateGlobalGraphAndNodeTransportIds(GameTestHelper h) {
    var original = line();
    var nonce = UUID.randomUUID();
    var epoch = UUID.randomUUID();
    var duplicateGraph = new TrackGraph();
    node(duplicateGraph, 100, 0, 0);
    duplicateGraph.setNetId(original.getNetId());
    var cache = cache(h, original, nonce, epoch);
    var old = cache.view().graphs().get(original.id);
    var invalid =
        new GraphSyncFrame(
            epoch, 1, false, List.of(GraphSyncEntry.capture(duplicateGraph)), List.of());
    h.assertTrue(
        cache.accept(nonce, wire(h, invalid), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.REJECTED
            && cache.view().graphs().size() == 1
            && cache.view().graphs().get(original.id) == old,
        "Distinct source UUIDs cannot publish duplicate active graph network IDs");
    var duplicateNode = new TrackGraph();
    duplicateNode.loadNode(
        at(120, 0, 0),
        original.locateNode(at(0, 0, 0)).getNetId(),
        new net.minecraft.world.phys.Vec3(0, 1, 0));
    var invalidNodes =
        new GraphSyncFrame(
            epoch,
            2,
            true,
            List.of(GraphSyncEntry.capture(original), GraphSyncEntry.capture(duplicateNode)),
            List.of());
    h.assertTrue(
        cache.accept(nonce, wire(h, invalidNodes), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.REJECTED
            && cache.view().graphs().size() == 1
            && cache.view().graphs().get(original.id) == old,
        "Duplicate active node network IDs reject the entire full snapshot");
    h.succeed();
  }

  @GameTest
  public void changedContentWithoutRevisionAndInConnectionEpochChangeRetainOldTopology(
      GameTestHelper h) {
    var graph = line();
    var nonce = UUID.randomUUID();
    var epoch = UUID.randomUUID();
    var cache = cache(h, graph, nonce, epoch);
    var old = cache.view().graphs().get(graph.id);
    graph.color ^= 0x12345;
    var unversioned =
        new GraphSyncFrame(epoch, 1, false, List.of(GraphSyncEntry.capture(graph)), List.of());
    h.assertTrue(
        cache.accept(nonce, wire(h, unversioned), h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.REJECTED
            && cache.view().graphs().get(graph.id) == old,
        "Full payload cannot change at the same graph revision");
    h.assertTrue(
        cache.accept(
                    nonce,
                    wire(h, snapshot(graph, UUID.randomUUID(), 2)),
                    h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.REJECTED
            && cache.view().graphs().get(graph.id) == old
            && !cache.view().ready(),
        "Another server epoch cannot overwrite an active native connection");
    h.succeed();
  }

  @GameTest
  public void nativePublisherBatchesRealMergeRemovalAndSplitAfterManagerValidation(
      GameTestHelper h) {
    var manager = new RailwayGraphManager(new RailwaySavedData());
    var a = line();
    var b = new TrackGraph();
    var first = node(b, 12, 0, 0);
    var second = node(b, 16, 0, 0);
    link(b, first, second);
    manager.putGraph(a);
    manager.putGraph(b);
    manager.flushMutations();
    var publisher = new RailwayGraphSync.Publisher(manager);
    var nonce = UUID.randomUUID();
    var cache = new RailwayGraphCache();
    cache.begin(nonce);
    cache.accept(nonce, wire(h, publisher.snapshot()), h.getLevel().registryAccess());
    b.transferAll(a);
    manager.removeGraph(b);
    link(a, a.locateNode(at(8, 0, 0)), first);
    manager.flushMutations();
    var update = publisher.captureChanges(h.getLevel().registryAccess()).orElseThrow();
    h.assertTrue(
        update.frame().graphs().size() == 1 && update.frame().removed().size() == 1,
        "Real merge/update and removed source owner share one frame");
    h.assertTrue(
        cache.accept(
                    nonce,
                    GraphSyncFrame.decode(update.bytes(), h.getLevel().registryAccess()),
                    h.getLevel().registryAccess())
                == RailwayGraphCache.Outcome.APPLIED
            && cache.view().graphs().size() == 1
            && cache.view().graphs().containsKey(a.id),
        "Native client sees only complete merged membership");
    a.disconnectNodes(a.locateNode(at(8, 0, 0)), first);
    manager.updateSplitGraph(h.getLevel(), a);
    manager.flushMutations();
    var split = publisher.captureChanges(h.getLevel().registryAccess()).orElseThrow();
    cache.accept(
        nonce,
        GraphSyncFrame.decode(split.bytes(), h.getLevel().registryAccess()),
        h.getLevel().registryAccess());
    h.assertTrue(
        cache.view().graphs().size() == 2
            && cache.view().graphs().values().stream()
                    .mapToInt(graph -> graph.getNodes().size())
                    .sum()
                == 5,
        "Actual split IDs and complete topology arrive together");
    publisher.close();
    h.succeed();
  }

  @GameTest
  public void registryEncodeFailureDoesNotAcknowledgeOrLosePendingLifecycleBatch(GameTestHelper h) {
    var manager = new RailwayGraphManager(new RailwaySavedData());
    var publisher = new RailwayGraphSync.Publisher(manager);
    var graph = line();
    var point = REGISTRY_MARKER.create();
    point.biome =
        h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
    point.setLocation(Couple.create(at(0, 0, 0), at(4, 0, 0)), 2);
    graph.addPoint(REGISTRY_MARKER, point);
    manager.putGraph(graph);
    manager.flushMutations();
    rejects(
        h,
        () -> publisher.captureChanges(RegistryAccess.EMPTY),
        "Actual registry-bound biome cannot encode against absent native lookup");
    h.assertTrue(publisher.sequence() == 0, "Encoding failure does not consume native sequence");
    var retried = publisher.captureChanges(h.getLevel().registryAccess()).orElseThrow();
    h.assertTrue(
        retried.frame().sequence() == 1
            && retried.frame().graphs().getFirst().graph().id.equals(graph.id),
        "Same original pending lifecycle retries with real registry data");
    publisher.close();
    h.succeed();
  }
}
