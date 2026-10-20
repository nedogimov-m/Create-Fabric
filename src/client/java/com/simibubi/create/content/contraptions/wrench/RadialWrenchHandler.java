package com.simibubi.create.content.contraptions.wrench;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.*;
import net.minecraft.world.phys.*;

/** Original opt-in ROTATE_MENU binding; Fabric's native input supports keyboard and mouse bindings. */
public final class RadialWrenchHandler {
    public static final KeyMapping ROTATE_MENU=KeyMappingHelper.registerKeyMapping(new KeyMapping("create.keyinfo.rotate_menu",InputConstants.UNKNOWN.getValue(),KeyMapping.Category.INVENTORY));
    public static int COOLDOWN;
    private static void open(Minecraft client){
        if(!ROTATE_MENU.isDown()||COOLDOWN>0||client.gameMode==null||client.player==null||client.level==null||client.player.isSpectator()||client.gui.screen()!=null||!client.player.getMainHandItem().is(AllItems.WRENCH)
                ||!(client.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||!LoadedLevelAccess.isLoaded(client.level,hit.getBlockPos()))return;
        RadialWrenchMenu.tryCreateFor(LoadedLevelAccess.blockState(client.level,hit.getBlockPos()),hit.getBlockPos(),client.level).ifPresent(client.gui::setScreen);
    }
    public static void register(){
        RadialWrenchPortrait.register();
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(COOLDOWN>0&&!ROTATE_MENU.isDown())COOLDOWN--;while(ROTATE_MENU.consumeClick())open(client);});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->COOLDOWN=0);
    }
    private RadialWrenchHandler(){}
}
