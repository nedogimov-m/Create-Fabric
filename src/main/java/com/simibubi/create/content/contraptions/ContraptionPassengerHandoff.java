package com.simibubi.create.content.contraptions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * World-owned seats may outlive a refused native vehicle spawn. Retain identities, never entities.
 */
public final class ContraptionPassengerHandoff extends SavedData {
  private record Entry(UUID passenger, BlockPos seat) {
    private Entry {
      seat = seat.immutable();
    }

    private static final Codec<Entry> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        UUIDUtil.CODEC.fieldOf("Passenger").forGetter(Entry::passenger),
                        BlockPos.CODEC.fieldOf("Seat").forGetter(Entry::seat))
                    .apply(instance, Entry::new));
  }

  public static final Codec<ContraptionPassengerHandoff> CODEC =
      Entry.CODEC
          .listOf()
          .fieldOf("Pending")
          .codec()
          .xmap(ContraptionPassengerHandoff::new, data -> List.copyOf(data.pending.values()));
  public static final SavedDataType<ContraptionPassengerHandoff> TYPE =
      new SavedDataType<>(
          Create.asResource("contraption_passenger_handoff"),
          ContraptionPassengerHandoff::new,
          CODEC,
          null);
  private final Map<UUID, Entry> pending = new LinkedHashMap<>();
  private boolean processing;
  private static boolean registered;

  public ContraptionPassengerHandoff() {}

  private ContraptionPassengerHandoff(List<Entry> entries) {
    for (var entry : entries) pending.put(entry.passenger(), entry);
  }

  private static ContraptionPassengerHandoff get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(TYPE);
  }

  public static void register() {
    if (registered) return;
    registered = true;
    ServerEntityEvents.ENTITY_UNLOAD.register(
        (entity, level) -> {
          var reason = entity.getRemovalReason();
          // Ordinary unload/logout retains the intent for the same original UUID on return.
          if (reason != null
              && (reason.shouldDestroy() || reason == Entity.RemovalReason.CHANGED_DIMENSION)) {
            var data = get(level);
            if (data.pending.remove(entity.getUUID()) != null) data.setDirty();
          }
        });
  }

  public static int pendingCount(ServerLevel level) {
    return get(level).pending.size();
  }

  public static boolean isPending(Level level, BlockPos pos) {
    return level instanceof ServerLevel server
        && get(server).pending.values().stream().anyMatch(entry -> entry.seat().equals(pos));
  }

  /**
   * Native eject must use the restored seat, which may be far from the old rounded-angle rider
   * pose.
   */
  public static @Nullable Vec3 pendingDismount(ServerLevel level, UUID passenger) {
    var entry = get(level).pending.get(passenger);
    return entry == null ? null : Vec3.atBottomCenterOf(entry.seat()).add(0, .5, 0);
  }

  /**
   * Call after successful world placement and before the original native owner ejects or retires.
   */
  public static void finish(
      ServerLevel level,
      Contraption structure,
      StructureTransform transform,
      List<Entity> passengers) {
    if (structure.phase() != Contraption.Phase.DISASSEMBLED)
      throw new IllegalStateException("Passenger handoff requires world-owned restored blocks");
    if(structure.entity instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity front)front.preparePassengerWorldHandoff(level,transform);
    var data = get(level);
    var arrivals = new ArrayList<Map.Entry<Entry, Entity>>();
    for (var passenger : List.copyOf(passengers)) {
      var local = structure.getSeatOf(passenger.getUUID());
      if (local == null
          || !passenger.isAlive()
          || passenger instanceof FakePlayer
          || passenger.level() != level) continue;
      var entry = new Entry(passenger.getUUID(), transform.apply(local));
      data.pending.put(entry.passenger(), entry);
      data.setDirty();
      arrivals.add(Map.entry(entry, passenger));
    }
    // Index every identity before native startRiding triggers old-vehicle callbacks.
    if (data.processing) return;
    data.processing = true;
    try {
      for (var arrival : arrivals)
        if (data.pending.get(arrival.getKey().passenger()) == arrival.getKey()
            && complete(level, arrival.getKey(), arrival.getValue(), true))
          data.remove(arrival.getKey());
    } finally {
      data.processing = false;
    }
  }

  /** Offline train players retain an original UUID/seat intent after actual world restoration. */
  public static void finishPlayers(
      ServerLevel level,
      Contraption structure,
      StructureTransform transform,
      Map<UUID, Integer> players) {
    if (structure.phase() != Contraption.Phase.DISASSEMBLED)
      throw new IllegalStateException("Player handoff requires actual world-owned train blocks");
    var arrivals = new ArrayList<Entry>();
    var seats = new HashSet<Integer>();
    for (var player : players.entrySet()) {
      int index = player.getValue();
      if (index < 0 || index >= structure.getSeats().size() || !seats.add(index))
        throw new IllegalArgumentException("Invalid original train player seat");
      arrivals.add(new Entry(player.getKey(), transform.apply(structure.getSeats().get(index))));
    }
    var data = get(level);
    for (var entry : arrivals) {
      // This actual later world restoration supersedes an older seat intent for the UUID.
      var previous = data.pending.put(entry.passenger(), entry);
      if (!entry.equals(previous)) data.setDirty();
    }
    if (data.processing) return;
    data.processing = true;
    try {
      for (var entry : arrivals) {
        var retained = data.pending.get(entry.passenger());
        if (retained != null && retained.equals(entry) && complete(level, retained, null, false))
          data.remove(retained);
      }
    } finally {
      data.processing = false;
    }
  }

  private void remove(Entry entry) {
    if (pending.get(entry.passenger()) == entry) {
      pending.remove(entry.passenger());
      setDirty();
    }
  }

  private static boolean complete(
      ServerLevel level, Entry entry, @Nullable Entity knownPassenger, boolean initialTransfer) {
    if (!LoadedEntityAccess.isReady(level, entry.seat())) return false;
    if (!(LoadedLevelAccess.blockState(level, entry.seat()).getBlock() instanceof SeatBlock))
      return true;
    var passenger = knownPassenger == null ? level.getEntity(entry.passenger()) : knownPassenger;
    if (passenger == null) {
      var player = level.getServer().getPlayerList().getPlayer(entry.passenger());
      return player != null && player.level() != level;
    }
    if (!passenger.isAlive() || passenger.level() != level || passenger instanceof FakePlayer)
      return true;
    var published = level.getEntity(entry.passenger());
    if (published != null && published != passenger) return true;
    var vehicle = passenger.getVehicle();
    if (vehicle instanceof SeatEntity && vehicle.blockPosition().equals(entry.seat())) return true;
    if (vehicle != null
        && !(initialTransfer
            && vehicle instanceof AbstractContraptionEntity owner
            && owner.getContraption() != null
            && owner.getContraption().phase() == Contraption.Phase.DISASSEMBLED)) return true;
    // A later voluntary move/new mount wins over an old restoration intent.
    if (!initialTransfer
        && passenger.position().distanceToSqr(Vec3.atBottomCenterOf(entry.seat())) > 16)
      return true;
    var seats =
        level.getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(entry.seat()));
    for (var seat : seats) if (seat.isVehicle() && !seat.hasPassenger(passenger)) return true;
    if(!(passenger instanceof net.minecraft.world.entity.player.Player)){
      try(var scope=com.simibubi.create.content.trains.entity.CarriagePlayerReferenceTransitions.preserveRootWorld(passenger,entry.seat())){return SeatBlock.trySitDown(level,entry.seat(),passenger);}
    }
    return SeatBlock.trySitDown(level, entry.seat(), passenger);
  }

  public static void tick(ServerLevel level) {
    var data = get(level);
    if (data.processing || data.pending.isEmpty()) return;
    data.processing = true;
    try {
      for (var entry : List.copyOf(data.pending.values()))
        if (data.pending.get(entry.passenger()) == entry && complete(level, entry, null, false))
          data.remove(entry);
    } finally {
      data.processing = false;
    }
  }
}
