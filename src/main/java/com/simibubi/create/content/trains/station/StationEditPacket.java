package com.simibubi.create.content.trains.station;

import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Native station configuration and actual server-authorized Train assembly/disassembly.
 */
public record StationEditPacket(
    BlockPos pos,
    UUID point,
    long expectedRevision,
    Action action,
    String name,
    boolean assemblyMode,
    DoorControl doors)
    implements CustomPacketPayload {
  public enum Action {
    CONFIGURE,
    PREFLIGHT,
    ASSEMBLE,
    DISASSEMBLE,
    REFRESH
  }

  public static final Type<StationEditPacket> TYPE =
      new Type<>(Create.asResource("configure_station"));
  public static final StreamCodec<RegistryFriendlyByteBuf, StationEditPacket> CODEC =
      CustomPacketPayload.codec(
          (packet, buf) -> {
            buf.writeBlockPos(packet.pos);
            buf.writeUUID(packet.point);
            buf.writeVarLong(packet.expectedRevision);
            buf.writeEnum(packet.action);
            buf.writeUtf(packet.name, 256);
            buf.writeBoolean(packet.assemblyMode);
            buf.writeVarInt(packet.doors.ordinal());
          },
          buf ->
              new StationEditPacket(
                  buf.readBlockPos(),
                  buf.readUUID(),
                  buf.readVarLong(),
                  buf.readEnum(Action.class),
                  buf.readUtf(256),
                  buf.readBoolean(),
                  DoorControl.byId(buf.readVarInt())));

  public StationEditPacket {
    pos = pos.immutable();
    Objects.requireNonNull(point);
    Objects.requireNonNull(action);
    Objects.requireNonNull(name);
    Objects.requireNonNull(doors);
    if (expectedRevision < 0 || name.length() > 256)
      throw new IllegalArgumentException("Invalid station configuration revision/name");
  }

  @Override
  public Type<StationEditPacket> type() {
    return TYPE;
  }
}
