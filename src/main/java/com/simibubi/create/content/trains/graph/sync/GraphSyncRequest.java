package com.simibubi.create.content.trains.graph.sync;

import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Read-only login/resynchronization request. It cannot create topology or chunk tickets. */
public record GraphSyncRequest(UUID nonce) implements CustomPacketPayload {
  public GraphSyncRequest {
    Objects.requireNonNull(nonce);
  }

  public static final Type<GraphSyncRequest> TYPE =
      new Type<>(Create.asResource("rail_graph_snapshot_request"));
  public static final StreamCodec<RegistryFriendlyByteBuf, GraphSyncRequest> CODEC =
      CustomPacketPayload.codec(
          (packet, buf) -> buf.writeUUID(packet.nonce),
          buf -> new GraphSyncRequest(buf.readUUID()));

  @Override
  public Type<GraphSyncRequest> type() {
    return TYPE;
  }
}
