package com.simibubi.create.test;

import static com.simibubi.create.test.RailGraphTestFixtures.*;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RailGeometryGameTests {
  public RailGeometryGameTests() {
    RailGraphTestFixtures.registerTypes();
  }

  @GameTest
  public void sourceQuarterTurnHandlesNormalsAndSecondaryAreGeometric(GameTestHelper h) {
    var c = curve();
    h.assertTrue(
        Math.abs(c.getRadius() - 8) < 1e-5
            && Math.abs(c.getHandleLength() - 8 * 4 / 3d * Math.tan(Math.PI / 8)) < 1e-4,
        "Source circular handle construction");
    h.assertTrue(c.getLength() > 12 && c.getLength() < 13, "Quarter-turn arc length");
    h.assertTrue(
        c.getPosition(0).equals(c.starts.getFirst())
            && c.getPosition(1).equals(c.starts.getSecond()),
        "Cubic uses exact source endpoints");
    for (int i = 0; i <= 10; i++) {
      double t = i / 10d;
      h.assertTrue(c.getNormal(t).distanceToSqr(UP) < 1e-8, "Planar up normal");
      h.assertTrue(
          c.getPosition(t).distanceToSqr(c.secondary().getPosition(1 - t)) < 1e-8,
          "Secondary reverses same physical curve");
    }
    h.succeed();
  }

  @GameTest
  public void relativeTagsAndNativeStreamPreserveCurveOwnershipAndSmoothing(GameTestHelper h) {
    var c = curve();
    c.smoothing = Couple.create(-3, 5);
    var origin = new BlockPos(109, 27, -42);
    var loaded = new BezierConnection(c.write(origin), origin);
    h.assertTrue(
        c.equalsSansMaterial(loaded)
            && loaded.primary == c.primary
            && loaded.smoothing.equals(c.smoothing),
        "Relative native tag restores world endpoints and metadata");
    var buf = new FriendlyByteBuf(Unpooled.buffer());
    try {
      c.write(buf);
      var network = new BezierConnection(buf);
      h.assertTrue(
          c.equalsSansMaterial(network)
              && network.smoothing.equals(c.smoothing)
              && buf.readableBytes() == 0,
          "Actual stream consumes exact complete source curve");
    } finally {
      buf.release();
    }
    h.succeed();
  }

  @GameTest
  public void curveIteratorIsDetachedAndRasterisationCoversActualPath(GameTestHelper h) {
    var c = curve();
    var segments = new ArrayList<BezierConnection.Segment>();
    c.forEach(segments::add);
    h.assertValueEqual(
        segments.size(), c.getSegmentCount() + 1, "Source endpoint-inclusive samples");
    h.assertTrue(
        segments.getFirst() != segments.getLast()
            && segments.getFirst().index == 0
            && segments.getLast().index == c.getSegmentCount(),
        "Retained segments are independent values");
    for (var segment : segments)
      h.assertTrue(
          Double.isFinite(segment.position.lengthSqr())
              && Math.abs(segment.derivative.length() - 1) < 1e-6
              && c.getBounds().contains(segment.position),
          "Finite normalized geometry remains inside physical curve bounds");
    h.assertTrue(
        !c.rasterise().isEmpty() && c.rasterise().values().stream().allMatch(Double::isFinite),
        "Real source paving footprint computed");
    var lut = c.getStepLUT();
    float original = lut[0];
    lut[0] = 999;
    h.assertTrue(c.getStepLUT()[0] == original, "Public LUT cannot corrupt lazy runtime");
    h.assertTrue(c.getGirderItemCost() == 2 * c.getTrackItemCost(), "Source physical girder cost");
    h.succeed();
  }

  @GameTest
  public void malformedCurvePairsAxesMaterialsAndSegmentsAreRejected(GameTestHelper h) {
    var c = curve();
    var tag = (CompoundTag) BezierConnection.CODEC.encodeStart(NbtOps.INSTANCE, c).getOrThrow();
    tag.getListOrEmpty("Positions").removeLast();
    h.assertTrue(
        BezierConnection.CODEC.parse(NbtOps.INSTANCE, tag).error().isPresent(),
        "Curve requires exactly two endpoints");
    tag = (CompoundTag) BezierConnection.CODEC.encodeStart(NbtOps.INSTANCE, c).getOrThrow();
    tag.putString("Material", "create:unknown_material");
    h.assertTrue(
        BezierConnection.CODEC.parse(NbtOps.INSTANCE, tag).error().isPresent(),
        "Unknown track material never becomes andesite");
    rejects(
        h,
        () ->
            new BezierConnection(
                c.bePositions,
                c.starts,
                Couple.create(Vec3.ZERO, new Vec3(0, 0, -1)),
                c.normals,
                true,
                false,
                c.getMaterial()),
        "Zero axis rejected before geometry");
    rejects(
        h,
        () -> new BezierTrackPointLocation(BlockPos.ZERO, -1),
        "Negative curve target segment rejected");
    h.succeed();
  }

  @GameTest
  public void dimensionalNodeStreamRetainsLargeWorldHeightWithoutShortTruncation(GameTestHelper h) {
    var original = at(2.5, 45000, -8.5).in(Level.END);
    original.yOffsetPixels = -7;
    var dimensions = new DimensionPalette();
    var buf = new FriendlyByteBuf(Unpooled.buffer());
    try {
      original.send(buf, dimensions);
      var decoded = TrackNodeLocation.receive(buf, dimensions);
      h.assertTrue(
          decoded.equals(original) && decoded.getY() == 90000,
          "Actual stream retains full height, dimension and smoothing");
    } finally {
      buf.release();
    }
    rejects(h, () -> dimensions.decode(999), "Invalid palette index has no Overworld fallback");
    h.succeed();
  }

  @GameTest
  public void stableNormalInterpolationHandlesParallelAndOppositeVectors(GameTestHelper h) {
    h.assertTrue(
        RailMath.slerp(.5f, UP, UP).distanceToSqr(UP) < 1e-10, "Identical normals remain stable");
    var antipodal = RailMath.slerp(.5f, UP, UP.scale(-1));
    h.assertTrue(
        Double.isFinite(antipodal.lengthSqr())
            && Math.abs(antipodal.length() - 1) < 1e-8
            && Math.abs(antipodal.dot(UP)) < 1e-6,
        "Opposite normals rotate through a finite orthogonal unit vector");
    h.succeed();
  }

  @GameTest
  public void actualCurvedCrossingHasFourReciprocalRecordsAndRemovalCleansThem(GameTestHelper h) {
    var graph = new TrackGraph();
    var c = curve();
    var a = node(graph, c.starts.getFirst().x, 0, c.starts.getFirst().z);
    var b = node(graph, c.starts.getSecond().x, 0, c.starts.getSecond().z);
    graph.connectNodes(a, b, c, c.getMaterial());
    var x = node(graph, 0, 0, 4.5);
    var y = node(graph, 12, 0, 4.5);
    link(graph, x, y);
    var curved = graph.getConnectionsFrom(a).get(b);
    var linear = graph.getConnectionsFrom(x).get(y);
    h.assertTrue(
        curved.getEdgeData().hasIntersections() && linear.getEdgeData().hasIntersections(),
        "Actual cubic intersects actual straight edge");
    var id = curved.getEdgeData().getIntersections().getFirst().id;
    for (var edge :
        List.of(
            curved, graph.getConnectionsFrom(b).get(a), linear, graph.getConnectionsFrom(y).get(x)))
      h.assertTrue(
          edge.getEdgeData().getIntersections().stream().anyMatch(value -> value.id.equals(id)),
          "One crossing UUID on all four directed edges");
    graph.validate();
    graph.disconnectNodes(a, b);
    h.assertTrue(
        !linear.getEdgeData().hasIntersections()
            && !graph.getConnectionsFrom(y).get(x).getEdgeData().hasIntersections(),
        "Deleting physical curve removes reciprocal crossing metadata");
    h.succeed();
  }
}
