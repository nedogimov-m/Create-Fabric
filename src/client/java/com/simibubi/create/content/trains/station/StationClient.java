package com.simibubi.create.content.trains.station;

import com.simibubi.create.content.trains.track.TrackTargetingClient;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;

/**
 * Native screen opens only after the actual owner BE identity/revision has reached this connection.
 */
public final class StationClient {
  private static StationNetworking.State pending;
  private static int lifetime;

  public static void register() {
    TrackTargetingClient.register();
    StationModels.register();
    net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE
        .register(
            (client, level) -> {
              pending = null;
              StationAssemblyAreas.clearClient();
            });
    ClientPlayNetworking.registerGlobalReceiver(
        StationNetworking.State.TYPE,
        (packet, context) -> {
          if (context.player() != context.client().player
              || context.client().level == null
              || !context.client().level.dimension().equals(packet.owner().dimension())) return;
          pending = packet;
          lifetime = 100;
        });
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (pending == null) return;
          if (--lifetime < 0
              || client.level == null
              || client.player == null
              || !client.level.dimension().equals(pending.owner().dimension())) {
            pending = null;
            return;
          }
          var pos = pending.owner().pos();
          if (!LoadedLevelAccess.isLoaded(client.level, pos)
              || !(LoadedLevelAccess.blockEntity(client.level, pos)
                  instanceof StationBlockEntity station)
              || !station.targeting().id().equals(pending.point())
              || station.configurationRevision() < pending.revision()
              || !client.player.isWithinBlockInteractionRange(pos, 1)) return;
          var ready = pending;
          pending = null;
          if (client.gui.screen() instanceof StationScreen screen
              && screen.position().equals(pos)
              && screen.pointId().equals(ready.point())) screen.apply(ready);
          else client.gui.setScreen(new StationScreen(ready));
        });
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          pending = null;
          StationAssemblyAreas.clearClient();
        });
  }

  private StationClient() {}
}
