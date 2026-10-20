package com.simibubi.create.content.logistics.tableCloth;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ShopStockPacket(BlockPos cloth,List<BigItemStack> stock) implements CustomPacketPayload {
    public static final Type<ShopStockPacket> TYPE=new Type<>(Create.asResource("cloth_shop_stock"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ShopStockPacket> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,ShopStockPacket::cloth,BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(1024)),ShopStockPacket::stock,ShopStockPacket::new);
    @Override public Type<ShopStockPacket> type(){return TYPE;}
    public record Query(BlockPos cloth) implements CustomPacketPayload {
        public static final Type<Query> TYPE=new Type<>(Create.asResource("cloth_shop_stock_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC=StreamCodec.of((buf,p)->buf.writeBlockPos(p.cloth),buf->new Query(buf.readBlockPos()));
        @Override public Type<Query> type(){return TYPE;}
    }
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(Query.TYPE,Query.CODEC);PayloadTypeRegistry.clientboundPlay().register(TYPE,CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Query.TYPE,(packet,context)->{var player=context.player();if(player.isSpectator()||!player.isWithinBlockInteractionRange(packet.cloth,4)||!(LoadedLevelAccess.blockEntity(player.level(),packet.cloth) instanceof TableClothBlockEntity cloth)||!cloth.isShop())return;var ticker=cloth.getTicker();if(ticker==null)return;var summary=ticker.getRecentSummary();var seen=new HashSet<com.simibubi.create.foundation.item.ItemSampleEquality.Key>();var entries=new ArrayList<BigItemStack>();for(var item:cloth.requestData.encodedRequest().stacks())if(seen.add(com.simibubi.create.foundation.item.ItemSampleEquality.key(item.stack)))entries.add(new BigItemStack(item.stack.copyWithCount(1),summary.getCountOf(item.stack)));if(entries.size()<=1024)ServerPlayNetworking.send(player,new ShopStockPacket(packet.cloth,List.copyOf(entries)));});
    }
}
