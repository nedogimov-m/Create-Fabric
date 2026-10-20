package com.simibubi.create.content.contraptions.minecart;

import com.simibubi.create.AllItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.*;

/** Pinned two-click particles/selection, carrying both actual native UUIDs to the server gesture. */
public final class CouplingHandlerClient {
    private static AbstractMinecart selectedCart;
    public static AbstractMinecart selectedCart(){return selectedCart;}
    private static void particles(AbstractMinecart cart,boolean highlight){
        var level=Minecraft.getInstance().level;if(level==null)return;var center=cart.getBoundingBox().getCenter();var random=level.getRandom();var type=highlight?ParticleTypes.END_ROD:new DustParticleOptions(0xFFFFFF,1);
        for(int index=0;index<(highlight?100:2);index++){var offset=new Vec3((random.nextDouble()-.5)*2,(random.nextDouble()-.5)*2,(random.nextDouble()-.5)*2);var point=offset.multiply(1,0,1).normalize().add(0,offset.y/8,0).add(center);level.addParticle(type,point.x,point.y,point.z,0,0,0);}
    }
    public static void onCartClicked(Player player,AbstractMinecart actual){
        if(Minecraft.getInstance().player!=player)return;
        if(selectedCart==null||selectedCart==actual){selectedCart=actual;particles(actual,true);return;}
        particles(actual,true);ClientPlayNetworking.send(new CouplingCreationPacket(selectedCart,actual));selectedCart=null;
    }
    public static void sneakClick(){selectedCart=null;}
    public static void register(){
        MinecartCouplingItem.setClientClickHandler(CouplingHandlerClient::onCartClicked);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->selectedCart=null);
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(selectedCart==null)return;if(client.player==null||client.level!=selectedCart.level()||selectedCart.isRemoved()||!client.player.getMainHandItem().is(AllItems.MINECART_COUPLING)&&!client.player.getOffhandItem().is(AllItems.MINECART_COUPLING)){selectedCart=null;return;}particles(selectedCart,false);});
    }
    private CouplingHandlerClient(){}
}
