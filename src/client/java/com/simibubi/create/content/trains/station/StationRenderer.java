package com.simibubi.create.content.trains.station;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Source flag, real target marker and source legal bogey locations on detached native meshes. */
public final class StationRenderer
    implements BlockEntityRenderer<StationBlockEntity, StationRenderer.State> {
  public record Piece(PartialModelRenderState model, Matrix4f transform, int light) {}

  public static final class State extends BlockEntityRenderState {
    public List<Piece> pieces = List.of();
    public boolean assembly;
    public int legalOffsets, occupiedOffsets;
    public float flagProgress;
  }

  public StationRenderer(BlockEntityRendererProvider.Context context) {}

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  public void extractRenderState(
      StationBlockEntity station,
      State state,
      float partial,
      Vec3 camera,
      ModelFeatureRenderer.CrumblingOverlay overlay) {
    BlockEntityRenderer.super.extractRenderState(station, state, partial, camera, overlay);
    var pieces = new ArrayList<Piece>();
    state.assembly = station.isAssembling();
    state.legalOffsets = state.occupiedOffsets = 0;
    var selected = station.targeting().selection();
    var level = station.getLevel();
    if (selected == null
        || level == null
        || !level.dimension().equals(selected.target().dimension())
        || !LoadedLevelAccess.isLoaded(level, selected.target().pos())) {
      state.pieces = List.of();
      return;
    }
    var trackPos = selected.target().pos();
    var physical = LoadedLevelAccess.blockState(level, trackPos);
    if (!(physical.getBlock() instanceof ITrackBlock track)) {
      state.pieces = List.of();
      return;
    }
    var flagAxis = selected.axis().scale(selected.direction().getStep());
    var nearest = Direction.getApproximateNearest(flagAxis.x, 0, flagAxis.z);
    float yaw = -nearest.toYRot() - 90;
    var difference =
        Vec3.atLowerCornerOf(trackPos.subtract(station.getBlockPos())).multiply(1, 0, 1);
    boolean flipped =
        difference.lengthSqr() != 0
            && difference.dot(Vec3.atLowerCornerOf(nearest.getClockWise().getStep())) > 0;
    float value = station.flagValue(partial),
        progress = (float) Math.pow(Math.min(value * 5, 1), 2);
    if (station.flagTarget() > 0 && !station.flagSettled() && progress == 1) {
      float wiggle = (value - .2f) / .8f;
      progress += (float) (Math.sin(wiggle * 2 * Math.PI * 4) / 8) / Math.max(1, 8 * wiggle);
    }
    state.flagProgress = progress;
    var flag = new PartialModelRenderState();
    AllPartialModels.extract(
        state.assembly ? StationModels.FLAG_ASSEMBLE : StationModels.FLAG_OFF, flag);
    tint(flag, 0xFFFFFFFF);
    var pose = new PoseStack();
    pose.translate(.5, .5, .5);
    pose.rotate(Axis.YP.rotationDegrees(yaw));
    float nudge = 1 / 512f;
    pose.translate(nudge, 9.5f / 16, flipped ? 14f / 16 - nudge : 2f / 16 + nudge);
    pose.translate(-.5, -.5, -.5);
    pose.rotate(Axis.XP.rotationDegrees((flipped ? 1 : -1) * (progress * 90 + 270)));
    pose.translate(.5f / 16, 0, 0);
    pose.rotate(Axis.YP.rotationDegrees(flipped ? 0 : 180));
    pose.translate(-.5f / 16, 0, 0);
    pieces.add(new Piece(flag, new Matrix4f(pose.last().pose()), state.lightCoords));
    if (!state.assembly) {
      var transform = TrackTargetingRenderer.extractTransform(station, station.targeting());
      if (transform != null) {
        var marker = new PartialModelRenderState();
        AllPartialModels.extract(StationModels.TARGET, marker);
        tint(marker, 0xFFFFFFFF);
        pieces.add(
            new Piece(
                marker,
                transform,
                net.minecraft.util.LightCoordsUtil.getLightCoords(level, trackPos)));
      }
    } else if (physical.getBlock() instanceof TrackBlock
        && station.publishedDirection() != null
        && station.publishedAreaLength() > 0) {
      var legal = new PartialModelRenderState();
      AllPartialModels.extract(StationModels.ASSEMBLY, legal);
      tint(legal, 0xFF96B5FF);
      var occupied = new PartialModelRenderState();
      AllPartialModels.extract(StationModels.ASSEMBLY, occupied);
      tint(occupied, 0xFFCAFF96);
      var direction = station.publishedDirection();
      var offsets = station.publishedBogeys();
      for (int index = 0; index < station.publishedAreaLength(); index++) {
        final int offset = index;
        boolean hasBogey = offsets.contains(index);
        boolean allowed = !(index < 3 || offsets.isEmpty()) || index == 0;
        if (allowed) allowed = offsets.stream().noneMatch(other -> Math.abs(other - offset) <= 2);
        if (!hasBogey && !allowed) continue;
        var pos = trackPos.relative(direction, index + 1);
        if (!LoadedLevelAccess.isLoaded(level, pos)) continue;
        var actual = LoadedLevelAccess.blockState(level, pos);
        if (!track.trackEquals(physical, actual)) continue;
        var strip = new PoseStack();
        strip.translate(Vec3.atLowerCornerOf(pos.subtract(station.getBlockPos())));
        strip.translate(.5, .5, .5);
        strip.rotate(
            Axis.YP.rotationDegrees(
                direction.getAxis() == Direction.Axis.X
                    ? -direction.toYRot()
                    : direction.toYRot()));
        strip.translate(-.5, -.5, -.5);
        pieces.add(
            new Piece(
                hasBogey ? occupied : legal,
                new Matrix4f(strip.last().pose()),
                net.minecraft.util.LightCoordsUtil.getLightCoords(level, pos)));
        if (hasBogey) state.occupiedOffsets++;
        else state.legalOffsets++;
      }
    }
    state.pieces = List.copyOf(pieces);
  }

  private static void tint(PartialModelRenderState model, int argb) {
    var mutable = Renderer.get().mutableMesh();
    var emitter = mutable.emitter();
    model.mesh.forEach(
        quad -> {
          emitter
              .copyFrom(quad)
              .chunkLayer(net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT)
              .multiplyColor(argb)
              .emit();
        });
    model.mesh = mutable.immutableCopy();
  }

  @Override
  public void submit(
      State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
    for (var piece : state.pieces) {
      pose.pushPose();
      pose.mulPose(piece.transform());
      piece.model().submit(pose, collector, piece.light(), OverlayTexture.NO_OVERLAY, 0);
      pose.popPose();
    }
  }

  @Override
  public boolean shouldRenderOffScreen() {
    return true;
  }

  @Override
  public int getViewDistance() {
    return 192;
  }
}
