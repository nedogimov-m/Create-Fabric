package com.simibubi.create.content.trains.graph;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

/** Actual reciprocal geometric crossing, independent of unported signal block implementation. */
public final class TrackEdgeIntersection {
  public double location, targetLocation;
  public Couple<TrackNodeLocation> target;
  public UUID groupId;
  public UUID id = UUID.randomUUID();
  public static final Codec<TrackEdgeIntersection> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      UUIDUtil.CODEC.fieldOf("Id").forGetter(value -> value.id),
                      RailCodecs.FINITE_DOUBLE
                          .fieldOf("Location")
                          .forGetter(value -> value.location),
                      RailCodecs.FINITE_DOUBLE
                          .fieldOf("TargetLocation")
                          .forGetter(value -> value.targetLocation),
                      RailCodecs.couple(TrackNodeLocation.CODEC)
                          .fieldOf("TargetEdge")
                          .forGetter(value -> value.target),
                      UUIDUtil.CODEC
                          .optionalFieldOf("GroupId")
                          .forGetter(value -> Optional.ofNullable(value.groupId)))
                  .apply(
                      i,
                      (id, location, targetLocation, target, group) -> {
                        var value = new TrackEdgeIntersection();
                        value.id = id;
                        value.location = location;
                        value.targetLocation = targetLocation;
                        value.target = target;
                        value.groupId = group.orElse(null);
                        return value;
                      }));

  public boolean isNear(double other) {
    return Math.abs(other - location) < 1 / 32f;
  }

  public boolean targets(TrackNodeLocation first, TrackNodeLocation second) {
    return first.equals(target.getFirst()) && second.equals(target.getSecond())
        || first.equals(target.getSecond()) && second.equals(target.getFirst());
  }

  public CompoundTag write(DimensionPalette dimensions) {
    var tag = new CompoundTag();
    tag.store("Id", UUIDUtil.CODEC, id);
    if (groupId != null) tag.store("GroupId", UUIDUtil.CODEC, groupId);
    tag.putDouble("Location", location);
    tag.putDouble("TargetLocation", targetLocation);
    tag.put("TargetEdge", RailCodecs.writeCouple(target, value -> value.write(dimensions)));
    return tag;
  }

  public static TrackEdgeIntersection read(CompoundTag tag, DimensionPalette dimensions) {
    var result = new TrackEdgeIntersection();
    result.id = tag.read("Id", UUIDUtil.CODEC).orElseThrow();
    result.groupId = tag.read("GroupId", UUIDUtil.CODEC).orElse(null);
    result.location = tag.getDoubleOr("Location", Double.NaN);
    result.targetLocation = tag.getDoubleOr("TargetLocation", Double.NaN);
    result.target =
        RailCodecs.readCouple(
            tag, "TargetEdge", value -> TrackNodeLocation.read(value, dimensions));
    if (!Double.isFinite(result.location) || !Double.isFinite(result.targetLocation))
      throw new IllegalArgumentException("Invalid railway crossing");
    return result;
  }
}
