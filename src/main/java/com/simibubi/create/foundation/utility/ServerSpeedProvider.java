package com.simibubi.create.foundation.utility;

import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.animation.LerpedFloat;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;

/** Pinned lag compensation, driven by real server ticks and client packet arrival intervals. */
public final class ServerSpeedProvider {
 private static final LerpedFloat modifier=LerpedFloat.linear();
 private static int clientTimer,serverTimer;
 private static boolean initialized,registered;
 public static Integer getSyncInterval(){return AllConfigs.server().tickrateSyncTimer.get();}
 public static float get(){return modifier.getValue();}
 /** Client caller skips this tick while an integrated game is paused, as in the source. */
 public static void clientTick(){modifier.tickChaser();clientTimer++;}
 public static void receive(){
  if(!initialized){initialized=true;clientTimer=0;return;}
  float target=(float)getSyncInterval()/Math.max(clientTimer,1);modifier.chase(Math.min(target,1),.25,LerpedFloat.Chaser.EXP);clientTimer=-1;
 }
 public static void clearClient(){initialized=false;clientTimer=0;modifier.startWithValue(0).chase(0,0,LerpedFloat.Chaser.IDLE);}
 public static void serverTick(){AllConfigs.activeServer().ifPresent(ServerSpeedProvider::serverTick);}
 private static void serverTick(MinecraftServer server){serverTimer++;if(serverTimer>getSyncInterval()){for(var player:server.getPlayerList().getPlayers())ServerPlayNetworking.send(player,Packet.INSTANCE);serverTimer=0;}}
 public static void register(){if(registered)return;registered=true;PayloadTypeRegistry.clientboundPlay().register(Packet.TYPE,Packet.CODEC);ServerTickEvents.END_SERVER_TICK.register(ServerSpeedProvider::serverTick);ServerLifecycleEvents.SERVER_STOPPED.register(server->serverTimer=0);}
 public record Packet() implements CustomPacketPayload {
  public static final Packet INSTANCE=new Packet();
  public static final Type<Packet> TYPE=new Type<>(Create.asResource("server_speed"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Packet> CODEC=StreamCodec.unit(INSTANCE);
  @Override public Type<Packet> type(){return TYPE;}
 }
 private ServerSpeedProvider(){}
}
