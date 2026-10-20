package com.simibubi.create.infrastructure.config;

import com.google.gson.JsonParser;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.goggles.GoggleOverlaySettings;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Native client lifecycle for the authoritative scope store and server-only snapshots. */
public final class ClientConfigHooks {
    public static void register(){
        AllConfigs.CLIENT_APPLY=GoggleOverlaySettings.INSTANCE::apply;
        GoggleOverlaySettings.INSTANCE.apply(AllConfigs.client());
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPacket.TYPE,(packet,context)->{
            try{AllConfigs.installRemoteServer(JsonParser.parseString(packet.document()).getAsJsonObject());}
            catch(IllegalArgumentException failure){Create.LOGGER.warn("Rejected invalid Create server configuration snapshot",failure);}
        });
        ClientPlayNetworking.registerGlobalReceiver(ServerSpeedProvider.Packet.TYPE,(packet,context)->ServerSpeedProvider.receive());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.level!=null&&!(client.hasSingleplayerServer()&&client.isPaused()))ServerSpeedProvider.clientTick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{
            AllConfigs.clearRemoteServer();ServerSpeedProvider.clearClient();
        });
    }
    private ClientConfigHooks(){}
}
