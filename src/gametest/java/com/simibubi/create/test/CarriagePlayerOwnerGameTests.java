package com.simibubi.create.test;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.server.players.NameAndId;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.*;

/**
 * Genuine native PlayerData save/logout/rejoin with original material and literal player ownership.
 */
public final class CarriagePlayerOwnerGameTests {
  private static final String STRUCTURE = "create_test:reservoir";
  private static final BlockPos R = new BlockPos(3, 6, 4),
      T = R.east(),
      S = T.south(2),
      B = T.east().above(),
      C = B.east(),
      I = B.east(2),
      SEAT = I.above();

  record Joined(ServerPlayer player, EmbeddedChannel channel) {
    void close() {
      if (player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player)
        player.level().getServer().getPlayerList().remove(player);
      channel.finishAndReleaseAll();
    }
  }

  record Fixture(
      StationBlockEntity station,
      Joined authorizer,
      Joined rider,
      BarrelBlockEntity barrel,
      SeatEntity seat,
      Entity pig,
      SuperGlueEntity glue,
      ItemStack parcel) {}

  static Joined join(GameTestHelper h) {
    var level = h.getLevel();
    var profile =
        new GameProfile(UUID.randomUUID(), "guard_" + UUID.randomUUID().toString().substring(0, 8));
    var player =
        new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
    var connection = new Connection(PacketFlow.SERVERBOUND);
    // This is the native GameTest login transport, not a FakePlayer or a player subclass.
    var channel = new EmbeddedChannel(connection);
    level
        .getServer()
        .getPlayerList()
        .placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
    player.setGameMode(GameType.SURVIVAL);
    var pos = Vec3.atCenterOf(h.absolutePos(S)).add(0, 0, 1);
    h.assertTrue(
        player.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), 0, 0, false),
        "Genuine native registered player reaches the actual source fixture");
    h.assertTrue(
        player.getClass() == ServerPlayer.class
            && !(player instanceof FakePlayer)
            && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player
            && level.getEntity(player.getUUID()) == player,
        "PlayerList and native level own this exact literal ServerPlayer and original UUID");
    return new Joined(player, channel);
  }

  private static ItemStack diamonds() {
    var stack = new ItemStack(Items.DIAMOND, 7);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Original guarded source diamonds"));
    return stack;
  }

  private static ItemStack parcel(GameTestHelper h) {
    var tool = new ItemStack(Items.DIAMOND_PICKAXE);
    tool.setDamageValue(37);
    tool.enchant(
        h.getLevel()
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.UNBREAKING),
        3);
    tool.set(DataComponents.CUSTOM_NAME, Component.literal("Original guarded registry-bound tool"));
    var box = new ItemStack(Items.SHULKER_BOX);
    box.set(DataComponents.CUSTOM_NAME, Component.literal("Original guarded nested parcel"));
    box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(tool)));
    return box;
  }

  static Fixture fixture(GameTestHelper h) {
    return fixture(h,BlockPos.ZERO);
  }

  static Fixture fixture(GameTestHelper h,BlockPos sourceOffset) {
    var R=CarriagePlayerOwnerGameTests.R.offset(sourceOffset);var T=CarriagePlayerOwnerGameTests.T.offset(sourceOffset);var S=CarriagePlayerOwnerGameTests.S.offset(sourceOffset);var B=CarriagePlayerOwnerGameTests.B.offset(sourceOffset);var C=CarriagePlayerOwnerGameTests.C.offset(sourceOffset);var I=CarriagePlayerOwnerGameTests.I.offset(sourceOffset);var SEAT=CarriagePlayerOwnerGameTests.SEAT.offset(sourceOffset);
    var rail = AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.XO);
    for (int i = 0; i < 25; i++) {
      h.setBlock(R.east(i).below(), Blocks.STONE);
      h.setBlock(R.east(i), rail);
    }
    for (int i = 0; i < 25; i++)
      h.assertTrue(
          TrackPropagator.reconcile(h.getLevel(), h.absolutePos(R.east(i)), rail, false).complete(),
          "Real native rails form the actual source graph");
    for (int dx = -2; dx <= 2; dx++)
      for (int dz = 0; dz <= 3; dz++) h.setBlock(S.offset(dx, -1, dz), Blocks.STONE);
    h.setBlock(S, AllBlocks.TRACK_STATION);
    var station = h.getBlockEntity(S, StationBlockEntity.class);
    h.assertTrue(
        station.setTarget(
            new TrackTargetingSelection(
                GlobalPos.of(h.getLevel().dimension(), h.absolutePos(T)),
                true,
                new Vec3(1, 0, 0),
                Optional.empty())),
        "The actual station owns its genuine original graph point");
    var authorizer = join(h);
    var rider = join(h);
    if(!sourceOffset.equals(BlockPos.ZERO)){var position=Vec3.atCenterOf(h.absolutePos(S)).add(0,0,1);for(var joined:List.of(authorizer,rider))h.assertTrue(joined.player.teleportTo(h.getLevel(),position.x,position.y,position.z,Set.of(),0,0,false),"Actual native PlayerList actor reaches the naturally loaded remote source station");}
    h.assertTrue(
        station.configure(authorizer.player, "Guarded original source", true, DoorControl.ALL),
        "Actual registered native authorizer enters source assembly mode");
    h.setBlock(
        B,
        AllBlocks.SMALL_BOGEY
            .defaultBlockState()
            .setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X));
    h.setBlock(
        C,
        AllBlocks.TRAIN_CONTROLS
            .defaultBlockState()
            .setValue(ControlsBlock.FACING, Direction.EAST));
    h.setBlock(I, Blocks.BARREL);
    h.setBlock(SEAT, AllBlocks.SEATS.get(DyeColor.RED));
    h.setBlock(B.above(), AllBlocks.SEATS.get(DyeColor.BLUE));
    var barrel = h.getBlockEntity(I, BarrelBlockEntity.class);
    var parcel = parcel(h);
    barrel.setItem(0, diamonds());
    barrel.setItem(1, parcel.copy());
    var glue =
        new SuperGlueEntity(
            h.getLevel(), SuperGlueEntity.span(h.absolutePos(B), h.absolutePos(SEAT)));
    h.assertTrue(h.getLevel().addFreshEntity(glue), "Real native glue joins original source cells");
    var pig = h.spawnWithNoFreeWill(EntityTypes.PIG, SEAT.above());
    pig.setNoGravity(true);
    h.assertTrue(
        SeatBlock.trySitDown(h.getLevel(), h.absolutePos(SEAT), pig)
            && pig.getVehicle() instanceof SeatEntity,
        "Original real NPC boards its actual source world seat");
    return new Fixture(
        station, authorizer, rider, barrel, (SeatEntity) pig.getVehicle(), pig, glue, parcel);
  }

  private static void cargo(GameTestHelper h, Fixture f, Train train) {
    var row = train.carriages.getFirst();
    var saved = row.write(new DimensionPalette(), h.getLevel().registryAccess());
    h.assertTrue(
        saved.contains("Cargo") && saved.contains("Passengers"),
        "Actual globally owned carriage remains natively persistable through native direct-player persistence");
    long diamonds = 0, boxes = 0;
    for (var view : row.storage.getAllItems()) {
      if (view.getResource().equals(ItemVariant.of(diamonds()))) diamonds += view.getAmount();
      if (view.getResource().equals(ItemVariant.of(f.parcel))) boxes += view.getAmount();
    }
    h.assertTrue(
        diamonds == 7 && boxes == 1,
        "Original named and nested registry-bound cargo remains exact");
    TrainManager.get(h.getLevel()).validateCustody();
  }

  private static void runManaged(GameTestHelper h, BiConsumer<Fixture, Train> action) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(
            () -> {
              NativeCarriageSourceReadiness.require(h, List.of(B, C, I, SEAT, B.above()));
              h.assertTrue(
                    StationAssemblyPreflight.check(f.station, f.authorizer.player)
                        .readyForCapture(),
                    "Actual wheel/entity metadata becomes ready through native ticks");
            })
        .thenExecute(
            () -> {
              Train train = null;
              try {
                h.assertTrue(
                    f.station.tryAssemble(f.authorizer.player),
                    "Real station assembles a genuine supported NPC-only source Train");
                train = f.station.getStation().getPresentTrain();
                h.assertTrue(
                    train != null
                        && TrainManager.get(h.getLevel()).get(train.id) == train
                        && train.carriages.size() == 1
                        && f.pig.getVehicle() == train.carriages.getFirst().anyAvailableEntity(),
                    "Actual accepted Train/frontend owns the exact original registered NPC object"
                        + " and UUID");
                action.accept(f, train);
                cargo(h, f, train);
              } finally {
                f.rider.player.stopRiding();
                if (train != null && TrainManager.get(h.getLevel()).get(train.id) == train)
                  h.assertTrue(
                      train.disassemble(
                          Direction.EAST, h.absolutePos(T.above()), f.authorizer.player),
                      "Actual fixture disassembly restores original world ownership before native"
                          + " logout");
                f.rider.close();
                f.authorizer.close();
              }
            })
        .thenSucceed();
  }

  private static int boardDirect(GameTestHelper h, Fixture f, Train train) {
    var front = train.carriages.getFirst().anyAvailableEntity();
    int seat = front.getContraption().getSeats().indexOf(B.above().subtract(B));
    h.assertTrue(seat >= 0 && front.addSittingPassenger(f.rider.player, seat)
            && f.rider.player.getVehicle() == front,
        "The literal registered player boards a real free direct seat under the strict policy");
    return seat;
  }

  /** The same public native task used by ServerConfigurationPacketListenerImpl in 26.3. */
  static final class Rejoin implements AutoCloseable {
    private final GameTestHelper h;
    private final GameProfile profile;
    private final Connection connection;
    private final EmbeddedChannel channel;
    private final PrepareSpawnTask task;
    private Joined returned;

    Rejoin(GameTestHelper h, GameProfile profile) {
      this.h = h;
      this.profile = profile;
      var level = h.getLevel();
      h.assertTrue(level.getServer().getPlayerList().getPlayer(profile.id()) == null
              && level.getEntity(profile.id()) == null,
          "Actual native logout relinquished the original player UUID before real rejoin");
      connection = new Connection(PacketFlow.SERVERBOUND);
      channel = new EmbeddedChannel(connection);
      task = new PrepareSpawnTask(level.getServer(), new NameAndId(profile));
      try {
        task.start(connection::send);
      } catch (RuntimeException | Error failure) {
        task.close();
        channel.finishAndReleaseAll();
        throw failure;
      }
    }

    void requireReady() {
      h.assertTrue(task.tick(),
          "The real native login task must finish its actual saved-position/chunk preparation");
    }

    Joined spawn() {
      // The native task creates the literal player, loads the actual profile's PlayerData,
      // registers it with PlayerList and level, and restores any allowed vanilla parent vehicle.
      var player = task.spawnPlayer(connection, CommonListenerCookie.createInitial(profile, false));
      returned = new Joined(player, channel);
      var level = h.getLevel();
      h.assertTrue(player.getClass() == ServerPlayer.class && !(player instanceof FakePlayer)
              && player.getUUID().equals(profile.id())
              && level.getServer().getPlayerList().getPlayer(profile.id()) == player
              && level.getEntity(profile.id()) == player,
          "Native login owns one new literal ServerPlayer loaded with the original UUID");
      return returned;
    }

    @Override
    public void close() {
      task.close();
      if (returned != null) {
        returned.player.stopRiding();
        returned.close();
      } else {
        // A native spawn failure may happen after registration but before returning the object.
        var registered = h.getLevel().getServer().getPlayerList().getPlayer(profile.id());
        if (registered != null) {
          registered.stopRiding();
          h.getLevel().getServer().getPlayerList().remove(registered);
        }
        channel.finishAndReleaseAll();
      }
    }
  }

  private static void canonicalOwners(GameTestHelper h, Fixture f, Train train,
      CarriageContraptionEntity front) {
    h.assertTrue(TrainManager.get(h.getLevel()).get(train.id) == train
            && front == train.carriages.getFirst().anyAvailableEntity()
            && !front.isRemoved() && front.isGloballyManaged()
            && h.getLevel().getEntity(front.getUUID()) == front
            && h.getLevel().getEntity(f.pig.getUUID()) == f.pig
            && !f.pig.isRemoved() && f.pig.getVehicle() == front,
        "Actual global Train, original native front and original NPC retain exact objects and UUID custody");
    cargo(h, f, train);
  }

  @GameTest(structure = STRUCTURE, maxTicks = 240)
  public void actualNativePlayerDataSaveCannotCopyGloballyOwnedBodyAndNpc(GameTestHelper h) {
    runManaged(h, (f, train) -> {
      boardDirect(h, f, train);
      var front = train.carriages.getFirst().anyAvailableEntity();
      var id = new NameAndId(f.rider.player.getGameProfile());
      h.assertTrue(!front.hasExactlyOnePlayerPassenger(),
          "The actual existing contraption virtual guard refuses vanilla vehicle ownership transfer");
      // Actual native saveAll writes the actual profile's PlayerData through PlayerDataStorage.
      h.getLevel().getServer().getPlayerList().saveAll();
      var playerData = h.getLevel().getServer().getPlayerList().loadPlayerData(id).orElseThrow();
      h.assertTrue(!playerData.contains("RootVehicle"),
          "Actual saved PlayerData must not duplicate a globally owned carriage body/NPC payload");
      h.assertTrue(f.rider.player.getVehicle() == front,
          "Online native save leaves the actual original direct player mounted");
      canonicalOwners(h, f, train, front);
    });
  }

  @GameTest(structure = STRUCTURE, maxTicks = 240)
  public void actualNativeLogoutAndRejoinKeepOriginalNpcAndAllowRealDirectReseating(GameTestHelper h) {
    var f = fixture(h);
    var train = new AtomicReference<Train>();
    var login = new AtomicReference<Rejoin>();
    var originalFront = new AtomicReference<CarriageContraptionEntity>();
    var originalSeat = new AtomicInteger(-1);
    var marker = new ItemStack(Items.EMERALD, 11);
    marker.set(DataComponents.CUSTOM_NAME, Component.literal("Original native PlayerData inventory"));
    var cleaned = new AtomicBoolean();
    var disassembled = new AtomicBoolean(true);
    Runnable cleanup = () -> {
      if (!cleaned.compareAndSet(false, true)) return;
      try {
        if (login.get() != null) login.get().close();
      } finally {
        try {
          f.rider.player.stopRiding();
          var actual = train.get();
          if (actual != null && TrainManager.get(h.getLevel()).get(actual.id) == actual)
            disassembled.set(actual.disassemble(
                Direction.EAST, h.absolutePos(T.above()), f.authorizer.player));
        } finally {
          f.rider.close();
          f.authorizer.close();
        }
      }
    };
    // Native login is asynchronous. Real test completion, failure and timeout all clean up
    // its native preparation and every actual registered player; no readiness is fabricated.
    ((GameTestHelperNativeInfoAccessor) (Object) h).create$getActualNativeTestInfo()
        .addListener(new GameTestListener() {
          public void testStructureLoaded(GameTestInfo info) {}
          public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
          public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
          public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) {
            cleanup.run();
          }
        });
    h.startSequence()
        .thenWaitUntil(() -> {
          NativeCarriageSourceReadiness.require(h, List.of(B, C, I, SEAT, B.above()));
          h.assertTrue(StationAssemblyPreflight.check(f.station, f.authorizer.player).readyForCapture(),
              "Actual source/query metadata becomes ready before real station capture");
        })
        .thenExecute(() -> {
          h.assertTrue(f.station.tryAssemble(f.authorizer.player),
              "Real station assembles a genuine supported NPC-only source Train");
          var actual = f.station.getStation().getPresentTrain();
          train.set(actual);
          h.assertTrue(actual != null && TrainManager.get(h.getLevel()).get(actual.id) == actual
                  && actual.carriages.size() == 1
                  && f.pig.getVehicle() == actual.carriages.getFirst().anyAvailableEntity(),
              "Actual Train owns its original native front and original NPC before real logout");
          originalSeat.set(boardDirect(h, f, actual));
          var front = actual.carriages.getFirst().anyAvailableEntity();
          originalFront.set(front);
          var profile = f.rider.player.getGameProfile();
          f.rider.player.getInventory().setItem(0, marker.copy());
          // The native PlayerList.remove performs its genuine file save, entity retirement and logout.
          f.rider.close();
          canonicalOwners(h, f, actual, front);
          var playerData = h.getLevel().getServer().getPlayerList()
              .loadPlayerData(new NameAndId(profile)).orElseThrow();
          h.assertTrue(!playerData.contains("RootVehicle"),
              "Actual native logout saves player data without migrating the real global body/NPCs");
          var input = TagValueInput.create(ProblemReporter.DISCARDING,
              h.getLevel().registryAccess(), playerData);
          var savedMarker = input.listOrEmpty("Inventory", ItemStackWithSlot.CODEC).stream()
              .filter(item -> item.slot() == 0).toList();
          h.assertTrue(savedMarker.size() == 1
                  && ItemStack.matches(savedMarker.getFirst().stack(), marker),
              "Actual native PlayerData file already contains all eleven original named emeralds");
          h.assertTrue(!front.getContraption().getSeatMapping().containsKey(profile.id()),
              "Actual native logout clears the departed direct player's live seat mapping");
          login.set(new Rejoin(h, profile));
        })
        .thenWaitUntil(() -> login.get().requireReady())
        .thenExecute(() -> {
          try {
            var returned = login.get().spawn();
            h.assertTrue(returned.player != f.rider.player
                    && ItemStack.matches(returned.player.getInventory().getItem(0), marker),
                "Real native PlayerData reload preserves original inventory on the new literal player object");
            var front = originalFront.get();
            int seat = originalSeat.get();
            h.assertTrue(!returned.player.isPassenger()
                    && front.addSittingPassenger(returned.player, seat)
                    && returned.player.getVehicle() == front
                    && front.getContraption().getSeatMapping().get(returned.player.getUUID()) == seat,
                "The actual rejoined literal player can board the original free direct native seat again");
            canonicalOwners(h, f, train.get(), front);
          } finally {
            cleanup.run();
          }
          h.assertTrue(disassembled.get(),
              "Actual fixture disassembly restores original world ownership before final native logout");
        })
        .thenSucceed();
  }
  @GameTest(structure = STRUCTURE, maxTicks = 240)
  public void actualIndependentOwnerAlsoKeepsNativePlayerDataAndLogoutFromTakingItsTree(
      GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(() -> {
          NativeCarriageSourceReadiness.require(h, List.of(B, C, I, SEAT, B.above()));
          h.assertTrue(StationAssemblyPreflight.check(f.station, f.authorizer.player).readyForCapture(),
              "Actual native source/wheel/query metadata becomes ready before independent capture");
        })
        .thenExecute(() -> {
          CarriageContraptionEntity owner = null;
          try {
            var body = new CarriageContraption(Direction.EAST);
            h.assertTrue(body.assemble(h.getLevel(), h.absolutePos(B)),
                "Actual source body captures original native blocks/cargo/NPC");
            owner = CarriageContraptionEntity.reserve(h.getLevel(), body);
            h.assertTrue(owner != null && ContraptionAssemblyGraph.assemble(h.getLevel(),
                List.of(new ContraptionAssemblyGraph.Root(owner, body)), complete -> {}).complete()
                && owner.initializeCapturedPassengers(),
                "Genuine native reservation/forest creates the real independent owner");
            int seat = body.getSeats().indexOf(B.above().subtract(B));
            h.assertTrue(owner.hasOwnedStructure() && !owner.isGloballyManaged()
                    && owner.shouldBeSaved() && !owner.hasExactlyOnePlayerPassenger()
                    && f.pig.getVehicle() == owner && seat >= 0
                    && owner.addSittingPassenger(f.rider.player, seat),
                "Actual chunk-owned independent body, original NPC and literal direct player have genuine custody");
            h.getLevel().getServer().getPlayerList().saveAll();
            var file = h.getLevel().getServer().getPlayerList()
                .loadPlayerData(new NameAndId(f.rider.player.getGameProfile())).orElseThrow();
            h.assertTrue(!file.contains("RootVehicle"),
                "Actual independent body uses the same existing virtual guard against PlayerData copies");
            f.rider.close();
            h.assertTrue(!owner.isRemoved() && owner.hasOwnedStructure() && owner.shouldBeSaved()
                    && h.getLevel().getEntity(owner.getUUID()) == owner
                    && h.getLevel().getEntity(f.pig.getUUID()) == f.pig
                    && !f.pig.isRemoved() && f.pig.getVehicle() == owner,
                "Actual PlayerList.remove preserves the exact independent native owner and original NPC UUIDs");
            var problems = new ProblemReporter.Collector();
            var output = TagValueOutput.createWithContext(problems, h.getLevel().registryAccess());
            h.assertTrue(owner.saveAsPassenger(output) && problems.isEmpty()
                    && output.buildResult().getBooleanOr("IndependentCarriage", false),
                "The actual independent native owner remains canonically saveable after real logout");
            h.assertTrue(owner.recoverIndependent(), "Actual independent recovery transfers source ownership back");
            var barrel = h.getBlockEntity(I, BarrelBlockEntity.class);
            h.assertTrue(ItemStack.matches(barrel.getItem(0), diamonds())
                    && ItemStack.matches(barrel.getItem(1), f.parcel)
                    && h.getLevel().getEntity(f.pig.getUUID()) == f.pig
                    && f.pig.getVehicle() instanceof SeatEntity,
                "Real restored world owns exact original NPC UUID/seat and named nested registry-bound cargo");
          } catch (AssemblyException failure) {
            h.fail("Actual independent fixture failed: " + failure.component.getString());
          } finally {
            if (owner != null && owner.hasOwnedStructure())
              h.assertTrue(owner.recoverIndependent(), "Actual fixture cleanup restores any remaining native owner");
            f.rider.close();
            f.authorizer.close();
          }
        }).thenSucceed();
  }
}
