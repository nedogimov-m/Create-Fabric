package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Source four/ninety character edits; server validates actual loaded BE and native interaction distance. */
public record ElevatorContactEditPacket(BlockPos pos,String shortName,String longName,DoorControl doorMode) implements CustomPacketPayload {
    public ElevatorContactEditPacket{pos=pos.immutable();if(shortName.length()>4||longName.length()>90)throw new IllegalArgumentException("Elevator floor label exceeds source bounds");}
    public static final Type<ElevatorContactEditPacket> TYPE=new Type<>(Create.asResource("elevator_contact_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ElevatorContactEditPacket> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeBlockPos(packet.pos);buf.writeUtf(packet.shortName,4);buf.writeUtf(packet.longName,90);buf.writeEnum(packet.doorMode);},buf->new ElevatorContactEditPacket(buf.readBlockPos(),buf.readUtf(4),buf.readUtf(90),buf.readEnum(DoorControl.class)));
    @Override public Type<ElevatorContactEditPacket> type(){return TYPE;}
    public record Open(BlockPos pos,String shortName,String longName,DoorControl doorMode,String currentFloor) implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(Create.asResource("elevator_contact_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeBlockPos(packet.pos);buf.writeUtf(packet.shortName);buf.writeUtf(packet.longName);buf.writeEnum(packet.doorMode);buf.writeUtf(packet.currentFloor);},buf->new Open(buf.readBlockPos(),buf.readUtf(),buf.readUtf(),buf.readEnum(DoorControl.class),buf.readUtf()));
        @Override public Type<Open> type(){return TYPE;}
    }
}
