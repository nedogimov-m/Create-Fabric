package com.simibubi.create.content.trains.signal;

import com.simibubi.create.Create;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Source group identities/colors/add-remove payload on the native Fabric play transport. */
public record SignalEdgeGroupPacket(List<UUID> ids, List<EdgeGroupColor> colors, boolean add)
    implements CustomPacketPayload {
  private static final int MAX_WIRE_ENTRIES = 32768;
  public static final Type<SignalEdgeGroupPacket> TYPE =
      new Type<>(Create.asResource("signal_edge_group"));
  public static final StreamCodec<FriendlyByteBuf, SignalEdgeGroupPacket> STREAM_CODEC =
      StreamCodec.of(
          (buffer, packet) -> {
            buffer.writeVarInt(packet.ids.size());
            for (var id : packet.ids) buffer.writeUUID(id);
            buffer.writeVarInt(packet.colors.size());
            for (var color : packet.colors) EdgeGroupColor.STREAM_CODEC.encode(buffer, color);
            buffer.writeBoolean(packet.add);
          },
          buffer -> {
            int size = count(buffer);
            var ids = new ArrayList<UUID>(size);
            for (int i = 0; i < size; i++) ids.add(buffer.readUUID());
            size = count(buffer);
            var colors = new ArrayList<EdgeGroupColor>(size);
            for (int i = 0; i < size; i++) colors.add(EdgeGroupColor.STREAM_CODEC.decode(buffer));
            return new SignalEdgeGroupPacket(ids, colors, buffer.readBoolean());
          });
  public static final StreamCodec<FriendlyByteBuf, SignalEdgeGroupPacket> CODEC = STREAM_CODEC;

  public SignalEdgeGroupPacket {
    ids = List.copyOf(ids);
    colors = List.copyOf(colors);
    if (ids.size() > MAX_WIRE_ENTRIES || colors.size() > MAX_WIRE_ENTRIES)
      throw new IllegalArgumentException("Signal group packet exceeds native payload batch size");
  }

  public SignalEdgeGroupPacket(UUID id, EdgeGroupColor color) {
    this(List.of(id), List.of(color), true);
  }

  private static int count(FriendlyByteBuf buffer) {
    int size = buffer.readVarInt();
    if (size < 0 || size > MAX_WIRE_ENTRIES)
      throw new IllegalArgumentException("Invalid signal group packet list size");
    return size;
  }

  @Override
  public Type<SignalEdgeGroupPacket> type() {
    return TYPE;
  }
}
