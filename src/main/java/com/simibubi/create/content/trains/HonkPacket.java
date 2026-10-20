package com.simibubi.create.content.trains;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsServerHandler;
import com.simibubi.create.content.trains.entity.Train;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;

public final class HonkPacket {
  public record Clientbound(UUID trainId, boolean honk) implements CustomPacketPayload {
    public static final Type<Clientbound> TYPE = new Type<>(Create.asResource("train_honk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Clientbound> CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            Clientbound::trainId,
            ByteBufCodecs.BOOL,
            Clientbound::honk,
            Clientbound::new);

    public Clientbound(Train train, boolean honk) {
      this(train.id, honk);
    }

    @Override
    public Type<Clientbound> type() {
      return TYPE;
    }
  }

  public record Serverbound(UUID trainId, boolean honk) implements CustomPacketPayload {
    public static final Type<Serverbound> TYPE = new Type<>(Create.asResource("train_honk_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Serverbound> CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            Serverbound::trainId,
            ByteBufCodecs.BOOL,
            Serverbound::honk,
            Serverbound::new);

    public Serverbound(Train train, boolean honk) {
      this(train.id, honk);
    }

    public void apply(ServerPlayer player) {
      var train = ControlsServerHandler.controlledTrain(player, trainId);
      if (train == null) return;
      train.determineHonk(player.level());
      if (honk && train.lowHonk == null) return;
      if (honk) TrainAdvancements.TRAIN_WHISTLE.awardTo(player);
      for (var watcher : PlayerLookup.all(player.level().getServer()))
        ServerPlayNetworking.send(watcher, new Clientbound(train, honk));
      if (honk) {
        var owner = train.carriages.getFirst().anyAvailableEntity();
        player
            .level()
            .gameEvent(owner == null ? player : owner, GameEvent.RESONATE_15, player.position());
      }
    }

    @Override
    public Type<Serverbound> type() {
      return TYPE;
    }
  }

  private HonkPacket() {}
}
