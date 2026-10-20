package com.simibubi.create.content.logistics.stockTicker;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public final class StockTickerPaymentInspection {
    public record Query(BlockPos pos) implements CustomPacketPayload{
        public static final Type<Query> TYPE=new Type<>(Create.asResource("stock_ticker_payment_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC=StreamCodec.of((buf,p)->buf.writeBlockPos(p.pos),buf->new Query(buf.readBlockPos()));
        @Override public Type<Query> type(){return TYPE;}
    }
    public record Permission(BlockPos pos,UUID frequency,boolean administrator) implements CustomPacketPayload{
        public static final Type<Permission> TYPE=new Type<>(Create.asResource("stock_ticker_payment_permission"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Permission> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Permission::pos,UUIDUtil.STREAM_CODEC,Permission::frequency,ByteBufCodecs.BOOL,Permission::administrator,Permission::new);
        @Override public Type<Permission> type(){return TYPE;}
    }
    public static Permission query(Player player,Query packet){
        var level=player.level();if(level.isClientSide()||player.isSpectator()||!player.isWithinBlockInteractionRange(packet.pos,1)||!level.mayInteract(player,packet.pos)||!(LoadedLevelAccess.blockEntity(level,packet.pos) instanceof StockTickerBlockEntity ticker))return null;
        return new Permission(packet.pos,ticker.behaviour.freqId,ticker.behaviour.mayAdministrate(player));
    }
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(Query.TYPE,Query.CODEC);PayloadTypeRegistry.clientboundPlay().register(Permission.TYPE,Permission.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Query.TYPE,(packet,context)->{var response=query(context.player(),packet);if(response!=null)ServerPlayNetworking.send(context.player(),response);});
    }
    private StockTickerPaymentInspection(){}
}
