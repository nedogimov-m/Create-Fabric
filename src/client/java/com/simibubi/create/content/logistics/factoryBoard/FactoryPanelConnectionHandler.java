package com.simibubi.create.content.logistics.factoryBoard;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.phys.*;

public final class FactoryPanelConnectionHandler {
    private static FactoryPanelPosition from;
    private static boolean relocate;
    public static void start(FactoryPanelPosition position,boolean moving) { from=position;relocate=moving; }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FactoryPanelEffectPacket.TYPE,(packet,context)->{
            var panel=FactoryPanelBehaviour.at(context.client().level,packet.to());
            if(panel!=null)panel.receivePulse(packet.from(),packet.success());
        });
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!level.isClientSide()||from==null||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
            if(!player.isShiftKeyDown()){
                var origin=FactoryPanelBehaviour.at(level,from);
                if(origin!=null){
                    var state=level.getBlockState(hit.getBlockPos());var pos=hit.getBlockPos();
                    if(relocate){var local=hit.getLocation().add(new Vec3(hit.getDirection().getStepX(),hit.getDirection().getStepY(),hit.getDirection().getStepZ()).scale(1/32d));pos=net.minecraft.core.BlockPos.containing(local);var slot=FactoryPanelBlock.getTargetedSlot(pos,origin.panelBE().getBlockState(),local);ClientPlayNetworking.send(new FactoryPanelMenu.Connect(new FactoryPanelPosition(pos,slot),from,true));}
                    else if(state.getBlock() instanceof FactoryPanelBlock){var slot=FactoryPanelBlock.getTargetedSlot(pos,state,hit.getLocation());ClientPlayNetworking.send(new FactoryPanelMenu.Connect(new FactoryPanelPosition(pos,slot),from,false));}
                    else if(FactoryPanelBehaviour.linkAt(level,new FactoryPanelPosition(pos,from.slot()))!=null)ClientPlayNetworking.send(new FactoryPanelMenu.Connect(new FactoryPanelPosition(pos,from.slot()),from,false));
                }
            }
            from=null;return InteractionResult.SUCCESS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(from!=null&&(client.level==null||client.player==null||FactoryPanelBehaviour.at(client.level,from)==null||!from.pos().closerThan(client.player.blockPosition(),16)))from=null;});
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("factory_panel_connections"),(graphics,delta)->{if(from!=null){var client=Minecraft.getInstance();graphics.centeredText(client.font,Component.translatable(relocate?"create.factory_panel.click_to_relocate":"create.factory_panel.click_second_panel"),graphics.guiWidth()/2,graphics.guiHeight()-70,0xFFFBDC7D);}});
    }
    private FactoryPanelConnectionHandler() {}
}
