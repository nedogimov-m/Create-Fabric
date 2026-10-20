package com.simibubi.create.content.trains.entity;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsRenderer;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/** Test-only reads of actual extracted native state; no production observation setters. */
public final class CarriageNativeRenderProbe {
  public record Snapshot(Object bogeyMesh, Object coverMesh, float wheelAngle, Matrix4f lever) {}

  public static Snapshot inspect(Minecraft client, UUID body, BlockPos controls) {
    Entity actual = null;
    for (var candidate : client.level.entitiesForRendering())
      if (candidate.getUUID().equals(body)) actual = candidate;
    if (!(actual instanceof CarriageContraptionEntity front)
        || front.getCarriage() == null
        || !front.validForRender
        || front.firstPositionUpdate)
      throw new AssertionError("Actual native carriage frontend is not render-ready");
    var renderer = client.getEntityRenderDispatcher().getRenderer(front);
    if (!(renderer instanceof CarriageContraptionEntityRenderer carriageRenderer))
      throw new AssertionError("Actual carriage has no native Vulkan entity renderer");
    var state = carriageRenderer.createRenderState(front, .5f);
    if (!state.visible || state.bogeys.size() != 2 || state.body.blocks.isEmpty())
      throw new AssertionError("Native carriage body/two actual bogey frames are absent");
    for (var bogey : state.bogeys)
      if (bogey.mesh().parts.isEmpty()
          || bogey.mesh().parts.stream().anyMatch(part -> part.model().mesh.size() == 0))
        throw new AssertionError("Actual source bogey contains empty detached native mesh");
    var actor = state.body.actors.stream().filter(part -> part.pos().equals(controls)).findFirst();
    if (actor.isEmpty()
        || !(actor.get().state() instanceof ControlsRenderer.State extracted)
        || extracted.body().isEmpty()
        || extracted.cover().mesh.size() == 0
        || extracted.lever().mesh.size() == 0)
      throw new AssertionError("Actual open controls body/cover/two levers are missing");
    if (state.body.blocks.stream().anyMatch(part -> part.pos().equals(controls)))
      throw new AssertionError("Actual controls were drawn twice as block and source actor");
    return new Snapshot(
        state.bogeys.getFirst().mesh().parts.getFirst().model().mesh,
        extracted.cover().mesh,
        state.bogeys.getFirst().mesh().wheelAngle,
        new Matrix4f(extracted.firstPose()));
  }

  private CarriageNativeRenderProbe() {}
}
