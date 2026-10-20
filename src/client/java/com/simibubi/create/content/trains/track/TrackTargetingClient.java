package com.simibubi.create.content.trains.track;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.*;

/**
 * Native curve item input uses the actual shared primary-curve picker; server retraces every hit.
 */
public final class TrackTargetingClient {
  public static void register() {
    UseItemCallback.EVENT.register(
        (player, level, hand) -> {
          if (!level.isClientSide()
              || hand != InteractionHand.MAIN_HAND
              || player.isSpectator()
              || !player.mayBuild()
              || !(player.getItemInHand(hand).getItem() instanceof TrackTargetingBlockItem))
            return InteractionResult.PASS;
          var client = Minecraft.getInstance();
          var hit = TrackBlockOutline.result();
          if (client.level != level
              || client.player != player
              || client.gui.screen() != null
              || hit == null
              || !(LoadedLevelAccess.blockEntity(level, hit.owner())
                  instanceof TrackBlockEntity owner)
              || !owner.getConnections().containsKey(hit.target())) return InteractionResult.PASS;
          ClientPlayNetworking.send(
              new CurvedTrackTargetingPacket(hit.owner(), hit.target(), hit.segment(), hand));
          return InteractionResult.SUCCESS;
        });
  }

  private TrackTargetingClient() {}
}
