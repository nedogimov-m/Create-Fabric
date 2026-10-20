package com.simibubi.create.content.redstone.diodes;

import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.phys.BlockHitResult;

public final class BrassDiodeSettingsClient {
    private static BlockPos heldPos;
    private static Direction heldFace;
    private static int heldTicks;
    public static void register(){
        BrassDiodeSettings.CLIENT_START=(pos,face)->{if(heldPos==null){heldPos=pos;heldFace=face;heldTicks=0;}};
        ClientTickEvents.END_CLIENT_TICK.register(BrassDiodeSettingsClient::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Create.asResource("diode_settings"),(graphics,delta)->{
            var client=Minecraft.getInstance();if(client.player==null||client.level==null||client.gui.screen()!=null||client.gui.hud.isHidden()||client.player.isSpectator()||!client.player.mayBuild()||client.player.isShiftKeyDown()||!(client.hitResult instanceof BlockHitResult hit)||!BrassDiodeSettings.testHit(LoadedLevelAccess.blockState(client.level,hit.getBlockPos()),hit)||!(LoadedLevelAccess.blockEntity(client.level,hit.getBlockPos())instanceof BrassDiodeBlockEntity diode))return;
            int y=graphics.guiHeight()-88;graphics.centeredText(client.font,diode.format(diode.getMaxState()),graphics.guiWidth()/2,y,0xFFFBDC7D);graphics.centeredText(client.font,GoggleTooltip.translate("gui.value_settings.hold_to_edit"),graphics.guiWidth()/2,y+12,0xFFFFFFFF);
        });
    }
    private static void tick(Minecraft client){
        if(heldPos==null)return;
        if(client.player==null||client.level==null||client.gui.screen()!=null||client.player.isSpectator()||!client.player.mayBuild()||client.player.isShiftKeyDown()||!(client.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(heldPos)||hit.getDirection()!=heldFace||!BrassDiodeSettings.testHit(LoadedLevelAccess.blockState(client.level,heldPos),hit)){heldPos=null;return;}
        if(!client.options.keyUse.isDown()){var pos=heldPos;heldPos=null;ClientPlayNetworking.send(new BrassDiodeSettings.Toggle(pos));return;}
        if(heldTicks++<5)return;
        var pos=heldPos;heldPos=null;if(LoadedLevelAccess.blockEntity(client.level,pos)instanceof BrassDiodeBlockEntity diode)client.gui.setScreen(new BrassDiodeScreen(pos,diode.getMaxState()));
    }
    private BrassDiodeSettingsClient(){}
}
