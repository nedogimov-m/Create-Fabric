package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.bogey.StandardBogeyBlockEntity;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;

/**
 * Proposed genuine station selection/placement/editor/bogey/preflight/reload/unload scene.
 * Region33000..33064,z0..32; unload34400. No fake train or final assembly. UNRUN.
 */
final class StationRenderingTest {
  private static final BlockPos T = new BlockPos(33008, 5, 5),
      S = T.south(3),
      FIRST = T.east().above(),
      SECOND = T.east(4).above();

  static void run(ClientGameTestContext context, TestSingleplayerContext world) {
    var server = world.getServer();
    server.runCommand("gamemode spectator @p");
    server.runCommand("tp @p 33011.5 8 6.5");
    context.waitFor(client -> client.level.isLoaded(S) && client.level.isLoaded(T.east(22)), 500);
    waitServer(
        context,
        world,
        level -> {
          for (int chunkX = 33000 >> 4; chunkX <= 33064 >> 4; chunkX++)
            for (int chunkZ = 0; chunkZ <= 32 >> 4; chunkZ++)
              if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return false;
          return true;
        },
        500,
        "Actual native Station floor footprint did not become FULL before initial fill");
    context.waitFor(
        client -> NativeScreenshotReadiness.skyReady(client, T.west(4), S, T.east(22)), 300);
    server.runCommand("fill 33000 4 0 33064 4 32 minecraft:smooth_quartz");
    waitServer(
        context,
        world,
        level -> {
          for (int x = 33000; x <= 33064; x++)
            for (int z = 0; z <= 32; z++)
              if (!LoadedLevelAccess.blockState(level, new BlockPos(x, 4, z))
                  .is(net.minecraft.world.level.block.Blocks.SMOOTH_QUARTZ)) return false;
          return true;
        },
        500,
        "Actual native Station floor fill did not place all2145 original quartz blocks");
    server.runCommand("fill 33004 5 5 33028 5 5 create:track[shape=xo]");
    server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
    server.runCommand("item replace entity @p weapon.mainhand with create:track_station 3");
    stand(context, world, 33011.5, 6.5);
    context.waitFor(
        client -> client.player.getMainHandItem().is(AllBlocks.TRACK_STATION.asItem()), 200);
    use(context, T, Vec3.atBottomCenterOf(T).add(0, .1, 0));
    context.waitFor(
        client -> client.player.getMainHandItem().has(AllDataComponents.TRACK_TARGETING_SELECTION),
        250);
    context.runOnClient(
        client -> {
          var selected =
              client.player.getMainHandItem().get(AllDataComponents.TRACK_TARGETING_SELECTION);
          if (!selected.target().pos().equals(T) || !selected.front())
            throw new AssertionError("Actual native station item selected wrong track/direction");
        });
    use(context, S.below(), Vec3.atCenterOf(S.below()).add(0, .499, 0));
    var pointId = new AtomicReference<UUID>();
    waitServer(
        context,
        world,
        level -> {
          var station = station(level);
          if (station == null || station.getStation() == null) return false;
          pointId.set(station.targeting().id());
          return station.getStation().ownedBy(station)
              && station.getStation().graph().manager() == RailwayGraphManager.get(level);
        },
        300,
        "Real native item placement did not create the actual world-owned station point");
    context.waitFor(
        client ->
            LoadedLevelAccess.blockEntity(client.level, S) instanceof StationBlockEntity station
                && station.getStation() != null
                && station.targeting().id().equals(pointId.get())
                && client.player.getMainHandItem().getCount() == 2
                && !client
                    .player
                    .getMainHandItem()
                    .has(AllDataComponents.TRACK_TARGETING_SELECTION),
        300);
    verifyRenderer(context, false);
    screenshot(
        context, "create-vulkan-station-real-item-selection-placement-source-flag-and-marker");
    server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
    stand(context, world, 33008.5, 10.5);
    open(context);
    editName(context, "Vulkan Station");
    click(context, StationScreen::doorCoordinate);
    click(context, StationScreen::saveCoordinate);
    waitServer(
        context,
        world,
        level ->
            station(level).stationName().equals("Vulkan Station")
                && station(level).doorControl() == DoorControl.NORTH,
        200,
        "Actual native station typing/button did not persist real name and door control");
    context.waitFor(
        client ->
            client.gui.screen() instanceof StationScreen screen
                && screen.snapshot().name().equals("Vulkan Station")
                && screen.snapshot().doors() == DoorControl.NORTH,
        200);
    screenshot(context, "create-vulkan-station-native-name-door-editor");
    click(context, StationScreen::modeCoordinate);
    waitServer(
        context,
        world,
        level ->
            station(level).isAssembling()
                && station(level).publishedAreaLength() == 19
                && !station(level).getStation().canNavigateVia(null),
        250,
        "Actual native mode button did not own its real source strip/graph restriction");
    context.waitFor(
        client ->
            client.gui.screen() instanceof StationScreen screen && screen.snapshot().assembling(),
        200);
    close(context);
    server.runCommand("item replace entity @p weapon.mainhand with create:railway_casing 4");
    stand(context, world, 33011.5, 7.5);
    context.waitFor(
        client -> client.player.getMainHandItem().is(AllBlocks.RAILWAY_CASING.asItem()), 200);
    use(context, T.east(), Vec3.atBottomCenterOf(T.east()).add(0, .1, 0));
    waitServer(
        context,
        world,
        level -> LoadedLevelAccess.blockState(level, FIRST).is(AllBlocks.SMALL_BOGEY),
        200,
        "Actual assembly-area native casing input did not create the first real bogey");
    context.waitFor(client -> client.player.getMainHandItem().getCount() == 3, 200);
    // Empty-hand spacing refusal is observed through native dispatch; stock-bearing kernel
    // rejection
    // is separately checked by StationGameTests so vanilla BlockItem fallback cannot obscure it.
    server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
    stand(context, world, 33012.5, 7.5);
    context.waitFor(client -> client.player.getMainHandItem().isEmpty(), 150);
    use(context, T.east(3), Vec3.atBottomCenterOf(T.east(3)).add(0, .1, 0));
    context.waitTicks(8);
    server.runOnServer(
        ignored -> {
          var level = world.getConnection().getServerLevel();
          var area = StationAssemblyArea.discover(station(level));
          if (area.isValidBogeyOffset(2)
              || !LoadedLevelAccess.blockState(level, T.east(3).above()).isAir())
            throw new AssertionError("Source2-block bogey exclusion failed in real native world");
        });
    server.runCommand("item replace entity @p weapon.mainhand with create:railway_casing 3");
    context.waitFor(
        client -> client.player.getMainHandItem().is(AllBlocks.RAILWAY_CASING.asItem()), 150);
    use(context, T.east(4), Vec3.atBottomCenterOf(T.east(4)).add(0, .1, 0));
    waitServer(
        context,
        world,
        level -> LoadedLevelAccess.blockState(level, SECOND).is(AllBlocks.SMALL_BOGEY),
        200,
        "Actual source3-block spacing did not allow second native bogey");
    context.waitFor(client -> client.player.getMainHandItem().getCount() == 2, 150);
    server.runOnServer(
        ignored -> {
          var level = world.getConnection().getServerLevel();
          for (var pos : List.of(FIRST, SECOND)) {
            var bogey = bogey(level, pos);
            var data = bogey.getBogeyData();
            data.putString("NativeStationMarker", "Exact original station bogey payload");
            bogey.setBogeyData(data);
          }
        });
    server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
    stand(context, world, 33008.5, 10.5);
    open(context);
    click(context, StationScreen::preflightCoordinate);
    context.waitFor(
        client ->
            client.gui.screen() instanceof StationScreen screen
                && screen.snapshot().preflight()
                    == StationAssemblyPreflight.Status.READY_FOR_CARRIAGE_CAPTURE,
        400);
    server.runOnServer(
        ignored -> {
          var level = world.getConnection().getServerLevel();
          var player = level.getServer().getPlayerList().getPlayers().getFirst();
          var report = StationAssemblyPreflight.check(station(level), player);
          if (!report.readyForCapture()
              || report.plan().wheelPoints().size() != 4
              || !report.plan().stillCurrent(station(level), player)
              || bogey(level, FIRST) == null
              || bogey(level, SECOND) == null)
            throw new AssertionError(
                "Real source wheel preflight is incomplete or captured world before actual Carriage"
                    + " implementation");
        });
    screenshot(
        context,
        "create-vulkan-station-real-layout-preflight-final-train-assembly-explicitly-unavailable");
    close(context);
    context.waitTicks(25);
    verifyRenderer(context, true);
    look(context, Vec3.atCenterOf(T.east(4)));
    screenshot(
        context, "create-vulkan-station-source-assembly-flag-legal-offsets-and-physical-bogeys");
    var originalModel =
        context.computeOnClient(
            client -> client.getModelManager().getModel(StationModels.FLAG_ASSEMBLE));
    var reload = context.computeOnClient(client -> client.reloadResourcePacks());
    context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 600);
    reload.join();
    context.runOnClient(
        client -> {
          if (client.getModelManager().getModel(StationModels.FLAG_ASSEMBLE) == originalModel)
            throw new AssertionError("Native resource reload retained old station flag model");
        });
    verifyRenderer(context, true);
    var oldStation = new AtomicReference<StationBlockEntity>();
    var oldFirst = new AtomicReference<StandardBogeyBlockEntity>();
    var oldSecond = new AtomicReference<StandardBogeyBlockEntity>();
    server.runOnServer(
        ignored -> {
          var level = world.getConnection().getServerLevel();
          oldStation.set(station(level));
          oldFirst.set(bogey(level, FIRST));
          oldSecond.set(bogey(level, SECOND));
        });
    server.runCommand("gamemode spectator @p");
    server.runCommand("tp @p 34400 20 34400");
    waitServer(
        context,
        world,
        level ->
            level.getChunkSource().getChunkNow(S.getX() >> 4, S.getZ() >> 4) == null
                && level.getChunkSource().getChunkNow(SECOND.getX() >> 4, SECOND.getZ() >> 4)
                    == null,
        800,
        "Actual station/bogey source chunks failed to leave native FULL state");
    NativeFixtureChunkUnload.finish(
        context, world, oldStation.get(), oldFirst.get(), oldSecond.get());
    server.runCommand("tp @p 33008.5 9 10.5");
    waitServer(
        context,
        world,
        level -> {
          var station = station(level);
          var first = bogey(level, FIRST);
          var second = bogey(level, SECOND);
          return station != null
              && station != oldStation.get()
              && station.getStation() != null
              && station.targeting().id().equals(pointId.get())
              && station.stationName().equals("Vulkan Station")
              && station.doorControl() == DoorControl.NORTH
              && station.isAssembling()
              && station.lastStatus() == StationAssemblyPreflight.Status.NOT_CHECKED
              && first != null
              && second != null
              && first != oldFirst.get()
              && second != oldSecond.get()
              && first
                  .getBogeyData()
                  .getStringOr("NativeStationMarker", "")
                  .equals("Exact original station bogey payload")
              && second
                  .getBogeyData()
                  .getStringOr("NativeStationMarker", "")
                  .equals("Exact original station bogey payload");
        },
        600,
        "True native unload/return reused owners, lost actual station/bogey data or persisted stale"
            + " preflight readiness");
    context.waitFor(
        client ->
            LoadedLevelAccess.blockEntity(client.level, S) instanceof StationBlockEntity station
                && station.getStation() != null
                && station.publishedBogeys().size() == 2,
        300);
    context.waitTicks(25);
    verifyRenderer(context, true);
    screenshot(
        context,
        "create-vulkan-station-distinct-native-owner-bogeys-and-graph-point-after-true-chunk-return");
    server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
    stand(context, world, 33008.5, 10.5);
    open(context);
    click(context, StationScreen::preflightCoordinate);
    context.waitFor(
        client ->
            client.gui.screen() instanceof StationScreen screen
                && screen.snapshot().preflight()
                    == StationAssemblyPreflight.Status.READY_FOR_CARRIAGE_CAPTURE,
        300);
    close(context);
    server.runCommand("item replace entity @p weapon.mainhand with create:wrench");
    context.waitFor(client -> client.player.getMainHandItem().is(AllItems.WRENCH), 150);
    context.getInput().holdKey(options -> options.keyShift);
    try {
      use(context, S, Vec3.atCenterOf(S).add(0, .25, 0));
    } finally {
      context.getInput().releaseKey(options -> options.keyShift);
    }
    waitServer(
        context,
        world,
        level -> {
          if (!LoadedLevelAccess.blockState(level, S).isAir()) return false;
          for (var graph : RailwayGraphManager.get(level).getTrackNetworks().values())
            if (graph.getPoint(StationEdgePoints.STATION, pointId.get()) != null) return false;
          return StationAssemblyAreas.ownersAt(level, T.east()).isEmpty();
        },
        250,
        "Actual survival native wrench destruction did not retire station point/assembly"
            + " authority");
  }

  private static void verifyRenderer(ClientGameTestContext context, boolean assembling) {
    context.runOnClient(
        client -> {
          var station = (StationBlockEntity) LoadedLevelAccess.blockEntity(client.level, S);
          var state =
              (StationRenderer.State)
                  client
                      .getBlockEntityRenderDispatcher()
                      .tryExtractRenderState(station, .5f, null, true);
          if (state == null
              || state.assembly != assembling
              || state.pieces.isEmpty()
              || state.pieces.getFirst().model().mesh.size() != 8)
            throw new AssertionError(
                "Actual source station flag8faces missing from native extracted mesh");
          var atlas=client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
          var blueprint=atlas.getSprite(Create.asResource("entity/blueprint_small"));
          var controlsBase=atlas.getSprite(Create.asResource("block/train_controller_base"));
          if(blueprint==atlas.missingSprite()||!blueprint.contents().name().equals(Create.asResource("entity/blueprint_small"))
              ||controlsBase==atlas.missingSprite()||!controlsBase.contents().name().equals(Create.asResource("block/train_controller_base")))
            throw new AssertionError("Actual native blocks atlas must contain the original Station blueprint and controls base, including after resource reload");
          if (assembling) {
            int[] flagFaces={0};
            state.pieces.getFirst().model().mesh.forEach(quad->{
              boolean originalPixels=true;
              for(int vertex=0;vertex<4;vertex++)originalPixels&=quad.u(vertex)>=blueprint.getU0()-.000001f&&quad.u(vertex)<=blueprint.getU1()+.000001f&&quad.v(vertex)>=blueprint.getV0()-.000001f&&quad.v(vertex)<=blueprint.getV1()+.000001f;
              if(originalPixels)flagFaces[0]++;
            });
            if(flagFaces[0]!=2)throw new AssertionError("Actual extracted assembly flag must use its original blueprint sprite on both cloth faces, not missing-texture UVs");
            if (state.occupiedOffsets != 2
                || state.legalOffsets < 1
                || state.flagProgress < .99f
                || state.pieces.stream().skip(1).anyMatch(piece -> piece.model().mesh.size() != 16))
              throw new AssertionError(
                  "Actual physical source assembly markers16faces/occupied2/legal offsets/flag lift"
                      + " mismatch");
          } else if (state.pieces.size() != 2 || state.pieces.get(1).model().mesh.size() != 6)
            throw new AssertionError("Actual source station target marker6faces missing");
          for (var piece : state.pieces)
            piece
                .model()
                .mesh
                .forEach(
                    quad -> {
                      if (quad.chunkLayer()
                          != net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT)
                        throw new AssertionError("Source native station cutout layer lost");
                    });
          for (var physical : AllBlocks.TRACK_STATION.getStateDefinition().getPossibleStates())
            if (client.getModelManager().getBlockStateModelSet().get(physical)
                == client.getModelManager().getBlockStateModelSet().missingModel())
              throw new AssertionError("Native station blockstate model missing " + physical);
        });
  }

  private static void stand(
      ClientGameTestContext context, TestSingleplayerContext world, double x, double z) {
    world.getServer().runCommand("gamemode survival @p");
    world.getServer().runCommand("tp @p " + x + " 5 " + z);
    context.waitFor(
        client ->
            !client.player.isSpectator()
                && client.player.isAlive()
                && client.player.onGround()
                && Math.abs(client.player.getY() - 5) < .1
                && Math.abs(client.player.getX() - x) < .1
                && Math.abs(client.player.getZ() - z) < .1,
        250);
  }

  private static void open(ClientGameTestContext context) {
    use(context, S, Vec3.atCenterOf(S).add(0, .25, 0));
    context.waitFor(client -> client.gui.screen() instanceof StationScreen, 200);
    context.waitTicks(2);
  }

  private static void close(ClientGameTestContext context) {
    context.getInput().pressKey(InputConstants.KEY_ESCAPE);
    context.waitFor(client -> client.gui.screen() == null, 150);
  }

  private static void editName(ClientGameTestContext context, String text) {
    var old =
        context.computeOnClient(
            client -> ((StationScreen) client.gui.screen()).nameBox().getValue().length());
    cursor(context, StationScreen::nameCoordinate);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.getInput().pressKey(InputConstants.KEY_END);
    for (int i = 0; i < old; i++) context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
    context.getInput().typeChars(text);
    context.runOnClient(
        client -> {
          if (!((StationScreen) client.gui.screen()).nameBox().getValue().equals(text))
            throw new AssertionError("Actual native name typing failed");
        });
  }

  private static void cursor(
      ClientGameTestContext context, Function<StationScreen, Vec2> coordinate) {
    var point =
        context.computeOnClient(
            client -> {
              var local = coordinate.apply((StationScreen) client.gui.screen());
              float scaleX = (float) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
              float scaleY = (float) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
              return new Vec2(local.x * scaleX, local.y * scaleY);
            });
    context.getInput().setCursorPos(point.x, point.y);
  }

  private static void click(
      ClientGameTestContext context, Function<StationScreen, Vec2> coordinate) {
    cursor(context, coordinate);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(3);
  }

  private static void use(ClientGameTestContext context, BlockPos pos, Vec3 target) {
    look(context, target);
    context.waitFor(
        client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos),
        150);
    context.waitTicks(6);
    context.getInput().pressKey(options -> options.keyUse);
  }

  private static void look(ClientGameTestContext context, Vec3 target) {
    var angle =
        context.computeOnClient(
            client -> {
              var delta = target.subtract(client.player.getEyePosition());
              return new Vec2(
                  (float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                  (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
            });
    context.getInput().lookAt(angle.x, angle.y);
  }

  private static void screenshot(ClientGameTestContext context, String name) {
    context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
    context.takeScreenshot(name);
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

  private static StationBlockEntity station(ServerLevel level) {
    return LoadedLevelAccess.blockEntity(level, S) instanceof StationBlockEntity station
        ? station
        : null;
  }

  private static StandardBogeyBlockEntity bogey(ServerLevel level, BlockPos pos) {
    return LoadedLevelAccess.blockEntity(level, pos) instanceof StandardBogeyBlockEntity bogey
        ? bogey
        : null;
  }
}
