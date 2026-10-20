package com.simibubi.create.foundation.blockEntity.behaviour;

import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public final class FilteringSettingsClient {
    private static BlockPos heldPos;
    private static Direction heldFace;
    private static InteractionHand heldHand;
    private static int heldTicks;
    public static void register(){
        FilteringSettings.CLIENT_START=(pos,face,hand)->{if(heldPos==null){heldPos=pos;heldFace=face;heldHand=hand;heldTicks=0;}};
        ClientTickEvents.END_CLIENT_TICK.register(FilteringSettingsClient::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,com.simibubi.create.Create.asResource("filter_settings"),(graphics,delta)->{
            var client=Minecraft.getInstance();if(client.player==null||client.level==null||client.gui.screen()!=null||client.gui.hud.isHidden()||!client.player.mayBuild()||client.player.isSpectator()||!(client.hitResult instanceof BlockHitResult hit)||!FilteringSettings.testHit(client.level,client.level.getBlockState(hit.getBlockPos()),hit.getBlockPos(),hit))return;
            if(com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer.coversNativeHud(client,hit))return;
            var filtering=FilteringSettings.resolve(client.level,hit.getBlockPos());if(filtering==null||!filtering.mayInteract(client.player))return;int y=graphics.guiHeight()-88;
            var block=client.level.getBlockState(hit.getBlockPos()).getBlock();
            var text=(block instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlock?Component.translatable("create.table_cloth.price_per_order"):block instanceof com.simibubi.create.content.logistics.crate.CreativeCrateBlock?Component.translatable("create.logistics.creative_crate.supply"):filtering.getLabel()).copy();if(filtering.isCountVisible())text.append(": ").append(filtering.upTo&&filtering.count==filtering.getMaxStackSize()?Component.translatable("create.logistics.filter.any_amount_short"):Component.literal((filtering.upTo?"\u2264":"=")+filtering.count));
            graphics.centeredText(client.font,text,graphics.guiWidth()/2,y,0xFFFBDC7D);
            graphics.centeredText(client.font,Component.translatable(filtering.isCountVisible()?"create.logistics.filter.hold_to_set_amount":filtering.getFilter().isEmpty()?"create.logistics.filter.click_to_set":"create.logistics.filter.click_to_replace"),graphics.guiWidth()/2,y+12,0xFFFFFFFF);
        });
    }
    private static void tick(Minecraft client){
        if(heldPos==null)return;
        if(client.player==null||client.level==null||client.gui.screen()!=null||!client.player.mayBuild()||client.player.isSpectator()||!(client.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(heldPos)||hit.getDirection()!=heldFace||!FilteringSettings.testHit(client.level,client.level.getBlockState(heldPos),heldPos,hit)){heldPos=null;return;}
        if(!client.options.keyUse.isDown()){
            ClientPlayNetworking.send(new FilteringSettings.SetFilter(heldPos,heldFace,heldHand,0,true));heldPos=null;return;
        }
        if(heldTicks++<5)return;
        var pos=heldPos;heldPos=null;var filtering=FilteringSettings.resolve(client.level,pos);if(filtering!=null&&filtering.isCountVisible())client.gui.setScreen(new FilterAmountScreen(pos,heldFace,heldHand,filtering.count,filtering.upTo,filtering.getMaxStackSize()));
    }
    private FilteringSettingsClient(){}
}
