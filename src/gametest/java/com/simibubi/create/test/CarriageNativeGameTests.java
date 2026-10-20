package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.minecart.TrainCargoManager;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.*;

/** Actual native accepted/refused carriage roots, original riders and committed cargo ownership. */
public final class CarriageNativeGameTests {
  private static final BlockPos P = new BlockPos(4, 3, 4), BARREL = P.west(), SEAT = BARREL.above();

  private record Fixture(
      CarriageContraption body,
      CarriageContraptionEntity owner,
      BarrelBlockEntity originalBarrel,
      Entity rider,
      ItemStack parcel) {
    TrainCargoManager cargo() {
      return (TrainCargoManager) body.getStorage();
    }
  }

  private static ItemStack diamonds(int amount) {
    var item = new ItemStack(Items.DIAMOND, amount);
    item.set(DataComponents.CUSTOM_NAME, Component.literal("Original carriage diamonds"));
    return item;
  }

  private static ItemStack parcel(GameTestHelper h) {
    var tool = new ItemStack(Items.DIAMOND_PICKAXE);
    tool.enchant(
        h.getLevel()
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.UNBREAKING),
        3);
    tool.setDamageValue(37);
    tool.set(
        DataComponents.CUSTOM_NAME, Component.literal("Original registry-bound carriage tool"));
    var parcel = new ItemStack(Items.SHULKER_BOX);
    parcel.set(DataComponents.CUSTOM_NAME, Component.literal("Original carriage parcel"));
    parcel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(tool)));
    return parcel;
  }

  private static Fixture capture(GameTestHelper h, boolean transfer) throws AssemblyException {
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
    h.setBlock(BARREL, Blocks.BARREL);
    h.setBlock(SEAT, AllBlocks.SEATS.get(DyeColor.RED));
    var barrel = h.getBlockEntity(BARREL, BarrelBlockEntity.class);
    var parcel = parcel(h);
    barrel.setItem(0, diamonds(7));
    barrel.setItem(1, parcel.copy());
    h.assertTrue(
        h.getLevel()
            .addFreshEntity(
                new SuperGlueEntity(
                    h.getLevel(), SuperGlueEntity.span(h.absolutePos(P), h.absolutePos(SEAT)))),
        "Actual narrow native glue joins the source bogey/barrel/seat");
    var rider = h.spawnWithNoFreeWill(EntityTypes.PIG, SEAT.above());
    rider.setNoGravity(true);
    h.assertTrue(
        SeatBlock.trySitDown(h.getLevel(), h.absolutePos(SEAT), rider),
        "Original native pig boards the actual source seat");
    var body = new CarriageContraption(Direction.EAST);
    h.assertTrue(
        body.assemble(h.getLevel(), h.absolutePos(P)), "Genuine source Carriage capture succeeds");
    h.assertTrue(
        body.getBlocks().containsKey(BARREL.subtract(P))
            && body.getSeats().contains(SEAT.subtract(P)),
        "Real captured geometry includes the cargo and occupied source seat");
    h.assertTrue(
        body.phase() == Contraption.Phase.CAPTURED && !body.getStorage().ownsContents(),
        "Pure capture does not own any source-world cargo");
    var owner = CarriageContraptionEntity.reserve(h.getLevel(), body);
    if (!transfer) return new Fixture(body, owner, barrel, rider, parcel);
    h.assertTrue(
        owner != null
            && owner.getContraption() == null
            && h.getLevel().getEntity(owner.getUUID()) == owner,
        "Native accepted empty reservation precedes the first source removal");
    var result =
        ContraptionAssemblyGraph.assemble(
            h.getLevel(), List.of(new ContraptionAssemblyGraph.Root(owner, body)), complete -> {});
    h.assertTrue(
        result.complete()
            && owner.getContraption() == body
            && body.entity == owner
            && owner.hasOwnedStructure(),
        "The real native forest binds the exact removed body to its accepted owner");
    h.assertBlockPresent(Blocks.AIR, BARREL);
    h.assertBlockPresent(Blocks.AIR, SEAT);
    h.assertTrue(
        owner.initializeCapturedPassengers(),
        "Actual accepted owner adopts captured original riders before any serialization");
    return new Fixture(body, owner, barrel, rider, parcel);
  }

  private static void originalRider(GameTestHelper h, Fixture f) {
    h.assertTrue(
        h.getLevel().getEntity(f.rider().getUUID()) == f.rider()
            && f.rider().getVehicle() == f.owner(),
        "The accepted native carriage owns the original rider object and UUID");
    h.assertTrue(
        f.body().getInitialPassengers().isEmpty(),
        "Completed real adoption releases only the transient source handoff");
  }

  private static void worldCargo(GameTestHelper h, ItemStack parcel, int amount) {
    var barrel = h.getBlockEntity(BARREL, BarrelBlockEntity.class);
    h.assertTrue(
        ItemStack.matches(barrel.getItem(0), diamonds(amount)),
        "Restored world cargo retains exact diamond count/name/components");
    h.assertTrue(
        ItemStack.matches(barrel.getItem(1), parcel),
        "Named shulker and nested damaged/enchanted registry-bound tool survive exactly");
  }

  private static void restore(GameTestHelper h, Fixture f, int diamonds) {
    h.assertTrue(f.owner().recoverIndependent(), "Actual independent native restoration completes");
    h.assertTrue(
        f.owner().isRemoved()
            && f.body().phase() == Contraption.Phase.DISASSEMBLED
            && !f.cargo().ownsContents(),
        "World owns restored cargo and the former native owner is consumed");
    h.assertTrue(
        h.getLevel().getEntity(f.rider().getUUID()) == f.rider()
            && f.rider().getVehicle() instanceof SeatEntity,
        "The original passenger reaches an actual restored stationary native seat");
    worldCargo(h, f.parcel(), diamonds);
  }

  @GameTest
  public void acceptedEmptyReservationThenNativeForestPreservesOriginalCargoAndRider(
      GameTestHelper h) throws Exception {
    var f = capture(h, false);
    h.assertTrue(
        f.owner() != null && f.owner().getContraption() == null,
        "The reservation starts genuinely empty");
    h.assertTrue(
        h.getLevel().getBlockEntity(h.absolutePos(BARREL)) == f.originalBarrel(),
        "Accepted empty entity has not touched the actual source BE");
    worldCargo(h, f.parcel(), 7);
    var result =
        ContraptionAssemblyGraph.assemble(
            h.getLevel(),
            List.of(new ContraptionAssemblyGraph.Root(f.owner(), f.body())),
            complete -> {});
    h.assertTrue(
        result.complete() && f.owner().initializeCapturedPassengers(),
        "Actual native commit and original passenger adoption succeed");
    originalRider(h, f);
    restore(h, f, 7);
    h.succeed();
  }

  @GameTest
  public void actualEntityLoadRefusalLeavesTheOriginalSourceWorldCargoAndSeatOwned(GameTestHelper h)
      throws Exception {
    var active = new AtomicBoolean(true);
    var refusals = new AtomicInteger();
    ServerEntityEvents.ENTITY_LOAD.register(
        (entity, level) -> {
          if (active.get()
              && level == h.getLevel()
              && entity instanceof CarriageContraptionEntity
              && entity.blockPosition().equals(h.absolutePos(P))) {
            refusals.incrementAndGet();
            entity.discard();
          }
        });
    Fixture f;
    try {
      f = capture(h, false);
    } finally {
      active.set(false);
    }
    h.assertTrue(
        refusals.get() == 1 && f.owner() == null,
        "Actual native load callback refuses the empty requested reservation");
    h.assertTrue(
        f.body().phase() == Contraption.Phase.CAPTURED && !f.cargo().ownsContents(),
        "Refusal never transfers ownership or fabricates a Train");
    h.assertTrue(
        h.getLevel().getBlockEntity(h.absolutePos(BARREL)) == f.originalBarrel()
            && f.rider().getVehicle() instanceof SeatEntity,
        "Original world BE and original stationary rider remain accepted");
    worldCargo(h, f.parcel(), 7);
    h.succeed();
  }

  @GameTest
  public void abortedAggregateAndNestedCargoTransactionsKeepVersionIdleAndExactItems(
      GameTestHelper h) throws Exception {
    var f = capture(h, true);
    originalRider(h, f);
    var cargo = f.cargo();
    for (int tick = 0; tick < 9; tick++) cargo.tickIdleCargoTracker();
    int version = cargo.getVersion();
    var items = cargo.getAllItems();
    var variant = ItemVariant.of(diamonds(1));
    try (var transaction = Transaction.openOuter()) {
      h.assertValueEqual(
          items.extract(variant, 2, transaction),
          2L,
          "Actual aggregate extraction stages two original items");
    }
    try (var outer = Transaction.openOuter()) {
      try (var inner = outer.openNested()) {
        h.assertValueEqual(
            items.extract(variant, 3, inner), 3L, "Nested actual extraction stages original cargo");
        inner.commit();
      }
    }
    h.assertValueEqual(
        cargo.getVersion(),
        version,
        "Outer abort and nested commit/outer rollback never publish an exchange");
    h.assertValueEqual(
        cargo.getTicksSinceLastExchange(), 9, "Aborted transactions retain actual idle time");
    restore(h, f, 7);
    h.succeed();
  }

  @GameTest
  public void aggregateAndRawMountedExtractionPublishExactlyOneFinalOuterExchange(GameTestHelper h)
      throws Exception {
    var f = capture(h, true);
    var cargo = f.cargo();
    cargo.tickIdleCargoTracker();
    int version = cargo.getVersion();
    var variant = ItemVariant.of(diamonds(1));
    var raw = cargo.getAllItemStorages().get(BARREL.subtract(P));
    try (var transaction = Transaction.openOuter()) {
      h.assertValueEqual(
          cargo.getAllItems().extract(variant, 2, transaction),
          2L,
          "Aggregate consumer takes two real original diamonds");
      h.assertValueEqual(
          raw.extract(variant, 1, transaction),
          1L,
          "Actual per-position mounted storage participates in the same transaction");
      transaction.commit();
    }
    h.assertValueEqual(
        cargo.getVersion(),
        version + 1,
        "Aggregate and direct storage observers share one final outer exchange");
    h.assertValueEqual(
        cargo.getTicksSinceLastExchange(), 0, "Committed actual exchange resets source idle time");
    restore(h, f, 4);
    h.succeed();
  }

  @GameTest
  public void actualMountedSlotSetterCountsOnlyChangedContentAndExpiresAfterRestoration(
      GameTestHelper h) throws Exception {
    var f = capture(h, true);
    var cargo = f.cargo();
    var mounted = cargo.getAllItemStorages().get(BARREL.subtract(P));
    int version = cargo.getVersion();
    cargo.tickIdleCargoTracker();
    mounted.setStackInSlot(0, diamonds(7));
    h.assertValueEqual(
        cargo.getVersion(), version, "Identical actual slot content does not invent an exchange");
    mounted.setStackInSlot(0, diamonds(5));
    h.assertValueEqual(
        cargo.getVersion(),
        version + 1,
        "Real direct content replacement observes one source exchange");
    h.assertValueEqual(
        cargo.getTicksSinceLastExchange(), 0, "Direct committed setter resets actual idle time");
    restore(h, f, 5);
    try (var transaction = Transaction.openOuter()) {
      h.assertValueEqual(
          mounted.extract(ItemVariant.of(diamonds(1)), 1, transaction),
          0L,
          "Held old mounted handle cannot take world-owned restored cargo");
      transaction.commit();
    }
    worldCargo(h, f.parcel(), 5);
    h.succeed();
  }

  private static CompoundTag save(CarriageContraptionEntity owner) {
    var problems = new ProblemReporter.Collector(owner.problemPath());
    var output = TagValueOutput.createWithContext(problems, owner.registryAccess());
    owner.saveWithoutId(output);
    if (!problems.isEmpty()) throw new IllegalStateException(problems.getReport());
    var tag = output.buildResult();
    tag.putString("id", EntityType.getKey(owner.getType()).toString());
    return tag;
  }

  @GameTest
  public void independentNativeSaveFactoryCycleRetainsBodyRiderAndFullRegistryCargoOnce(
      GameTestHelper h) throws Exception {
    var f = capture(h, true);
    var bodyId = f.owner().getUUID();
    var riderId = f.rider().getUUID();
    var oldItems = f.cargo().getAllItems();
    var snapshot = save(f.owner());
    h.assertTrue(
        snapshot.getBooleanOr("IndependentCarriage", false) && !snapshot.contains("TrainId"),
        "A real independent owner persists without a fictitious global Train");
    var body = snapshot.getCompound("Contraption").orElseThrow();
    h.assertTrue(
        body.getIntOr("NativeStandaloneCargo", -1) == 1 && !body.getListOrEmpty("items").isEmpty(),
        "The one actual independent native chunk record owns complete cargo");
    f.rider().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
    f.owner().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
    h.assertTrue(
        h.getLevel().getEntity(bodyId) == null && h.getLevel().getEntity(riderId) == null,
        "Original native UUIDs actually retire before any factory reload");
    try (var transaction = Transaction.openOuter()) {
      h.assertValueEqual(
          oldItems.extract(ItemVariant.of(diamonds(1)), 1, transaction),
          0L,
          "Retired independent owner revokes held actual cargo handles");
      transaction.commit();
    }
    var problems = new ProblemReporter.Collector();
    var loaded =
        EntityType.loadEntityRecursive(
            TagValueInput.create(problems, h.getLevel().registryAccess(), snapshot),
            h.getLevel(),
            EntitySpawnReason.LOAD,
            entity -> entity);
    h.assertTrue(
        problems.isEmpty() && loaded instanceof CarriageContraptionEntity,
        "Native entity factory decodes actual independent body/passengers/cargo");
    var owner = (CarriageContraptionEntity) loaded;
    h.assertTrue(
        h.getLevel().tryAddFreshEntityWithPassengers(owner),
        "Native level accepts the actual restored entity forest");
    h.assertTrue(
        owner != f.owner() && owner.getUUID().equals(bodyId) && owner.getCarriage() == null,
        "Native new object retains original body identity without creating a Train");
    var rider = h.getLevel().getEntity(riderId);
    h.assertTrue(
        rider != null && rider != f.rider() && rider.getVehicle() == owner,
        "Exactly the reloaded original passenger UUID rides the actual accepted owner");
    var restored =
        new Fixture(
            (CarriageContraption) owner.getContraption(),
            owner,
            f.originalBarrel(),
            rider,
            f.parcel());
    restore(h, restored, 7);
    h.succeed();
  }
}
