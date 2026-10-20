package com.simibubi.create.content.trains.signal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.Level;

/** Source client group metadata, scoped to the actual current native connection. */
public final class SignalGroupClient {
  private static final Map<UUID, SignalEdgeGroup> GROUPS = new LinkedHashMap<>();
  private static final Map<UUID, SignalEdgeGroup> VIEW = Collections.unmodifiableMap(GROUPS);
  private static ClientPacketListener connection;

  private static void begin(ClientPacketListener handler) {
    GROUPS.clear();
    connection = handler;
  }

  public static Map<UUID, SignalEdgeGroup> groups(Level level) {
    var client = Minecraft.getInstance();
    return client.level == level && client.getConnection() == connection && connection != null
        ? VIEW : Map.of();
  }

  private static void receive(SignalEdgeGroupPacket packet) {
    for (int i = 0; i < packet.ids().size(); i++) {
      var id = packet.ids().get(i);
      if (!packet.add()) {
        GROUPS.remove(id);
        continue;
      }
      var group = new SignalEdgeGroup(id);
      if (i < packet.colors().size()) group.color = packet.colors().get(i);
      GROUPS.put(id, group);
    }
  }

  public static void register() {
    ClientPlayConnectionEvents.INIT.register((handler, client) -> begin(handler));
    ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
      if (connection != handler) begin(handler);
    });
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
      if (connection == handler) {
        GROUPS.clear();
        connection = null;
      }
    });
    ClientPlayNetworking.registerGlobalReceiver(SignalEdgeGroupPacket.TYPE, (packet, context) -> {
      var client = context.client();
      if (connection != null && client.getConnection() == connection
          && context.player() == client.player) receive(packet);
    });
  }

  private SignalGroupClient() {}
}
