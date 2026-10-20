package com.simibubi.create.content.logistics.tunnel;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

public final class TunnelSettingsClient {
    private static BlockPos held;
    private static int ticks;
    public static void register(){
        TunnelSettings.CLIENT_START=pos->{if(held==null){held=pos;ticks=0;}};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(held==null)return;
            if(client.player==null||client.level==null||client.gui.screen()!=null||!client.player.mayBuild()||client.player.isSpectator()||!client.options.keyUse.isDown()||!(client.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(held)||!TunnelSettings.modeHit(hit)){held=null;return;}
            if(ticks++<5)return;var pos=held;held=null;if(client.level.getBlockEntity(pos) instanceof BrassTunnelBlockEntity tunnel&&tunnel.hasDistributionBehaviour())client.gui.setScreen(new TunnelModeScreen(pos,tunnel.getSelectionMode().ordinal()));
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,com.simibubi.create.Create.asResource("tunnel_settings"),(graphics,delta)->{
            var client=net.minecraft.client.Minecraft.getInstance();if(client.player==null||client.level==null||client.gui.screen()!=null||client.gui.hud.isHidden()||client.player.isSpectator()||!(client.hitResult instanceof BlockHitResult hit)||!TunnelSettings.modeHit(hit)||!(client.level.getBlockEntity(hit.getBlockPos()) instanceof BrassTunnelBlockEntity tunnel)||!tunnel.hasDistributionBehaviour())return;
            if(tunnel.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE)!=null)return;
            int y=graphics.guiHeight()-88;graphics.centeredText(client.font,Component.translatable(tunnel.getSelectionMode().translationKey()),graphics.guiWidth()/2,y,0xFFFBDC7D);graphics.centeredText(client.font,Component.translatable("create.gui.value_settings.hold_to_edit"),graphics.guiWidth()/2,y+12,0xFFFFFFFF);
        });
    }
    private TunnelSettingsClient(){}
}
