package com.simibubi.create.content.trains.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Genuine selected source track, including dimension and original axis to reject stale selections.
 */
public record TrackTargetingSelection(
    GlobalPos target, boolean front, Vec3 axis, Optional<BezierTrackPointLocation> curve) {
  public static final Codec<TrackTargetingSelection> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      GlobalPos.CODEC.fieldOf("Target").forGetter(TrackTargetingSelection::target),
                      Codec.BOOL.fieldOf("Front").forGetter(TrackTargetingSelection::front),
                      RailCodecs.VECTOR.fieldOf("Axis").forGetter(TrackTargetingSelection::axis),
                      BezierTrackPointLocation.CODEC
                          .optionalFieldOf("Curve")
                          .forGetter(TrackTargetingSelection::curve))
                  .apply(i, TrackTargetingSelection::new));
  public static final net.minecraft.network.codec.StreamCodec<
          net.minecraft.network.RegistryFriendlyByteBuf, TrackTargetingSelection>
      STREAM_CODEC =
          net.minecraft.network.codec.StreamCodec.of(
              (buf, selection) -> {
                buf.writeIdentifier(selection.target.dimension().identifier());
                buf.writeBlockPos(selection.target.pos());
                buf.writeBoolean(selection.front);
                buf.writeDouble(selection.axis.x);
                buf.writeDouble(selection.axis.y);
                buf.writeDouble(selection.axis.z);
                buf.writeBoolean(selection.curve.isPresent());
                selection.curve.ifPresent(
                    c -> BezierTrackPointLocation.STREAM_CODEC.encode(buf, c));
              },
              buf -> {
                var target =
                    GlobalPos.of(
                        net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            buf.readIdentifier()),
                        buf.readBlockPos());
                boolean front = buf.readBoolean();
                var axis = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                var curve =
                    buf.readBoolean()
                        ? Optional.of(BezierTrackPointLocation.STREAM_CODEC.decode(buf))
                        : Optional.<BezierTrackPointLocation>empty();
                return new TrackTargetingSelection(target, front, axis, curve);
              });

  public TrackTargetingSelection {
    target = GlobalPos.of(Objects.requireNonNull(target).dimension(), target.pos().immutable());
    Objects.requireNonNull(curve);
    Objects.requireNonNull(axis);
    if (!Double.isFinite(axis.lengthSqr()) || axis.lengthSqr() < 1e-12)
      throw new IllegalArgumentException("Invalid selected track axis");
    if (curve.isPresent() && curve.get().segment() < 0)
      throw new IllegalArgumentException("Invalid selected curve segment");
  }

  public Direction.AxisDirection direction() {
    return front ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
  }

  public TrackGraphLocation resolve(Level level) {
    if (!level.dimension().equals(target.dimension())) return null;
    if (!LoadedLevelAccess.isLoaded(level, target.pos()))
      throw new UnloadedTrackException(level, target.pos());
    var state = LoadedLevelAccess.blockState(level, target.pos());
    if (!(state.getBlock() instanceof ITrackBlock track)) return null;
    if (curve.isEmpty()) {
      var axes = track.getTrackAxes(level, target.pos(), state);
      if (axes.stream().noneMatch(actual -> actual.lengthSqr() >= 1e-12
          && actual.distanceToSqr(axis) <= 1e-8)) return null;
      return TrackGraphHelper.getGraphLocationAt(level, target.pos(), direction(), axis);
    }
    var end = curve.get().curveTarget();
    if (!LoadedLevelAccess.isLoaded(level, end)) throw new UnloadedTrackException(level, end);
    if (!(LoadedLevelAccess.blockEntity(level, target.pos()) instanceof TrackBlockEntity first)
        || !(LoadedLevelAccess.blockEntity(level, end) instanceof TrackBlockEntity second))
      return null;
    var actual = first.getConnections().get(end);
    var reverse = second.getConnections().get(target.pos());
    if (actual == null
        || !actual.isPrimary()
        || reverse == null
        || reverse.isPrimary()
        || actual.getMaterial() != reverse.getMaterial()
        || !actual.equalsSansMaterial(reverse)
        || curve.get().segment() >= actual.getSegmentCount() - 1) return null;
    return TrackGraphHelper.getBezierGraphLocationAt(level, target.pos(), direction(), curve.get());
  }
}
