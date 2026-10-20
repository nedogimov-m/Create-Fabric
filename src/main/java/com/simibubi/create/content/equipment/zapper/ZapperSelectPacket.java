package com.simibubi.create.content.equipment.zapper;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
public record ZapperSelectPacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final ZapperSelectPacket INSTANCE=new ZapperSelectPacket();
    public static final Type<ZapperSelectPacket> TYPE=new Type<>(Create.asResource("zapper_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ZapperSelectPacket> STREAM_CODEC=StreamCodec.unit(INSTANCE);
    @Override public Type<ZapperSelectPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->{var player=context.player();if(player.getMainHandItem().getItem() instanceof ZapperItem)ZapperInteractionHandler.trySelect(player.getMainHandItem(),player);});}
}
