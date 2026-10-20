package com.simibubi.create.infrastructure.config;

import com.google.gson.JsonParser;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only the validated server snapshot travels to clients; no settings mutation payload exists. */
public record ConfigSyncPacket(String document) implements CustomPacketPayload {
 public static final Type<ConfigSyncPacket> TYPE=new Type<>(Create.asResource("configuration_sync"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ConfigSyncPacket> CODEC=CustomPacketPayload.codec((packet,buffer)->buffer.writeUtf(packet.document,32767),buffer->new ConfigSyncPacket(buffer.readUtf(32767)));
 public ConfigSyncPacket{if(!JsonParser.parseString(document).isJsonObject())throw new IllegalArgumentException("Expected complete native server configuration object");}
 @Override public Type<ConfigSyncPacket> type(){return TYPE;}
 public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sender.sendPacket(new ConfigSyncPacket(AllConfigs.store(ConfigScope.SERVER).serverDocument().toString())));}
}
