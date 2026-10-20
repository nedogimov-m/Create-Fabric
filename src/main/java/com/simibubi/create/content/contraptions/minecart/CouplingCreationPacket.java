package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.Create;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/** Actual native click pair, checked against both real server interactions and native UUID identity. */
public record CouplingCreationPacket(int firstId,UUID firstUuid,int secondId,UUID secondUuid) implements CustomPacketPayload {
    public static final Type<CouplingCreationPacket> TYPE=new Type<>(Create.asResource("minecart_coupling_creation"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CouplingCreationPacket> CODEC=StreamCodec.of((buffer,packet)->{buffer.writeVarInt(packet.firstId);buffer.writeUUID(packet.firstUuid);buffer.writeVarInt(packet.secondId);buffer.writeUUID(packet.secondUuid);},buffer->new CouplingCreationPacket(buffer.readVarInt(),buffer.readUUID(),buffer.readVarInt(),buffer.readUUID()));
    public CouplingCreationPacket(AbstractMinecart first,AbstractMinecart second){this(first.getId(),first.getUUID(),second.getId(),second.getUUID());}
    @Override public Type<CouplingCreationPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->context.server().execute(()->MinecartCouplingItem.acceptCreation(context.player(),packet)));}
}
