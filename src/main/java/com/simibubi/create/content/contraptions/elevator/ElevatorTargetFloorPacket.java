package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.Create;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Native id alone cannot authorize a recycled/reloaded cabin. */
public record ElevatorTargetFloorPacket(int entityId,UUID owner,int targetY) implements CustomPacketPayload {
    public static final Type<ElevatorTargetFloorPacket> TYPE=new Type<>(Create.asResource("elevator_target_floor"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ElevatorTargetFloorPacket> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeVarInt(packet.entityId);buf.writeUUID(packet.owner);buf.writeInt(packet.targetY);},buf->new ElevatorTargetFloorPacket(buf.readVarInt(),buf.readUUID(),buf.readInt()));
    @Override public Type<ElevatorTargetFloorPacket> type(){return TYPE;}
}
