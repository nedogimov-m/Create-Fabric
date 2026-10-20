package com.simibubi.create.content.trains.track;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Source physical target-overlay transform, extracted before submission. No world is retained. */
public final class TrackTargetingRenderer {
  public static Matrix4f extractTransform(BlockEntity owner, TrackTargetingBehaviour<?> targeting) {
    var selected = targeting.selection();
    var level = owner.getLevel();
    if (level == null
        || selected == null
        || !level.dimension().equals(selected.target().dimension())
        || !LoadedLevelAccess.isLoaded(level, selected.target().pos())) return null;
    var pos = selected.target().pos();
    var state = LoadedLevelAccess.blockState(level, pos);
    if (!(state.getBlock() instanceof TrackBlock track)) return null;
    var existingTrack = TrackBlockEntityAccess.existing(level, pos);
    if (state.getValue(TrackBlock.HAS_BE) && existingTrack == null) return null;
    var pose = new PoseStack();
    pose.translate(Vec3.atLowerCornerOf(pos.subtract(owner.getBlockPos())));
    Vec3 axis = null, difference, normal;
    if (selected.curve().isPresent()) {
      var key = selected.curve().get();
      if (!LoadedLevelAccess.isLoaded(level, key.curveTarget())
          || !(TrackBlockEntityAccess.existing(level, pos) instanceof TrackBlockEntity entity)
          || !(TrackBlockEntityAccess.existing(level, key.curveTarget())
              instanceof TrackBlockEntity other)) return null;
      var curve = entity.getConnections().get(key.curveTarget());
      var reverse = other.getConnections().get(pos);
      if (curve == null
          || !curve.isPrimary()
          || reverse == null
          || reverse.isPrimary()
          || !curve.equalsSansMaterial(reverse)
          || curve.getMaterial() != reverse.getMaterial()
          || key.segment() < 0
          || key.segment() >= curve.getSegmentCount() - 1) return null;
      double count = Math.floor(curve.getLength() * 2), segment = key.segment() + 1;
      if (count < 1) return null;
      double t = segment / count;
      var offset = curve.getPosition(t);
      normal = curve.getNormal(t);
      difference =
          curve
              .getPosition((segment + 1) / count)
              .subtract(curve.getPosition((segment - 1) / count))
              .normalize();
      pose.translate(offset.subtract(Vec3.atBottomCenterOf(pos)));
      pose.translate(0, -4 / 16f, 0);
    } else {
      var axes = track.getTrackAxes(level, pos, state);
      if (axes.size() != 1 || axes.getFirst().distanceToSqr(selected.axis()) > 1e-8) return null;
      axis = axes.getFirst();
      difference = axis.scale(selected.direction().getStep()).normalize();
      normal = track.getUpNormal(level, pos, state);
    }
    var angles = TrackRenderer.getModelAngles(normal, difference);
    pose.translate(.5, .5, .5);
    pose.rotate(Axis.YP.rotation((float) angles.y));
    pose.rotate(Axis.XP.rotation((float) angles.x));
    pose.translate(-.5, -.5, -.5);
    if (axis != null)
      pose.translate(
          0,
          axis.y != 0 ? 7 / 16f : 0,
          axis.y != 0 ? selected.direction().getStep() * 2.5f / 16f : 0);
    else {
      pose.translate(0, 4 / 16f, 0);
      if (selected.direction() == Direction.AxisDirection.NEGATIVE) {
        pose.translate(.5, .5, .5);
        pose.rotate(Axis.YP.rotation((float) Math.PI));
        pose.translate(-.5, -.5, -.5);
      }
    }
    if (selected.curve().isEmpty()) {
      if (existingTrack instanceof TrackBlockEntity trackEntity && trackEntity.tilt.smoothingAngle.isPresent()) {
        double yOffset = 0;
        for (var curve : trackEntity.getConnections().values())
          yOffset += curve.starts.getFirst().y - pos.getY();
        pose.translate(.5, .5, .5);
        pose.rotate(Axis.XP.rotationDegrees((float) (-selected.direction().getStep() * trackEntity.tilt.smoothingAngle.get())));
        pose.translate(-.5, -.5, -.5);
        pose.translate(0, yOffset / 2, 0);
      }
    }
    return new Matrix4f(pose.last().pose());
  }

  private TrackTargetingRenderer() {}
}
