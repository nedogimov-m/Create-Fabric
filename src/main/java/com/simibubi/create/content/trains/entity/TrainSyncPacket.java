package com.simibubi.create.content.trains.entity;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.TrainConfig;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Actual public Train descriptions only; body/cargo/passenger persistence has a separate owner. */
public record TrainSyncPacket(UUID epoch,long sequence,boolean reset,TrainConfig.Values configuration,List<Train> additions,List<UUID> removals)implements CustomPacketPayload {
    public static final Type<TrainSyncPacket> TYPE=new Type<>(Create.asResource("train_sync"));
    public TrainSyncPacket{Objects.requireNonNull(epoch);Objects.requireNonNull(configuration);additions=List.copyOf(additions);removals=List.copyOf(removals);if(sequence<0||additions.size()>64||removals.size()>4096)throw new IllegalArgumentException("Invalid native public Train frame");var ids=new HashSet<UUID>();for(var train:additions)if(!ids.add(train.id))throw new IllegalArgumentException("Duplicate native public Train");for(var id:removals)if(!ids.add(id))throw new IllegalArgumentException("Conflicting native public Train removal");}
    public static final StreamCodec<RegistryFriendlyByteBuf,TrainSyncPacket> CODEC=StreamCodec.of((buffer,value)->{buffer.writeUUID(value.epoch);buffer.writeVarLong(value.sequence);buffer.writeBoolean(value.reset);TrainConfig.Values.STREAM_CODEC.encode(buffer,value.configuration);buffer.writeVarInt(value.additions.size());for(var train:value.additions)Train.STREAM_CODEC.encode(buffer,train);buffer.writeVarInt(value.removals.size());for(var id:value.removals)buffer.writeUUID(id);},buffer->{var epoch=buffer.readUUID();long sequence=buffer.readVarLong();boolean reset=buffer.readBoolean();var config=TrainConfig.Values.STREAM_CODEC.decode(buffer);int count=bounded(buffer,64);var trains=new ArrayList<Train>();for(int i=0;i<count;i++)trains.add(Train.STREAM_CODEC.decode(buffer));count=bounded(buffer,4096);var removed=new ArrayList<UUID>();for(int i=0;i<count;i++)removed.add(buffer.readUUID());return new TrainSyncPacket(epoch,sequence,reset,config,trains,removed);});
    private static int bounded(RegistryFriendlyByteBuf buffer,int maximum){int value=buffer.readVarInt();if(value<0||value>maximum)throw new io.netty.handler.codec.DecoderException("Invalid native Train description count");return value;}
    @Override public Type<TrainSyncPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
}
