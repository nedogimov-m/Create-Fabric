package com.simibubi.create.content.trains.graph.sync;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.Level;

/**
 * Real native graph connection/cache. Incomplete or rejected frames never publish partial topology.
 */
public final class RailwayGraphClient {
  private record Incoming(GraphSyncPart header, GraphSyncPart.Assembly assembly, long expires) {}

  private static final RailwayGraphCache CACHE = new RailwayGraphCache();
  private static final Map<UUID, Incoming> incoming = new LinkedHashMap<>();
  private static ClientPacketListener connection;
  private static UUID nonce;
  private static long ticks, lastRequest = Long.MIN_VALUE, snapshotPublications;
  private static int reserved;

  public static RailwayGraphCache.View view() {
    return CACHE.view();
  }

  public static long completedSnapshots() {
    return snapshotPublications;
  }

  private static void begin(ClientPacketListener handler) {
    connection = handler;
    nonce = UUID.randomUUID();
    incoming.clear();
    reserved = 0;
    ticks = 0;
    lastRequest = Long.MIN_VALUE;
    snapshotPublications = 0;
    CACHE.begin(nonce);
  }

  private static void disconnected(ClientPacketListener handler) {
    if (connection != handler) return;
    connection = null;
    nonce = null;
    incoming.clear();
    reserved = 0;
    lastRequest = Long.MIN_VALUE;
    snapshotPublications = 0;
    CACHE.disconnect();
  }

  public static void requestSnapshot() {
    if (connection == null || nonce == null || !ClientPlayNetworking.canSend(GraphSyncRequest.TYPE))
      return;
    if (lastRequest != Long.MIN_VALUE && ticks - lastRequest < 20) return;
    lastRequest = ticks;
    ClientPlayNetworking.send(new GraphSyncRequest(nonce));
  }

  private static void remove(UUID transfer) {
    var removed = incoming.remove(transfer);
    if (removed != null) reserved -= removed.assembly.reservedBytes();
  }

  private static void receive(GraphSyncPart packet, Minecraft client) {
    if (connection == null
        || client.getConnection() != connection
        || nonce == null
        || !nonce.equals(packet.nonce())) return;
    var view = CACHE.view();
    if (packet.sequence() < view.sequence()
        || !packet.snapshot() && packet.sequence() == view.sequence()) return;
    try {
      var transfer = incoming.get(packet.transfer());
      if (transfer == null) {
        if (incoming.size() >= 4 || packet.length() > 128 * 1024 * 1024 - reserved)
          throw new IllegalArgumentException("Native graph assembly memory budget exceeded");
        transfer = new Incoming(packet, new GraphSyncPart.Assembly(packet), ticks + 400);
        incoming.put(packet.transfer(), transfer);
        reserved += packet.length();
      }
      var complete = transfer.assembly.accept(packet);
      if (complete.isEmpty()) return;
      remove(packet.transfer());
      var frame = GraphSyncFrame.decode(complete.get(), connection.registryAccess());
      if (!frame.epoch().equals(packet.epoch())
          || frame.sequence() != packet.sequence()
          || frame.snapshot() != packet.snapshot())
        throw new IllegalArgumentException(
            "Native graph fragment header disagrees with full frame");
      var outcome = CACHE.accept(nonce, frame, connection.registryAccess());
      if (outcome == RailwayGraphCache.Outcome.APPLIED) {
        if (frame.snapshot()) snapshotPublications++;
        for (var entry : List.copyOf(incoming.entrySet()))
          if (entry.getValue().header.sequence() <= frame.sequence()) remove(entry.getKey());
      } else if (outcome == RailwayGraphCache.Outcome.NEEDS_SNAPSHOT) requestSnapshot();
      else if (outcome == RailwayGraphCache.Outcome.REJECTED)
        Create.LOGGER.error("{}", CACHE.view().problem());
    } catch (RuntimeException invalid) {
      remove(packet.transfer());
      CACHE.failed("Native graph frame rejected: " + invalid.getMessage(), false);
      Create.LOGGER.error(
          "Native rail graph frame failed; existing complete topology retained", invalid);
    }
  }

  /** Completed native topology for the current connection and exact client level only. */
  public static Collection<TrackGraph> graphs(Level level) {
    var client = Minecraft.getInstance();
    var view = CACHE.view();
    return view.ready() && client.level == level && client.getConnection() == connection
        ? view.graphs().values()
        : List.of();
  }

  public static void register() {
    ClientPlayConnectionEvents.INIT.register((handler, client) -> begin(handler));
    ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          if (connection != handler) begin(handler);
          requestSnapshot();
        });
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnected(handler));
    ClientPlayNetworking.registerGlobalReceiver(
        GraphSyncPart.TYPE,
        (packet, context) -> {
          if (context.player() == context.client().player) receive(packet, context.client());
        });
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          ticks++;
          boolean expired = false;
          for (var entry : List.copyOf(incoming.entrySet()))
            if (entry.getValue().expires < ticks) {
              remove(entry.getKey());
              expired = true;
            }
          if (expired)
            CACHE.failed("Native graph transfer expired before complete publication", true);
          if (connection != null && !CACHE.view().ready() && CACHE.view().retryable())
            requestSnapshot();
        });
    TrackGraphHelper.setClientGraphLookup(RailwayGraphClient::graphs);
  }

  private RailwayGraphClient() {}
}
