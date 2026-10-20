package com.simibubi.create.content.contraptions.actors.trainControls;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Real source actor stop releases its actual controls session before native body retirement. */
public final class ControlsMovementBehaviour implements MovementBehaviour {
  public static final class LeverAngles {
    public final net.createmod.catnip.animation.LerpedFloat steering =
        net.createmod.catnip.animation.LerpedFloat.linear();
    public final net.createmod.catnip.animation.LerpedFloat speed =
        net.createmod.catnip.animation.LerpedFloat.linear();
    public final net.createmod.catnip.animation.LerpedFloat equip =
        net.createmod.catnip.animation.LerpedFloat.linear();
  }

  @Override
  public void tick(MovementContext context) {
    if (!context.world.isClientSide()) return;
    if (!(context.temporaryData instanceof LeverAngles)) context.temporaryData = new LeverAngles();
    var angles = (LeverAngles) context.temporaryData;
    angles.steering.tickChaser();
    angles.speed.tickChaser();
    angles.equip.tickChaser();
  }

  @Override
  public @Nullable ItemStack canBeDisabledVia(MovementContext context) {
    return null;
  }

  @Override
  public void stopMoving(MovementContext context) {
    if (!context.world.isClientSide()
        && context.contraption.entity instanceof CarriageContraptionEntity front)
      ControlsServerHandler.stopOwner(front);
  }
}
