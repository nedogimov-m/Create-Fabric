package com.simibubi.create.content.redstone.link;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;

public final class LinkClientOverlay{
    public static void register(){HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("link_frequency"),(gui,delta)->{
        var client=Minecraft.getInstance();if(client.player==null||client.level==null||client.gui.hud.isHidden()||!(client.hitResult instanceof BlockHitResult hit))return;
        var link=BlockEntityBehaviour.get(client.level,hit.getBlockPos(),LinkBehaviour.TYPE);if(link==null)return;
        var local=hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        for(boolean first:new boolean[]{true,false})if(RedstoneLinkFrequencySlot.test(link.blockEntity.getBlockState(),first,local)){
            var label=Component.translatable(first?"create.logistics.firstFrequency":"create.logistics.secondFrequency");
            gui.text(client.font,label,gui.guiWidth()/2+12,gui.guiHeight()/2-14,0xFFFFC050,false);
            var hint=Component.translatable("create.logistics.filter."+(link.getNetworkKey().get(first).getStack().isEmpty()?"click_to_set":"click_to_replace"));
            gui.text(client.font,hint,gui.guiWidth()/2+12,gui.guiHeight()/2,0xFFE0E0E0,false);break;
        }
    });}
    private LinkClientOverlay(){}
}
