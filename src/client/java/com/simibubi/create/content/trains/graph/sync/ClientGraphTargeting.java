package com.simibubi.create.content.trains.graph.sync;

import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Source geometry against the current native world and a complete synchronized graph revision. */
public final class ClientGraphTargeting {
  @Nullable
  public static TrackGraphLocation straight(BlockPos position, AxisDirection direction, Vec3 axis) {
    var client = Minecraft.getInstance();
    if (client.level == null || !RailwayGraphClient.view().ready()) return null;
    try {
      return TrackGraphHelper.getGraphLocationAt(client.level, position, direction, axis);
    } catch (UnloadedTrackException pending) {
      return null;
    }
  }

  @Nullable
  public static TrackGraphLocation curve(
      BlockPos position, AxisDirection direction, BezierTrackPointLocation target) {
    var client = Minecraft.getInstance();
    if (client.level == null || !RailwayGraphClient.view().ready()) return null;
    try {
      return TrackGraphHelper.getBezierGraphLocationAt(client.level, position, direction, target);
    } catch (UnloadedTrackException pending) {
      return null;
    }
  }

  /** Uses Doors' genuine loaded primary curve hit, never another picker or synthetic target. */
  @Nullable
  public static TrackGraphLocation curve(
      com.simibubi.create.content.trains.track.TrackCurveSelection.Hit hit,
      AxisDirection direction) {
    return hit == null
        ? null
        : curve(hit.owner(), direction, new BezierTrackPointLocation(hit.target(), hit.segment()));
  }

  private ClientGraphTargeting() {}
}
