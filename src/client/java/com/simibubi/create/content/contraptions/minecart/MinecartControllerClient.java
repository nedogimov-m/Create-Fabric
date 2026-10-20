package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.content.contraptions.minecart.capability.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/** Public topology applies only to the actual native client cart matching both id and UUID. */
public final class MinecartControllerClient {
    private record Pending(MinecartControllerUpdatePacket packet,long expires){}
    private static final Map<UUID,Pending> WAITING=new LinkedHashMap<>();private static ClientLevel level;private static long ticks;
    private static void world(ClientLevel actual){if(level==actual)return;if(level!=null)CapabilityMinecartController.clear(level);WAITING.clear();level=actual;ticks=0;}
    private static boolean apply(MinecartControllerUpdatePacket packet){
        if(level==null||!(level.getEntity(packet.entityId()) instanceof AbstractMinecart cart)||!cart.getUUID().equals(packet.cartId()))return false;
        MinecartController.get(cart).applyPublicData(packet.data());CapabilityMinecartController.loaded(cart);return true;
    }
    public static void register(){
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->world(null));
        ClientEntityEvents.ENTITY_LOAD.register((entity,actual)->{world(actual);if(entity instanceof AbstractMinecart cart){CapabilityMinecartController.loaded(cart);var pending=WAITING.remove(cart.getUUID());if(pending!=null&&pending.expires>=ticks)apply(pending.packet);}});
        ClientEntityEvents.ENTITY_UNLOAD.register((entity,actual)->{if(entity instanceof AbstractMinecart cart)CapabilityMinecartController.unloaded(cart);});
        ClientPlayNetworking.registerGlobalReceiver(MinecartControllerUpdatePacket.TYPE,(packet,context)->{world(context.client().level);if(level==null||apply(packet))return;if(WAITING.size()>=1024)WAITING.remove(WAITING.keySet().iterator().next());WAITING.put(packet.cartId(),new Pending(packet,ticks+200));});
        ClientTickEvents.END_CLIENT_TICK.register(client->{world(client.level);ticks++;WAITING.values().removeIf(pending->pending.expires<ticks);});
    }
    private MinecartControllerClient(){}
}
