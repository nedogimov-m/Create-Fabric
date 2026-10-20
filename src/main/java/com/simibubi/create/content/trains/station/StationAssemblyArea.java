package com.simibubi.create.content.trains.station;

import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.bogey.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Source straight assembly strip, discovered completely before it can authorize any mutation. */
public record StationAssemblyArea(
    GlobalPos owner,
    UUID pointId,
    UUID graphId,
    long graphRevision,
    BlockPos target,
    Direction direction,
    int length,
    List<Cell> cells,
    List<Bogey> bogeys) {
  public static final int MAX_LENGTH = 512, MAX_BOGEYS = 200;

  public record Cell(BlockPos pos, BlockState state, BlockEntity entity, CompoundTag data) {
    public Cell {
      pos = pos.immutable();
      data = data == null ? null : data.copy();
    }

    @Override
    public CompoundTag data() {
      return data == null ? null : data.copy();
    }
  }

  public record Bogey(
      int offset,
      BlockPos trackPos,
      BlockPos pos,
      AbstractBogeyBlock<?> block,
      AbstractBogeyBlockEntity entity,
      boolean upsideDown,
      CompoundTag data) {
    public Bogey {
      trackPos = trackPos.immutable();
      pos = pos.immutable();
      data = data.copy();
    }

    @Override
    public CompoundTag data() {
      return data.copy();
    }
  }

  public StationAssemblyArea {
    target = target.immutable();
    cells = List.copyOf(cells);
    bogeys = List.copyOf(bogeys);
  }

  public int offset(BlockPos pos) {
    var delta = pos.subtract(target);
    int along =
        delta.getX() * direction.getStepX()
            + delta.getY() * direction.getStepY()
            + delta.getZ() * direction.getStepZ();
    return target.relative(direction, along).equals(pos) ? along - 1 : Integer.MIN_VALUE;
  }

  public boolean contains(BlockPos pos) {
    int offset = offset(pos);
    return offset >= 0 && offset < length;
  }

  public boolean isValidBogeyOffset(int offset) {
    if (offset < 0 || offset >= length || ((offset < 3 || bogeys.isEmpty()) && offset != 0))
      return false;
    return bogeys.stream().noneMatch(b -> Math.abs(offset - b.offset) <= 2);
  }

  public static StationAssemblyArea discover(StationBlockEntity station) {
    if (!(station.getLevel() instanceof ServerLevel level))
      throw new IllegalArgumentException("Actual native server station required");
    var point = station.getStation();
    if (point == null
        || !point.ownedBy(station)
        || point.graph() == null
        || RailwayGraphManager.get(level).getTrackNetworks().get(point.graph().id) != point.graph())
      throw new IllegalStateException("Station has no actual owned manager graph point");
    var selected = station.targeting().selection();
    var graphLocation = station.targeting().determineGraphLocation();
    if (selected == null
        || selected.curve().isPresent()
        || graphLocation == null
        || graphLocation.graph != point.graph()
        || !station.targeting().isOrthogonal())
      throw new IllegalArgumentException(
          "Assembly requires the actual station's straight cardinal track");
    var target = selected.target().pos();
    var initial = LoadedLevelAccess.blockState(level, target);
    if (!(initial.getBlock() instanceof ITrackBlock track))
      throw new IllegalArgumentException("Station target track disappeared");
    var axes = track.getTrackAxes(level, target, initial);
    var axis = axes.getFirst().normalize().scale(selected.direction().getStep());
    if (axis.y != 0
        || Math.abs(axis.x) + Math.abs(axis.z) < 1 - 1e-6
        || Math.abs(axis.x) > 1e-6 && Math.abs(axis.z) > 1e-6)
      throw new IllegalArgumentException("Assembly requires a horizontal cardinal source axis");
    var direction = Direction.getApproximateNearest(axis.x, axis.y, axis.z);
    var up = BlockPos.containing(track.getUpNormal(level, target, initial));
    if (up.getX() != 0 || up.getZ() != 0 || Math.abs(up.getY()) != 1)
      throw new IllegalArgumentException("Actual unit bogey support normal required");
    var cells = new ArrayList<Cell>();
    var bogeys = new ArrayList<Bogey>();
    int length = 0;
    int configuredLength=com.simibubi.create.content.trains.TrainConfig.get(level).maxAssemblyLength();
    for (int index = 0; index < configuredLength; index++) {
      if (index == configuredLength - 1) {
        length = index;
        break;
      }
      var rail = target.relative(direction, index + 1);
      if (!LoadedLevelAccess.isLoaded(level, rail)) throw new UnloadedTrackException(level, rail);
      var state = LoadedLevelAccess.blockState(level, rail);
      if (!track.trackEquals(initial, state)) {
        length = Math.max(0, index - 1);
        break;
      }
      var entity = LoadedLevelAccess.blockEntity(level, rail);
      cells.add(
          new Cell(
              rail,
              state,
              entity,
              entity == null ? null : entity.saveWithoutMetadata(level.registryAccess())));
      for (boolean upsideDown : new boolean[] {false, true}) {
        var pos = rail.offset(upsideDown ? up.multiply(-1) : up);
        if (!LoadedLevelAccess.isLoaded(level, pos)) throw new UnloadedTrackException(level, pos);
        var candidate = LoadedLevelAccess.blockState(level, pos);
        if (!(candidate.getBlock() instanceof AbstractBogeyBlock<?> block)
            || block.isUpsideDown(candidate) != upsideDown) continue;
        if (!(LoadedLevelAccess.blockEntity(level, pos) instanceof AbstractBogeyBlockEntity actual))
          throw new IllegalStateException("Actual registered bogey has no native BE");
        bogeys.add(new Bogey(index, rail, pos, block, actual, upsideDown, actual.getBogeyData()));
        break;
      }
    }
    return new StationAssemblyArea(
        GlobalPos.of(level.dimension(), station.getBlockPos()),
        point.id,
        point.graph().id,
        point.graph().revision(),
        target,
        direction,
        length,
        cells,
        bogeys);
  }
}
