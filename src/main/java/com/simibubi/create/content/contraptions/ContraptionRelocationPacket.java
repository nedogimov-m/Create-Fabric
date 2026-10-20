package com.simibubi.create.content.contraptions;

import com.simibubi.create.Create;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Source post-relocation ten-tick damage grace, applied to the actual tracked oriented entity. */
public record ContraptionRelocationPacket(int entityId) implements CustomPacketPayload {
    public static final Type<ContraptionRelocationPacket> TYPE=
        new Type<>(Create.asResource("contraption_relocation"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ContraptionRelocationPacket> CODEC=
        StreamCodec.of((buffer,packet)->buffer.writeInt(packet.entityId),
            buffer->new ContraptionRelocationPacket(buffer.readInt()));
    public static final StreamCodec<RegistryFriendlyByteBuf,ContraptionRelocationPacket> STREAM_CODEC=CODEC;
    @Override public Type<ContraptionRelocationPacket> type(){return TYPE;}
}
