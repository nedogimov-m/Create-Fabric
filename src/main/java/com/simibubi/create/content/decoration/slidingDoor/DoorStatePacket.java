package com.simibubi.create.content.decoration.slidingDoor;

import com.simibubi.create.Create;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A bounded authoritative door update; the second half is derived from captured block state. */
public record DoorStatePacket(int entityId,UUID uuid,BlockPos pos,boolean open) implements CustomPacketPayload {
    public static final Type<DoorStatePacket> TYPE=new Type<>(Create.asResource("contraption_door"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DoorStatePacket> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeVarInt(packet.entityId);buf.writeUUID(packet.uuid);buf.writeBlockPos(packet.pos);buf.writeBoolean(packet.open);},buf->new DoorStatePacket(buf.readVarInt(),buf.readUUID(),buf.readBlockPos(),buf.readBoolean()));
    @Override public Type<DoorStatePacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
}
