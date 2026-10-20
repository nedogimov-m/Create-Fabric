package com.simibubi.create.content.contraptions.actors.trainControls;

import com.simibubi.create.Create;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** An acknowledgement of actual server controls ownership; never predicts a successful session. */
public record ControlsSessionPacket(
    int entityId, UUID body, BlockPos controls, UUID session, boolean active)
    implements CustomPacketPayload {
  public static final Type<ControlsSessionPacket> TYPE =
      new Type<>(Create.asResource("controls_session"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ControlsSessionPacket> CODEC =
      StreamCodec.of(
          (buf, packet) -> {
            buf.writeVarInt(packet.entityId());
            buf.writeUUID(packet.body());
            buf.writeBlockPos(packet.controls());
            buf.writeUUID(packet.session());
            buf.writeBoolean(packet.active());
          },
          buf ->
              new ControlsSessionPacket(
                  buf.readVarInt(),
                  buf.readUUID(),
                  buf.readBlockPos(),
                  buf.readUUID(),
                  buf.readBoolean()));

  public ControlsSessionPacket {
    controls = controls.immutable();
  }

  @Override
  public Type<ControlsSessionPacket> type() {
    return TYPE;
  }
}
