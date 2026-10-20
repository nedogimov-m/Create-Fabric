package com.simibubi.create.content.logistics.stockTicker;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.phys.BlockHitResult;

public final class StockTickerPaymentInspectionClient {
    private static int ticks;
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(StockTickerPaymentInspection.Permission.TYPE,(packet,context)->{var level=context.client().level;if(level!=null&&level.getBlockEntity(packet.pos()) instanceof StockTickerBlockEntity ticker&&ticker.behaviour.freqId.equals(packet.frequency()))ticker.receivePaymentPermission(context.player().getUUID(),packet.frequency(),packet.administrator());});
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(++ticks<10)return;ticks=0;if(client.player==null||client.level==null||client.gui.screen()!=null||client.player.isSpectator()||!(client.hitResult instanceof BlockHitResult hit)||!(client.level.getBlockEntity(hit.getBlockPos()) instanceof StockTickerBlockEntity ticker)||!ticker.hasPayments())return;ClientPlayNetworking.send(new StockTickerPaymentInspection.Query(hit.getBlockPos()));});
    }
    private StockTickerPaymentInspectionClient(){}
}
