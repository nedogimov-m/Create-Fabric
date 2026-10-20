package com.simibubi.create.content.equipment;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EquipmentConfigPacket(EquipmentConfig.Values values)implements CustomPacketPayload {
    public static final Type<EquipmentConfigPacket> TYPE=new Type<>(Create.asResource("equipment_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EquipmentConfigPacket> CODEC=EquipmentConfig.Values.STREAM_CODEC.map(EquipmentConfigPacket::new,EquipmentConfigPacket::values);
    @Override public Type<EquipmentConfigPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
}
