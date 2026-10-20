package com.simibubi.create.content.contraptions.minecart.capability;

import com.simibubi.create.Create;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Real tracking identity plus public topology/pose; no paid receipt, mounted cargo or private item data. */
public record MinecartControllerUpdatePacket(int entityId,UUID cartId,CompoundTag data) implements CustomPacketPayload {
    public static final Type<MinecartControllerUpdatePacket> TYPE=new Type<>(Create.asResource("minecart_controller"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MinecartControllerUpdatePacket> CODEC=CustomPacketPayload.codec((packet,buffer)->{buffer.writeVarInt(packet.entityId);buffer.writeUUID(packet.cartId);ByteBufCodecs.COMPOUND_TAG.encode(buffer,packet.data);},buffer->new MinecartControllerUpdatePacket(buffer.readVarInt(),buffer.readUUID(),ByteBufCodecs.COMPOUND_TAG.decode(buffer)));
    public MinecartControllerUpdatePacket{MinecartController.rejectPrivateData(data);data=data.copy();}
    @Override public Type<MinecartControllerUpdatePacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);}
    public static void send(MinecartController controller){var cart=controller.cart();if(cart==null||cart.level().isClientSide())return;var packet=new MinecartControllerUpdatePacket(cart.getId(),cart.getUUID(),controller.publicData());for(var player:PlayerLookup.tracking(cart))ServerPlayNetworking.send(player,packet);}
    public static void send(MinecartController controller,ServerPlayer player){var cart=controller.cart();if(cart!=null)ServerPlayNetworking.send(player,new MinecartControllerUpdatePacket(cart.getId(),cart.getUUID(),controller.publicData()));}
}
