package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.Create;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ElevatorFloorListPacket(int entityId,UUID owner,List<ElevatorColumn.Floor> floors,int targetY,boolean arrived) implements CustomPacketPayload {
    public ElevatorFloorListPacket{floors=List.copyOf(floors);}
    public static final Type<ElevatorFloorListPacket> TYPE=new Type<>(Create.asResource("elevator_floor_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ElevatorFloorListPacket> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeVarInt(packet.entityId);buf.writeUUID(packet.owner);buf.writeVarInt(packet.floors.size());for(var floor:packet.floors){buf.writeInt(floor.y());buf.writeUtf(floor.shortName());buf.writeUtf(floor.longName());}buf.writeInt(packet.targetY);buf.writeBoolean(packet.arrived);},buf->{int id=buf.readVarInt();var owner=buf.readUUID();int count=buf.readVarInt();if(count<0||count>4096)throw new IllegalArgumentException("Invalid native floor list count");var floors=new ArrayList<ElevatorColumn.Floor>();for(int i=0;i<count;i++)floors.add(new ElevatorColumn.Floor(buf.readInt(),buf.readUtf(),buf.readUtf()));return new ElevatorFloorListPacket(id,owner,floors,buf.readInt(),buf.readBoolean());});
    @Override public Type<ElevatorFloorListPacket> type(){return TYPE;}
    public record RequestFloorList(int entityId,UUID owner) implements CustomPacketPayload {
        public static final Type<RequestFloorList> TYPE=new Type<>(Create.asResource("elevator_request_floors"));
        public static final StreamCodec<RegistryFriendlyByteBuf,RequestFloorList> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeVarInt(packet.entityId);buf.writeUUID(packet.owner);},buf->new RequestFloorList(buf.readVarInt(),buf.readUUID()));
        @Override public Type<RequestFloorList> type(){return TYPE;}
    }
}
