package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.Create;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ToolboxEquipPacket(BlockPos toolboxPos,UUID owner,int slot,int hotbarSlot) implements CustomPacketPayload {
    public static final Type<ToolboxEquipPacket> TYPE=new Type<>(Create.asResource("toolbox_equip"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ToolboxEquipPacket> STREAM_CODEC=StreamCodec.of((buffer,packet)->{buffer.writeBoolean(packet.toolboxPos!=null);if(packet.toolboxPos!=null){buffer.writeBlockPos(packet.toolboxPos);buffer.writeUUID(packet.owner);}buffer.writeVarInt(packet.slot);buffer.writeVarInt(packet.hotbarSlot);},buffer->{BlockPos pos=null;UUID owner=null;if(buffer.readBoolean()){pos=buffer.readBlockPos();owner=buffer.readUUID();}return new ToolboxEquipPacket(pos,owner,buffer.readVarInt(),buffer.readVarInt());});
    @Override public Type<ToolboxEquipPacket> type(){return TYPE;}
    public void handle(ServerPlayer player){ToolboxHandler.equip(player,toolboxPos,owner,slot,hotbarSlot);}
}
