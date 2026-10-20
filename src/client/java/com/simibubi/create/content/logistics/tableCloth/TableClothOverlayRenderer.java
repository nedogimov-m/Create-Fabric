package com.simibubi.create.content.logistics.tableCloth;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;

public final class TableClothOverlayRenderer {
    private static int age;
    public static void register(){
        ShoppingListItem.TOOLTIP_BAKE=list->list.bakeEntries(Minecraft.getInstance().level,null);
        ClientPlayNetworking.registerGlobalReceiver(ShopStockPacket.TYPE,(packet,context)->{if(context.client().level!=null&&context.client().level.getBlockEntity(packet.cloth()) instanceof TableClothBlockEntity cloth){var stock=new InventorySummary();for(var item:packet.stock())stock.add(item);cloth.clientStock=stock;}});
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(client.player==null||client.level==null||client.gui.screen()!=null||++age<15)return;age=0;if(client.hitResult instanceof BlockHitResult hit&&client.level.getBlockEntity(hit.getBlockPos()) instanceof TableClothBlockEntity cloth&&cloth.isShop())ClientPlayNetworking.send(new ShopStockPacket.Query(cloth.getBlockPos()));});
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("cloth_shop"),(gui,delta)->{
            var client=Minecraft.getInstance();if(client.player==null||client.level==null||client.gui.screen()!=null||client.gui.hud.isHidden()||client.player.isSpectator())return;
            var held=client.player.getMainHandItem();var list=ShoppingListItem.getList(held);var lines=new ArrayList<Component>();
            if(client.hitResult instanceof BlockHitResult hit&&client.level.getBlockEntity(hit.getBlockPos()) instanceof TableClothBlockEntity cloth&&cloth.isShop()&&!(cloth.priceTag.mayInteract(client.player)&&cloth.targetsPriceTag(hit))){
                for(var item:cloth.requestData.encodedRequest().stacks())lines.add(item.stack.getHoverName().copy().append(" x"+item.count));
                lines.add(Component.translatable("create.table_cloth.price_per_order").append(": ").append(cloth.getPaymentItem().isEmpty()?Component.translatable("create.stock_keeper.no_price_set"):cloth.getPaymentItem().getHoverName().copy().append(" x"+cloth.getPaymentAmount())));
                lines.add(Component.translatable("create.table_cloth.stock",cloth.getStockLevelForTrade(list)));if(list!=null&&list.getPurchases(cloth.getBlockPos())>0)lines.add(Component.translatable("create.table_cloth.selected",list.getPurchases(cloth.getBlockPos())));
            }else if(client.hitResult instanceof EntityHitResult hit&&list!=null&&StockTickerInteractionHandler.getStockTickerPosition(hit.getEntity())!=null){var baked=list.bakeEntries(client.level,null);if(baked.valid()){for(var item:baked.goods().getStacksByCount())lines.add(item.stack.getHoverName().copy().append(" x"+item.count));lines.add(Component.translatable("create.table_cloth.total_cost"));for(var item:baked.payments().getStacksByCount())lines.add(item.stack.getHoverName().copy().append(" x"+item.count));}}
            int y=gui.guiHeight()-72-lines.size()*11;for(var line:lines){gui.centeredText(client.font,line,gui.guiWidth()/2,y,0xFFFBDC7D);y+=11;}
        });
    }
    private TableClothOverlayRenderer(){}
}
