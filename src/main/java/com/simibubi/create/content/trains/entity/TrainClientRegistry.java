package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.trains.TrainConfig;
import java.util.*;
import java.util.function.Function;
import net.minecraft.world.level.Level;

/** Actual received public train descriptions; no private cargo, bodies or passenger records. */
public final class TrainClientRegistry {
  public interface ClientEffects {
    void tick(Carriage.DimensionalCarriageEntity dimensional);

    void portalCutoffChanged();

    void stop();
  }

  private static Object connection;
  private static UUID epoch;
  private static long sequence = -1;
  private static Map<UUID, Train> trains = Map.of();
  private static Function<CarriageContraptionEntity, ClientEffects> effects;

  public static void begin(Object actualConnection) {
    Objects.requireNonNull(actualConnection);
    if (actualConnection == connection) return;
    connection = actualConnection;
    epoch = null;
    sequence = -1;
    trains = Map.of();
    TrainConfig.clearClient();
  }

  public static void end(Object actualConnection) {
    if (connection != actualConnection) return;
    connection = null;
    epoch = null;
    sequence = -1;
    trains = Map.of();
    TrainConfig.clearClient();
  }

  public static boolean accept(Object actualConnection, TrainSyncPacket packet) {
    if (connection == null || connection != actualConnection) return false;
    boolean sameEpoch = packet.epoch().equals(epoch);
    if (!packet.reset() && !sameEpoch || sameEpoch && packet.sequence() <= sequence) return false;
    var next = new LinkedHashMap<UUID, Train>(packet.reset() ? Map.of() : trains);
    for (var train : packet.additions()) {
      for (var carriage : train.carriages) {
        if (carriage.train != train
            || carriage.storage == null
            || carriage.storage.ownsContents()
            || !carriage.serialisedEntity.isEmpty()
            || !carriage.serialisedPassengers.isEmpty())
          throw new IllegalArgumentException(
              "Public train description contains private or authoritative custody");
        carriage.storage.initialize();
        if (!carriage.storage.getAllItemStorages().isEmpty()
            || !carriage.storage.getFluids().storages.isEmpty())
          throw new IllegalArgumentException("Public train description contains cargo");
      }
      next.put(train.id, train);
    }
    packet.removals().forEach(next::remove);
    var published = Collections.unmodifiableMap(next);
    // All received descriptions validate before either the map or its matching configuration
    // changes.
    TrainConfig.installClient(packet.configuration());
    trains = published;
    epoch = packet.epoch();
    sequence = packet.sequence();
    return true;
  }

  public static Train get(Level level, UUID id) {
    return level != null && level.isClientSide() && connection != null ? trains.get(id) : null;
  }

  public static Map<UUID, Train> trains() {
    return trains;
  }

  public static UUID epoch() {
    return epoch;
  }

  public static long sequence() {
    return sequence;
  }

  public static void installEffects(
      Function<CarriageContraptionEntity, ClientEffects> actualFactory) {
    effects = Objects.requireNonNull(actualFactory);
  }

  static ClientEffects createEffects(CarriageContraptionEntity entity) {
    if (!entity.level().isClientSide() || effects == null)
      throw new IllegalStateException("Actual native carriage client effects are not registered");
    return Objects.requireNonNull(effects.apply(entity));
  }

  private TrainClientRegistry() {}
}
