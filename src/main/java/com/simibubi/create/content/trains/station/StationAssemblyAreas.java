package com.simibubi.create.content.trains.station;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;

/** Ephemeral index of actual loaded assembly-mode station BEs; every mutation rescans the owner. */
public final class StationAssemblyAreas {
  private static final Map<Level, Map<BlockPos, java.lang.ref.WeakReference<StationBlockEntity>>>
      LEVELS = new WeakHashMap<>();

  public static synchronized void update(StationBlockEntity station) {
    var level = station.getLevel();
    if (level == null) return;
    var map = LEVELS.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
    if (station.isAssembling() && station.publishedAreaLength() > 0)
      map.put(station.getBlockPos(), new java.lang.ref.WeakReference<>(station));
    else map.remove(station.getBlockPos());
  }

  public static synchronized void remove(StationBlockEntity station) {
    var map = LEVELS.get(station.getLevel());
    if (map == null) return;
    var ref = map.get(station.getBlockPos());
    if (ref != null && ref.get() == station) map.remove(station.getBlockPos());
  }

  public static synchronized void clear(Level level) {
    LEVELS.remove(level);
  }

  public static synchronized void clearClient() {
    LEVELS.keySet().removeIf(Level::isClientSide);
  }

  public static synchronized List<StationBlockEntity> ownersAt(Level level, BlockPos track) {
    var map = LEVELS.get(level);
    if (map == null) return List.of();
    var result = new ArrayList<StationBlockEntity>();
    for (var entry : List.copyOf(map.entrySet())) {
      var station = entry.getValue().get();
      if (station == null) {
        map.remove(entry.getKey(), entry.getValue());
        continue;
      }
      if (station.isRemoved()
          || !LoadedLevelAccess.isLoaded(level, station.getBlockPos())
          || LoadedLevelAccess.blockEntity(level, station.getBlockPos()) != station) {
        map.remove(entry.getKey(), entry.getValue());
        continue;
      }
      if (station.publishedContains(track)) result.add(station);
    }
    return List.copyOf(result);
  }

  public static synchronized boolean conflicts(
      StationBlockEntity station, StationAssemblyArea area) {
    for (var cell : area.cells()) {
      if (!area.contains(cell.pos())) continue;
      for (var owner : ownersAt(station.getLevel(), cell.pos())) if (owner != station) return true;
    }
    return false;
  }

  private static synchronized void clearServer(net.minecraft.server.MinecraftServer server) {
    LEVELS.keySet().removeIf(level -> level.getServer() == server);
  }

  public static synchronized void register() {
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.register(
        (server, level) -> clear(level));
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> clearServer(server));
    com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register(
        (player, level, hand, hit) -> {
          if (player.isSpectator()
              || !player.mayBuild()
              || !player.isWithinBlockInteractionRange(hit.getBlockPos(), 1)
              || !LoadedLevelAccess.isLoaded(level, hit.getBlockPos()))
            return InteractionResult.PASS;
          var state = LoadedLevelAccess.blockState(level, hit.getBlockPos());
          if (!(state.getBlock()
              instanceof com.simibubi.create.content.trains.track.ITrackBlock track))
            return InteractionResult.PASS;
          var owners = ownersAt(level, hit.getBlockPos());
          if (owners.isEmpty()) return InteractionResult.PASS;
          if (owners.size() != 1) return InteractionResult.FAIL;
          if (level.isClientSide()) return InteractionResult.SUCCESS;
          return owners.getFirst().trackClicked(player, hand, track, state, hit.getBlockPos())
              ? InteractionResult.SUCCESS
              : InteractionResult.PASS;
        });
  }

  private StationAssemblyAreas() {}
}
