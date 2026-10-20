package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.signal.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Actual quiet removal transfers only a real railway point owner and its private migration. */
public final class CarriageRailwayCaptureGameTests {
  private static final String STRUCTURE = "create_test:reservoir";
  private static final BlockPos R = new BlockPos(3, 6, 4),
      P = new BlockPos(7, 7, 8),
      A = P.west(),
      B = A.west(3),
      C = B.south(3);

  private static void rails(GameTestHelper h) {
    var state = AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE, TrackShape.XO);
    for (int i = 0; i < 13; i++) {
      h.setBlock(R.east(i).below(), Blocks.STONE);
      h.setBlock(R.east(i), state);
    }
    for (int i = 0; i < 13; i++)
      h.assertTrue(
          TrackPropagator.reconcile(h.getLevel(), h.absolutePos(R.east(i)), state, false)
              .complete(),
          "Physical loaded source rails discover the genuine native graph");
  }

  private static TrackTargetingSelection selection(GameTestHelper h, boolean front) {
    return new TrackTargetingSelection(
        GlobalPos.of(h.getLevel().dimension(), h.absolutePos(R.east(4))),
        front,
        new Vec3(1, 0, 0),
        Optional.empty());
  }

  private static CarriageContraptionEntity capture(GameTestHelper h) throws AssemblyException {
    h.setBlock(
        P,
        AllBlocks.SMALL_BOGEY
            .defaultBlockState()
            .setValue(AbstractBogeyBlock.AXIS, Direction.Axis.X));
    h.setBlock(
        P.east(),
        AllBlocks.TRAIN_CONTROLS
            .defaultBlockState()
            .setValue(ControlsBlock.FACING, Direction.EAST));
    var body = new CarriageContraption(Direction.EAST);
    h.assertTrue(
        body.assemble(h.getLevel(), h.absolutePos(P))
            && body.getBlocks().containsKey(A.subtract(P)),
        "Real carriage captures the adjacent actual railway BE");
    var owner = CarriageContraptionEntity.reserve(h.getLevel(), body);
    h.assertTrue(owner != null, "Real empty native root is accepted before quiet railway removal");
    var result =
        ContraptionAssemblyGraph.assemble(
            h.getLevel(), List.of(new ContraptionAssemblyGraph.Root(owner, body)), complete -> {});
    h.assertTrue(
        result.complete() && owner.hasOwnedStructure(),
        "Genuine native forest commits the real captured railway body");
    h.assertBlockPresent(Blocks.AIR, A);
    return owner;
  }

  private static void privateOnly(GameTestHelper h, CarriageContraptionEntity owner, String field) {
    var body = owner.getContraption();
    var local = A.subtract(P);
    var privateData = body.getBlocks().get(local).nbt();
    h.assertTrue(
        privateData != null
            && privateData.getCompoundOrEmpty(field).getCompound("Migrate").isPresent(),
        "Latest full migration is owned only by the actual private captured BE payload");
    var spawn = body.writeNBT(h.getLevel().registryAccess(), true);
    boolean found = false;
    for (var value : spawn.getCompound("Blocks").orElseThrow().getListOrEmpty("BlockList")) {
      var entry = (CompoundTag) value;
      if (entry.getLongOr("Pos", Long.MIN_VALUE) != local.asLong()) continue;
      found = true;
      h.assertFalse(
          entry.getCompoundOrEmpty("Data").getCompoundOrEmpty(field).contains("Migrate"),
          "Actual native moving-body public snapshot omits private point cargo/migration");
    }
    h.assertTrue(found, "Public body snapshot retains the genuine visible railway block");
  }

  @GameTest(structure = STRUCTURE)
  public void quietActualStationCaptureRetiresOldPointAndRestoresExactRegistryBoundPortCargo(
      GameTestHelper h) throws Exception {
    rails(h);
    h.setBlock(A.below(), Blocks.STONE);
    h.setBlock(A, AllBlocks.TRACK_STATION);
    var station = h.getBlockEntity(A, StationBlockEntity.class);
    h.assertTrue(
        station.setTarget(selection(h, true)) && station.getStation() != null,
        "Actual world station binds its genuine manager point");
    var original = station.getStation();
    var graph = original.graph();
    var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var enchant =
        h.getLevel()
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.UNBREAKING);
    var tool = new ItemStack(Items.DIAMOND_PICKAXE);
    tool.enchant(enchant, 3);
    tool.setDamageValue(37);
    tool.set(DataComponents.CUSTOM_NAME, Component.literal("Actual quiet-captured station tool"));
    var parcel = new ItemStack(Items.SHULKER_BOX);
    parcel.set(
        DataComponents.CUSTOM_NAME, Component.literal("Original quiet-captured station parcel"));
    parcel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(tool)));
    var slots =
        new ArrayList<ItemStack>(Collections.nCopies(GlobalPackagePort.SLOTS, ItemStack.EMPTY));
    slots.set(7, parcel.copy());
    var port = new GlobalPackagePort("Quiet Capture Address", slots, true);
    var encoded = (CompoundTag) GlobalStation.CODEC.encodeStart(ops, original).getOrThrow();
    encoded.put(
        "Ports",
        GlobalStation.Port.CODEC
            .listOf()
            .encodeStart(ops, List.of(new GlobalStation.Port(station.getBlockPos().east(), port)))
            .getOrThrow());
    // Real full source SavedData decoding seeds an actual offline port, not an invented provider.
    var point = StationEdgePoints.STATION.codec().parse(ops, encoded).getOrThrow();
    var id = original.id;
    graph.removePoint(StationEdgePoints.STATION, id);
    graph.addPoint(StationEdgePoints.STATION, point);
    station.targeting().resolveTick();
    h.assertTrue(
        station.getStation() == point && point.ownedBy(station),
        "Real graph now owns the full decoded source station cargo");
    var owner = capture(h);
    h.assertTrue(
        graph.getPoint(StationEdgePoints.STATION, id) == null && station.getStation() == null,
        "Actual quiet removal retires exactly the old world-owned station point");
    privateOnly(h, owner, "StationData");
    h.assertTrue(
        owner.recoverIndependent(),
        "Actual native source restoration consumes the genuine captured station body");
    var restored = h.getBlockEntity(A, StationBlockEntity.class);
    restored.targeting().resolveTick();
    var fresh = restored.getStation();
    h.assertTrue(
        restored != station
            && fresh != null
            && fresh != point
            && !fresh.id.equals(id)
            && fresh.ownedBy(restored)
            && fresh.graph().getPoint(StationEdgePoints.STATION, fresh.id) == fresh,
        "Actual restored owner has fresh identity and real target geometry in the genuine manager"
            + " graph");
    var cargo = fresh.ports().getFirst().data();
    var box = cargo.items().get(7);
    var nested =
        box.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().findFirst().orElseThrow();
    h.assertTrue(
        cargo.primed()
            && cargo.address().equals("Quiet Capture Address")
            && cargo.items().size() == 18
            && ItemStack.matches(box, parcel)
            && nested.getDamageValue() == 37
            && nested.get(DataComponents.ENCHANTMENTS).getLevel(enchant) == 3,
        "Native capture/removal/restore preserves all18 source port slots, address/primed and exact"
            + " nested registry-bound cargo");
    h.assertFalse(
        restored
            .getUpdateTag(h.getLevel().registryAccess())
            .getCompoundOrEmpty("StationData")
            .contains("Migrate"),
        "Actual station public BE update does not expose private migration");
    graph.validate();
    h.succeed();
  }

  private static SignalBlockEntity signal(GameTestHelper h, BlockPos pos, boolean front) {
    h.setBlock(pos, AllBlocks.TRACK_SIGNAL);
    var signal = h.getBlockEntity(pos, SignalBlockEntity.class);
    h.assertTrue(
        signal.setTarget(selection(h, front)) && signal.getSignal() != null,
        "Actual native signal owner binds its real source boundary");
    return signal;
  }

  @GameTest(structure = STRUCTURE)
  public void quietSharedSignalCaptureRetiresOnlyItsActualOwnerAndRejoinsThroughRealCanMerge(
      GameTestHelper h) throws Exception {
    rails(h);
    var a = signal(h, A, true);
    var b = signal(h, B, true);
    var c = signal(h, C, false);
    var boundary = a.getSignal();
    var graph = boundary.graph();
    var id = boundary.id;
    h.assertTrue(
        b.getSignal() == boundary && c.getSignal() == boundary,
        "Three actual owners share one genuine graph boundary");
    var owner = capture(h);
    h.assertTrue(
        graph.getPoint(SignalEdgePoints.SIGNAL, id) == boundary
            && !boundary.hasOwner(a.owner())
            && boundary.hasOwner(b.owner())
            && boundary.hasOwner(c.owner())
            && boundary.owners(true).size() + boundary.owners(false).size() == 2,
        "Native quiet capture removes exactly the actual moving signal owner and preserves both"
            + " other real owners/old UUID");
    privateOnly(h, owner, "Targeting");
    h.assertTrue(
        owner.recoverIndependent(),
        "Actual native body restoration completes before signal rebind");
    var restored = h.getBlockEntity(A, SignalBlockEntity.class);
    var candidate = restored.targeting().id();
    h.assertTrue(
        restored != a && !candidate.equals(id),
        "Actual transformed restored BE begins with its genuine fresh point identity");
    restored.targeting().resolveTick();
    h.assertTrue(
        restored.getSignal() == boundary
            && restored.targeting().id().equals(id)
            && boundary.hasOwner(restored.owner())
            && boundary.hasOwner(b.owner())
            && boundary.hasOwner(c.owner())
            && boundary.owners(true).size() + boundary.owners(false).size() == 3,
        "Only the real source canMerge path rejoins the surviving boundary; other owners are never"
            + " cloned from migration");
    h.assertTrue(
        h.getBlockEntity(B, SignalBlockEntity.class) == b
            && h.getBlockEntity(C, SignalBlockEntity.class) == c,
        "Both untouched original native owner objects remain world-owned");
    h.assertFalse(
        restored
            .getUpdateTag(h.getLevel().registryAccess())
            .getCompoundOrEmpty("Targeting")
            .contains("Migrate"),
        "Actual public signal update omits private migration");
    graph.validate();
    h.succeed();
  }
}
