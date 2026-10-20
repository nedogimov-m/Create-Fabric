package com.simibubi.create.content.trains.graph.sync;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.RailwayGraphManager;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Native server graph lifecycle transport. Complete mutation batches publish after manager flush.
 */
public final class RailwayGraphSync {
  private static final Map<MinecraftServer, Publisher> SERVERS =
      Collections.synchronizedMap(new WeakHashMap<>());

  public static final class Publisher {
    private record Version(int netId, long revision) {}

    private static final class Peer {
      final UUID nonce;
      long lastSnapshot = Long.MIN_VALUE;

      Peer(UUID nonce) {
        this.nonce = nonce;
      }
    }

    private final UUID epoch = UUID.randomUUID();
    private long sequence;
    private final RailwayGraphManager manager;
    private final Consumer<RailwayGraphManager.Mutation> listener;
    private final Map<UUID, Version> known = new LinkedHashMap<>();
    private final Set<UUID> dirty = new LinkedHashSet<>();
    private final Map<UUID, Long> removed = new HashMap<>();
    private final Map<ServerGamePacketListenerImpl, Peer> peers = new IdentityHashMap<>();

    public Publisher(RailwayGraphManager manager) {
      this.manager = Objects.requireNonNull(manager);
      for (var graph : manager.getTrackNetworks().values())
        known.put(graph.id, new Version(graph.getNetId(), graph.revision()));
      listener =
          event -> {
            dirty.add(event.graph());
            if (event.other() != null) dirty.add(event.other());
            if (event.kind() == RailwayGraphManager.Kind.REMOVED)
              removed.merge(event.graph(), event.revision(), Math::max);
          };
      manager.addListener(listener);
    }

    public UUID epoch() {
      return epoch;
    }

    public long sequence() {
      return sequence;
    }

    public GraphSyncFrame snapshot() {
      return new GraphSyncFrame(
          epoch,
          sequence,
          true,
          manager.getTrackNetworks().values().stream().map(GraphSyncEntry::capture).toList(),
          List.of());
    }

    /** Produces real changed/removed state, preserving graph UUID/net IDs and mutation order. */
    private Optional<GraphSyncFrame> planChanges() {
      var changed = new ArrayList<GraphSyncEntry>();
      var deletions = new ArrayList<GraphSyncFrame.Removed>();
      for (var graph : manager.getTrackNetworks().values()) {
        var prior = known.get(graph.id);
        if (prior == null
            || prior.netId != graph.getNetId()
            || prior.revision != graph.revision()
            || dirty.contains(graph.id)) changed.add(GraphSyncEntry.capture(graph));
      }
      for (var entry : known.entrySet())
        if (!manager.getTrackNetworks().containsKey(entry.getKey()))
          deletions.add(
              new GraphSyncFrame.Removed(
                  entry.getKey(),
                  entry.getValue().netId,
                  Math.max(entry.getValue().revision, removed.getOrDefault(entry.getKey(), 0L))));
      if (changed.isEmpty() && deletions.isEmpty()) {
        dirty.clear();
        removed.clear();
        return Optional.empty();
      }
      return Optional.of(
          new GraphSyncFrame(epoch, Math.incrementExact(sequence), false, changed, deletions));
    }

    public record Transmission(GraphSyncFrame frame, byte[] bytes) {
      public Transmission {
        bytes = bytes.clone();
      }

      @Override
      public byte[] bytes() {
        return bytes.clone();
      }
    }

    /** Freeze full registry-aware bytes before acknowledging a source lifecycle batch. */
    public Optional<Transmission> captureChanges(
        net.minecraft.core.HolderLookup.Provider registries) {
      var planned = planChanges();
      if (planned.isEmpty()) return Optional.empty();
      var frame = planned.get();
      var bytes = frame.encode(registries);
      known.clear();
      for (var graph : manager.getTrackNetworks().values())
        known.put(graph.id, new Version(graph.getNetId(), graph.revision()));
      sequence = frame.sequence();
      dirty.clear();
      removed.clear();
      return Optional.of(new Transmission(frame, bytes));
    }

    public void close() {
      manager.removeListener(listener);
      peers.clear();
    }

    private void request(ServerPlayer player, UUID nonce, long tick) {
      if (!ServerPlayNetworking.canSend(player, GraphSyncPart.TYPE)) return;
      var peer = peers.get(player.connection);
      if (peer != null && !peer.nonce.equals(nonce)) return;
      if (peer == null) {
        peer = new Peer(nonce);
        peers.put(player.connection, peer);
      }
      if (peer.lastSnapshot != Long.MIN_VALUE && tick - peer.lastSnapshot < 20) return;
      peer.lastSnapshot = tick;
      var frame = snapshot();
      send(player, peer.nonce, frame, frame.encode(player.level().registryAccess()));
    }

    private void tick(MinecraftServer server) {
      var transmission = captureChanges(server.registryAccess());
      if (transmission.isEmpty() || peers.isEmpty()) return;
      var frame = transmission.get().frame();
      var bytes = transmission.get().bytes;
      for (var handler : List.copyOf(peers.keySet())) {
        var peer = peers.get(handler);
        if (peer == null) continue;
        var player = handler.player;
        if (player.connection != handler || player.hasDisconnected()) {
          peers.remove(handler);
          continue;
        }
        send(player, peer.nonce, frame, bytes);
      }
    }
  }

  private static Publisher get(MinecraftServer server) {
    return SERVERS.computeIfAbsent(server, key -> new Publisher(RailwayGraphManager.get(key)));
  }

  private static void send(ServerPlayer player, UUID nonce, GraphSyncFrame frame, byte[] bytes) {
    for (var part : GraphSyncPart.split(nonce, frame, bytes))
      ServerPlayNetworking.send(player, part);
  }

  public static void register() {
    PayloadTypeRegistry.clientboundPlay().register(GraphSyncPart.TYPE, GraphSyncPart.CODEC);
    PayloadTypeRegistry.serverboundPlay().register(GraphSyncRequest.TYPE, GraphSyncRequest.CODEC);
    ServerPlayNetworking.registerGlobalReceiver(
        GraphSyncRequest.TYPE,
        (packet, context) ->
            get(context.player().level().getServer())
                .request(context.player(), packet.nonce(), context.player().level().getGameTime()));
    ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> get(server));
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> {
          var publisher = SERVERS.get(server);
          if (publisher != null) publisher.peers.remove(handler);
        });
    // Register after RailwayGraphManager.register(): its real point/mutation batch finishes first.
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          try {
            get(server).tick(server);
          } catch (RuntimeException failure) {
            Create.LOGGER.error("Failed to publish native railway graph update", failure);
          }
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> {
          var publisher = SERVERS.remove(server);
          if (publisher != null) publisher.close();
        });
  }

  private RailwayGraphSync() {}
}
