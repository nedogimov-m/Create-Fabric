package com.simibubi.create.content.contraptions.actors.trainControls;

import com.simibubi.create.content.trains.*;
import com.simibubi.create.content.trains.entity.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import org.jspecify.annotations.Nullable;

/** Native source input expiry, applied only to an actual accepted server driver session. */
public final class ControlsServerHandler {
  private static final int TIMEOUT = 30;
  private static final Map<ServerLevel, Map<UUID, Session>> SESSIONS = new IdentityHashMap<>();
  private static boolean registered;

  private static final class Session {
    final CarriageContraptionEntity owner;
    final BlockPos controls;
    final UUID id = UUID.randomUUID();
    final Map<Integer, Long> keys = new LinkedHashMap<>();
    long received, sequence = -1;

    Session(CarriageContraptionEntity owner, BlockPos controls, long now) {
      this.owner = owner;
      this.controls = controls.immutable();
      received = now;
    }

    ControlsSessionPacket packet(boolean active) {
      return new ControlsSessionPacket(owner.getId(), owner.getUUID(), controls, id, active);
    }
  }

  public static boolean toggle(
      ServerPlayer player, CarriageContraptionEntity owner, BlockPos controls) {
    var world = player.level();
    var map = SESSIONS.computeIfAbsent(world, ignored -> new LinkedHashMap<>());
    var old = map.get(player.getUUID());
    if (old != null) {
      end(player, map, old);
      if (old.owner == owner && old.controls.equals(controls)) return true;
    }
    if (!owner.isGloballyManaged()
        || owner.level() != world
        || !owner.startControlling(controls, player)) return false;
    var session = new Session(owner, controls, world.getGameTime());
    map.put(player.getUUID(), session);
    ServerPlayNetworking.send(player, session.packet(true));
    return true;
  }

  private static void receive(ServerPlayer player, ControlsInputPacket input) {
    var map = SESSIONS.get(player.level());
    var session = map == null ? null : map.get(player.getUUID());
    if (session == null
        || !session.id.equals(input.session())
        || session.owner.getId() != input.entityId()
        || !session.owner.getUUID().equals(input.body())
        || !session.controls.equals(input.controls())
        || input.sequence() <= session.sequence) return;
    long now = player.level().getGameTime();
    if (now - session.received > TIMEOUT || !session.owner.isControlledBy(player)) {
      end(player, map, session);
      return;
    }
    session.sequence = input.sequence();
    if (input.stop()) {
      end(player, map, session);
      return;
    }
    session.received = now;
    for (int key : input.buttons()) {
      if (input.press()) session.keys.put(key, now + TIMEOUT);
      else session.keys.remove(key);
    }
  }

  private static void tick(ServerLevel world) {
    var map = SESSIONS.get(world);
    if (map == null) return;
    long now = world.getGameTime();
    for (var entry : List.copyOf(map.entrySet())) {
      var session = entry.getValue();
      var player = world.getServer().getPlayerList().getPlayer(entry.getKey());
      if (player == null
          || player.level() != world
          || now - session.received > TIMEOUT
          || !session.owner.isControlledBy(player)) {
        if (player != null) end(player, map, session);
        else {
          session.owner.stopControlling(session.controls, entry.getKey());
          map.remove(entry.getKey(), session);
        }
        continue;
      }
      session.keys.values().removeIf(expiry -> expiry <= now);
      if (!session.owner.control(session.controls, List.copyOf(session.keys.keySet()), player))
        end(player, map, session);
    }
    if (map.isEmpty()) SESSIONS.remove(world);
  }

  private static void end(ServerPlayer player, Map<UUID, Session> map, Session session) {
    session.owner.stopControlling(session.controls, player.getUUID());
    map.remove(player.getUUID(), session);
    ServerPlayNetworking.send(player, session.packet(false));
  }

  public static void stopOwner(CarriageContraptionEntity owner) {
    if (!(owner.level() instanceof ServerLevel world)) return;
    var map = SESSIONS.get(world);
    if (map == null) return;
    for (var entry : List.copyOf(map.entrySet())) {
      if (entry.getValue().owner != owner) continue;
      var player = world.getServer().getPlayerList().getPlayer(entry.getKey());
      if (player != null) end(player, map, entry.getValue());
      else {
        owner.stopControlling(entry.getValue().controls, entry.getKey());
        map.remove(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Throttle and honk share the same actual current driver authority as physical train controls.
   */
  public static @Nullable Train controlledTrain(ServerPlayer player, UUID id) {
    var map = SESSIONS.get(player.level());
    var session = map == null ? null : map.get(player.getUUID());
    if (session == null
        || player.level().getGameTime() - session.received > TIMEOUT
        || !session.owner.isControlledBy(player)
        || session.owner.getCarriage() == null) return null;
    var train = session.owner.getCarriage().train;
    return train.id.equals(id) && !train.isRestoring() && train.manager().get(id) == train
        ? train
        : null;
  }

  public static void register() {
    if (registered) return;
    registered = true;
    PayloadTypeRegistry.serverboundPlay()
        .register(ControlsInputPacket.TYPE, ControlsInputPacket.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(ControlsSessionPacket.TYPE, ControlsSessionPacket.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(TrainHUDUpdatePacket.Clientbound.TYPE, TrainHUDUpdatePacket.Clientbound.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(TrainHUDUpdatePacket.Serverbound.TYPE, TrainHUDUpdatePacket.Serverbound.CODEC);
    PayloadTypeRegistry.clientboundPlay().register(TrainPromptPacket.TYPE, TrainPromptPacket.CODEC);
    PayloadTypeRegistry.clientboundPlay()
        .register(HonkPacket.Clientbound.TYPE, HonkPacket.Clientbound.CODEC);
    PayloadTypeRegistry.serverboundPlay()
        .register(HonkPacket.Serverbound.TYPE, HonkPacket.Serverbound.CODEC);
    ServerPlayNetworking.registerGlobalReceiver(
        ControlsInputPacket.TYPE, (packet, context) -> receive(context.player(), packet));
    ServerPlayNetworking.registerGlobalReceiver(
        TrainHUDUpdatePacket.Serverbound.TYPE, (packet, context) -> packet.apply(context.player()));
    ServerPlayNetworking.registerGlobalReceiver(
        HonkPacket.Serverbound.TYPE, (packet, context) -> packet.apply(context.player()));
    ServerTickEvents.START_LEVEL_TICK.register(ControlsServerHandler::tick);
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, server) -> {
          var player = handler.player;
          var map = SESSIONS.get(player.level());
          var session = map == null ? null : map.remove(player.getUUID());
          if (session != null) session.owner.stopControlling(session.controls, player.getUUID());
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> SESSIONS.keySet().removeIf(world -> world.getServer() == server));
  }

  private ControlsServerHandler() {}
}
