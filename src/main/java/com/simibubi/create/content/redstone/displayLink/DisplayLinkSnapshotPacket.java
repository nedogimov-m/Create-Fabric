package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Actual menu availability/capacity updates, scoped to its native container identity. */
public record DisplayLinkSnapshotPacket(int containerId,DisplayLinkMenu.Opening opening)implements CustomPacketPayload {
    public static final Type<DisplayLinkSnapshotPacket> TYPE=new Type<>(Create.asResource("display_link_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DisplayLinkSnapshotPacket> STREAM_CODEC=StreamCodec.of((buffer,packet)->{buffer.writeContainerId(packet.containerId);DisplayLinkMenu.Opening.CODEC.encode(buffer,packet.opening);},buffer->new DisplayLinkSnapshotPacket(buffer.readContainerId(),DisplayLinkMenu.Opening.CODEC.decode(buffer)));
    @Override public Type<DisplayLinkSnapshotPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,STREAM_CODEC);}
}
