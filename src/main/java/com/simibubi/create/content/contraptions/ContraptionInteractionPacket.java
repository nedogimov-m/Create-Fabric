package com.simibubi.create.content.contraptions;

import com.simibubi.create.Create;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

public record ContraptionInteractionPacket(int entityId, UUID entityUuid, BlockPos localPos, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<ContraptionInteractionPacket> TYPE = new Type<>(Create.asResource("contraption_interaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionInteractionPacket> CODEC = CustomPacketPayload.codec((packet, buf) -> {
        buf.writeVarInt(packet.entityId); buf.writeUUID(packet.entityUuid); buf.writeBlockPos(packet.localPos); buf.writeEnum(packet.hand);
    }, buf -> new ContraptionInteractionPacket(buf.readVarInt(), buf.readUUID(), buf.readBlockPos(), buf.readEnum(InteractionHand.class)));
    @Override public Type<ContraptionInteractionPacket> type() { return TYPE; }
}
