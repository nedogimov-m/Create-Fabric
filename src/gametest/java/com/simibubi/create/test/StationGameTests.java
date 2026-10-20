package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.bogey.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/**
 * Proposed actual station/target/graph/layout tests. No test represents final train assembly.
 * UNRUN.
 */
public final class StationGameTests {
  private static final String STRUCTURE = "create_test:reservoir";
  private static final BlockPos R = new BlockPos(4, 6, 4), T = R.east(), S = T.south(2);

  private static BlockState rail() {
    return AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.XO);
  }

  private static void line(GameTestHelper h) {
    for (int i = 0; i < 15; i++) {
      h.setBlock(R.east(i).below(), Blocks.STONE);
      h.setBlock(R.east(i), rail());
    }
    for (int i = 0; i < 15; i++)
      h.assertTrue(
          TrackPropagator.reconcile(h.getLevel(), h.absolutePos(R.east(i)), rail(), false)
              .complete(),
          "Actual loaded line must produce its genuine native graph");
  }

  private static Player player(GameTestHelper h) {
    var player = h.makeMockPlayer(GameType.SURVIVAL);
    player.setPos(Vec3.atBottomCenterOf(h.absolutePos(S)).add(0, 0, 1));
    return player;
  }

  private static void near(Player player, BlockPos pos) {
    player.setPos(Vec3.atCenterOf(pos).add(0, 0, 1));
  }

  private static TrackTargetingSelection selection(GameTestHelper h) {
    return new TrackTargetingSelection(
        GlobalPos.of(h.getLevel().dimension(), h.absolutePos(T)),
        true,
        new Vec3(1, 0, 0),
        Optional.empty());
  }

  private static StationBlockEntity station(GameTestHelper h) {
    line(h);
    h.setBlock(S.below(), Blocks.STONE);
    h.setBlock(S, AllBlocks.TRACK_STATION);
    var station = h.getBlockEntity(S, StationBlockEntity.class);
    h.assertTrue(
        station.setTarget(selection(h)), "Actual native station accepts its real track selection");
    h.assertTrue(
        station.getStation() != null, "Actual point belongs to the real RailwayGraphManager");
    return station;
  }

  private static StationBlockEntity assembling(GameTestHelper h, Player player) {
    var station = station(h);
    h.assertTrue(
        station.configure(player, "Native Station", true, DoorControl.ALL),
        "Actual station enters its real source assembly mode");
    return station;
  }

  private static boolean click(
      GameTestHelper h, StationBlockEntity station, Player player, int offset) {
    var pos = h.absolutePos(T.east(offset + 1));
    near(player, pos);
    var state = LoadedLevelAccess.blockState(h.getLevel(), pos);
    return station.trackClicked(
        player, InteractionHand.MAIN_HAND, (ITrackBlock) state.getBlock(), state, pos);
  }

  private static ItemStack stock() {
    var stack = new ItemStack(AllBlocks.RAILWAY_CASING, 5);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Original casing stock"));
    return stack;
  }

  private static void physicalBogey(GameTestHelper h, int offset) {
    h.setBlock(
        T.east(offset + 1).above(),
        AllBlocks.SMALL_BOGEY
            .defaultBlockState()
            .setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X));
  }

  private static InteractionResult itemClick(GameTestHelper h, Player player, BlockPos at) {
    var pos = h.absolutePos(at);
    near(player, pos);
    return player
        .getMainHandItem()
        .getItem()
        .useOn(
            new UseOnContext(
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                    Vec3.atBottomCenterOf(pos).add(0, .125, 0), Direction.UP, pos, false)));
  }

  private static void awaitPreflight(
      GameTestHelper h,
      StationBlockEntity station,
      Player player,
      java.util.function.Consumer<StationAssemblyPreflight.Plan> assertion) {
    near(player, station.getBlockPos());
    h.succeedWhen(
        () -> {
          var result = StationAssemblyPreflight.check(station, player);
          h.assertTrue(
              result.readyForCapture(),
              "Actual native entity readiness and genuine wheel points must resolve: "
                  + result.status());
          assertion.accept(result.plan());
        });
  }

  @GameTest(structure = STRUCTURE)
  public void realStationBindsOneWorldOwnedPointOnItsPhysicalEdge(GameTestHelper h) {
    var station = station(h);
    var point = station.getStation();
    var location = selection(h).resolve(h.getLevel());
    h.assertTrue(
        point.ownedBy(station)
            && point.graph() == location.graph
            && point.graph().manager() == RailwayGraphManager.get(h.getLevel()),
        "Actual manager, graph and owner identities");
    h.assertTrue(
        point
            .graph()
            .getConnection(location.edge.map(location.graph::locateNode))
            .getEdgeData()
            .getPoints()
            .contains(point),
        "Actual scalar attachment exists on the real edge");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void repeatedNativeTicksRetainSamePointAndGraphRevision(GameTestHelper h) {
    var station = station(h);
    var point = station.getStation();
    long revision = point.graph().revision();
    for (int i = 0; i < 30; i++)
      StationBlockEntity.tick(
          h.getLevel(), station.getBlockPos(), station.getBlockState(), station);
    h.assertTrue(
        station.getStation() == point && point.graph().revision() == revision,
        "No duplicate publication or dirty topology from stable owner ticks");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void secondStationCannotStealAnOccupiedPhysicalMarker(GameTestHelper h) {
    var station = station(h);
    var elsewhere = S.south(2);
    h.setBlock(elsewhere, AllBlocks.TRACK_STATION);
    var other = h.getBlockEntity(elsewhere, StationBlockEntity.class);
    h.assertFalse(
        other.setTarget(selection(h)), "Source marker overlap denies the actual second station");
    h.assertTrue(
        other.getStation() == null && station.getStation().ownedBy(station),
        "Original real owner remains intact");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void forgedSavedUuidCannotAdoptAnotherNativeBlockOwner(GameTestHelper h) {
    var station = station(h);
    var data = station.saveWithFullMetadata(h.getLevel().registryAccess());
    var elsewhere = h.absolutePos(S.south(2));
    var clone =
        (StationBlockEntity)
            BlockEntity.loadStatic(
                elsewhere, station.getBlockState(), data, h.getLevel().registryAccess());
    clone.setLevel(h.getLevel());
    clone.targeting().resolveTick();
    h.assertTrue(
        clone.getStation() == null && station.getStation().ownedBy(station),
        "Same saved point UUID at another actual location cannot steal graph custody");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void nativeItemSelectsThenPlacesAnActualBoundStationAndSpendsOne(GameTestHelper h) {
    line(h);
    h.setBlock(S.below(), Blocks.STONE);
    var player = player(h);
    player.setYRot(-90);
    var stack = new ItemStack(AllBlocks.TRACK_STATION, 3);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Station stock"));
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    h.assertTrue(
        itemClick(h, player, T).consumesAction()
            && stack.has(AllDataComponents.TRACK_TARGETING_SELECTION),
        "Real native item stores typed selected track");
    h.assertTrue(
        itemClick(h, player, S.below()).consumesAction(), "Native BlockItem places real station");
    var station = h.getBlockEntity(S, StationBlockEntity.class);
    h.assertTrue(
        station.getStation() != null
            && stack.getCount() == 2
            && !stack.has(AllDataComponents.TRACK_TARGETING_SELECTION)
            && stack.has(DataComponents.CUSTOM_NAME),
        "Actual point and exact inventory/component effects");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void failedNativePlacementRetainsSelectionAndOriginalItems(GameTestHelper h) {
    line(h);
    var player = player(h);
    player.setYRot(-90);
    var stack = new ItemStack(AllBlocks.TRACK_STATION, 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    itemClick(h, player, T);
    var selected = stack.get(AllDataComponents.TRACK_TARGETING_SELECTION);
    h.setBlock(S.below(), Blocks.STONE);
    h.setBlock(S, Blocks.STONE);
    h.assertFalse(
        itemClick(h, player, S.below()).consumesAction(), "Actual blocked native placement fails");
    h.assertTrue(
        stack.getCount() == 2
            && Objects.equals(stack.get(AllDataComponents.TRACK_TARGETING_SELECTION), selected)
            && h.getBlockState(S).is(Blocks.STONE),
        "Failed placement preserves source selection, stock and obstruction");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void zeroAxisTrackCannotCrashOrCreateNativeTargetSelection(GameTestHelper h) {
    line(h);
    h.setBlock(T, rail().setValue(TrackBlock.SHAPE, TrackShape.NONE));
    var player = player(h);
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.TRACK_STATION));
    h.assertTrue(
        itemClick(h, player, T) == InteractionResult.FAIL
            && !player.getMainHandItem().has(AllDataComponents.TRACK_TARGETING_SELECTION),
        "NONE target is explicitly rejected before nearest-axis lookup");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void crossDimensionOrChangedAxisSelectionCannotBind(GameTestHelper h) {
    line(h);
    h.setBlock(S, AllBlocks.TRACK_STATION);
    var station = h.getBlockEntity(S, StationBlockEntity.class);
    var actual = selection(h);
    h.assertFalse(
        station.setTarget(
            new TrackTargetingSelection(
                GlobalPos.of(Level.NETHER, actual.target().pos()),
                true,
                actual.axis(),
                Optional.empty())),
        "Different native dimension rejected");
    h.assertFalse(
        station.setTarget(
            new TrackTargetingSelection(
                actual.target(), true, new Vec3(0, 0, 1), Optional.empty())),
        "Stale physical axis rejected");
    h.assertTrue(station.getStation() == null, "No partial point publication");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void realAbsentFrontierPublishesNoPointAndCreatesNoChunkTicket(GameTestHelper h) {
    var station = station(h);
    var tag = new CompoundTag();
    station.targeting().write(tag);
    var saved = tag.getCompound("TargetingSelection").orElseThrow();
    var far = new BlockPos(25000000, 64, 25000000);
    saved.store("TargetTrack", BlockPos.CODEC, far.subtract(station.getBlockPos()));
    tag.put("TargetingSelection", saved);
    var detached = new StationBlockEntity(station.getBlockPos(), station.getBlockState());
    detached.setLevel(h.getLevel());
    detached.targeting().read(tag);
    var graph = station.getStation().graph();
    long revision = graph.revision();
    h.assertTrue(
        detached.targeting().resolveTick() == null
            && detached.targeting().status()
                == TrackTargetingBehaviour.Status.PENDING_LOADED_FRONTIER,
        "Actual missing chunk is explicit pending");
    h.assertTrue(
        !LoadedLevelAccess.isLoaded(h.getLevel(), far)
            && graph.revision() == revision
            && station.getStation().ownedBy(station),
        "No native terrain ticket or graph/owner mutation");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void sourceStripReservesItsTerminalRailAndRequiresFirstOffsetZero(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    var area = StationAssemblyArea.discover(station);
    h.assertTrue(
        area.length() == 12
            && area.isValidBogeyOffset(0)
            && !area.isValidBogeyOffset(1)
            && !area.isValidBogeyOffset(2)
            && !area.isValidBogeyOffset(3),
        "Thirteen rails after marker produce source12-cell usable strip and first-only0");
    physicalBogey(h, 0);
    area = StationAssemblyArea.discover(station);
    h.assertTrue(
        !area.isValidBogeyOffset(0) && !area.isValidBogeyOffset(2) && area.isValidBogeyOffset(3),
        "Actual first bogey opens real legal source positions at3+");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void stationAuthorizedSurvivalClickCreatesBogeyAndDebitsOneNamedCasing(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    player.setItemInHand(InteractionHand.MAIN_HAND, stock());
    h.assertTrue(
        click(h, station, player, 0), "Real station authorizes source physical first bogey");
    var bogey = h.getBlockEntity(T.east().above(), StandardBogeyBlockEntity.class);
    h.assertTrue(
        bogey.getBlockState().getValue(AbstractBogeyBlock.AXIS) == Direction.Axis.X
            && player.getMainHandItem().getCount() == 4
            && player.getMainHandItem().has(DataComponents.CUSTOM_NAME),
        "Actual registered native BE, axis and exact casing debit");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void validOffsetWithoutCasingCannotCreateBogeyOrDestroyObstruction(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    h.setBlock(T.east().above(), Blocks.BARREL);
    var before = h.getLevel().getBlockEntity(h.absolutePos(T.east().above()));
    h.assertFalse(click(h, station, player, 0), "Source survival casing requirement");
    h.assertTrue(
        h.getLevel().getBlockEntity(h.absolutePos(T.east().above())) == before,
        "Original actual barrel BE remains");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void adjacentSourceClickCyclesExistingBogeyWithoutSpendingCasing(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    player.setItemInHand(InteractionHand.MAIN_HAND, stock());
    click(h, station, player, 0);
    var original = h.getBlockEntity(T.east().above(), StandardBogeyBlockEntity.class);
    var data = original.getBogeyData();
    data.putString("NativeMarker", "Original bogey data");
    original.setBogeyData(data);
    h.assertTrue(click(h, station, player, 1), "Source plus/minus1 selects genuine existing bogey");
    var actual = h.getBlockEntity(T.east().above(), StandardBogeyBlockEntity.class);
    h.assertTrue(
        actual.getBlockState().is(AllBlocks.LARGE_BOGEY)
            && actual.getBogeyData().getStringOr("NativeMarker", "").equals("Original bogey data")
            && player.getMainHandItem().getCount() == 4,
        "Actual size replacement preserves source data and costs no casing");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void sourceDistanceTwoRejectsNewBogeyWithoutDebitingOrReplacingWorld(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    player.setItemInHand(InteractionHand.MAIN_HAND, stock());
    click(h, station, player, 0);
    h.assertFalse(
        click(h, station, player, 2), "Source2-block exclusion has no nearby +/-1 bogey to cycle");
    h.assertTrue(
        h.getBlockState(T.east(3).above()).isAir() && player.getMainHandItem().getCount() == 4,
        "No illegal native bogey or debit");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void leavingAssemblyDisablesActualAreaAuthorization(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    h.assertTrue(
        !StationAssemblyAreas.ownersAt(h.getLevel(), h.absolutePos(T.east())).isEmpty(),
        "Real loaded area indexed");
    h.assertTrue(
        station.configure(player, "Native Station", false, DoorControl.NONE), "Real mode exit");
    h.assertTrue(
        StationAssemblyAreas.ownersAt(h.getLevel(), h.absolutePos(T.east())).isEmpty()
            && station.getStation().canNavigateVia(null),
        "Area and genuine graph restriction removed");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void readOnlyPreflightReportsNoBogeysWithoutChangingBlocksOrGraph(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    var graph = station.getStation().graph();
    long revision = graph.revision();
    var result = StationAssemblyPreflight.check(station, player);
    h.assertTrue(
        result.status() == StationAssemblyPreflight.Status.NO_BOGEYS
            && result.plan() == null
            && graph.revision() == revision
            && h.getBlockState(T.east().above()).isAir(),
        "Actual failed read-only preflight has no fabricated Train/assembly commit");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void physicalOffStationBogeyAndTooCloseLayoutHaveExplicitFailures(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    physicalBogey(h, 3);
    h.assertTrue(
        StationAssemblyPreflight.check(station, player).status()
            == StationAssemblyPreflight.Status.FRONTMOST_BOGEY,
        "Actual first physical offset must be0");
    physicalBogey(h, 0);
    physicalBogey(h, 2);
    h.succeedWhen(
        () ->
            h.assertTrue(
                StationAssemblyPreflight.check(station, player).status()
                    == StationAssemblyPreflight.Status.BOGEYS_TOO_CLOSE,
                "Actual source spacing rejected after native entity readiness"));
  }

  @GameTest(structure = STRUCTURE)
  public void realWheelPreflightStopsBeforeCarriageCaptureAndWorldRemoval(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    physicalBogey(h, 0);
    physicalBogey(h, 3);
    awaitPreflight(
        h,
        station,
        player,
        plan -> {
          h.assertValueEqual(
              plan.wheelPoints().size(), 4, "Two actual graph wheel points per real bogey");
          for (var point : plan.wheelPoints())
            h.assertTrue(
                point.edge != null && point.node1 != null && point.node2 != null,
                "Actual reciprocal graph coordinates");
          h.assertTrue(
              h.getBlockState(T.east().above()).is(AllBlocks.SMALL_BOGEY)
                  && h.getBlockState(T.east(4).above()).is(AllBlocks.SMALL_BOGEY),
              "Read-only preflight never captures physical world blocks");
        });
  }

  @GameTest(structure = STRUCTURE)
  public void capturePlanRejectsActualBogeyDataChangeBeforeCommit(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    physicalBogey(h, 0);
    awaitPreflight(
        h,
        station,
        player,
        plan -> {
          var entity = h.getBlockEntity(T.east().above(), StandardBogeyBlockEntity.class);
          var data = entity.getBogeyData();
          data.putString("ChangedAfterPlan", "Actual mutation");
          entity.setBogeyData(data);
          h.assertFalse(
              plan.stillCurrent(station, player),
              "Changed native bogey data invalidates actual saved witnesses");
        });
  }

  @GameTest(structure = STRUCTURE)
  public void capturePlanRejectsSamePositionNativeBogeyReplacement(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    physicalBogey(h, 0);
    awaitPreflight(
        h,
        station,
        player,
        plan -> {
          h.setBlock(T.east().above(), Blocks.AIR);
          physicalBogey(h, 0);
          h.assertFalse(
              plan.stillCurrent(station, player),
              "Same state/position with a new real BE does not retain custody witness");
        });
  }

  @GameTest(structure = STRUCTURE)
  public void capturePlanRejectsStationRevisionChange(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    physicalBogey(h, 0);
    awaitPreflight(
        h,
        station,
        player,
        plan -> {
          h.assertTrue(
              station.configure(player, "Changed Station", true, DoorControl.NORTH),
              "Actual permitted reconfiguration");
          h.assertFalse(
              plan.stillCurrent(station, player),
              "Stale native station configuration invalidates transient capture contract");
        });
  }

  @GameTest(structure = STRUCTURE)
  public void distantAndAdventurePlayersCannotConfigureOrAuthorizeNativeBogey(GameTestHelper h) {
    var station = station(h);
    var player = player(h);
    player.setPos(player.position().add(30, 0, 0));
    h.assertFalse(
        station.configure(player, "Forged", true, DoorControl.ALL), "Actual native reach denied");
    var adventure = h.makeMockServerPlayer(GameType.ADVENTURE);
    h.assertTrue(
        adventure.gameMode() == GameType.ADVENTURE && !adventure.mayBuild(),
        "Genuine native server mock applies actual Adventure abilities before checking station"
            + " denial");
    near(adventure, station.getBlockPos());
    h.assertFalse(
        station.configure(adventure, "Forged", true, DoorControl.ALL), "Actual mayBuild denied");
    h.assertTrue(
        !station.isAssembling() && station.stationName().equals("Track Station"),
        "No accepted state fabricated");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void nativeFactoryRoundtripPreservesUuidRelativeTargetNameDoorsAndRevision(
      GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    station.configure(player, "Exact native name", true, DoorControl.WEST);
    var tag = station.saveWithFullMetadata(h.getLevel().registryAccess());
    var restored =
        (StationBlockEntity)
            BlockEntity.loadStatic(
                station.getBlockPos(), station.getBlockState(), tag, h.getLevel().registryAccess());
    h.assertTrue(
        restored != station
            && restored.getType() == AllBlockEntityTypes.TRACK_STATION
            && restored.targeting().id().equals(station.targeting().id())
            && restored.targeting().selection().equals(station.targeting().selection())
            && restored.stationName().equals("Exact native name")
            && restored.doorControl() == DoorControl.WEST
            && restored.configurationRevision() == station.configurationRevision(),
        "Genuine registry factory preserves actual source/configuration fields");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void actualStationPointCodecPreservesCompleteNestedNamedOfflinePortBuffer(
      GameTestHelper h) {
    var station = station(h);
    var items = new ArrayList<ItemStack>(Collections.nCopies(18, ItemStack.EMPTY));
    var nested = new ItemStack(Items.DIAMOND, 7);
    nested.set(DataComponents.CUSTOM_NAME, Component.literal("Original station mail"));
    var box = new ItemStack(Items.SHULKER_BOX);
    box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(nested)));
    box.set(DataComponents.CUSTOM_NAME, Component.literal("Nested offline port cargo"));
    items.set(4, box);
    var port = new GlobalPackagePort("Native Address", items, true);
    var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var data =
        (CompoundTag) GlobalStation.CODEC.encodeStart(ops, station.getStation()).getOrThrow();
    data.put(
        "Ports",
        GlobalStation.Port.CODEC
            .listOf()
            .encodeStart(ops, List.of(new GlobalStation.Port(station.getBlockPos().east(), port)))
            .getOrThrow());
    var restored = StationEdgePoints.STATION.codec().parse(ops, data).getOrThrow();
    var actual = restored.ports().getFirst().data();
    h.assertTrue(
        restored.owner().equals(station.getStation().owner())
            && restored.id.equals(station.targeting().id())
            && actual.primed()
            && actual.address().equals("Native Address")
            && actual.items().size() == 18
            && ItemStack.isSameItemSameComponents(actual.items().get(4), box),
        "Complete real registry-aware station/port item-component codec");
    actual.items().get(4).shrink(1);
    h.assertTrue(
        restored.ports().getFirst().data().items().get(4).getCount() == 1,
        "Read-out item copies cannot mutate graph-owned source cargo");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void malformedPortSlotsCannotSilentlyDropSavedCargo(GameTestHelper h) {
    var station = station(h);
    var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var data =
        (CompoundTag) GlobalStation.CODEC.encodeStart(ops, station.getStation()).getOrThrow();
    var port =
        (CompoundTag)
            GlobalPackagePort.CODEC.encodeStart(ops, new GlobalPackagePort()).getOrThrow();
    port.put("OfflineBuffer", new ListTag());
    var entry = new CompoundTag();
    entry.store("Pos", BlockPos.CODEC, station.getBlockPos());
    entry.put("Data", port);
    var ports = new ListTag();
    ports.add(entry);
    data.put("Ports", ports);
    boolean rejected = false;
    try {
      rejected = GlobalStation.CODEC.parse(ops, data).error().isPresent();
    } catch (IllegalArgumentException invalid) {
      rejected = true;
    }
    h.assertTrue(
        rejected, "Invalid complete saved port fails explicitly instead of successful empty cargo");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void typedSelectionAndActualStationPacketsRoundtripThroughNativeRegistryBuffer(
      GameTestHelper h) {
    var station = station(h);
    var selected = selection(h);
    var packet =
        new StationEditPacket(
            station.getBlockPos(),
            station.targeting().id(),
            station.configurationRevision(),
            StationEditPacket.Action.CONFIGURE,
            "Native packet",
            true,
            DoorControl.SOUTH);
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      TrackTargetingSelection.STREAM_CODEC.encode(buffer, selected);
      h.assertTrue(
          TrackTargetingSelection.STREAM_CODEC.decode(buffer).equals(selected),
          "Actual typed selection stream");
      StationEditPacket.CODEC.encode(buffer, packet);
      h.assertTrue(
          StationEditPacket.CODEC.decode(buffer).equals(packet),
          "Actual bounded station action payload");
      var snapshot = StationNetworking.snapshot(station, true);
      StationNetworking.State.CODEC.encode(buffer, snapshot);
      h.assertTrue(
          StationNetworking.State.CODEC.decode(buffer).equals(snapshot),
          "Actual UUID/revision/status native snapshot");
    } finally {
      buffer.release();
    }
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void packetWithForeignPointUuidOrFarOwnerCannotEditLoadedStation(GameTestHelper h) {
    var station = station(h);
    var player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
    near(player, station.getBlockPos());
    var packet =
        new StationEditPacket(
            station.getBlockPos(),
            UUID.randomUUID(),
            station.configurationRevision(),
            StationEditPacket.Action.CONFIGURE,
            "Forged",
            true,
            DoorControl.NONE);
    h.assertFalse(
        StationNetworking.apply(player, packet),
        "Actual loaded BE rejects foreign graph point UUID");
    var far = new BlockPos(25000000, 64, 25000000);
    h.assertFalse(
        StationNetworking.apply(
            player,
            new StationEditPacket(
                far,
                station.targeting().id(),
                0,
                StationEditPacket.Action.CONFIGURE,
                "Forged",
                true,
                DoorControl.NONE)),
        "No loaded native owner at claimed far coordinate");
    h.assertTrue(
        !LoadedLevelAccess.isLoaded(h.getLevel(), far) && !station.isAssembling(),
        "No ticket or actual station mutation");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void actualStationDestructionRemovesItsPointAndEphemeralArea(GameTestHelper h) {
    var player = player(h);
    var station = assembling(h, player);
    var graph = station.getStation().graph();
    var id = station.targeting().id();
    h.getLevel().destroyBlock(station.getBlockPos(), false);
    h.assertTrue(
        graph.getPoint(StationEdgePoints.STATION, id) == null
            && StationAssemblyAreas.ownersAt(h.getLevel(), h.absolutePos(T.east())).isEmpty(),
        "Native preRemove retires actual point and cache, not a fake detached record");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void actualNativeUnloadFlagDoesNotDestroyPersistentStationPoint(GameTestHelper h) {
    var station = station(h);
    var point = station.getStation();
    station.setRemoved();
    h.assertTrue(
        point.graph() != null
            && point.graph().getPoint(StationEdgePoints.STATION, point.id) == point,
        "Native setRemoved alone is chunk retirement, not actual station destruction");
    station.clearRemoved();
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void stalePhysicalTrackCannotAuthorizeConfigFromCachedPoint(GameTestHelper h) {
    var station = station(h);
    var player = player(h);
    h.setBlock(T, Blocks.AIR);
    h.assertFalse(
        station.configure(player, "Forged after rail break", true, DoorControl.NONE),
        "Actual selected physical target revalidated before cached point authority");
    h.assertTrue(
        !station.isAssembling(), "No actual assembly mode after source rail disappearance");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void foreignPointMigrationCannotReplaceTheActualNativeOwner(GameTestHelper h) {
    var station = station(h);
    var point = station.getStation();
    var data = new CompoundTag();
    var dimensions = new DimensionPalette();
    point.write(data, h.getLevel().registryAccess(), dimensions);
    dimensions.write(data);
    data.store("Id", UUIDUtil.CODEC, UUID.randomUUID());
    boolean rejected = false;
    try {
      station.invalidateEdgePoint(data);
    } catch (IllegalArgumentException foreign) {
      rejected = true;
    }
    h.assertTrue(
        rejected && station.getStation() == point && point.ownedBy(station),
        "Foreign source point migration cannot alter genuine native owner state");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void breakingActualTargetRailRetiresPointAndKeepsCompleteMigrationInTheOwningStation(
      GameTestHelper h) {
    var station = station(h);
    var id = station.targeting().id();
    h.getLevel().destroyBlock(h.absolutePos(T), false);
    h.succeedWhen(
        () -> {
          station.targeting().resolveTick();
          for (var graph : RailwayGraphManager.get(h.getLevel()).getTrackNetworks().values())
            h.assertTrue(
                graph.getPoint(StationEdgePoints.STATION, id) == null,
                "Actual destroyed rail cannot retain a compressed station point");
          var data = new CompoundTag();
          station.targeting().write(data);
          h.assertTrue(
              station.getStation() == null
                  && data.getCompound("Migrate").isPresent()
                  && h.getBlockEntity(S, StationBlockEntity.class) == station,
              "Real station survives with source migration data; no accepted fake graph"
                  + " attachment");
        });
  }

  @GameTest(structure = STRUCTURE)
  public void
      physicalTrackMigrationPreservesNamedNestedEnchantedOfflinePortThroughActualRegistryOps(
          GameTestHelper h) {
    var station = station(h);
    var original = station.getStation();
    var graph = original.graph();
    var registries = h.getLevel().registryAccess();
    var enchant =
        registries
            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
    var tool = new ItemStack(Items.DIAMOND_PICKAXE);
    tool.enchant(enchant, 3);
    tool.setDamageValue(37);
    tool.set(DataComponents.CUSTOM_NAME, Component.literal("Original registry-bound station mail"));
    var box = new ItemStack(Items.SHULKER_BOX);
    box.set(DataComponents.CUSTOM_NAME, Component.literal("Actual enchanted offline port parcel"));
    box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(tool)));
    var slots =
        new ArrayList<ItemStack>(Collections.nCopies(GlobalPackagePort.SLOTS, ItemStack.EMPTY));
    slots.set(7, box);
    var port = new GlobalPackagePort("Registry Address", slots, true);
    var ops = registries.createSerializationContext(NbtOps.INSTANCE);
    // Actual complete typed-point codec seeds source offline data; no postbox transfer is invented.
    var data = (CompoundTag) GlobalStation.CODEC.encodeStart(ops, original).getOrThrow();
    data.put(
        "Ports",
        GlobalStation.Port.CODEC
            .listOf()
            .encodeStart(ops, List.of(new GlobalStation.Port(station.getBlockPos().east(), port)))
            .getOrThrow());
    var decoded = StationEdgePoints.STATION.codec().parse(ops, data).getOrThrow();
    var id = original.id;
    graph.removePoint(StationEdgePoints.STATION, id);
    graph.addPoint(StationEdgePoints.STATION, decoded);
    station.targeting().resolveTick();
    h.assertTrue(
        station.getStation() == decoded && decoded.ownedBy(station) && decoded.graph() == graph,
        "Real manager owns the full decoded source station point");
    h.getLevel().destroyBlock(h.absolutePos(T), false);
    h.assertTrue(
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(T), rail(), true).complete(),
        "Actual physical track destruction completes real point invalidation");
    station.targeting().resolveTick();
    var migrated = new CompoundTag();
    station.targeting().write(migrated);
    h.assertTrue(
        station.getStation() == null && migrated.getCompound("Migrate").isPresent(),
        "Actual native owner receives complete real point migration");
    h.setBlock(T, rail());
    h.assertTrue(
        TrackPropagator.reconcile(h.getLevel(), h.absolutePos(T), rail(), false).complete(),
        "Actual restored rail creates its genuine current graph");
    station.targeting().resolveTick();
    var restored = station.getStation();
    h.assertTrue(
        restored != null
            && restored != decoded
            && restored.id.equals(id)
            && restored.ownedBy(station)
            && restored.graph().manager() == RailwayGraphManager.get(h.getLevel()),
        "Real current manager and same native owner rebind migrated point without a fake"
            + " attachment");
    var restoredPort = restored.ports().getFirst().data();
    var restoredBox = restoredPort.items().get(7);
    var restoredTool =
        restoredBox
            .get(DataComponents.CONTAINER)
            .nonEmptyItemCopyStream()
            .findFirst()
            .orElseThrow();
    h.assertTrue(
        restoredPort.primed()
            && restoredPort.address().equals("Registry Address")
            && restoredPort.items().size() == 18
            && restoredBox.getCount() == 1
            && ItemStack.isSameItemSameComponents(restoredBox, box)
            && restoredTool.getDamageValue() == 37
            && restoredTool.get(DataComponents.ENCHANTMENTS).getLevel(enchant) == 3,
        "Passed native RegistryOps preserve exact named/nested items and real dynamic enchantment"
            + " holder across point.write/read migration");
    h.succeed();
  }
  @GameTest(structure = STRUCTURE)
  public void retainedOriginalMailStacksKeepTheirTailsAcrossRealFabricCommitAndRollback(GameTestHelper h) {
    var original=new ItemStack(Items.DIAMOND,7);original.set(DataComponents.CUSTOM_NAME,Component.literal("Original seven retained mail diamonds"));
    var nested=new ItemStack(Items.SHULKER_BOX);nested.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(original.copy())));
    var items=new ArrayList<ItemStack>(Collections.nCopies(18,ItemStack.EMPTY));items.set(3,original);items.set(8,nested);
    var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var port=GlobalPackagePort.CODEC.parse(ops,GlobalPackagePort.CODEC.encodeStart(ops,new GlobalPackagePort("Original legacy mail",items,true)).getOrThrow()).getOrThrow();
    var resource=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(original);
    try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
      h.assertValueEqual(port.storage().extract(resource,3,tx),3L,"Real retained-buffer transaction extracts exactly three original units");
      h.assertTrue(ItemStack.matches(port.items().get(3),original.copyWithCount(4)),"The original named tail remains in its actual native slot during the transaction");
    }
    h.assertTrue(ItemStack.matches(port.items().get(3),original),"Actual Fabric rollback restores all seven original named units");
    try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
      h.assertValueEqual(port.storage().extract(resource,3,tx),3L,"Real commit transfers exactly three units");tx.commit();
    }
    var view=java.util.stream.StreamSupport.stream(port.storage().spliterator(),false).filter(slot->slot.getResource().equals(resource)).findFirst().orElseThrow();
    h.assertTrue(view.getAmount()==4&&ItemStack.matches(port.items().get(3),original.copyWithCount(4)),"Storage views and durable native buffer retain the exact four-unit tail");
    try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
      h.assertValueEqual(view.extract(resource,2,tx),2L,"Actual per-slot view extraction preserves its remaining source tail");
      h.assertValueEqual(port.storage().insert(resource,1,tx),0L,"New generic-item insertion remains refused by the actual parcel-only mailbox admission");
    }
    h.assertTrue(view.getAmount()==4&&ItemStack.matches(port.items().get(8),nested),"Rollback retains the full original tail and every nested container component");
    h.succeed();
  }

  @GameTest(structure = STRUCTURE)
  public void actualPostboxRestoresAndPersistsCompleteOldNativeMailWithoutCountClamping(GameTestHelper h) {
    var station=station(h);var pos=S.south().below();h.setBlock(pos,AllBlocks.POSTBOXES.get(DyeColor.WHITE));
    var box=h.getBlockEntity(pos,com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity.class);
    h.assertTrue(box.setTarget(new com.simibubi.create.content.logistics.packagePort.PackagePortTarget.TrainStationFrogportTarget(station.getBlockPos().subtract(box.getBlockPos())))&&box.isBoundTo(station.getStation()),"Real native Postbox binds the actual source station");
    var original=new ItemStack(Items.DIAMOND,7);original.set(DataComponents.CUSTOM_NAME,Component.literal("Original restored seven mail diamonds"));
    var nested=new ItemStack(Items.SHULKER_BOX);nested.set(DataComponents.CUSTOM_NAME,Component.literal("Original nested retained mail"));nested.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(original.copy())));
    var items=new ArrayList<ItemStack>(Collections.nCopies(18,ItemStack.EMPTY));items.set(3,original);items.set(8,nested);
    // Port records returned by ports() deliberately copy their data. Seed the actual graph-owned
    // buffer through its real complete RegistryOps codec, not by mutating a read-out snapshot.
    var registries=h.getLevel().registryAccess();var ops=registries.createSerializationContext(NbtOps.INSTANCE);
    var originalPoint=station.getStation();var graph=originalPoint.graph();
    var pointTag=(CompoundTag)GlobalStation.CODEC.encodeStart(ops,originalPoint).getOrThrow();
    pointTag.put("Ports",GlobalStation.Port.CODEC.listOf().encodeStart(ops,List.of(new GlobalStation.Port(box.getBlockPos(),new GlobalPackagePort(box.addressFilter,items,true)))).getOrThrow());
    var decodedPoint=StationEdgePoints.STATION.codec().parse(ops,pointTag).getOrThrow();
    h.assertTrue(decodedPoint.getType()==StationEdgePoints.STATION,"The genuine registered native point codec supplies its actual required railway type before graph adoption");
    graph.removePoint(StationEdgePoints.STATION,originalPoint.id);graph.addPoint(StationEdgePoints.STATION,decodedPoint);station.targeting().resolveTick();
    h.assertTrue(station.getStation()==decodedPoint&&decodedPoint.graph()==graph&&decodedPoint.ownedBy(station),"The actual manager graph owns the complete decoded native station and original retained buffer");
    box.refreshTarget();
    var port=decodedPoint.ports().stream().filter(entry->entry.pos().equals(box.getBlockPos())).findFirst().orElseThrow().data();
    h.assertTrue(!port.primed()&&ItemStack.matches(box.inventory.getItem(3),original)&&ItemStack.matches(box.inventory.getItem(8),nested),"Actual bound handoff restores all original counts and nested components without conversion into packages");
    h.assertFalse(box.inventory.canPlaceItem(0,original),"Native menu still refuses new ordinary-item mail insertion");
    var resource=net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(original);
    try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
      h.assertValueEqual(box.inventory.storage.extract(resource,3,tx),3L,"Real native Postbox Fabric extraction transfers exactly three old-buffer units");tx.commit();
    }
    var committedPort=decodedPoint.ports().stream().filter(entry->entry.pos().equals(box.getBlockPos())).findFirst().orElseThrow().data();
    h.assertTrue(ItemStack.matches(box.inventory.getItem(3),original.copyWithCount(4))&&ItemStack.matches(committedPort.items().get(3),original.copyWithCount(4)),"Committed native inventory and station retained owner agree on the exact four-unit original tail");
    var saved=box.saveWithFullMetadata(registries);
    var decoded=(com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity)BlockEntity.loadStatic(box.getBlockPos(),h.getBlockState(pos),saved,registries);
    h.assertTrue(decoded!=null&&ItemStack.matches(decoded.inventory.getItem(3),original.copyWithCount(4))&&ItemStack.matches(decoded.inventory.getItem(8),nested),"Actual registered native BE factory/save-load preserves full modern original cargo; no chunk-unload claim");
    var slot=(CompoundTag)ItemStack.CODEC.encodeStart(ops,original).getOrThrow();slot.putByte("Slot",(byte)3);
    var entries=new ListTag();entries.add(slot);var legacy=new CompoundTag();legacy.putInt("Size",18);legacy.put("Items",entries);var root=new CompoundTag();root.put("Inventory",legacy);
    decoded.inventory.read(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,registries,root));
    h.assertTrue(ItemStack.matches(decoded.inventory.getItem(3),original),"The genuine source ItemStackHandler compound/Slot format retains all seven original named units");
    entries.add(slot.copy());boolean refused=false;
    try{decoded.inventory.read(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,registries,root));}catch(IllegalArgumentException invalid){refused=true;}
    h.assertTrue(refused&&ItemStack.matches(decoded.inventory.getItem(3),original),"Malformed duplicate original legacy slots fail before replacing any already-owned native cargo");
    h.succeed();
  }
}
