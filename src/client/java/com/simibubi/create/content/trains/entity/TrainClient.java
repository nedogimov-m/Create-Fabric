package com.simibubi.create.content.trains.entity;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Native connection-bound publication of actual received public Train descriptions. */
public final class TrainClient {
  private static ClientPacketListener connection;

  public static void register() {
    TrainRelocatorClient.register();
    TrainClientRegistry.installEffects(CarriageClientEffects::forEntity);
    net.minecraft.client.renderer.entity.EntityRenderers.register(
        com.simibubi.create.AllEntityTypes.CARRIAGE_CONTRAPTION,
        CarriageContraptionEntityRenderer::new);
    com.simibubi.create.content.contraptions.render.ActorRenderer.REGISTRY.register(
        com.simibubi.create.AllBlocks.TRAIN_CONTROLS,
        new com.simibubi.create.content.contraptions.actors.trainControls.ControlsRenderer());
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
        com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler::tick);
    net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
        net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
        Create.asResource("train_hud"),
        com.simibubi.create.content.trains.TrainHUD::render);
    ClientPlayConnectionEvents.INIT.register(
        (handler, client) -> {
          connection = handler;
          TrainClientRegistry.begin(handler);
        });
    ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          if (connection != handler) {
            connection = handler;
            TrainClientRegistry.begin(handler);
          }
        });
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          if (connection != handler) return;
          com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler.reset(
              false);
          CarriageClientEffects.clearAll();
          TrainClientRegistry.end(handler);
          if (connection == handler) connection = null;
        });
    ClientPlayNetworking.registerGlobalReceiver(
        com.simibubi.create.content.contraptions.actors.trainControls.ControlsSessionPacket.TYPE,
        (packet, context) -> {
          if (connection != null && context.client().getConnection() == connection)
            com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler.accept(
                packet, context.client());
        });
    ClientPlayNetworking.registerGlobalReceiver(
        com.simibubi.create.content.trains.TrainHUDUpdatePacket.Clientbound.TYPE,
        (packet, context) -> {
          if (connection == null || context.client().getConnection() != connection) return;
          var train = TrainClientRegistry.get(context.client().level, packet.trainId());
          if (train != null) {
            train.throttle = packet.throttle();
            train.speed = packet.speed();
            train.fuelTicks = packet.fuelTicks();
          }
        });
    ClientPlayNetworking.registerGlobalReceiver(
        TrainPromptPacket.TYPE,
        (packet, context) -> {
          if (connection == null
              || context.client().getConnection() != connection
              || !com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler
                  .active()) return;
          com.simibubi.create.content.trains.TrainHUD.currentPrompt = packet.text();
          com.simibubi.create.content.trains.TrainHUD.currentPromptShadow = packet.shadow();
          com.simibubi.create.content.trains.TrainHUD.promptKeepAlive = 30;
        });
    ClientPlayNetworking.registerGlobalReceiver(
        com.simibubi.create.content.trains.HonkPacket.Clientbound.TYPE,
        (packet, context) -> {
          if (connection == null || context.client().getConnection() != connection) return;
          var train = TrainClientRegistry.get(context.client().level, packet.trainId());
          if (train != null)
            train.honkTicks =
                packet.honk() ? train.honkTicks == 0 ? 20 : 13 : train.honkTicks > 5 ? 6 : 0;
        });
    ClientPlayNetworking.registerGlobalReceiver(
        TrainSyncPacket.TYPE,
        (packet, context) -> {
          if (connection == null
              || context.client().getConnection() != connection
              || context.player() != context.client().player) return;
          try {
            TrainClientRegistry.accept(connection, packet);
          } catch (RuntimeException malformed) {
            Create.LOGGER.error(
                "Rejected actual native Train descriptions; previous complete cache retained",
                malformed);
          }
        });
  }

  private TrainClient() {}
}
