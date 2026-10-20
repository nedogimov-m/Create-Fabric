package com.simibubi.create.content.trains;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsServerHandler;
import com.simibubi.create.content.trains.entity.Train;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Genuine train HUD values and bounded throttle requests through the actual current driver. */
public final class TrainHUDUpdatePacket {
  public record Clientbound(UUID trainId, double throttle, double speed, int fuelTicks)
      implements CustomPacketPayload {
    public static final Type<Clientbound> TYPE = new Type<>(Create.asResource("train_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Clientbound> CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            Clientbound::trainId,
            ByteBufCodecs.DOUBLE,
            Clientbound::throttle,
            ByteBufCodecs.DOUBLE,
            Clientbound::speed,
            ByteBufCodecs.VAR_INT,
            Clientbound::fuelTicks,
            Clientbound::new);

    public Clientbound(Train train) {
      this(
          train.id,
          train.throttle,
          train.speedBeforeStall == null ? train.speed : train.speedBeforeStall,
          train.fuelTicks);
    }

    public Clientbound {
      validateThrottle(throttle);
      if (!Double.isFinite(speed) || fuelTicks < 0)
        throw new IllegalArgumentException("Invalid native train HUD state");
    }

    @Override
    public Type<Clientbound> type() {
      return TYPE;
    }
  }

  public record Serverbound(UUID trainId, double throttle) implements CustomPacketPayload {
    public static final Type<Serverbound> TYPE = new Type<>(Create.asResource("train_throttle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Serverbound> CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            Serverbound::trainId,
            ByteBufCodecs.DOUBLE,
            Serverbound::throttle,
            Serverbound::new);

    public Serverbound(Train train, double throttle) {
      this(train.id, throttle);
    }

    public Serverbound {
      validateThrottle(throttle);
    }

    public void apply(ServerPlayer player) {
      var train = ControlsServerHandler.controlledTrain(player, trainId);
      if (train == null) return;
      train.throttle = throttle;
      train.manager().markDirty();
      net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
          player, new Clientbound(train));
    }

    @Override
    public Type<Serverbound> type() {
      return TYPE;
    }
  }

  private static void validateThrottle(double throttle) {
    if (!Double.isFinite(throttle) || throttle < 0 || throttle > 1)
      throw new IllegalArgumentException("Invalid native train throttle");
  }

  private TrainHUDUpdatePacket() {}
}
