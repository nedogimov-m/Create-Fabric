package com.simibubi.create.content.contraptions.actors.trainControls;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public final class ControlsInteractionBehaviour extends MovingInteractionBehaviour {
  @Override
  public boolean handlePlayerInteraction(
      Player player, InteractionHand hand, BlockPos local, AbstractContraptionEntity owner) {
    if (hand != InteractionHand.MAIN_HAND
        || player.getItemInHand(hand).is(AllItems.WRENCH)
        || !(owner instanceof CarriageContraptionEntity front)) return false;
    if (player.level().isClientSide()) return front.getCarriage() != null;
    return player instanceof ServerPlayer actual
        && ControlsServerHandler.toggle(actual, front, local);
  }
}
