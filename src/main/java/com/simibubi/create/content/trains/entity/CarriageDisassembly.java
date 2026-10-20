package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Actual station restoration, retaining every original row through a partial native forest commit.
 */
public final class CarriageDisassembly {
  private record Prepared(
      Carriage carriage,
      CarriageContraptionEntity owner,
      CarriageRestorationState state,
      Map<UUID, Integer> players) {}

  public static boolean begin(Train train, Direction direction, BlockPos station) {
    if (train == null
        || train.trafficManager() == null
        || direction == null
        || !direction.getAxis().isHorizontal()
        || station == null
        || !train.canDisassemble()) return false;
    var prepared = new ArrayList<Prepared>();
    ServerLevel world = null;
    int offset = 1;
    try {
      for (int i = 0; i < train.carriages.size(); i++) {
        boolean backwards = train.currentlyBackwards;
        var carriage = train.carriages.get(backwards ? train.carriages.size() - i - 1 : i);
        var owner = carriage.anyAvailableEntity();
        if (owner == null
            || !owner.hasOwnedStructure()
            || !(owner.level() instanceof ServerLevel level)
            || world != null && world != level) return false;
        world = level;
        int distance = backwards ? Math.addExact(offset, carriage.bogeySpacing) : offset;
        var target =
            station
                .relative(direction, distance)
                .below(carriage.leadingBogey().isUpsideDown() ? 2 : 0);
        var transform = owner.restorationAt(target);
        var state =
            new CarriageRestorationState(
                owner.getUUID(),
                world.dimension(),
                transform,
                owner.getContraption().getBlocks().keySet().stream()
                    .map(transform::apply)
                    .toList());
        var row = prepare(carriage, owner, state, world);
        if (row == null) return false;
        prepared.add(row);
        offset = Math.addExact(offset, carriage.bogeySpacing);
        if (i < train.carriageSpacing.size())
          offset =
              Math.addExact(
                  offset,
                  train.carriageSpacing.get(backwards ? train.carriageSpacing.size() - i - 1 : i));
      }
      if (world == null || world.getServer() != train.manager().server()) return false;
      preflight(world, prepared, Set.of());
      // Intent is durable before the first native placement. It does not assert world ownership.
      for (var row : prepared) row.carriage().restoration = row.state();
      train.manager().markDirty();
      return resume(train);
    } catch (AssemblyException | ArithmeticException unavailable) {
      return false;
    }
  }

  public static boolean resume(Train train) {
    if (train == null || train.trafficManager() == null || train.carriages.isEmpty()) return false;
    var first = train.carriages.getFirst().restoration;
    if (first == null) return false;
    var world = train.manager().server().getLevel(first.dimension);
    if (world == null) return false;
    var pending = new ArrayList<Prepared>();
    var occupied = new LinkedHashSet<BlockPos>();
    try {
      for (var carriage : train.carriages) {
        var state = carriage.restoration;
        if (state == null || !state.dimension.equals(first.dimension))
          throw new IllegalStateException("Incomplete actual Train restoration formation");
        if (state.restored) {
          for (var cell : state.cells)
            if (!occupied.add(cell))
              throw new IllegalStateException("Overlapping consumed train receipts");
          continue;
        }
        var owner = carriage.restorationFront(world);
        var row = prepare(carriage, owner, state, world);
        if (row == null) return false;
        pending.add(row);
      }
      if (pending.isEmpty()) {
        finishJournal(world, train.carriages);
        return true;
      }
      preflight(world, pending, occupied);
      var journal = first.journal;
      for (var carriage : train.carriages) {
        if (carriage.restoration == first) continue;
        journal.addAll(carriage.restoration.journal);
        carriage.restoration.journal.clear();
      }
      var roots =
          pending.stream()
              .map(
                  row ->
                      new ContraptionAssemblyGraph.RestorationRoot(
                          row.owner(), row.state().transform()))
              .toList();
      var rows = new IdentityHashMap<AbstractContraptionEntity, Prepared>();
      for (var row : pending) rows.put(row.owner(), row);
      try {
        ContraptionAssemblyGraph.restoreForest(
            roots, journal, owner -> complete(world, rows.get(owner)), train.manager()::markDirty);
      } finally {
        // A later refusal/exception cannot reintroduce a root whose real cargo is world-owned.
        for (var row : pending)
          if (!row.state().restored
              && row.owner().getContraption().phase() == Contraption.Phase.DISASSEMBLED)
            complete(world, row);
        train.manager().markDirty();
      }
      return train.carriages.stream().allMatch(Carriage::isWorldRestored);
    } catch (AssemblyException unavailable) {
      return false;
    }
  }

  private static Prepared prepare(
      Carriage carriage,
      CarriageContraptionEntity owner,
      CarriageRestorationState state,
      ServerLevel world) {
    if (owner == null
        || owner.getCarriage() != carriage
        || carriage.presentInMultipleDimensions()
        || owner.level() != world
        || owner.isRemoved()
        || world.getEntity(owner.getUUID()) != owner
        || !owner.getUUID().equals(state.body)
        || !owner.hasOwnedStructure()
        || owner.getContraption().getStorage() != carriage.storage
        || !Mth.equal(owner.pitch, 0)
        || !Mth.equal(((owner.yaw % 90) + 360) % 90, 0)
        || !owner.initializeCapturedPassengers()) return null;
    var dimensional = carriage.getDimensionalIfPresent(world.dimension());
    if (dimensional == null) return null;
    dimensional.updatePassengerLoadout();
    var players = carriage.pendingRestorationPlayers();
    // Serialized NPCs must first become real accepted passengers. Offline players retain only
    // UUIDs.
    if (players == null) return null;
    for (var seat : players.values())
      if (seat < 0 || seat >= owner.getContraption().getSeats().size())
        throw new IllegalStateException(
            "Actual pending player restoration seat is outside its body");
    return new Prepared(carriage, owner, state, players);
  }

  private static void preflight(ServerLevel world, List<Prepared> roots, Set<BlockPos> occupied)
      throws AssemblyException {
    var claimed = new HashSet<>(occupied);
    var identities = new HashSet<UUID>();
    for (var row : roots) {
      var actual =
          row.owner().getContraption().getBlocks().keySet().stream()
              .map(row.state().transform()::apply)
              .collect(java.util.stream.Collectors.toSet());
      if (!actual.equals(row.state().cells))
        throw new IllegalStateException("Actual owned body changed its durable restoration extent");
      validateNode(world, row.owner(), row.state().transform(), claimed, identities, 0);
    }
  }

  private static void validateNode(
      ServerLevel world,
      AbstractContraptionEntity owner,
      StructureTransform transform,
      Set<BlockPos> claimed,
      Set<UUID> identities,
      int depth)
      throws AssemblyException {
    if (depth > 64
        || !identities.add(owner.getUUID())
        || identities.size() > 1024
        || owner.level() != world
        || world.getEntity(owner.getUUID()) != owner
        || !owner.hasOwnedStructure())
      throw AssemblyException.entitiesNotReady(owner.blockPosition());
    var body = owner.getContraption();
    body.validateRestoration(world, transform);
    for (var local : body.getBlocks().keySet()) {
      var cell = transform.apply(local);
      if (!claimed.add(cell) || !world.getWorldBorder().isWithinBounds(cell))
        throw AssemblyException.structureChanged(cell);
      var present = LoadedLevelAccess.blockState(world, cell);
      if (!present.canBeReplaced()
          || present.hasBlockEntity()
          || LoadedLevelAccess.blockEntity(world, cell) != null)
        throw AssemblyException.structureChanged(cell);
    }
    for (var child : body.getStabilizedSubContraptions().children().entrySet()) {
      var nativeChild = body.getStabilizedSubContraptions().resolve(world, child.getKey());
      validateNode(
          world,
          nativeChild,
          nativeChild.restorationAt(transform.apply(child.getValue().connected())),
          claimed,
          identities,
          depth + 1);
    }
  }

  private static void complete(ServerLevel world, Prepared row) {
    if (row == null || row.state().restored) return;
    var body = row.owner().getContraption();
    if (body.phase() != Contraption.Phase.DISASSEMBLED || body.getStorage().ownsContents())
      throw new IllegalStateException(
          "Native restoration has not actually committed this carriage");
    var transform = row.state().transform();
    ContraptionPassengerHandoff.finishPlayers(world, body, transform, row.players());
    row.carriage().serialisedPassengers.clear();
    for (var entry : body.getBlocks().entrySet()) {
      if (!(entry.getValue().state().getBlock() instanceof AbstractBogeyBlock<?>)) continue;
      var bogey =
          entry.getKey().equals(BlockPos.ZERO)
              ? row.carriage().leadingBogey()
              : row.carriage().trailingBogey();
      if (LoadedLevelAccess.blockEntity(world, transform.apply(entry.getKey()))
          instanceof AbstractBogeyBlockEntity restored) restored.setBogeyData(bogey.bogeyData);
    }
    row.carriage().completeWorldRestoration(row.owner());
  }

  private static void finishJournal(ServerLevel world, List<Carriage> carriages) {
    var journal = new LinkedHashSet<BlockPos>();
    for (var carriage : carriages) journal.addAll(carriage.restoration.journal);
    if (!journal.isEmpty()) DeferredContraptionRestoration.finishOrQueue(world, journal);
    for (var carriage : carriages) carriage.restoration.journal.clear();
  }

  private CarriageDisassembly() {}
}
