package com.simibubi.create.content.trains.graph;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** Actual graph vertex; object identity is retained through graph transfer and split. */
public final class TrackNode {
  final int netId;
  final Vec3 normal;
  final TrackNodeLocation location;

  public TrackNode(TrackNodeLocation location, int netId, Vec3 normal) {
    this.location = Objects.requireNonNull(location).copy();
    this.netId = netId;
    this.normal = RailCodecs.nonzero(normal).normalize();
    if (netId <= 0) throw new IllegalArgumentException("Invalid railway node transport id");
  }

  public TrackNodeLocation getLocation() {
    return location.copy();
  }

  public int getNetId() {
    return netId;
  }

  public Vec3 getNormal() {
    return normal;
  }
}
