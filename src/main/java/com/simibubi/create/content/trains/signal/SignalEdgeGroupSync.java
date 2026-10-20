package com.simibubi.create.content.trains.signal;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.TrainManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Publishes the actual TrainManager groups; the remembered colors only suppress duplicate packets. */
public final class SignalEdgeGroupSync {
  private static final int BATCH_SIZE = 4096;
  private static final Map<MinecraftServer, Publisher> SERVERS =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static final class Publisher {
    private final TrainManager manager;
    private Map<UUID, EdgeGroupColor> known;

    Publisher(MinecraftServer server) {
      manager = TrainManager.get(server);
      known = capture();
    }

    private Map<UUID, EdgeGroupColor> capture() {
      var current = new LinkedHashMap<UUID, EdgeGroupColor>();
      manager.signalEdgeGroups().forEach((id, group) -> current.put(id, group.color));
      return current;
    }

    void sendAll(ServerPlayer player) {
      if (player.level().getServer() != manager.server())
        throw new IllegalArgumentException("Foreign native signal group connection");
      for (var packet : additions(capture())) send(player, packet);
    }

    void tick() {
      var current = capture();
      var added = new LinkedHashMap<UUID, EdgeGroupColor>();
      current.forEach((id, color) -> {
        if (known.get(id) != color) added.put(id, color);
      });
      var removed = new ArrayList<UUID>();
      for (var id : known.keySet()) if (!current.containsKey(id)) removed.add(id);
      var packets = new ArrayList<>(additions(added));
      for (int start = 0; start < removed.size(); start += BATCH_SIZE)
        packets.add(new SignalEdgeGroupPacket(
            removed.subList(start, Math.min(start + BATCH_SIZE, removed.size())), List.of(), false));
      for (var player : manager.server().getPlayerList().getPlayers())
        for (var packet : packets) send(player, packet);
      known = current;
    }
  }

  private static List<SignalEdgeGroupPacket> additions(Map<UUID, EdgeGroupColor> entries) {
    var packets = new ArrayList<SignalEdgeGroupPacket>();
    var ids = new ArrayList<UUID>(BATCH_SIZE);
    var colors = new ArrayList<EdgeGroupColor>(BATCH_SIZE);
    for (var entry : entries.entrySet()) {
      ids.add(entry.getKey());
      colors.add(entry.getValue());
      if (ids.size() == BATCH_SIZE) {
        packets.add(new SignalEdgeGroupPacket(ids, colors, true));
        ids.clear();
        colors.clear();
      }
    }
    if (!ids.isEmpty()) packets.add(new SignalEdgeGroupPacket(ids, colors, true));
    return packets;
  }

  private static void send(ServerPlayer player, SignalEdgeGroupPacket packet) {
    if (ServerPlayNetworking.canSend(player, SignalEdgeGroupPacket.TYPE))
      ServerPlayNetworking.send(player, packet);
  }

  public static void register() {
    PayloadTypeRegistry.clientboundPlay().register(SignalEdgeGroupPacket.TYPE, SignalEdgeGroupPacket.CODEC);
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
        SERVERS.computeIfAbsent(server, Publisher::new).sendAll(handler.player));
    // Create registers this after the real graph/traffic tick and its topology transport.
    ServerTickEvents.END_SERVER_TICK.register(server -> {
      try {
        SERVERS.computeIfAbsent(server, Publisher::new).tick();
      } catch (RuntimeException failure) {
        Create.LOGGER.error("Failed to publish actual signal group colors", failure);
      }
    });
    ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
  }

  private SignalEdgeGroupSync() {}
}
