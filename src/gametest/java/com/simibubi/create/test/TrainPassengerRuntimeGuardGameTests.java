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
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.*;

/**
 * Actual registered native players, world-owned NPC trees and source-preserving assembly refusal.
 */
public final class TrainPassengerRuntimeGuardGameTests {
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
        "Actual globally owned carriage remains natively persistable after runtime mount refusal");
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
                f.rider.player.stopRiding();f.rider.close();
                f.authorizer.close();
              }
            })
        .thenSucceed();
  }

  private static Entity outsideTree(GameTestHelper h, Fixture f) {
    var outside = h.spawnWithNoFreeWill(EntityTypes.PIG, S.south(4));
    outside.setNoGravity(true);
    h.assertTrue(
        f.rider.player.startRiding(outside, true, true)
            && f.rider.player.getVehicle() == outside
            && h.getLevel().getEntity(outside.getUUID()) == outside,
        "Real registered ServerPlayer can still ride a genuine ordinary world NPC");
    return outside;
  }

  @GameTest(structure=STRUCTURE,maxTicks=220)
  public void actualRegisteredPlayerCanUseNestedNpcAndLegacyDirectCarriageSeat(GameTestHelper h){runManaged(h,(f,train)->{
    var front=train.carriages.getFirst().anyAvailableEntity();h.assertTrue(f.rider.player.startRiding(f.pig,true,true)&&f.rider.player.getVehicle()==f.pig&&f.pig.getVehicle()==front,"Actual native descendant mount keeps literal player and original NPC under the owned frontend");
    var journal=CarriagePlayerReferences.get(h.getLevel().getServer());var relation=journal.link(f.rider.player.getUUID());h.assertTrue(relation!=null&&relation.token().parent().equals(f.pig.getUUID()),"Native parent-list adoption published exact nested relationship before callbacks");
    f.rider.player.stopRiding();int seat=front.getContraption().getSeats().indexOf(B.above().subtract(B));h.assertTrue(seat>=0&&front.addSittingPassenger(f.rider.player,seat)&&f.rider.player.getVehicle()==front,"Native direct seating remains supported after a voluntary nested dismount");
    var saved=train.carriages.getFirst().write(new DimensionPalette(),h.getLevel().registryAccess());boolean direct=false;for(var key:saved.getCompoundOrEmpty("Passengers").keySet()){
      var graph=CarriagePassengerGraph.fromLegacy(saved.getCompoundOrEmpty("Passengers").getCompoundOrEmpty(key),UUID.randomUUID());if(graph.root().equals(f.rider.player.getUUID())){direct=true;h.assertTrue(graph.nodes().size()==1&&graph.node(graph.root()) instanceof CarriagePassengerGraph.PlayerReference&&graph.node(graph.root()).parent().isEmpty(),"Direct player persists as a UUID-only root, with no NPC/player material payload");}}
    h.assertTrue(direct&&journal.link(f.rider.player.getUUID())==null,"Later direct native mount supersedes old nested restoration intent");
  });}

  @GameTest(structure=STRUCTURE,maxTicks=220)
  public void movingSeatAdoptsRealExternalNpcPlayerTreeWithOnlyOneRootSeat(GameTestHelper h){runManaged(h,(f,train)->{
    var front=train.carriages.getFirst().anyAvailableEntity();var outside=outsideTree(h,f);int seat=front.getContraption().getSeats().indexOf(B.above().subtract(B));
    h.assertTrue(front.addSittingPassenger(outside,seat)&&outside.getVehicle()==front&&f.rider.player.getVehicle()==outside&&h.getLevel().getEntity(outside.getUUID())==outside&&front.getContraption().getSeatMapping().get(outside.getUUID())==seat&&!front.getContraption().getSeatMapping().containsKey(f.rider.player.getUUID()),"Native adoption carries the same original NPC/player objects and assigns a seat only to the actual root");
    var relation=CarriagePlayerReferences.get(h.getLevel().getServer()).link(f.rider.player.getUUID());h.assertTrue(relation!=null&&relation.root().equals(outside.getUUID())&&relation.token().parent().equals(outside.getUUID()),"External native tree gets the exact accepted root/parent relationship");
  });}

  @GameTest(structure=STRUCTURE,maxTicks=220)
  public void nativeNpcToNpcAdoptionRetainsActualPlayerDepthWithoutExtraCarriageSeat(GameTestHelper h){runManaged(h,(f,train)->{
    var outside=outsideTree(h,f);var front=train.carriages.getFirst().anyAvailableEntity();h.assertTrue(outside.startRiding(f.pig,true,true)&&outside.getVehicle()==f.pig&&f.rider.player.getVehicle()==outside&&f.pig.getVehicle()==front&&!front.getContraption().getSeatMapping().containsKey(outside.getUUID()),"Genuine NPC-to-NPC mount retains literal player depth and one real root seat");
    var link=CarriagePlayerReferences.get(h.getLevel().getServer()).link(f.rider.player.getUUID());h.assertTrue(link!=null&&link.root().equals(f.pig.getUUID())&&link.token().parent().equals(outside.getUUID()),"Immediate nested NPC parent remains distinct from the actual carriage seat root");
  });}

  @GameTest(structure = STRUCTURE, maxTicks = 200)
  public void realAcceptedIndependentForestPersistsNativeNpcAndNestedPlayerReferences(
      GameTestHelper h) {
    var f = fixture(h);
    h.startSequence()
        .thenWaitUntil(
            () -> {
              NativeCarriageSourceReadiness.require(h, List.of(B, C, I, SEAT, B.above()));
              h.assertTrue(
                    StationAssemblyPreflight.check(f.station, f.authorizer.player)
                        .readyForCapture(),
                    "Real source readiness arises through native ticks before genuine independent"
                        + " capture");
            })
        .thenExecute(
            () -> {
              CarriageContraptionEntity owner = null;
              try {
                var body = new CarriageContraption(Direction.EAST);
                h.assertTrue(
                    body.assemble(h.getLevel(), h.absolutePos(B)),
                    "Actual source carriage capture succeeds");
                owner = CarriageContraptionEntity.reserve(h.getLevel(), body);
                h.assertTrue(
                    owner != null
                        && ContraptionAssemblyGraph.assemble(
                                h.getLevel(),
                                List.of(new ContraptionAssemblyGraph.Root(owner, body)),
                                complete -> {})
                            .complete()
                        && owner.initializeCapturedPassengers(),
                    "Real accepted empty reservation and native forest adopt original NPC");
                h.assertTrue(
                    !owner.isGloballyManaged()
                        && owner.hasOwnedStructure()
                        && owner.shouldBeSaved()
                        && h.getLevel().getEntity(owner.getUUID()) == owner
                        && f.pig.getVehicle() == owner,
                    "Genuine independent native body owns the exact original NPC without any"
                        + " fabricated Train");
                h.assertTrue(f.rider.player.startRiding(f.pig,true,true)&&f.rider.player.getVehicle()==f.pig,"Actual independent owned frontend accepts the same literal nested player");
                var problems = new ProblemReporter.Collector();
                var output =
                    TagValueOutput.createWithContext(problems, h.getLevel().registryAccess());
                h.assertTrue(
                    owner.saveAsPassenger(output)
                        && problems.isEmpty()
                        && output.buildResult().getBooleanOr("IndependentCarriage", false)&&output.buildResult().contains("IndependentPassengers")&&!output.buildResult().contains("Passengers"),
                    "Actual native independent save contains one typed NPC/reference graph without recursive material copies");
                h.assertTrue(
                    owner.recoverIndependent(),
                    "Real independent recovery restores the original source world");
                var barrel = h.getBlockEntity(I, BarrelBlockEntity.class);
                h.assertTrue(
                    ItemStack.matches(barrel.getItem(0), diamonds())
                        && ItemStack.matches(barrel.getItem(1), f.parcel)
                        && f.pig.getVehicle() instanceof SeatEntity
                        && h.getLevel().getEntity(f.pig.getUUID()) == f.pig
                        && f.rider.player.getVehicle()==f.pig,
                    "Actual world restoration preserves original NPC UUID/seat and exact nested"
                        + " named cargo");
              } catch (com.simibubi.create.content.contraptions.AssemblyException exception) {
                h.fail("Actual independent source capture/transfer failed: "
                    + exception.component.getString());
              } finally {
                if (owner != null && owner.hasOwnedStructure())
                  h.assertTrue(
                      owner.recoverIndependent(),
                      "Actual failed fixture must restore owned native cargo");
                f.rider.player.stopRiding();f.rider.close();
                f.authorizer.close();
              }
            })
        .thenSucceed();
  }
}
