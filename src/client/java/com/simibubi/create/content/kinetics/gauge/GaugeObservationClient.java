package com.simibubi.create.content.kinetics.gauge;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
/** Actual native goggles-HUD producer: source reading deduplication plus bounded retry until real server acceptance. */
public final class GaugeObservationClient {
 private record Reading(StressGaugeBlockEntity gauge,float target,double capacity,double impact,float speed,UUID owner){}
 private static Reading pending;
 private static long counter,pendingNonce,lastRequest=Long.MIN_VALUE;
 private static boolean acknowledged;
 public static void register(){ClientPlayNetworking.registerGlobalReceiver(GaugeObservationNetworking.Acknowledged.TYPE,(packet,context)->{var client=context.client();if(pending!=null&&packet.nonce()==pendingNonce&&packet.pos().equals(pending.gauge.getBlockPos())&&client.level==pending.gauge.getLevel()&&client.level.dimension().identifier().equals(packet.dimension()))acknowledged=true;});ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{if(pending!=null&&(pending.gauge.getLevel()!=client.level||pending.gauge.isRemoved()))reset();});}
 public static void observe(BlockPos pos){var client=Minecraft.getInstance();if(client.level==null||client.player==null||!client.player.mayBuild()||!GogglesItem.isWearingGoggles(client.player)||!(LoadedLevelAccess.blockEntity(client.level,pos) instanceof StressGaugeBlockEntity gauge))return;var reading=new Reading(gauge,gauge.dialTarget,gauge.getNetworkCapacity(),gauge.getNetworkStress(),gauge.getTheoreticalSpeed(),gauge.advancementOwner());if(!reading.equals(pending)){pending=reading;pendingNonce=++counter;lastRequest=Long.MIN_VALUE;acknowledged=false;}if(acknowledged)return;long now=client.level.getGameTime();if(lastRequest!=Long.MIN_VALUE&&now>=lastRequest&&now-lastRequest<2)return;lastRequest=now;ClientPlayNetworking.send(new GaugeObservedPacket(pos,pendingNonce));}
 public static boolean acknowledged(BlockPos pos){return pending!=null&&pending.gauge.getBlockPos().equals(pos)&&acknowledged;}
 private static void reset(){pending=null;lastRequest=Long.MIN_VALUE;acknowledged=false;}
 private GaugeObservationClient(){}
}
