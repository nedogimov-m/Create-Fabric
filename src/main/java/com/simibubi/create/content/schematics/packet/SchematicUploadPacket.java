package com.simibubi.create.content.schematics.packet;

import com.simibubi.create.Create;
import com.simibubi.create.content.schematics.ServerSchematicLoader;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Bounded ordered native upload transport; all mutations authenticate the actual live server menu. */
public record SchematicUploadPacket(Action action,int menu,UUID token,String file,long offset,BlockPos table,byte[] data) implements CustomPacketPayload {
    public enum Action {BEGIN,WRITE,FINISH,CANCEL}
    public SchematicUploadPacket{Objects.requireNonNull(action);Objects.requireNonNull(token);Objects.requireNonNull(file);table=table.immutable();data=data.clone();if(file.length()>128||data.length>32767||offset<0||action!=Action.WRITE&&data.length!=0)throw new IllegalArgumentException("Invalid native schematic upload frame");}
    @Override public byte[] data(){return data.clone();}
    public static final Type<SchematicUploadPacket> TYPE=new Type<>(Create.asResource("schematic_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SchematicUploadPacket> CODEC=StreamCodec.of((buffer,value)->{buffer.writeEnum(value.action);buffer.writeVarInt(value.menu);buffer.writeUUID(value.token);buffer.writeUtf(value.file,128);buffer.writeVarLong(value.offset);buffer.writeBlockPos(value.table);buffer.writeByteArray(value.data);},buffer->new SchematicUploadPacket(buffer.readEnum(Action.class),buffer.readVarInt(),buffer.readUUID(),buffer.readUtf(128),buffer.readVarLong(),buffer.readBlockPos(),buffer.readByteArray(32767)));
    public static SchematicUploadPacket begin(int menu,UUID token,String file,long size,BlockPos table){return new SchematicUploadPacket(Action.BEGIN,menu,token,file,size,table,new byte[0]);}
    public static SchematicUploadPacket write(int menu,UUID token,long offset,byte[] bytes){return new SchematicUploadPacket(Action.WRITE,menu,token,"",offset,BlockPos.ZERO,bytes);}
    public static SchematicUploadPacket finish(int menu,UUID token){return new SchematicUploadPacket(Action.FINISH,menu,token,"",0,BlockPos.ZERO,new byte[0]);}
    public static SchematicUploadPacket cancel(int menu,UUID token){return new SchematicUploadPacket(Action.CANCEL,menu,token,"",0,BlockPos.ZERO,new byte[0]);}
    public boolean handle(ServerPlayer player){
        var loader=ServerSchematicLoader.get(player.level().getServer());if(action==Action.CANCEL)return loader.cancelForMenu(player,menu,token);if(player.containerMenu.containerId!=menu)return false;
        return switch(action){case BEGIN->loader.handleNewUpload(player,menu,token,file,offset,table);case WRITE->loader.handleWriteRequest(player,token,offset,data);case FINISH->loader.handleFinishedUpload(player,token);case CANCEL->throw new AssertionError("Native cancel already handled");};
    }
    @Override public Type<SchematicUploadPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,CODEC);PayloadTypeRegistry.clientboundPlay().register(SchematicUploadStatusPacket.TYPE,SchematicUploadStatusPacket.CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.handle(context.player()));}
}
