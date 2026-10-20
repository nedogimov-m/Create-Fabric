package com.simibubi.create.content.contraptions.actors.trainControls;

import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Source key deltas/keepalive, bound to the accepted body UUID and current real input session. */
public record ControlsInputPacket(
    int entityId,
    UUID body,
    BlockPos controls,
    UUID session,
    long sequence,
    List<Integer> buttons,
    boolean press,
    boolean stop)
    implements CustomPacketPayload {
  public static final Type<ControlsInputPacket> TYPE =
      new Type<>(Create.asResource("controls_input"));
  public static final StreamCodec<RegistryFriendlyByteBuf, ControlsInputPacket> CODEC =
      StreamCodec.of(
          (buf, packet) -> {
            buf.writeVarInt(packet.entityId());
            buf.writeUUID(packet.body());
            buf.writeBlockPos(packet.controls());
            buf.writeUUID(packet.session());
            buf.writeVarLong(packet.sequence());
            buf.writeVarInt(packet.buttons().size());
            for (int button : packet.buttons()) buf.writeByte(button);
            buf.writeBoolean(packet.press());
            buf.writeBoolean(packet.stop());
          },
          buf -> {
            int entity = buf.readVarInt();
            var body = buf.readUUID();
            var pos = buf.readBlockPos();
            var session = buf.readUUID();
            long sequence = buf.readVarLong();
            int count = buf.readVarInt();
            if (count < 0 || count > 6)
              throw new io.netty.handler.codec.DecoderException("Invalid train key count");
            var keys = new ArrayList<Integer>();
            for (int i = 0; i < count; i++) keys.add((int) buf.readUnsignedByte());
            return new ControlsInputPacket(
                entity, body, pos, session, sequence, keys, buf.readBoolean(), buf.readBoolean());
          });

  public ControlsInputPacket {
    Objects.requireNonNull(body);
    controls = controls.immutable();
    Objects.requireNonNull(session);
    buttons = List.copyOf(buttons);
    if (entityId < 0
        || sequence < 0
        || buttons.size() > 6
        || new HashSet<>(buttons).size() != buttons.size()
        || buttons.stream().anyMatch(key -> key < 0 || key > 5))
      throw new IllegalArgumentException("Invalid native train input");
  }

  @Override
  public Type<ControlsInputPacket> type() {
    return TYPE;
  }
}
