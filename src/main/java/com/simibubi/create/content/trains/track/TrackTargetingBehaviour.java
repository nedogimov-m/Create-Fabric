package com.simibubi.create.content.trains.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.signal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Source point ownership/relative targeting as actual native BE composition. */
public final class TrackTargetingBehaviour<T extends TrackEdgePoint> extends com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour {
  public static final com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<TrackTargetingBehaviour<?>> TYPE=new com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<>();
  @Override public com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<?> getType(){return TYPE;}
  @Override public void tick(){super.tick();resolveTick();}
  @Override public boolean isSafeNBT(){return true;}
  private record State(
      BlockPos targetOffset,
      net.minecraft.resources.ResourceKey<Level> dimension,
      boolean front,
      Vec3 axis,
      Optional<BezierTrackPointLocation> relativeCurve) {
    static final Codec<State> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        BlockPos.CODEC.fieldOf("TargetTrack").forGetter(State::targetOffset),
                        Level.RESOURCE_KEY_CODEC.fieldOf("Dimension").forGetter(State::dimension),
                        Codec.BOOL.fieldOf("TargetDirection").forGetter(State::front),
                        RailCodecs.VECTOR.fieldOf("Axis").forGetter(State::axis),
                        BezierTrackPointLocation.CODEC
                            .optionalFieldOf("Bezier")
                            .forGetter(State::relativeCurve))
                    .apply(i, State::new));
  }

  public enum Status {
    UNBOUND,
    BOUND,
    PENDING_LOADED_FRONTIER,
    INVALID_TRACK,
    OCCUPIED,
    FOREIGN_OWNER
  }

  private final EdgePointType<T> type;
  private final Runnable changed;
  private UUID id = UUID.randomUUID();
  private State state;
  private NeoForgeTrackTargetingReference originalTargeting;
  private Vec3 originalRotatedAxis;
  private CompoundTag migration;
  private T point;
  private Status status = Status.UNBOUND;

  public TrackTargetingBehaviour(BlockEntity owner, EdgePointType<T> type, Runnable changed) {
    super(Objects.requireNonNull(owner));
    this.type = Objects.requireNonNull(type);
    this.changed = Objects.requireNonNull(changed);
  }

  public EdgePointType<T> pointType() {
    return type;
  }

  public UUID id() {
    return id;
  }

  public Status status() {
    return status;
  }

  public T getEdgePoint() {
    return status == Status.BOUND ? point : null;
  }

  public boolean isOnCurve() {
    return state != null ? state.relativeCurve.isPresent() : originalTargeting != null && originalTargeting.curve().isPresent();
  }

  public boolean isOrthogonal() {
    var axis = state == null ? Vec3.ZERO : state.axis;
    return state != null
        && !isOnCurve()
        && axis.y == 0
        && (Math.abs(axis.x) > 1 - 1e-6 && Math.abs(axis.z) < 1e-6
            || Math.abs(axis.z) > 1 - 1e-6 && Math.abs(axis.x) < 1e-6);
  }

  public TrackTargetingSelection selection() {
    if (state == null) return null;
    var curve =
        state.relativeCurve.map(
            c ->
                new BezierTrackPointLocation(
                    blockEntity.getBlockPos().offset(c.curveTarget()), c.segment()));
    return new TrackTargetingSelection(
        GlobalPos.of(state.dimension, blockEntity.getBlockPos().offset(state.targetOffset)),
        state.front,
        state.axis,
        curve);
  }

  public boolean hasValidTrack() {
    var level = blockEntity.getLevel();
    return level != null
        && selection() != null
        && selection().target().dimension().equals(level.dimension())
        && com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(
            level, getGlobalPosition())
        && getTrackBlockState().getBlock() instanceof ITrackBlock;
  }

  public net.minecraft.world.level.block.state.BlockState getTrackBlockState() {
    return com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(
        blockEntity.getLevel(), getGlobalPosition());
  }

  public ITrackBlock getTrack() {
    return (ITrackBlock) getTrackBlockState().getBlock();
  }

  public BlockPos getGlobalPosition() {
    var selected = selection();
    return selected != null ? selected.target().pos() : originalTargeting != null
        ? blockEntity.getBlockPos().offset(originalTargeting.offset()) : blockEntity.getBlockPos();
  }

  public BlockPos getPositionForMapMarker() {
    var target=getGlobalPosition();var curve=getTargetBezier();
    if(curve!=null&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(blockEntity.getLevel(),target) instanceof TrackBlockEntity track){
      var connection=track.getConnections().get(curve.curveTarget());
      if(connection!=null){double segments=net.minecraft.util.Mth.floor(connection.getLength()*2);return BlockPos.containing(connection.getPosition((curve.segment()+1)/segments));}
    }
    return target;
  }

  public Direction.AxisDirection getTargetDirection() {
    boolean front = state != null ? state.front : originalTargeting == null || originalTargeting.front();
    return front ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
  }

  public BezierTrackPointLocation getTargetBezier() {
    var selected = selection();
    return selected != null ? selected.curve().orElse(null) : originalTargeting == null ? null
        : originalTargeting.curve().map(c -> new BezierTrackPointLocation(
            blockEntity.getBlockPos().offset(c.curveTarget()), c.segment())).orElse(null);
  }

  public TrackGraphLocation determineGraphLocation() {
    var selected = selection();
    return selected == null || blockEntity.getLevel() == null ? null : selected.resolve(blockEntity.getLevel());
  }

  public static boolean overlaps(
      TrackGraphLocation location, EdgePointType<?> type, boolean front, UUID ignore) {
    var nodes = location.edge.map(location.graph::locateNode);
    var edge = location.graph.getConnection(nodes);
    if (edge == null) return true;
    for (var point : edge.getEdgeData().getPoints()) {
      if (point.id.equals(ignore)) continue;
      double distance = Math.abs(location.position - point.getLocationOn(edge));
      if (distance > .75 || distance < .25 && point.canCoexistWith(type, front)) continue;
      return true;
    }
    return false;
  }

  private boolean actualOwner(TrackEdgePoint candidate) {
    return !(candidate instanceof SingleBlockEntityEdgePoint single)
        || blockEntity.getLevel() != null
            && single
                .owner()
                .equals(GlobalPos.of(blockEntity.getLevel().dimension(), blockEntity.getBlockPos()));
  }

  /**
   * Initial native placement/configuration. Full target discovery precedes any graph detachment.
   */
  public boolean configure(TrackTargetingSelection selected) {
    var level = blockEntity.getLevel();
    if (level == null || level.isClientSide()) return false;
    var location = selected.resolve(level);
    if (location == null || overlaps(location, type, selected.front(), id)) return false;
    // Native configuration may arrive after disk load and before the first BE tick.
    // Resolve the old actual graph owner before replacing its saved direction/target.
    T previous = findExisting();
    if (previous != null) {
      if (!actualOwner(previous)) return false;
      detachActualOwner(previous, getTargetDirection() == Direction.AxisDirection.POSITIVE);
      point = null;
    }
    state =
        new State(
            selected.target().pos().subtract(blockEntity.getBlockPos()),
            selected.target().dimension(),
            selected.front(),
            selected.axis(),
            selected
                .curve()
                .map(
                    c ->
                        new BezierTrackPointLocation(
                            c.curveTarget().subtract(blockEntity.getBlockPos()), c.segment())));
    originalTargeting = null;
    originalRotatedAxis = null;
    status = Status.UNBOUND;
    changed.run();
    return true;
  }

  private T findExisting() {
    T existing = null;
    for (var graph : RailwayGraphManager.get(blockEntity.getLevel()).getTrackNetworks().values()) {
      var found = graph.getPoint(type, id);
      if (found == null) continue;
      if (existing != null && existing != found)
        throw new IllegalStateException("Duplicate world-owned railway point UUID");
      existing = found;
    }
    return existing;
  }

  private boolean containsActualOwner(T candidate) {
    return candidate
        .invalidationTargets(blockEntity.getLevel())
        .contains(GlobalPos.of(blockEntity.getLevel().dimension(), blockEntity.getBlockPos()));
  }

  private void detachActualOwner(T previous, boolean front) {
    if (containsActualOwner(previous)) {
      previous.preflightInvalidation(blockEntity.getLevel());
      captureMigration(previous);
      previous.blockEntityRemoved(blockEntity.getBlockPos(), front);
    } else {
      // A shared UUID alone grants no authority to copy another owner's migration data.
      migration = null;
    }
    // Other genuine Signal owners retain their actual old point and UUID.
    // The detached owner must use a fresh identity until a real canMerge join succeeds.
    if (previous.graph() != null && previous.graph().getPoint(type, previous.id) == previous)
      id = UUID.randomUUID();
  }

  private void captureMigration(T point) {
    var tag = new CompoundTag();
    var dimensions = new DimensionPalette();
    point.write(tag, blockEntity.getLevel().registryAccess(), dimensions);
    dimensions.write(tag);
    migration = tag;
  }

  public void invalidateEdgePoint(CompoundTag data) {
    if (!data.read("Id", UUIDUtil.CODEC).orElseThrow().equals(id))
      throw new IllegalArgumentException(
          "Foreign railway point migration cannot replace this native owner");
    migration = data.copy();
    point = null;
    status = Status.UNBOUND;
    changed.run();
  }

  public T resolveTick() {
    var level = blockEntity.getLevel();
    if (level == null) return null;
    try {
      if (originalTargeting != null && !resolveOriginalTarget(level)) return null;
      if (state == null) return null;
      canonicalizeTransformedAxis(level);
      var location = determineGraphLocation();
      if (location == null) {
        point = null;
        status = Status.INVALID_TRACK;
        return null;
      }
      if (level.isClientSide()) {
        var cached = location.graph.getPoint(type, id);
        point = cached != null && actualOwner(cached) ? cached : null;
        status = point == null ? Status.UNBOUND : Status.BOUND;
        return point;
      }
      T existing = findExisting();
      if (existing != null) {
        if (!actualOwner(existing)) {
          point = null;
          status = Status.FOREIGN_OWNER;
          return null;
        }
        var edge = location.graph.getConnection(location.edge.map(location.graph::locateNode));
        if (existing.graph() == location.graph
            && edge != null
            && edge.getEdgeData().getPoints().contains(existing)
            && Math.abs(existing.getLocationOn(edge) - location.position) < 1e-6) {
          if (existing.canMerge() && !containsActualOwner(existing)) {
            existing.blockEntityAdded(blockEntity, state.front);
            location.graph.markDirty();
            changed.run();
          }
          originalRotatedAxis = null;
          point = existing;
          status = Status.BOUND;
          return point;
        }
        // Only a complete new target can retire a known actual old owner point.
        if (overlaps(location, type, state.front, id)) {
          status = Status.OCCUPIED;
          return null;
        }
        detachActualOwner(existing, state.front);
        point = null;
      }
      if (originalRotatedAxis != null) {
        var originalEdge = location.graph.getConnection(location.edge.map(location.graph::locateNode));
        if (originalEdge == null) { status = Status.INVALID_TRACK; return null; }
        double alignment = originalEdge.getDirectionAt(location.position)
            .scale(state.front ? -1 : 1).dot(originalRotatedAxis);
        originalRotatedAxis = null;
        if (alignment < -.85) {
          state = new State(state.targetOffset, state.dimension, !state.front, state.axis, state.relativeCurve);
          changed.run();
          return null;
        }
        changed.run();
      }
      if (overlaps(location, type, state.front, id)) {
        status = Status.OCCUPIED;
        return null;
      }
      var actualEdge = location.graph.getConnection(location.edge.map(location.graph::locateNode));
      if (actualEdge == null) {
        status = Status.INVALID_TRACK;
        return null;
      }
      for (var other : List.copyOf(actualEdge.getEdgeData().getPoints())) {
        if (other.getType() != type
            || !other.canMerge()
            || Math.abs(other.getLocationOn(actualEdge) - location.position) >= .25) continue;
        other.blockEntityAdded(blockEntity, state.front);
        @SuppressWarnings("unchecked")
        T merged = (T) other;
        id = other.id;
        point = merged;
        migration = null;
        status = Status.BOUND;
        location.graph.markDirty();
        changed.run();
        return merged;
      }
      var fresh = type.create();
      if (migration != null)
        fresh.read(migration, level.registryAccess(), true, DimensionPalette.read(migration));
      fresh.setId(id);
      var edge = location.graph.getConnection(location.edge.map(location.graph::locateNode));
      if (edge == null) {
        status = Status.INVALID_TRACK;
        return null;
      }
      boolean reverse = state.front || fresh instanceof SingleBlockEntityEdgePoint;
      fresh.setLocation(
          reverse ? location.edge : location.edge.swap(),
          reverse ? location.position : edge.getLength() - location.position);
      fresh.blockEntityAdded(blockEntity, state.front);
      location.graph.addPoint(type, fresh);
      point = fresh;
      migration = null;
      status = Status.BOUND;
      changed.run();
      return fresh;
    } catch (UnloadedTrackException pending) {
      status = Status.PENDING_LOADED_FRONTIER;
      return null;
    }
  }

  /** Source3955 never saved Dimension/Axis. Resolve only from this actual loaded owner world. */
  private boolean resolveOriginalTarget(Level level) {
    var original = originalTargeting;
    if (!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level, blockEntity.getBlockPos())
        || com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level, blockEntity.getBlockPos()) != blockEntity) {
      status = Status.UNBOUND;
      return false;
    }
    var target = blockEntity.getBlockPos().offset(original.offset());
    if (!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level, target))
      throw new UnloadedTrackException(level, target);
    var actual = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level, target);
    if (!(actual.getBlock() instanceof ITrackBlock track)) {status = Status.INVALID_TRACK; return false;}
    var axes = track.getTrackAxes(level, target, actual);
    if (axes.isEmpty()) {status = Status.INVALID_TRACK; return false;}
    // This is the actual original selection rule, including crossings: first real track axis.
    var axis = axes.getFirst();
    RailCodecs.nonzero(axis);
    state = new State(original.offset(), level.dimension(), original.front(), axis, original.curve());
    originalRotatedAxis = original.rotatedAxis().orElse(null);
    originalTargeting = null;
    if (!level.isClientSide()) changed.run();
    return true;
  }

  /** Called on actual block destruction, never merely native chunk unload. */
  public void destroy() {
    var level = blockEntity.getLevel();
    if (level == null || level.isClientSide()) return;
    for (var graph : List.copyOf(RailwayGraphManager.get(level).getTrackNetworks().values())) {
      var actual = graph.getPoint(type, id);
      if (actual != null && actualOwner(actual))
        actual.blockEntityRemoved(
            blockEntity.getBlockPos(), getTargetDirection() == Direction.AxisDirection.POSITIVE);
    }
    point = null;
    status = Status.UNBOUND;
  }

  private void canonicalizeTransformedAxis(Level level) {
    if (state == null
        || state.relativeCurve.isPresent()
        || !level.dimension().equals(state.dimension)
        || !com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(
            level, getGlobalPosition())) return;
    var actual = getTrackBlockState();
    if (!(actual.getBlock() instanceof ITrackBlock track)) return;
    var axes = track.getTrackAxes(level, getGlobalPosition(), actual);
    if (axes.size() != 1) return;
    var axis = axes.getFirst();
    if (Math.abs(axis.lengthSqr() - state.axis.lengthSqr()) > 1e-6) return;
    double alignment = axis.normalize().dot(state.axis.normalize());
    if (Math.abs(alignment) < 1 - 1e-6) return;
    if (axis.distanceToSqr(state.axis) < 1e-12) return;
    state =
        new State(
            state.targetOffset,
            state.dimension,
            alignment < 0 ? !state.front : state.front,
            axis,
            state.relativeCurve);
    if (!level.isClientSide()) changed.run();
  }

  /**
   * Source relative transform. Pure saved geometry/identity change: no world discovery or graph
   * mutation. Native capture/removal owns retirement of the old world's actual point before
   * transformed placement.
   */
  public void transform(
      BlockEntity blockEntity,
      com.simibubi.create.content.contraptions.StructureTransform transform) {
    if (blockEntity != this.blockEntity)
      throw new IllegalArgumentException("Transform must target this actual owner BE");
    id = UUID.randomUUID();
    if (originalTargeting != null) originalTargeting = originalTargeting.transform(transform);
    if (originalRotatedAxis != null) originalRotatedAxis = transform.applyWithoutOffsetUncentered(originalRotatedAxis);
    if (state != null)
      state =
          new State(
              transform.applyWithoutOffset(state.targetOffset),
              blockEntity.getLevel() == null ? state.dimension : blockEntity.getLevel().dimension(),
              state.front,
              transform.applyWithoutOffsetUncentered(state.axis),
              state.relativeCurve.map(
                  c ->
                      new BezierTrackPointLocation(
                          transform.applyWithoutOffset(c.curveTarget()), c.segment())));
    point = null;
    status = Status.UNBOUND;
    blockEntity.setChanged();
    if (blockEntity.getLevel() != null) changed.run();
  }

  @FunctionalInterface
  public interface Movement {
    /** Called only after this exact original BE has actually left its world cell. */
    void committed();
  }

  /** Pure loaded actual-owner check, including every genuine owner of a shared signal. */
  public void validateMovement() throws com.simibubi.create.content.contraptions.AssemblyException {
    movementSource();
  }

  private T movementSource() throws com.simibubi.create.content.contraptions.AssemblyException {
    var level = blockEntity.getLevel();
    if (!(level instanceof net.minecraft.server.level.ServerLevel server))
      throw new IllegalStateException("Native server point capture required");
    var previous = findExisting();
    if (previous == null) return null;
    if (!actualOwner(previous) || !containsActualOwner(previous))
      throw com.simibubi.create.content.contraptions.AssemblyException.structureChanged(
          blockEntity.getBlockPos());
    try {
      previous.preflightInvalidation(level);
      for (var position : previous.invalidationTargets(level)) {
        var world = server.getServer().getLevel(position.dimension());
        if (world == null)
          throw com.simibubi.create.content.contraptions.AssemblyException.entitiesNotReady(
              blockEntity.getBlockPos());
        com.simibubi.create.foundation.utility.LoadedEntityAccess.requireReady(
            world, position.pos());
        var actual =
            com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(
                world, position.pos());
        if (!(actual instanceof TrackTargetingBlockItem.Target target)
            || !target.targeting().id().equals(previous.id))
          throw com.simibubi.create.content.contraptions.AssemblyException.structureChanged(
              position.pos());
      }
    } catch (UnloadedTrackException unavailable) {
      throw com.simibubi.create.content.contraptions.AssemblyException.entitiesNotReady(
          blockEntity.getBlockPos());
    }
    return previous;
  }

  /** Latest full RegistryOps migration belongs only in the actual private captured BE data. */
  public Movement captureMovement(CompoundTag privateTargeting)
      throws com.simibubi.create.content.contraptions.AssemblyException {
    if (!privateTargeting.read("Id", UUIDUtil.CODEC).orElseThrow().equals(id))
      throw new IllegalArgumentException("Captured targeting data has another original owner");
    var previous = movementSource();
    if (previous == null) return null;
    var snapshot = new CompoundTag();
    var dimensions = new DimensionPalette();
    previous.write(snapshot, blockEntity.getLevel().registryAccess(), dimensions);
    dimensions.write(snapshot);
    privateTargeting.put("Migrate", snapshot);
    boolean front = getTargetDirection() == Direction.AxisDirection.POSITIVE;
    return () -> {
      var level = blockEntity.getLevel();
      if (level == null
          || com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(
                  level, blockEntity.getBlockPos())
              == blockEntity)
        throw new IllegalStateException("Original railway owner is still world-owned");
      if (findExisting() != previous || !containsActualOwner(previous))
        throw new IllegalStateException(
            "Original point ownership changed during quiet native capture");
      previous.blockEntityRemoved(blockEntity.getBlockPos(), front);
      point = null;
      status = Status.UNBOUND;
    };
  }

  /** Public snapshots retain real targeting geometry/identity and never private migration cargo. */
  public void writeClient(CompoundTag tag) {
    tag.store("Id", UUIDUtil.CODEC, id);
    if (state != null) tag.store("TargetingSelection", State.CODEC, state);
    else tag.remove("TargetingSelection");
    if (originalTargeting != null) tag.store("OriginalTargeting", NeoForgeTrackTargetingReference.CODEC, originalTargeting);
    else tag.remove("OriginalTargeting");
    tag.remove("OriginalRotatedAxis");
    tag.remove("Migrate");
  }

  public void write(CompoundTag tag) {
    writeClient(tag);
    if (originalRotatedAxis != null) tag.store("OriginalRotatedAxis", RailCodecs.VECTOR, originalRotatedAxis);
    if (migration != null) tag.put("Migrate", migration.copy());
  }

  public void read(CompoundTag tag) {
    id = tag.read("Id", UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
    state =
        tag.contains("TargetingSelection")
            ? tag.read("TargetingSelection", State.CODEC).orElseThrow()
            : null;
    originalTargeting = tag.contains("OriginalTargeting")
        ? tag.read("OriginalTargeting", NeoForgeTrackTargetingReference.CODEC).orElseThrow() : null;
    if (state != null && originalTargeting != null)
      throw new IllegalArgumentException("Contradictory native and original pending targeting");
    originalRotatedAxis = tag.contains("OriginalRotatedAxis")
        ? tag.read("OriginalRotatedAxis", RailCodecs.VECTOR).orElseThrow() : null;
    if (originalRotatedAxis != null) RailCodecs.nonzero(originalRotatedAxis);
    migration = tag.getCompound("Migrate").map(CompoundTag::copy).orElse(null);
    point = null;
    status = Status.UNBOUND;
  }
  @Override public void read(net.minecraft.world.level.storage.ValueInput input,boolean clientPacket){
    CompoundTag data;
    if(blockEntity instanceof com.simibubi.create.content.trains.station.StationBlockEntity)
      data=input.read("StationData",CompoundTag.CODEC).orElseGet(CompoundTag::new);
    else data=input.read("Targeting",CompoundTag.CODEC).orElseGet(CompoundTag::new);
    read(data);
  }
  @Override public void write(net.minecraft.world.level.storage.ValueOutput output,boolean clientPacket){
    var data=blockEntity instanceof com.simibubi.create.content.trains.station.StationBlockEntity station
      ?station.writeStationData(clientPacket):new CompoundTag();
    if(clientPacket)writeClient(data);else write(data);
    output.store(blockEntity instanceof com.simibubi.create.content.trains.station.StationBlockEntity
      ?"StationData":"Targeting",CompoundTag.CODEC,data);
  }

}
