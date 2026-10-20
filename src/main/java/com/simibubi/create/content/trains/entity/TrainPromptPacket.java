package com.simibubi.create.content.trains.entity;

import com.simibubi.create.Create;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record TrainPromptPacket(Component text, boolean shadow) implements CustomPacketPayload {
  public static final Type<TrainPromptPacket> TYPE = new Type<>(Create.asResource("train_prompt"));
  public static final StreamCodec<RegistryFriendlyByteBuf, TrainPromptPacket> CODEC =
      StreamCodec.composite(
          ComponentSerialization.STREAM_CODEC,
          TrainPromptPacket::text,
          ByteBufCodecs.BOOL,
          TrainPromptPacket::shadow,
          TrainPromptPacket::new);

  @Override
  public Type<TrainPromptPacket> type() {
    return TYPE;
  }
}
