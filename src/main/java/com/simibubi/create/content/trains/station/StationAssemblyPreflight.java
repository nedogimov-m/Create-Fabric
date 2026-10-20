package com.simibubi.create.content.trains.station;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Actual source layout and wheel-point preflight. It captures/removes no world blocks. */
public final class StationAssemblyPreflight {
  public enum Status {
    NOT_CHECKED,
    READY_FOR_CARRIAGE_CAPTURE,
    INVALID_OWNER,
    INVALID_TRACK,
    NO_BOGEYS,
    FRONTMOST_BOGEY,
    BOGEYS_TOO_CLOSE,
    TOO_MANY_BOGEYS,
    INCOMPATIBLE_BOGEY,
    GRAPH_NOT_READY,
    UNLOADED_FRONTIER,
    ENTITIES_NOT_READY,
    PERMISSION_DENIED
  }

  public record Plan(
      StationAssemblyArea area, long configurationRevision, List<TravellingPoint> wheelPoints) {
    public Plan {
      wheelPoints = List.copyOf(wheelPoints);
    }

    /**
     * Called again by the actual capture owner immediately before staging a real carriage.
     */
    public boolean stillCurrent(StationBlockEntity station, Player player) {
      if (station.configurationRevision() != configurationRevision) return false;
      var current = check(station, player);
      if (current.plan == null) return false;
      var other = current.plan.area;
      if (!area.owner().equals(other.owner())
          || !area.pointId().equals(other.pointId())
          || !area.graphId().equals(other.graphId())
          || area.graphRevision() != other.graphRevision()
          || area.length() != other.length()
          || area.direction() != other.direction()
          || area.cells().size() != other.cells().size()
          || area.bogeys().size() != other.bogeys().size()) return false;
      for (int i = 0; i < area.cells().size(); i++) {
        var a = area.cells().get(i);
        var b = other.cells().get(i);
        if (!a.pos().equals(b.pos())
            || a.state() != b.state()
            || a.entity() != b.entity()
            || !Objects.equals(a.data(), b.data())) return false;
      }
      for (int i = 0; i < area.bogeys().size(); i++) {
        var a = area.bogeys().get(i);
        var b = other.bogeys().get(i);
        if (a.entity() != b.entity()
            || a.block() != b.block()
            || a.offset() != b.offset()
            || !a.pos().equals(b.pos())
            || a.upsideDown() != b.upsideDown()
            || !a.data().equals(b.data())) return false;
      }
      return true;
    }
  }

  public record Report(Status status, Plan plan, String detail) {
    public boolean readyForCapture() {
      return status == Status.READY_FOR_CARRIAGE_CAPTURE && plan != null;
    }

    public static Report failed(Status status, String detail) {
      return new Report(status, null, detail);
    }
  }

  public static Report check(StationBlockEntity station, Player player) {
    if (!(station.getLevel() instanceof ServerLevel level)
        || !StationPermissions.canEdit(player, station, true))
      return Report.failed(Status.PERMISSION_DENIED, "Actual station edit permission required");
    if (!station.isAssembling())
      return Report.failed(Status.INVALID_OWNER, "Station is not in assembly mode");
    try {
      var area = StationAssemblyArea.discover(station);
      if (area.bogeys().isEmpty())
        return Report.failed(Status.NO_BOGEYS, "No actual bogeys on the assembly strip");
      if (area.bogeys().getFirst().offset() != 0)
        return Report.failed(Status.FRONTMOST_BOGEY, "Frontmost bogey must be at source offset0");
      if (area.bogeys().size() > com.simibubi.create.content.trains.TrainConfig.get(level).maxBogeyCount())
        return Report.failed(Status.TOO_MANY_BOGEYS, "Source max bogey count exceeded");
      int previous = -100;
      for (var bogey : area.bogeys()) {
        if (bogey.offset() - previous < 3)
          return Report.failed(
              Status.BOGEYS_TOO_CLOSE, "Source bogey spacing must be at least3blocks");
        previous = bogey.offset();
        if (bogey.offset() >= area.length())
          return Report.failed(Status.INVALID_TRACK, "Bogey occupies the reserved terminal rail");
        LoadedEntityAccess.requireReady(level, bogey.pos());
        if (!level.mayInteract(player, bogey.pos()))
          return Report.failed(Status.PERMISSION_DENIED, "Bogey world permission denied");
        var rail = LoadedLevelAccess.blockState(level, bogey.trackPos());
        if (!(rail.getBlock() instanceof ITrackBlock track)
            || bogey.block().isOnIncompatibleTrack(track.getMaterial(), bogey.entity().getStyle())
            || bogey
                    .entity()
                    .getBlockState()
                    .getValue(com.simibubi.create.content.trains.bogey.AbstractBogeyBlock.AXIS)
                != area.direction().getAxis())
          return Report.failed(
              Status.INCOMPATIBLE_BOGEY, "Actual bogey style/material/axis is incompatible");
      }
      var points = wheelPoints(level, station, area);
      if (points.size() != area.bogeys().size() * 2)
        return Report.failed(
            Status.GRAPH_NOT_READY,
            "Not all actual source wheel points resolve on the owned graph");
      return new Report(
          Status.READY_FOR_CARRIAGE_CAPTURE,
          new Plan(area, station.configurationRevision(), points),
          "Actual source layout and native graph wheel points are ready");
    } catch (UnloadedTrackException missing) {
      return Report.failed(Status.UNLOADED_FRONTIER, "Actual assembly frontier is not loaded");
    } catch (AssemblyException missingEntities) {
      return Report.failed(Status.ENTITIES_NOT_READY, "Actual native entities are not ready");
    } catch (IllegalArgumentException invalid) {
      return Report.failed(Status.INVALID_TRACK, invalid.getMessage());
    } catch (IllegalStateException invalid) {
      return Report.failed(Status.INVALID_OWNER, invalid.getMessage());
    }
  }

  private static List<TravellingPoint> wheelPoints(
      ServerLevel level, StationBlockEntity station, StationAssemblyArea area) {
    var state = LoadedLevelAccess.blockState(level, area.target());
    var track = (ITrackBlock) state.getBlock();
    var center =
        Vec3.atBottomCenterOf(area.target())
            .add(0, track.getElevationAtCenter(level, area.target(), state), 0);
    var direction = Vec3.atLowerCornerOf(area.direction().getStep());
    TrackNodeLocation location = null;
    for (var end : track.getConnected(level, area.target(), state, true, null))
      if (end.getLocation().subtract(center).normalize().distanceToSqr(direction) < 1e-8)
        location = end;
    if (location == null) return List.of();
    var offsets = new ArrayList<Double>();
    for (var bogey : area.bogeys()) {
      double spacing = bogey.block().getWheelPointSpacing();
      offsets.add(bogey.offset() + .5 - spacing / 2);
      offsets.add(bogey.offset() + .5 + spacing / 2);
    }
    var result = new ArrayList<TravellingPoint>();
    TrackGraph graph = null;
    TrackNode second = null;
    for (int step = 0; step < area.length() * 2 + 40 && result.size() < offsets.size(); step++) {
      double walked = step / 2d;
      var current = location;
      location =
          new TrackNodeLocation(current.getLocation().add(direction.scale(.5)))
              .in(current.dimension);
      if (graph == null) graph = RailwayGraphManager.get(level).getGraph(level, current);
      if (graph == null) continue;
      if (!graph.id.equals(area.graphId()) || graph.revision() != area.graphRevision())
        return List.of();
      var node = graph.locateNode(current);
      if (node == null) continue;
      for (int pointIndex = result.size(); pointIndex < offsets.size(); pointIndex++) {
        double offset = offsets.get(pointIndex);
        if (offset > walked) break;
        if (second == null) {
          for (var entry : graph.getConnectionsFrom(node).entrySet()) {
            if (entry.getValue().isTurn()
                || Math.abs(entry.getValue().getDirection(true).normalize().dot(direction) + 1)
                    > 1e-8) continue;
            if (second != null && second != entry.getKey()) return List.of();
            second = entry.getKey();
          }
        }
        if (second == null) return List.of();
        var edge = graph.getConnectionsFrom(node).get(second);
        double position = walked - offset;
        if (edge == null || edge.isTurn() || position < 0 || position > edge.getLength() + 1e-6)
          return List.of();
        result.add(
            new TravellingPoint(
                node, second, edge, position, area.bogeys().get(pointIndex / 2).upsideDown()));
      }
      second = node;
    }
    return List.copyOf(result);
  }

  private StationAssemblyPreflight() {}
}
