package com.simibubi.create.content.kinetics.motor;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.List;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.phys.BlockHitResult;

public final class KineticSpeedSettingsClient {
    private static BlockPos heldPos;
    private static Direction heldFace;
    private static int heldTicks;
    public static void register(){
        com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient.register();
        KineticSpeedSettings.CLIENT_START=(pos,face)->{if(heldPos==null){heldPos=pos;heldFace=face;heldTicks=0;}};
        ClientTickEvents.END_CLIENT_TICK.register(KineticSpeedSettingsClient::tick);
    }
    private static void hover(Minecraft client){
        if(client.player==null||client.level==null||client.gui.screen()!=null||client.gui.hud.isHidden()||!client.player.mayBuild()||client.player.isSpectator()||client.player.isShiftKeyDown()||!(client.hitResult instanceof BlockHitResult hit))return;
        var state=client.level.getBlockState(hit.getBlockPos());if(!KineticSpeedSettings.supports(state)||!KineticSpeedSettings.testHit(state,hit)||!(client.level.getBlockEntity(hit.getBlockPos()) instanceof KineticBlockEntity kinetic))return;
        var label=kinetic instanceof CreativeMotorBlockEntity motor?motor.generatedSpeed.label.copy():GoggleTooltip.translate("kinetics.speed_controller.rotation_speed");
        ValueSettingsClient.INSTANCE.showHoverTip(List.of(label,GoggleTooltip.translate("gui.value_settings.hold_to_edit")));
    }
    private static void tick(Minecraft client){
        hover(client);
        if(heldPos==null)return;
        if(client.player==null||client.level==null||client.gui.screen()!=null||!client.options.keyUse.isDown()||!client.player.mayBuild()||client.player.isShiftKeyDown()||client.player.isSpectator()||!(client.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(heldPos)||hit.getDirection()!=heldFace||!KineticSpeedSettings.testHit(client.level.getBlockState(heldPos),hit)) {heldPos=null;return;}
        if(heldTicks++<5)return;
        var pos=heldPos;heldPos=null;if(client.level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic)client.gui.setScreen(new KineticSpeedScreen(pos,KineticSpeedSettings.value(kinetic)));
    }
    private KineticSpeedSettingsClient(){}
}
