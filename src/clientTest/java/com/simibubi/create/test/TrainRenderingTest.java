package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.actors.trainControls.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.TrainHUD;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

/**
 * Proposed actual station input -> accepted Train -> native driving/rendering/cargo -> genuine
 * save/chunk retirement/return -> station GUI world restoration. Region45000..45128,z0..48. This
 * source is a proposed native regression, not a runtime-success receipt.
 */
final class TrainRenderingTest {
  private static final BlockPos T = new BlockPos(45048, 5, 16),
      S = T.south(3),
      B = T.east().above(),
      C = B.east(),
      I = B.east(2),
      PLAYER_SEAT = B.above(),
      PIG_SEAT = B.east(3).above(),
      SECOND = B.east(6),
      LAST = B.east(7),
      SENTINEL = T.south(6);
  private static final BlockPos LOCAL_CONTROLS = C.subtract(B);

  static void run(ClientGameTestContext context, TestSingleplayerContext world) {
    int clientView = context.computeOnClient(client -> client.options.renderDistance().get());
    int serverView =
        world.getServer().computeOnServer(server -> server.getPlayerList().getViewDistance());
    boolean hidden = context.computeOnClient(client -> client.gui.hud.isHidden());
    try {
      context.runOnClient(
          client -> {
            client.options.renderDistance().set(6);
            client.options.broadcastOptions();
            if (client.gui.hud.isHidden()) client.gui.hud.toggle();
          });
      world.getServer().runOnServer(server -> server.getPlayerList().setViewDistance(6));
      waitServer(
          context,
          world,
          level ->
              actual(level).requestedViewDistance() == 6
                  && level.getServer().getPlayerList().getViewDistance() == 6,
          250,
          "Native client-information packet never acknowledged the real six-chunk view");
      runVisible(context, world);
      TrainVehiclePacketRenderingTest.verifyVanillaBoat(context, world);
    } finally {
      TrainVehiclePacketProbe.clear();
      context.getInput().releaseKey(options -> options.keyUp);
      context.getInput().releaseKey(options -> options.keyDown);
      context.getInput().releaseKey(options -> options.keyJump);
      context.getInput().releaseKey(options -> options.keyShift);
      world.getServer().runOnServer(server -> server.getPlayerList().setViewDistance(serverView));
      context.runOnClient(
          client -> {
            client.options.renderDistance().set(clientView);
            client.options.broadcastOptions();
            if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
          });
    }
  }

  private static void runVisible(ClientGameTestContext context, TestSingleplayerContext world) {
    var server = world.getServer();
    server.runCommand("gamemode spectator @p");
    server.runCommand("tp @p 45064.5 18 24.5");
    context.waitFor(
        client ->
            client.level.isLoaded(new BlockPos(45000, 4, 0))
                && client.level.isLoaded(new BlockPos(45128, 4, 48)),
        500);
    waitServer(
        context,
        world,
        TrainRenderingTest::allSourceChunks,
        600,
        "Actual native view did not load the full physical Train fixture");
    NativeScreenshotReadiness.beforeFloor(context, world, new BlockPos(45000, 4, 0), new BlockPos(45128, 4, 48), T, S, B.below(), LAST.below());
    server.runCommand("fill 45000 4 0 45128 4 48 minecraft:smooth_quartz");
    NativeScreenshotReadiness.afterFloor(context, world, new BlockPos(45000, 4, 0), new BlockPos(45128, 4, 48));
    server.runCommand("fill 45008 5 16 45120 5 16 create:track[shape=xo]");
    waitServer(
        context,
        world,
        level -> {
          for (int x = 45008; x <= 45120; x++) {
            var pos = new BlockPos(x, 5, 16);
            var result =
                TrackPropagator.reconcile(
                    level, pos, LoadedLevelAccess.blockState(level, pos), false);
            if (!result.complete()) return false;
          }
          return true;
        },
        500,
        "Actual connected native rails did not complete genuine graph discovery");
    server.runCommand("clear @p");
    server.runCommand("item replace entity @p weapon.mainhand with create:track_station 2");
    stand(context, world, T.getX() + 2.5, T.getZ() + 1.5);
    context.waitFor(
        client -> client.player.getMainHandItem().is(AllBlocks.TRACK_STATION.asItem()), 200);
    useBlock(context, T, Vec3.atBottomCenterOf(T).add(0, .1, 0));
    context.waitFor(
        client -> client.player.getMainHandItem().has(AllDataComponents.TRACK_TARGETING_SELECTION),
        200);
    context.runOnClient(
        client -> {
          var selected =
              client.player.getMainHandItem().get(AllDataComponents.TRACK_TARGETING_SELECTION);
          if (!selected.target().pos().equals(T) || !selected.front())
            throw new AssertionError(
                "Actual source station item selected an unexpected physical side");
        });
    useBlock(context, S.below(), Vec3.atCenterOf(S.below()).add(0, .499, 0));
    var pointId = new AtomicReference<UUID>();
    waitServer(
        context,
        world,
        level -> {
          var station = station(level);
          if (station == null || station.getStation() == null) return false;
          pointId.set(station.targeting().id());
          return station.getStation().ownedBy(station);
        },
        300,
        "Native station item placement never created its genuine world-owned graph point");
    server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
    stand(context, world, T.getX() + .5, S.getZ() + 2.5);
    openStation(context);
    nameStation(context, "Native Vulkan Train");
    clickStation(context, StationScreen::saveCoordinate);
    waitServer(
        context,
        world,
        level -> station(level).stationName().equals("Native Vulkan Train"),
        200,
        "Actual native station name/save input failed");
    context.waitFor(
        client ->
            client.gui.screen() instanceof StationScreen screen
                && screen.snapshot().name().equals("Native Vulkan Train"),
        200);
    clickStation(context, StationScreen::modeCoordinate);
    context.waitFor(
        client ->
            client.gui.screen() instanceof StationScreen screen && screen.snapshot().assembling(),
        200);
    close(context);
    server.runCommand("item replace entity @p weapon.mainhand with create:railway_casing 2");
    stand(context, world, B.getX() + 2.5, B.getZ() + 2.5);
    context.waitFor(
        client -> client.player.getMainHandItem().is(AllBlocks.RAILWAY_CASING.asItem()), 200);
    useBlock(context, B.below(), Vec3.atBottomCenterOf(B.below()).add(0, .1, 0));
    waitServer(
        context,
        world,
        level -> LoadedLevelAccess.blockState(level, B).is(AllBlocks.SMALL_BOGEY),
        200,
        "Actual native casing input did not create the first source bogey");
    stand(context, world, SECOND.getX() + .5, SECOND.getZ() + 2.5);
    useBlock(context, SECOND.below(), Vec3.atBottomCenterOf(SECOND.below()).add(0, .1, 0));
    waitServer(
        context,
        world,
        level -> LoadedLevelAccess.blockState(level, SECOND).is(AllBlocks.SMALL_BOGEY),
        200,
        "Actual native casing input did not create the second source bogey");
    NativeContraptionFixtures.awaitEntityReady(context, world, 400, B, LAST, PIG_SEAT);
    var parcel = new AtomicReference<ItemStack>();
    var pig = new AtomicReference<Entity>();
    var originalBarrel = new AtomicReference<BarrelBlockEntity>();
    server.runOnServer(
        ignored -> {
          var level = world.getConnection().getServerLevel();
          level.setBlockAndUpdate(
              C,
              AllBlocks.TRAIN_CONTROLS
                  .defaultBlockState()
                  .setValue(ControlsBlock.FACING, Direction.EAST));
          level.setBlockAndUpdate(
              B.east(5),
              AllBlocks.TRAIN_CONTROLS
                  .defaultBlockState()
                  .setValue(ControlsBlock.FACING, Direction.WEST));
          for (var pos : List.of(I, LAST, SENTINEL))
            level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
          for (var pos : List.of(B.east(3), B.east(4)))
            level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
          level.setBlockAndUpdate(
              PLAYER_SEAT, AllBlocks.SEATS.get(DyeColor.BLUE).defaultBlockState());
          level.setBlockAndUpdate(PIG_SEAT, AllBlocks.SEATS.get(DyeColor.RED).defaultBlockState());
          var barrel = (BarrelBlockEntity) level.getBlockEntity(I);
          barrel.setItem(0, diamonds(7));
          parcel.set(parcel(level.registryAccess()));
          barrel.setItem(1, parcel.get().copy());
          originalBarrel.set(barrel);
          ((BarrelBlockEntity) level.getBlockEntity(SENTINEL))
              .setItem(0, new ItemStack(Items.EMERALD, 11));
          var animal = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
          if (animal == null)
            throw new AssertionError("Actual native pig factory refused source passenger");
          animal.setNoAi(true);
          animal.setPersistenceRequired();
          animal.setCustomName(Component.literal("Original native Train passenger"));
          animal.setPos(Vec3.atCenterOf(PIG_SEAT).add(0, .5, 0));
          if (!level.addFreshEntity(animal))
            throw new AssertionError("Actual native source pig was not accepted");
          NativeContraptionFixtures.sitDown(level, PIG_SEAT, animal);
          pig.set(animal);
          if (!level.addFreshEntity(
              new SuperGlueEntity(level, SuperGlueEntity.span(B, LAST.above()))))
            throw new AssertionError("Actual native source carriage glue was refused");
        });
    NativeContraptionFixtures.awaitCaptureReady(context, world, 400, B, LAST.above());
    server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
    stand(context, world, T.getX() + .5, S.getZ() + 2.5);
    openStation(context);
    clickStation(context, StationScreen::preflightCoordinate);
    context.waitFor(
        client ->
            client.gui.screen() instanceof StationScreen screen
                && screen.snapshot().preflight()
                    == StationAssemblyPreflight.Status.READY_FOR_CARRIAGE_CAPTURE,
        400);
    world.getConnection().waitForChunksRender();
    screenshot(
        context,
        "create-vulkan-train-real-source-bogeys-controls-glue-passenger-and-station-preflight");
    clickStation(context, StationScreen::assemblyCoordinate);
    var train = new AtomicReference<Train>();
    var originalFront = new AtomicReference<CarriageContraptionEntity>();
    waitServer(
        context,
        world,
        level -> {
          var found = station(level).getStation().getPresentTrain();
          if (found == null || TrainManager.get(level).get(found.id) != found) return false;
          var front = found.carriages.getFirst().anyAvailableEntity();
          if (front == null || front.getContraption() == null) return false;
          train.set(found);
          originalFront.set(front);
          return found.carriages.size() == 1
              && found.doubleEnded
              && found.carriages.getFirst().isOnTwoBogeys()
              && found.carriages.getFirst().bogeySpacing == 6
              && pig.get().getVehicle() == front
              && originalBarrel.get().isRemoved()
              && LoadedLevelAccess.blockEntity(level, I) == null;
        },
        500,
        "Actual station GUI did not publish the complete accepted original native Train");
    var body = originalFront.get().getUUID();
    var pigId = pig.get().getUUID();
    waitClientFront(context, body);
    close(context);
    stand(context, world, B.getX() + .5, B.getZ() + 3.5);
    TrainVehiclePacketProbe.watch(body);
    clickMounted(context, world, body, PLAYER_SEAT.subtract(B), new Vec3(.5, .25, .5));
    waitServer(
        context,
        world,
        level -> actual(level).getVehicle() == level.getEntity(body),
        250,
        "Native moving-seat input did not board the actual player on the accepted carriage");
    context.waitFor(
        client ->
            client.player.getVehicle() != null && client.player.getVehicle().getUUID().equals(body),
        250);
    // The real moving barrel menu must change the one global mounted manager.
    int beforeMenu =
        server.computeOnServer(ignored -> train.get().carriages.getFirst().storage.getVersion());
    clickMounted(context, world, body, I.subtract(B), new Vec3(.5, .5, .5));
    context.waitFor(
        client ->
            client.gui.screen()
                    instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen
                && client.player.containerMenu instanceof ChestMenu
                && ItemStack.matches(client.player.containerMenu.getSlot(0).getItem(), diamonds(7))
                && ItemStack.matches(
                    client.player.containerMenu.getSlot(1).getItem(),
                    parcel(client.level.registryAccess())),
        250);
    clickMenu(context, 0, InputConstants.MOUSE_BUTTON_RIGHT);
    context.waitFor(
        client ->
            ItemStack.matches(client.player.containerMenu.getCarried(), diamonds(4))
                && ItemStack.matches(client.player.containerMenu.getSlot(0).getItem(), diamonds(3)),
        200);
    clickMenu(context, 27, InputConstants.MOUSE_BUTTON_LEFT);
    context.waitFor(
        client ->
            client.player.containerMenu.getCarried().isEmpty()
                && ItemStack.matches(client.player.getInventory().getItem(9), diamonds(4)),
        200);
    waitServer(
        context,
        world,
        level ->
            train.get().carriages.getFirst().storage.getVersion() > beforeMenu
                && cargo(train.get(), diamonds(1)) == 3
                && ItemStack.matches(actual(level).getInventory().getItem(9), diamonds(4)),
        200,
        "Native mounted-menu input did not commit the original cargo split through the sole"
            + " manager");
    screenshot(context, "create-vulkan-train-real-moving-barrel-menu-original-named-nested-cargo");
    close(context);
    clickMounted(context, world, body, LOCAL_CONTROLS, new Vec3(.5, .5, .5));
    context.waitFor(
        client ->
            ControlsHandler.active() && ControlsHandler.getContraption().getUUID().equals(body),
        250);
    waitServer(
        context,
        world,
        level -> originalFront.get().isControlledBy(actual(level)),
        250,
        "Actual moving-controls click never received authoritative native driver acknowledgement");
    var parked =
        context.computeOnClient(
            client -> CarriageNativeRenderProbe.inspect(client, body, LOCAL_CONTROLS));
    Vec3 start =
        server.computeOnServer(
            ignored ->
                train.get().carriages.getFirst().getLeadingPoint().getPosition(train.get().graph));
    // Reverse departure leaves the original one-way station ahead for the real forward return.
    context.getInput().holdKey(options -> options.keyDown);
    try {
      context.waitFor(
          client -> ControlsHandler.currentlyPressed.contains(1) && TrainHUD.currentPrompt != null,
          200);
      waitServer(
          context,
          world,
          level ->
              Math.abs(train.get().speed) > .01
                  && train.get().getCurrentStation() == null
                  && train
                          .get()
                          .carriages
                          .getFirst()
                          .getLeadingPoint()
                          .getPosition(train.get().graph)
                          .distanceTo(start)
                      > 8,
          450,
          "Actual physical reverse key never moved the genuine native Train eight blocks");
      context.waitFor(
          client ->
              ControlsHandler.currentlyPressed.contains(1)
                  && Math.abs(ControlsHandler.getContraption().getCarriage().train.speed) > .01,
          250);
      var moving =
          context.computeOnClient(
              client -> CarriageNativeRenderProbe.inspect(client, body, LOCAL_CONTROLS));
      if (moving.wheelAngle() == parked.wheelAngle() || moving.lever().equals(parked.lever()))
        throw new AssertionError(
            "Real native wheel and speed lever did not respond to physical driving");
      screenshot(
          context,
          "create-vulkan-train-physical-native-driving-bogey-wheels-controls-and-source-hud");
    } finally {
      context.getInput().releaseKey(options -> options.keyDown);
    }
    waitServer(
        context,
        world,
        level ->
            Math.abs(train.get().speed) < .000001
                && train.get().targetSpeed == 0
                && originalFront.get().isControlledBy(actual(level)),
        350,
        "Actual released native input did not brake the genuine Train to rest");
    server.runOnServer(
        ignored -> {
          var level = world.getConnection().getServerLevel();
          assertCargo(level, train.get(), parcel.get());
          if (train.get().navigation.findNearestApproachable(true) != station(level).getStation())
            throw new AssertionError(
                "Original physical station is not actually approachable on the native return path");
        });
    context.getInput().holdKey(options -> options.keyUp);
    context.getInput().holdKey(options -> options.keyJump);
    try {
      waitServer(
          context,
          world,
          level ->
              train.get().getCurrentStation() == station(level).getStation()
                  && Math.abs(train.get().speed) < .000001,
          650,
          "Actual forward/jump input did not navigate and brake at the original real station");
    } finally {
      context.getInput().releaseKey(options -> options.keyUp);
      context.getInput().releaseKey(options -> options.keyJump);
    }
    screenshot(context, "create-vulkan-train-real-source-station-approach-and-native-braking");
    TrainVehiclePacketProbe.assertTrain();
    server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(originalFront.get().getControllingPassenger()==actual(level)||!originalFront.get().isControlledBy(actual(level)))throw new AssertionError("Actual source Train must keep its accepted controls session without vanilla passenger coordinate authority");});
    if(Boolean.getBoolean("create.test.nativeTrace"))System.out.println("CREATE_TRAIN_ACTUAL_VEHICLE_PACKET_BOUNDARIES "+TrainVehiclePacketProbe.snapshot());
    TrainVehiclePacketProbe.clear();
    context.getInput().holdKey(options -> options.keyShift);
    try {
      context.waitFor(client -> !ControlsHandler.active() && !client.player.isPassenger(), 250);
      waitServer(
          context,
          world,
          level ->
              !actual(level).isPassenger() && originalFront.get().getControllingPlayer().isEmpty(),
          250,
          "Actual native dismount/controls-release input did not reach the server");
    } finally {
      context.getInput().releaseKey(options -> options.keyShift);
    }
    var mesh =
        context.computeOnClient(
            client -> CarriageNativeRenderProbe.inspect(client, body, LOCAL_CONTROLS));
    var reload = context.computeOnClient(Minecraft::reloadResourcePacks);
    context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 650);
    reload.join();
    var reloaded =
        context.computeOnClient(
            client -> CarriageNativeRenderProbe.inspect(client, body, LOCAL_CONTROLS));
    if (mesh.bogeyMesh() == reloaded.bogeyMesh() || mesh.coverMesh() == reloaded.coverMesh())
      throw new AssertionError(
          "Actual native reload retained an obsolete bogey or train-controls mesh");
    var oldStation =
        server.computeOnServer(ignored -> station(world.getConnection().getServerLevel()));
    var oldSentinel =
        server.computeOnServer(
            ignored -> world.getConnection().getServerLevel().getBlockEntity(SENTINEL));
    server.runOnServer(
        nativeServer -> {
          if (!nativeServer.saveEverything(true, true, true))
            throw new AssertionError("Actual native Train save failed");
        });
    server.runCommand("gamemode spectator @p");
    server.runCommand("tp @p 46400.5 20 46400.5");
    waitServer(
        context,
        world,
        level -> !anySourceChunk(level),
        900,
        "Genuine native Train fixture chunks did not leave FULL state after real teleport");
    NativeFixtureChunkUnload.finish(context, world, oldStation, oldSentinel);
    waitServer(
        context,
        world,
        level ->
            originalFront.get().isRemoved()
                && pig.get().isRemoved()
                && level.getEntity(body) == null
                && level.getEntity(pigId) == null
                && train.get().carriages.getFirst().anyAvailableEntity() == null,
        850,
        "Actual original carriage frontend and original NPC did not retire after source chunks"
            + " unloaded");
    server.runOnServer(
        nativeServer -> {
          var level = world.getConnection().getServerLevel();
          if (TrainManager.get(level).get(train.get().id) != train.get())
            throw new AssertionError(
                "Ordinary chunk unload unexpectedly replaced the actual globally owned Train");
          assertCargo(level, train.get(), parcel.get());
          if (!train.get().carriages.getFirst().custodyIdentities().bodies().equals(Set.of(body)))
            throw new AssertionError("Actual retired row lost the original body UUID");
          if (!nativeServer.saveEverything(true, true, true))
            throw new AssertionError("Native retired-row global save failed");
        });
    context.waitFor(client -> find(client, body) == null && find(client, pigId) == null, 400);
    server.runCommand("tp @p 45064.5 18 24.5");
    waitServer(
        context,
        world,
        level -> {
          var front = level.getEntity(body);
          var rider = level.getEntity(pigId);
          return allSourceChunks(level)
              && station(level) != null
              && station(level) != oldStation
              && station(level).targeting().id().equals(pointId.get())
              && front instanceof CarriageContraptionEntity actualFront
              && front != originalFront.get()
              && actualFront.getCarriage() == train.get().carriages.getFirst()
              && rider != null
              && rider != pig.get()
              && rider.getVehicle() == front
              && level.getBlockEntity(SENTINEL) != oldSentinel
              && level.getBlockEntity(SENTINEL) instanceof BarrelBlockEntity sentinel
              && sentinel.getItem(0).is(Items.EMERALD)
              && sentinel.getItem(0).getCount() == 11;
        },
        850,
        "True native return did not decode distinct original-UUID front/NPC/station/cargo owners");
    waitClientFront(context, body);
    world.getConnection().waitForChunksRender();
    context.runOnClient(client -> CarriageNativeRenderProbe.inspect(client, body, LOCAL_CONTROLS));
    server.runOnServer(
        ignored -> assertCargo(world.getConnection().getServerLevel(), train.get(), parcel.get()));
    look(
        context,
        context.computeOnClient(
            client -> ((CarriageContraptionEntity) find(client, body)).position()));
    screenshot(
        context,
        "create-vulkan-train-distinct-native-frontend-npc-original-uuids-after-true-chunk-return");
    stand(context, world, T.getX() + .5, S.getZ() + 2.5);
    openStation(context);
    context.waitFor(
        client ->
            client.gui.screen() instanceof StationScreen screen
                && screen.snapshot().presentTrain() != null
                && screen.snapshot().canDisassemble(),
        350);
    clickStation(context, StationScreen::assemblyCoordinate);
    waitServer(
        context,
        world,
        level ->
            TrainManager.get(level).get(train.get().id) == null
                && train.get().carriages.stream().allMatch(Carriage::isWorldRestored),
        600,
        "Actual native station disassemble input did not finish every real restoration row");
    close(context);
    server.runOnServer(
        ignored -> {
          var level = world.getConnection().getServerLevel();
          var restored = (BarrelBlockEntity) level.getBlockEntity(I);
          if (restored == null
              || restored == originalBarrel.get()
              || !ItemStack.matches(restored.getItem(0), diamonds(3))
              || !ItemStack.matches(restored.getItem(1), parcel.get())
              || !LoadedLevelAccess.blockState(level, B).is(AllBlocks.SMALL_BOGEY)
              || !LoadedLevelAccess.blockState(level, SECOND).is(AllBlocks.SMALL_BOGEY)
              || !ItemStack.matches(actual(level).getInventory().getItem(9), diamonds(4))
              || level.getEntity(body) != null
              || !(level.getEntity(pigId).getVehicle() instanceof SeatEntity seat)
              || !seat.blockPosition().equals(PIG_SEAT))
            throw new AssertionError(
                "Actual world restoration lost original geometry/NPC UUID/nested cargo or"
                    + " duplicated a body");
          var row = train.get().carriages.getFirst();
          var receipt = row.write(new DimensionPalette(), level.registryAccess());
          var restoration = receipt.getCompoundOrEmpty("Restoration");
          if (!restoration.getBooleanOr("WorldRestored", false)
              || !restoration.read("Body", UUIDUtil.CODEC).orElseThrow().equals(body)
              || restoration.read("WrittenCells", BlockPos.CODEC.listOf()).orElseThrow().isEmpty()
              || receipt.contains("Cargo")
              || receipt.contains("Entity")
              || receipt.contains("Passengers")
              || receipt.contains("EntityPositioning")
              || row.storage.ownsContents())
            throw new AssertionError(
                "Consumed actual carriage row is not an original-UUID thin world-restoration"
                    + " receipt");
        });
    context.waitFor(
        client ->
            LoadedLevelAccess.blockEntity(client.level, I) instanceof BarrelBlockEntity
                && client.level.getBlockState(B).is(AllBlocks.SMALL_BOGEY)
                && find(client, body) == null,
        350);
    look(context, Vec3.atCenterOf(I));
    screenshot(
        context,
        "create-vulkan-train-station-gui-disassembly-original-world-cargo-passenger-and-thin-receipt");
  }

  private static ItemStack diamonds(int count) {
    var stack = new ItemStack(Items.DIAMOND, count);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Original native Train diamonds"));
    return stack;
  }

  private static ItemStack parcel(HolderLookup.Provider registries) {
    var tool = new ItemStack(Items.DIAMOND_PICKAXE);
    tool.setDamageValue(37);
    tool.enchant(
        registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
    tool.set(
        DataComponents.CUSTOM_NAME, Component.literal("Original registry-bound native Train tool"));
    var parcel = new ItemStack(Items.SHULKER_BOX);
    parcel.set(
        DataComponents.CUSTOM_NAME, Component.literal("Original nested native Train parcel"));
    parcel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(tool)));
    return parcel;
  }

  private static long cargo(Train train, ItemStack stack) {
    var variant = ItemVariant.of(stack);
    long amount = 0;
    for (var row : train.carriages)
      for (var view : row.storage.getAllItems())
        if (view.getResource().equals(variant)) amount += view.getAmount();
    return amount;
  }

  private static void assertCargo(ServerLevel level, Train train, ItemStack parcel) {
    if (cargo(train, diamonds(1)) != 3
        || cargo(train, parcel) != 1
        || !ItemStack.matches(actual(level).getInventory().getItem(9), diamonds(4)))
      throw new AssertionError(
          "Actual global/native-player cargo split or nested original tool changed");
    TrainManager.get(level).validateCustody();
  }

  private static boolean allSourceChunks(ServerLevel level) {
    for (int x = 45000 >> 4; x <= 45128 >> 4; x++)
      for (int z = 0; z <= 48 >> 4; z++)
        if (level.getChunkSource().getChunkNow(x, z) == null) return false;
    return true;
  }

  private static boolean anySourceChunk(ServerLevel level) {
    for (int x = 45000 >> 4; x <= 45128 >> 4; x++)
      for (int z = 0; z <= 48 >> 4; z++)
        if (level.getChunkSource().getChunkNow(x, z) != null) return true;
    return false;
  }

  private static StationBlockEntity station(ServerLevel level) {
    return LoadedLevelAccess.blockEntity(level, S) instanceof StationBlockEntity station
        ? station
        : null;
  }

  private static net.minecraft.server.level.ServerPlayer actual(ServerLevel level) {
    return level.getServer().getPlayerList().getPlayers().getFirst();
  }

  private static Entity find(Minecraft client, UUID id) {
    for (var entity : client.level.entitiesForRendering())
      if (entity.getUUID().equals(id)) return entity;
    return null;
  }

  private static void waitClientFront(ClientGameTestContext context, UUID body) {
    context.waitFor(
        client ->
            find(client, body) instanceof CarriageContraptionEntity front
                && front.getContraption() != null
                && front.getCarriage() != null
                && front.validForRender
                && !front.firstPositionUpdate,
        450);
  }

  private static void stand(
      ClientGameTestContext context, TestSingleplayerContext world, double x, double z) {
    world.getServer().runCommand("gamemode survival @p");
    world.getServer().runCommand("tp @p " + x + " 5 " + z);
    context.waitFor(
        client ->
            !client.player.isSpectator()
                && client.player.onGround()
                && Math.abs(client.player.getX() - x) < .1
                && Math.abs(client.player.getZ() - z) < .1,
        250);
  }

  private static void clickMounted(
      ClientGameTestContext context,
      TestSingleplayerContext world,
      UUID body,
      BlockPos local,
      Vec3 within) {
    look(
        context,
        context.computeOnClient(
            client ->
                ((CarriageContraptionEntity) find(client, body))
                    .toGlobalVector(Vec3.atLowerCornerOf(local).add(within), 1)));
    waitServer(
        context,
        world,
        level -> {
          if (!(level.getEntity(body) instanceof CarriageContraptionEntity front)) return false;
          var player = actual(level);
          var hit =
              front.rayTrace(
                  player.getEyePosition(),
                  player.getEyePosition().add(player.getViewVector(1).scale(5)),
                  1);
          return hit != null && hit.getBlockPos().equals(local);
        },
        200,
        "Actual player ray did not reach the real mounted Train interaction cell " + local);
    context.waitTicks(6);
    context.getInput().pressKey(options -> options.keyUse);
  }

  private static void openStation(ClientGameTestContext context) {
    useBlock(context, S, Vec3.atCenterOf(S).add(0, .25, 0));
    context.waitFor(client -> client.gui.screen() instanceof StationScreen, 250);
    context.waitTicks(2);
  }

  private static void nameStation(ClientGameTestContext context, String name) {
    int old =
        context.computeOnClient(
            client -> ((StationScreen) client.gui.screen()).nameBox().getValue().length());
    clickStation(context, StationScreen::nameCoordinate);
    context.getInput().pressKey(InputConstants.KEY_END);
    for (int i = 0; i < old; i++) context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
    context.getInput().typeChars(name);
  }

  private static void clickStation(
      ClientGameTestContext context, Function<StationScreen, Vec2> coordinate) {
    var point =
        context.computeOnClient(
            client -> {
              var p = coordinate.apply((StationScreen) client.gui.screen());
              float scaleX = (float) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
              float scaleY = (float) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
              return new Vec2(p.x * scaleX, p.y * scaleY);
            });
    context.getInput().setCursorPos(point.x, point.y);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTicks(3);
  }

  private static void clickMenu(ClientGameTestContext context, int index, int button) {
    var point =
        context.computeOnClient(
            client -> {
              var screen = client.gui.screen();
              var menu = (ChestMenu) client.player.containerMenu;
              var slot = menu.getSlot(index);
              float scaleX = (float) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
              float scaleY = (float) client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight();
              return new Vec2(
                  ((screen.width - 176) / 2 + slot.x + 8) * scaleX,
                  ((screen.height - (114 + menu.getRowCount() * 18)) / 2 + slot.y + 8) * scaleY);
            });
    context.getInput().setCursorPos(point.x, point.y);
    context.waitTicks(2);
    context.getInput().pressMouse(button);
  }

  private static void close(ClientGameTestContext context) {
    context.getInput().pressKey(InputConstants.KEY_ESCAPE);
    context.waitFor(client -> client.gui.screen() == null, 200);
  }

  private static void useBlock(ClientGameTestContext context, BlockPos pos, Vec3 point) {
    look(context, point);
    context.waitFor(
        client -> client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos),
        200);
    context.waitTicks(6);
    context.getInput().pressKey(options -> options.keyUse);
  }

  private static void look(ClientGameTestContext context, Vec3 target) {
    var angles =
        context.computeOnClient(
            client -> {
              var d = target.subtract(client.player.getEyePosition());
              return new Vec2(
                  (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                  (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
            });
    context.getInput().lookAt(angles.x, angles.y);
  }

  private static void waitServer(
      ClientGameTestContext context,
      TestSingleplayerContext world,
      Predicate<ServerLevel> condition,
      int limit,
      String message) {
    var ready = new AtomicBoolean();
    for (int tick = 0; tick < limit; tick++) {
      world
          .getServer()
          .runOnServer(
              ignored -> ready.set(condition.test(world.getConnection().getServerLevel())));
      if (ready.get()) return;
      context.waitTicks(1);
    }
    throw new AssertionError(message);
  }

  private static void screenshot(ClientGameTestContext context, String name) {
    context.waitFor(client -> client.gui.overlay() == null, 250);
    context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
    context.takeScreenshot(name);
  }
}
