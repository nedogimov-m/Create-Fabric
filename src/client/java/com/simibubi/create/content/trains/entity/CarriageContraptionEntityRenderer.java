package com.simibubi.create.content.trains.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.trains.bogey.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Native source carriage/bogey extraction; submit retains only detached models, poses and light.
 */
public final class CarriageContraptionEntityRenderer
    extends EntityRenderer<CarriageContraptionEntity, CarriageContraptionEntityRenderer.State> {
  private final ContraptionEntityRenderer body;

  public static final class State extends EntityRenderState {
    final ContraptionEntityRenderer.State body = new ContraptionEntityRenderer.State();

    record Bogey(BogeyRenderState mesh, Matrix4fc pose, int light) {}

    final List<Bogey> bogeys = new ArrayList<>();
    boolean visible;
  }

  public CarriageContraptionEntityRenderer(EntityRendererProvider.Context context) {
    super(context);
    body = new ContraptionEntityRenderer(context);
  }

  @Override
  public State createRenderState() {
    return new State();
  }

  @Override
  protected AABB getBoundingBoxForCulling(CarriageContraptionEntity entity, float partial) {
    return super.getBoundingBoxForCulling(entity, partial).inflate(3);
  }

  @Override
  public boolean shouldRender(
      CarriageContraptionEntity entity,
      Frustum frustum,
      double x,
      double y,
      double z,
      float partial) {
    var carriage = entity.getCarriage();
    if (carriage != null)
      for (var bogey : carriage.bogeys)
        if (bogey != null) bogey.couplingAnchors.replace(ignored -> null);
    return super.shouldRender(entity, frustum, x, y, z, partial);
  }

  @Override
  public void extractRenderState(CarriageContraptionEntity entity, State state, float partial) {
    super.extractRenderState(entity, state, partial);
    state.visible = false;
    state.bogeys.clear();
    if (!(entity.getContraption() instanceof CarriageContraption structure)) return;
    body.extractRenderState(entity, state.body, partial);
    state.body.blocks.removeIf(
        part ->
            structure.getBlocks().get(part.pos()).state().getBlock()
                instanceof
                com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock);
    // A real independent recovery owner remains visible before/without a public Train description.
    if (!entity.validForRender || entity.firstPositionUpdate || entity.getCarriage() == null) {
      state.visible = true;
      return;
    }
    // Generic physical bogey BEs are replaced by the actual carriage wheel-frame renderer.
    state.body.blocks.removeIf(
        part ->
            structure.isHiddenInPortal(part.pos())
                || structure.getBlocks().get(part.pos()).state().getBlock()
                    instanceof AbstractBogeyBlock<?>);
    state.body.blockEntities.removeIf(
        part ->
            structure.isHiddenInPortal(part.pos())
                || structure.getBlocks().get(part.pos()).state().getBlock()
                    instanceof AbstractBogeyBlock<?>);
    state.body.actors.removeIf(part -> structure.isHiddenInPortal(part.pos()));
    var carriage = entity.getCarriage();
    var position = entity.getPosition(partial);
    float viewYaw = entity.getViewYRot(partial), viewPitch = entity.getViewXRot(partial);
    for (var bogey : carriage.bogeys) {
      if (bogey == null) continue;
      var local =
          bogey.isLeading
              ? BlockPos.ZERO
              : BlockPos.ZERO.relative(
                  entity.getInitialOrientation().getCounterClockWise(), carriage.bogeySpacing);
      if (!structure.isHiddenInPortal(local)) {
        var mesh = new BogeyRenderState();
        BogeyRenderers.extract(
            bogey.getStyle(),
            bogey.getSize(),
            bogey.bogeyData,
            bogey.wheelAngle.getValue(partial),
            partial,
            mesh,
            true);
        var matrix = pose(bogey, carriage.bogeySpacing, viewYaw, viewPitch, partial);
        // Same native OBJ mesh-origin correction already used by actual world bogey rendering.
        matrix.translate(0, -1.5f - 1 / 128f, 0);
        var anchor = bogey.getAnchorPosition();
        var lightPos =
            BlockPos.containing(anchor == null ? entity.getLightProbePosition(partial) : anchor);
        int light =
            entity.level().isLoaded(lightPos)
                ? LightCoordsUtil.getLightCoords(entity.level(), lightPos)
                : 0;
        state.bogeys.add(new State.Bogey(mesh, new Matrix4f(matrix), light));
      }
      bogey.updateCouplingAnchor(
          position, viewPitch, viewYaw, carriage.bogeySpacing, partial, bogey.isLeading);
      if (!carriage.isOnTwoBogeys())
        bogey.updateCouplingAnchor(
            position, viewPitch, viewYaw, carriage.bogeySpacing, partial, !bogey.isLeading);
    }
    state.visible = true;
  }

  private static float radians(float degrees) {
    return (float) Math.toRadians(degrees);
  }

  static Matrix4f pose(
      CarriageBogey bogey, int spacing, float viewYaw, float viewPitch, float partial) {
    boolean down = bogey.isUpsideDown(), frontDown = bogey.carriage.leadingBogey().isUpsideDown();
    return new Matrix4f()
        .rotateY(radians(viewYaw + 90))
        .rotateX(radians(-viewPitch))
        .rotateY((float) Math.PI)
        .translate(0, 0, bogey.isLeading ? 0 : -spacing)
        .rotateY(-(float) Math.PI)
        .rotateX(radians(viewPitch))
        .rotateY(radians(-viewYaw - 90))
        .rotateY(radians(bogey.yaw.getValue(partial)))
        .rotateX(radians(bogey.pitch.getValue(partial)))
        .translate(0, .5f, 0)
        .rotateZ(down ? (float) Math.PI : 0)
        .translate(0, down != frontDown ? 2 : 0, 0);
  }

  @Override
  public void submit(
      State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
    if (!state.visible) return;
    body.submit(state.body, pose, collector, camera);
    for (var bogey : state.bogeys) {
      pose.pushPose();
      pose.mulPose(bogey.pose());
      bogey
          .mesh()
          .submit(pose, collector, bogey.light(), OverlayTexture.NO_OVERLAY, state.outlineColor);
      pose.popPose();
    }
  }
}
