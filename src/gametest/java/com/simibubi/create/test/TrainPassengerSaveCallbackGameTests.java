package com.simibubi.create.test;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/**
 * Actual registered native players, world-owned NPC trees and source-preserving mixed passenger capture.
 */
public final class TrainPassengerSaveCallbackGameTests {
  private static final String STRUCTURE = "create_test:reservoir";
  private static final BlockPos R = new BlockPos(3, 6, 4),
      T = R.east(),
      S = T.south(2),
      B = T.east().above(),
      C = B.east(),
      I = B.east(2),
      SEAT = I.above();

  private record Joined(ServerPlayer player, EmbeddedChannel channel) {
    void close() {
      if (player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player)
        player.level().getServer().getPlayerList().remove(player);
      channel.finishAndReleaseAll();
    }
  }

  private record Fixture(
      StationBlockEntity station,
      Joined authorizer,
      Joined rider,
      BarrelBlockEntity barrel,
      SeatEntity seat,
      Entity pig,
      SuperGlueEntity glue,
      ItemStack parcel) {}

  private static Joined join(GameTestHelper h) {
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

  private static Fixture fixture(GameTestHelper h) {
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

  private static void mount(GameTestHelper h, Fixture fixture) {
    var player = fixture.rider.player;player.getInventory().clearContent();var stock=new ItemStack(Items.EMERALD,11);stock.set(DataComponents.CUSTOM_NAME,Component.literal("Original late-save nested inventory "+player.getUUID()));player.getInventory().setItem(0,stock);
    h.assertTrue(
        player.startRiding(fixture.pig, true, true)
            && player.getVehicle() == fixture.pig
            && fixture.pig.hasPassenger(player),
        "Actual registered native ServerPlayer becomes the real source NPC passenger");
  }

  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void actualNativeBarrelSaveAddedPlayerTransfersLatestSourceWithExactCargo(
      GameTestHelper h) {
    var fixture = fixture(h);
    var active = new AtomicBoolean(true);
    var callbacks = new AtomicInteger();
    var reservations = new ArrayList<CarriageContraptionEntity>();
    var scope = new AtomicReference<NativeBarrelSaveFaultFixtures.Scope>();
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, level) -> {
          if (!active.get()
              || level != h.getLevel()
              || !(entity instanceof CarriageContraptionEntity front)
              || !front.blockPosition().equals(h.absolutePos(B))) return;
          reservations.add(front);
          h.assertTrue(
              reservations.size() == 1
                  && front.getContraption() == null
                  && level.getEntity(front.getUUID()) == front
                  && fixture.rider.player.getVehicle() == null
                  && fixture.pig.getVehicle() == fixture.seat,
              "Actual reservation follows the initial safe NPC-only source capture");
          scope.set(
              NativeBarrelSaveFaultFixtures.afterSave(
                  fixture.barrel,
                  () -> {
                    callbacks.incrementAndGet();
                    h.assertTrue(
                        !front.isRemoved()
                            && front.getContraption() == null
                            && level.getEntity(front.getUUID()) == front
                            && h.getBlockEntity(I, BarrelBlockEntity.class) == fixture.barrel
                            && !fixture.barrel.isRemoved()
                            && ItemStack.matches(fixture.barrel.getItem(0), diamonds())
                            && ItemStack.matches(fixture.barrel.getItem(1), fixture.parcel),
                        "Real native original barrel save completes with exact world custody"
                            + " and genuine cargo after reservation");
                    h.assertBlockPresent(AllBlocks.SMALL_BOGEY, B);
                    h.assertBlockPresent(AllBlocks.TRAIN_CONTROLS, C);
                    h.assertBlockPresent(AllBlocks.SEATS.get(DyeColor.RED), SEAT);
                    mount(h, fixture);
                  }));
        });
    h.startSequence()
        .thenWaitUntil(
            () -> {
              NativeCarriageSourceReadiness.require(h, List.of(B, C, I, SEAT));
              h.assertTrue(
                    StationAssemblyPreflight.check(fixture.station, fixture.authorizer.player)
                        .readyForCapture(),
                    "Genuine source wheel/entity metadata becomes ready through ordinary native"
                        + " ticks");
            })
        .thenExecute(
            () -> {
              try {
                var registry = Set.copyOf(TrainManager.get(h.getLevel()).trains().keySet());
                h.assertTrue(fixture.station.tryAssemble(fixture.authorizer.player),"A literal player added by the real final native BE save is captured with the actual source tree");
                h.assertTrue(callbacks.get()==1&&scope.get()!=null&&scope.get().fired(),"Real one-shot Barrel.saveAdditional RETURN hook actually ran before source removal");
                var train=CarriageNestedPlayerLifecycleGameTests.assertOriginalCapture(h,fixture.station,fixture.authorizer.player,fixture.rider.player,fixture.pig,fixture.barrel,diamonds(),fixture.parcel);
                var expected=new HashSet<>(registry);expected.add(train.id);h.assertTrue(TrainManager.get(h.getLevel()).trains().keySet().equals(expected)&&reservations.size()==1&&reservations.getFirst()==train.carriages.getFirst().anyAvailableEntity(),"The one actual reserved endpoint owns the accepted final native source state");
                CarriageNestedPlayerLifecycleGameTests.assertOriginalWorldRestore(h,train,fixture.authorizer.player,fixture.rider.player,fixture.pig,diamonds(),fixture.parcel);
              } finally {
                active.set(false);
                if (scope.get() != null) scope.get().close();
                try{CarriageNestedPlayerLifecycleGameTests.cleanupActualCapture(h,fixture.station,fixture.authorizer.player,reservations);}
                finally{fixture.rider.player.stopRiding();fixture.rider.close();fixture.authorizer.close();}
              }
            })
        .thenSucceed();
  }
}
