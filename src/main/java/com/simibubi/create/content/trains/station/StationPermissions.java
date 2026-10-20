package com.simibubi.create.content.trains.station;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.world.entity.player.Player;

/**
 * Permissions on actual native station and target positions; no ownership is inferred from packets.
 */
public final class StationPermissions {
  public static boolean canEdit(Player player, StationBlockEntity station, boolean requireRange) {
    var level = station.getLevel();
    if (level == null
        || player.level() != level
        || !player.isAlive()
        || player.isSpectator()
        || !player.mayBuild()
        || !LoadedLevelAccess.isLoaded(level, station.getBlockPos())
        || LoadedLevelAccess.blockEntity(level, station.getBlockPos()) != station
        || !level.mayInteract(player, station.getBlockPos())
        || requireRange && !player.isWithinBlockInteractionRange(station.getBlockPos(), 1))
      return false;
    var selected = station.targeting().selection();
    if (selected == null
        || !selected.target().dimension().equals(level.dimension())
        || !LoadedLevelAccess.isLoaded(level, selected.target().pos())
        || !level.mayInteract(player, selected.target().pos())) return false;
    try {
      var point = station.getStation();
      var location = selected.resolve(level);
      if (point == null
          || !point.ownedBy(station)
          || location == null
          || point.graph() != location.graph) return false;
      var edge = location.graph.getConnection(location.edge.map(location.graph::locateNode));
      return edge != null
          && edge.getEdgeData().getPoints().contains(point)
          && Math.abs(point.getLocationOn(edge) - location.position) < 1e-6;
    } catch (com.simibubi.create.content.trains.track.UnloadedTrackException missing) {
      return false;
    }
  }

  private StationPermissions() {}
}
