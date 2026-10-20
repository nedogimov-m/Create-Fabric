package com.simibubi.create.content.contraptions.actors.trainControls;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.*;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Source open cover/two lever transforms, detached native meshes and no live reads during submit.
 */
public final class ControlsRenderer implements ActorRenderer<ControlsRenderer.State> {
  public record State(
      PartialModelRenderState body,
      PartialModelRenderState cover,
      PartialModelRenderState lever,
      Matrix4f coverPose,
      Matrix4f firstPose,
      Matrix4f secondPose,
      int light) {}

  @Override
  public State extract(
      MovementContext context, VirtualRenderWorld world, float partial, Vec3 camera) {
    if (!(context.contraption.entity instanceof CarriageContraptionEntity front)) return null;
    var state = context.state.setValue(ControlsBlock.OPEN, true);
    if (!(context.temporaryData instanceof ControlsMovementBehaviour.LeverAngles))
      context.temporaryData = new ControlsMovementBehaviour.LeverAngles();
    var angles = (ControlsMovementBehaviour.LeverAngles) context.temporaryData;
    boolean controlled =
        ControlsHandler.getContraption() == front
            && context.localPos.equals(ControlsHandler.getControlsPos());
    boolean inverted =
        state.getValue(ControlsBlock.FACING) != front.getInitialOrientation().getCounterClockWise();
    angles.equip.chase(controlled ? 1 : 0, .2, Chaser.EXP);
    angles.steering.chase(
        controlled
            ? (ControlsHandler.currentlyPressed.contains(3) ? 1 : 0)
                - (ControlsHandler.currentlyPressed.contains(2) ? 1 : 0)
            : 0,
        controlled ? .2 : 0,
        Chaser.EXP);
    angles.speed.chase(
        controlled
            ? Math.min(context.motion.length(), .5) * (front.movingBackwards ^ inverted ? -1 : 1)
            : 0,
        controlled ? .2 : 0,
        Chaser.EXP);
    var body = world.models.extract(world, context.localPos, state);
    var cover = new PartialModelRenderState();
    var lever = new PartialModelRenderState();
    AllPartialModels.extract(AllPartialModels.TRAIN_CONTROLS_COVER, cover);
    AllPartialModels.extract(AllPartialModels.TRAIN_CONTROLS_LEVER, lever);
    float yaw =
        (float)
            Math.toRadians(180 + AngleHelper.horizontalAngle(state.getValue(ControlsBlock.FACING)));
    var coverPose =
        new Matrix4f().translate(.5f, .5f, .5f).rotateY(yaw).translate(-.5f, -.5f, -.5f);
    return new State(
        body,
        cover,
        lever,
        coverPose,
        lever(yaw, angles.equip.getValue(partial), angles.speed.getValue(partial) * 70 - 25, true),
        lever(yaw, angles.equip.getValue(partial), angles.steering.getValue(partial) * 15, false),
        LightCoordsUtil.getLightCoords(world, context.localPos));
  }

  private static Matrix4f lever(float yaw, float equip, float angle, boolean first) {
    float height = -.15f + .2f * equip * equip;
    return new Matrix4f()
        .translate(.5f, .5f, .5f)
        .rotateY(yaw)
        .translate(0, .25f, .25f)
        .rotateX((float) Math.toRadians(Math.clamp(angle, -45, 45) - 45))
        .translate(0, height, 0)
        .rotateX((float) Math.toRadians(45))
        .translate(-.5f, -.5f, -.5f)
        .translate(first ? 0 : 6 / 16f, -6 / 16f, -3 / 16f);
  }

  @Override
  public void submit(
      State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
    state.body().submit(pose, collector, state.light(), OverlayTexture.NO_OVERLAY, 0);
    part(state.cover(), state.coverPose(), state.light(), pose, collector);
    part(state.lever(), state.firstPose(), state.light(), pose, collector);
    part(state.lever(), state.secondPose(), state.light(), pose, collector);
  }

  private static void part(
      PartialModelRenderState mesh,
      Matrix4f transform,
      int light,
      PoseStack pose,
      SubmitNodeCollector collector) {
    pose.pushPose();
    pose.mulPose(transform);
    mesh.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
    pose.popPose();
  }
}
