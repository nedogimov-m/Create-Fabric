package com.simibubi.create.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.*;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.graph.sync.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Proposed independent native worlds: real tracks -> actual payloads -> cache -> loaded targeting.
 * It never calls a client packet receiver/cache accept directly or invents a train entity.
 */
public final class RailGraphSyncRenderingTest implements FabricClientGameTest {
  private static final BlockPos P = new BlockPos(29010, 6, 6), M = P.east(4), Q = P.offset(8, 0, 8);

  private record Stamp(
      UUID id,
      int netId,
      long revision,
      Map<TrackNodeLocation, Integer> nodes,
      TrackGraph original) {}

  @Override
  public void runTest(ClientGameTestContext context) {
    if (Boolean.getBoolean("create.test.onlyPipes")
        && !Boolean.getBoolean("create.test.onlyRailGraphSync")) return;
    context.runOnClient(
        client -> {
          var backend = RenderSystem.getDevice().getDeviceInfo().backendName();
          if (!backend.toLowerCase(Locale.ROOT).contains("vulkan"))
            throw new AssertionError("Actual native graph/track scene needs Vulkan: " + backend);
        });
    var firstNonce = new AtomicReference<UUID>();
    var firstEpoch = new AtomicReference<UUID>();
    var retired = new AtomicReference<UUID>();
    try (var world =
        context
            .worldBuilder()
            .adjustSettings(
                creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
            .create()) {
      ready(context);
      firstNonce.set(context.computeOnClient(client -> RailwayGraphClient.view().nonce()));
      firstEpoch.set(context.computeOnClient(client -> RailwayGraphClient.view().epoch()));
      world.getServer().runCommand("tp @p 29014.5 10 12.5");
      context.waitFor(client -> client.level.isLoaded(P) && client.level.isLoaded(Q), 400);
      world.getServer().runCommand("fill 29008 5 3 29022 5 15 minecraft:smooth_quartz");
      for (int i = 0; i < 9; i++) block(world, P.east(i), "create:track[shape=xo]");
      var initial = stamp(context, world, M);
      retired.set(initial.id);
      matches(context, M, initial);
      screenshot(context, "create-vulkan-rail-graph-native-login-update-and-straight-target");
      block(world, M, "minecraft:air");
      waitServer(
          context,
          world,
          level -> {
            var a = target(level, P);
            var b = target(level, P.east(8));
            return a != null && b != null && a.graph != b.graph;
          },
          300,
          "Actual middle rail removal did not split real server graphs");
      var left = stamp(context, world, P);
      var right = stamp(context, world, P.east(8));
      matches(context, P, left);
      matches(context, P.east(8), right);
      context.runOnClient(
          client -> {
            if (ClientGraphTargeting.straight(
                    M, Direction.AxisDirection.NEGATIVE, new Vec3(1, 0, 0))
                != null) throw new AssertionError("Client targets removed physical gap");
          });
      block(world, M, "create:track[shape=xo]");
      waitServer(
          context,
          world,
          level -> {
            var a = target(level, P);
            var b = target(level, P.east(8));
            return a != null && b != null && a.graph == b.graph;
          },
          300,
          "Actual gap placement did not merge real graph owners");
      var merged = stamp(context, world, M);
      matches(context, M, merged);
      context.waitFor(client -> RailwayGraphClient.view().graphs().size() == 1, 300);
      world
          .getServer()
          .runOnServer(
              ignored -> {
                var level = world.getConnection().getServerLevel();
                level.setBlockAndUpdate(
                    P,
                    AllBlocks.TRACK
                        .defaultBlockState()
                        .setValue(TrackBlock.SHAPE, TrackShape.XO)
                        .setValue(TrackBlock.HAS_BE, true));
                level.setBlockAndUpdate(Q.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(
                    Q,
                    AllBlocks.TRACK
                        .defaultBlockState()
                        .setValue(TrackBlock.SHAPE, TrackShape.ZO)
                        .setValue(TrackBlock.HAS_BE, true));
                var first = (TrackBlockEntity) level.getBlockEntity(P);
                var second = (TrackBlockEntity) level.getBlockEntity(Q);
                var axisA = new Vec3(1, 0, 0);
                var axisB = new Vec3(0, 0, -1);
                var up = new Vec3(0, 1, 0);
                var a =
                    ((ITrackBlock) level.getBlockState(P).getBlock())
                        .getCurveStart(level, P, level.getBlockState(P), axisA);
                var b =
                    ((ITrackBlock) level.getBlockState(Q).getBlock())
                        .getCurveStart(level, Q, level.getBlockState(Q), axisB);
                var curve =
                    new BezierConnection(
                        Couple.create(P, Q),
                        Couple.create(a, b),
                        Couple.create(axisA, axisB),
                        Couple.create(up, up),
                        true,
                        false,
                        TrackMaterial.ANDESITE);
                first.addConnection(curve);
                second.addConnection(curve.secondary());
              });
      var curveStamp = new AtomicReference<Stamp>();
      waitServer(
          context,
          world,
          level -> {
            var location =
                TrackGraphHelper.getBezierGraphLocationAt(
                    level, P, Direction.AxisDirection.NEGATIVE, new BezierTrackPointLocation(Q, 0));
            if (location == null) return false;
            curveStamp.set(capture(location.graph));
            return true;
          },
          300,
          "Actual reciprocal world curve did not reach real server graph");
      context.waitFor(
          client -> {
            var actual =
                ClientGraphTargeting.curve(
                    P, Direction.AxisDirection.NEGATIVE, new BezierTrackPointLocation(Q, 0));
            return actual != null
                && actual.graph.id.equals(curveStamp.get().id)
                && actual.graph.revision() == curveStamp.get().revision;
          },
          400);
      context.runOnClient(
          client -> {
            var actual =
                ClientGraphTargeting.curve(
                    P, Direction.AxisDirection.NEGATIVE, new BezierTrackPointLocation(Q, 0));
            if (actual.graph == curveStamp.get().original || actual.position != .5)
              throw new AssertionError("Curve target is not independent native client geometry");
            actual.graph.validate();
          });
      screenshot(context, "create-vulkan-rail-graph-actual-reciprocal-curve-native-sync-target");
      var oldView = context.computeOnClient(client -> RailwayGraphClient.view());
      var oldGraph = oldView.graphs().get(curveStamp.get().id);
      var reload = context.computeOnClient(client -> client.reloadResourcePacks());
      context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 600);
      reload.join();
      context.runOnClient(
          client -> {
            var current = RailwayGraphClient.view();
            if (!current.ready()
                || !current.nonce().equals(firstNonce.get())
                || !current.epoch().equals(firstEpoch.get())
                || current.graphs().get(curveStamp.get().id) != oldGraph)
              throw new AssertionError(
                  "Resource reload erased or replaced unchanged actual graph revision");
          });
      // A real read-only native request tests snapshot transport, never direct cache publication.
      long before = context.computeOnClient(client -> RailwayGraphClient.completedSnapshots());
      context.waitTicks(21);
      context.runOnClient(client -> RailwayGraphClient.requestSnapshot());
      context.waitFor(
          client ->
              RailwayGraphClient.view().ready()
                  && RailwayGraphClient.completedSnapshots() > before
                  && ClientGraphTargeting.curve(
                          P, Direction.AxisDirection.NEGATIVE, new BezierTrackPointLocation(Q, 0))
                      != null,
          300);
      var oldFirstCurve = new AtomicReference<TrackBlockEntity>();
      var oldSecondCurve = new AtomicReference<TrackBlockEntity>();
      world
          .getServer()
          .runOnServer(
              ignored -> {
                var level = world.getConnection().getServerLevel();
                oldFirstCurve.set((TrackBlockEntity) LoadedLevelAccess.blockEntity(level, P));
                oldSecondCurve.set((TrackBlockEntity) LoadedLevelAccess.blockEntity(level, Q));
              });
      world.getServer().runCommand("tp @p 30400.5 10 12.5");
      waitServer(
          context,
          world,
          level ->
              level.getChunkSource().getChunkNow(P.getX() >> 4, P.getZ() >> 4) == null
                  && level.getChunkSource().getChunkNow(Q.getX() >> 4, Q.getZ() >> 4) == null,
          800,
          "Actual native track chunks stayed visible after player departed");
      // The real public native full-save path completes queued native chunk teardown.
      world
          .getServer()
          .runOnServer(
              ignored -> {
                if (!world
                    .getConnection()
                    .getServerLevel()
                    .getServer()
                    .saveEverything(true, true, true))
                  throw new AssertionError("Actual native track world save/flush was refused");
              });
      waitServer(
          context,
          world,
          level -> oldFirstCurve.get().isRemoved() && oldSecondCurve.get().isRemoved(),
          800,
          "Actual original curve BEs were not removed by native chunk unload");
      context.waitFor(client -> !client.level.isLoaded(P) && !client.level.isLoaded(Q), 600);
      context.runOnClient(
          client -> {
            var current = RailwayGraphClient.view();
            if (!current.ready()
                || current.graphs().get(curveStamp.get().id) != oldGraph
                || ClientGraphTargeting.straight(
                        M, Direction.AxisDirection.NEGATIVE, new Vec3(1, 0, 0))
                    != null
                || ClientGraphTargeting.curve(
                        P, Direction.AxisDirection.NEGATIVE, new BezierTrackPointLocation(Q, 0))
                    != null)
              throw new AssertionError(
                  "Unloaded physical tracks changed full cache or remained targetable");
          });
      world.getServer().runCommand("tp @p 29014.5 10 12.5");
      waitServer(
          context,
          world,
          level -> {
            var first = LoadedLevelAccess.blockEntity(level, P);
            var second = LoadedLevelAccess.blockEntity(level, Q);
            return first instanceof TrackBlockEntity a
                && second instanceof TrackBlockEntity b
                && a != oldFirstCurve.get()
                && b != oldSecondCurve.get()
                && a.getConnections().containsKey(Q)
                && b.getConnections().containsKey(P);
          },
          600,
          "Native return did not restore reciprocal curves into distinct actual BEs");
      context.waitFor(
          client -> {
            var actual =
                ClientGraphTargeting.curve(
                    P, Direction.AxisDirection.NEGATIVE, new BezierTrackPointLocation(Q, 0));
            return actual != null && actual.graph.id.equals(curveStamp.get().id);
          },
          600);
      screenshot(
          context, "create-vulkan-rail-graph-real-native-unload-return-retains-graph-and-curve");
      for (int i = 0; i < 9; i++) block(world, P.east(i), "minecraft:air");
      block(world, Q, "minecraft:air");
      waitServer(
          context,
          world,
          level -> RailwayGraphManager.get(level).getTrackNetworks().isEmpty(),
          400,
          "Real native track destruction left stale server graph owners");
      context.waitFor(
          client ->
              RailwayGraphClient.view().ready() && RailwayGraphClient.view().graphs().isEmpty(),
          400);
      screenshot(
          context, "create-vulkan-rail-graph-real-removal-clears-complete-client-membership");
    }
    context.waitFor(
        client ->
            RailwayGraphClient.view().nonce() == null
                && !RailwayGraphClient.view().ready()
                && RailwayGraphClient.view().graphs().isEmpty(),
        300);
    try (var second =
        context
            .worldBuilder()
            .adjustSettings(
                creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
            .create()) {
      ready(context);
      context.runOnClient(
          client -> {
            var current = RailwayGraphClient.view();
            if (current.nonce().equals(firstNonce.get())
                || current.epoch().equals(firstEpoch.get())
                || current.graphs().containsKey(retired.get()))
              throw new AssertionError("New actual connection inherited prior nonce/epoch/graph");
          });
      second.getServer().runCommand("tp @p 29014.5 10 12.5");
      context.waitFor(client -> client.level.isLoaded(P), 400);
      second.getServer().runCommand("setblock 29010 5 6 minecraft:stone");
      block(second, P, "create:track[shape=xo]");
      matches(context, P, stamp(context, second, P));
      screenshot(context, "create-vulkan-rail-graph-fresh-native-world-login-after-disconnect");
    }
    context.waitFor(
        client ->
            RailwayGraphClient.view().nonce() == null
                && RailwayGraphClient.view().graphs().isEmpty(),
        300);
  }

  private static void ready(ClientGameTestContext context) {
    context.waitFor(
        client ->
            client.player != null
                && RailwayGraphClient.view().ready()
                && RailwayGraphClient.view().epoch() != null,
        400);
  }

  private static TrackGraphLocation target(ServerLevel level, BlockPos pos) {
    return TrackGraphHelper.getGraphLocationAt(
        level, pos, Direction.AxisDirection.NEGATIVE, new Vec3(1, 0, 0));
  }

  private static Stamp capture(TrackGraph graph) {
    var nodes = new LinkedHashMap<TrackNodeLocation, Integer>();
    for (var location : graph.getNodes())
      nodes.put(location, graph.locateNode(location).getNetId());
    return new Stamp(graph.id, graph.getNetId(), graph.revision(), Map.copyOf(nodes), graph);
  }

  private static Stamp stamp(
      ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos) {
    var stamp = new AtomicReference<Stamp>();
    waitServer(
        context,
        world,
        level -> {
          var location = target(level, pos);
          if (location == null) return false;
          stamp.set(capture(location.graph));
          return true;
        },
        300,
        "Actual registered track did not create a native server graph");
    return stamp.get();
  }

  private static void matches(ClientGameTestContext context, BlockPos pos, Stamp expected) {
    context.waitFor(
        client -> {
          var view = RailwayGraphClient.view();
          var graph = view.graphs().get(expected.id);
          return view.ready()
              && graph != null
              && graph.revision() == expected.revision
              && ClientGraphTargeting.straight(
                      pos, Direction.AxisDirection.NEGATIVE, new Vec3(1, 0, 0))
                  != null;
        },
        400);
    context.runOnClient(
        client -> {
          var graph = RailwayGraphClient.view().graphs().get(expected.id);
          if (graph == expected.original
              || graph.getNetId() != expected.netId
              || !graph.getNodes().equals(expected.nodes.keySet()))
            throw new AssertionError("Actual client graph membership/identity differs from server");
          for (var entry : expected.nodes.entrySet())
            if (graph.locateNode(entry.getKey()).getNetId() != entry.getValue())
              throw new AssertionError("Source native transport node ID changed");
          graph.validate();
        });
  }

  private static void block(TestSingleplayerContext world, BlockPos pos, String state) {
    world
        .getServer()
        .runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state);
  }

  private static void waitServer(
      ClientGameTestContext context,
      TestSingleplayerContext world,
      Predicate<ServerLevel> condition,
      int ticks,
      String message) {
    var ready = new AtomicBoolean();
    for (int tick = 0; tick < ticks && !ready.get(); tick++) {
      world
          .getServer()
          .runOnServer(
              ignored -> ready.set(condition.test(world.getConnection().getServerLevel())));
      if (!ready.get()) context.waitTicks(1);
    }
    if (!ready.get()) throw new AssertionError(message);
  }

  private static void screenshot(ClientGameTestContext context, String name) {
    context.getInput().lookAt(P);
    context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
    context.takeScreenshot(name);
  }
}
