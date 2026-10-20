package com.simibubi.create.content.legacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Source viewer-yaw/time colors, captured as native integer tints during item extraction. */
public record ChromaticCompoundColor(int layer) implements ItemTintSource {
    public static final MapCodec<ChromaticCompoundColor> MAP_CODEC=Codec.intRange(0,2).fieldOf("layer")
        .xmap(ChromaticCompoundColor::new,ChromaticCompoundColor::layer);
    private static final int CLOCK_PERIOD=1_728_000;
    private static ClientLevel clockLevel;
    private static int ticks;

    public static void register(){
        ItemTintSources.ID_MAPPER.put(Create.asResource("chromatic_compound"),MAP_CODEC);
        ClientTickEvents.START_CLIENT_TICK.register(client->{ensureLevel(client);if(!client.isPaused())ticks=(ticks+1)%CLOCK_PERIOD;});
        ClientPlayNetworking.registerGlobalReceiver(LegacyConfigPacket.TYPE,(packet,context)->
            AllConfigs.server().recipes.installClient(packet.settings()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->AllConfigs.server().recipes.clearClient());
    }
    private static void ensureLevel(Minecraft client){if(clockLevel!=client.level){clockLevel=client.level;ticks=0;}}
    @Override public MapCodec<ChromaticCompoundColor> type(){return MAP_CODEC;}
    @Override public int calculate(ItemStack stack,ClientLevel level,LivingEntity owner){
        var client=Minecraft.getInstance();ensureLevel(client);
        float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var viewer=client.player;float yaw=viewer==null?0:viewer.getViewYRot(partial);
        return color(layer,yaw,ticks+partial);
    }

    /** Same source phase, color pairs, frequencies and truncating ARGB channel interpolation. */
    public static int color(int layer,float viewerYaw,float renderTime){
        float progress=(float)(viewerYaw/180*Math.PI)+renderTime/10f;
        return switch(layer){
            case 0->mix(0xFF6E5773,0xFF6B3074,(Mth.sin(progress)+1)/2);
            case 1->mix(0xFFD45D79,0xFF6E5773,(Mth.sin((float)(progress+Math.PI))+1)/2);
            case 2->mix(0xFFEA9085,0xFFD45D79,(Mth.sin((float)(progress*1.5f+Math.PI))+1)/2);
            default->0;
        };
    }
    private static int mix(int from,int to,float weight){
        int a1=from>>24,r1=from>>16&255,g1=from>>8&255,b1=from&255;
        int a2=to>>24,r2=to>>16&255,g2=to>>8&255,b2=to&255;
        return ((int)(a1+(a2-a1)*weight)<<24)+((int)(r1+(r2-r1)*weight)<<16)
            +((int)(g1+(g2-g1)*weight)<<8)+(int)(b1+(b2-b1)*weight);
    }
}
